package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Query-time rule engine to convert technical audit events to regulator-friendly text.
 * Phase 1: extract only from event fields + path variables (no request/response body persisted at this stage).
 */
@Component
public class OperationMappingEngine {

    private static final Logger log = LoggerFactory.getLogger(OperationMappingEngine.class);

    public static final class ResolvedOperation {
        public Long ruleId;               // matched rule id (nullable for fallback)
        public String moduleName;         // e.g., 用户管理/角色管理/数据资产管理
        public String actionType;         // 查询/新增/修改/删除/登录/登出/部分更新/执行
        public String description;        // 操作内容
        public String sourceTable;        // 源表（中文）
        public String eventClass;         // SecurityEvent/AuditEvent
    }

    private static final class CompiledRule {
        final AuditOperationMapping raw;
        final PathPattern pattern;
        final Pattern statusPattern;
        CompiledRule(AuditOperationMapping raw, PathPattern pattern, Pattern statusPattern) {
            this.raw = raw;
            this.pattern = pattern;
            this.statusPattern = statusPattern;
        }
    }

    private final AuditOperationMappingRepository repo;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PathPatternParser parser = new PathPatternParser();
    private volatile List<CompiledRule> rules = List.of();
    private final Map<String, PathPattern> patternCache = new ConcurrentHashMap<>();

    public OperationMappingEngine(AuditOperationMappingRepository repo, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (mappingTableExists()) {
            reload();
        } else {
            log.debug("audit_operation_mapping table not ready at startup; waiting for Liquibase");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onLiquibaseReady() {
        if (!mappingTableExists()) {
            log.debug("audit_operation_mapping table still absent when Liquibase event triggered; will retry later");
            return;
        }
        reload();
    }

    @Scheduled(fixedDelay = 30000L)
    public void reload() {
        if (!mappingTableExists()) {
            log.debug("audit_operation_mapping table not available yet; skipping reload");
            return;
        }
        try {
            List<AuditOperationMapping> enabled = repo.findAllByEnabledTrueOrderByOrderValueAscIdAsc();
            List<CompiledRule> compiled = new ArrayList<>(enabled.size());
            for (AuditOperationMapping r : enabled) {
                String pat = normalizePattern(r.getUrlPattern());
                PathPattern pp = patternCache.computeIfAbsent(pat, p -> parser.parse(p));
                Pattern statusPattern = compileStatusPattern(r);
                compiled.add(new CompiledRule(r, pp, statusPattern));
            }
            this.rules = compiled;
            log.info("Loaded {} audit operation mappings", compiled.size());
        } catch (DataAccessException ex) {
            log.debug("audit_operation_mapping unavailable during reload ({}); will retry", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to reload audit operation mappings", ex);
        }
    }

    public Optional<ResolvedOperation> resolve(AuditEvent event) {
        if (event == null || !StringUtils.hasText(event.getRequestUri())) return Optional.empty();
        String uri = event.getRequestUri();
        String method = (event.getHttpMethod() == null ? "" : event.getHttpMethod().toUpperCase(Locale.ROOT));
        Map<String, Object> details = parseDetails(event.getDetails());
        String statusCode = extractStatusCode(details, event);
        CompiledRule best = null;
        PathPattern.PathMatchInfo bestInfo = null;
        for (CompiledRule r : rules) {
            if (!methodMatches(method, r.raw.getHttpMethod())) continue;
            if (!statusMatches(statusCode, r.statusPattern)) continue;
            PathPattern.PathMatchInfo info = r.pattern.matchAndExtract(PathContainer.parsePath(uri));
            if (info == null) continue;
            if (best == null) {
                best = r; bestInfo = info; continue;
            }
            // Prefer more specific by comparing pattern specificity
            // PathPattern implements Comparable (more specific -> greater?) not directly; use length heuristics
            best = moreSpecific(best, r); // simplified heuristic
            if (best == r) bestInfo = info;
        }
        if (best == null) return Optional.empty();
        return Optional.of(render(best, bestInfo, event, details));
    }

    private boolean methodMatches(String req, String rule) {
        if (!StringUtils.hasText(rule) || "ALL".equalsIgnoreCase(rule)) return true;
        if (!StringUtils.hasText(req)) return false;
        if ("HEAD".equalsIgnoreCase(req) || "OPTIONS".equalsIgnoreCase(req)) return false; // ignore
        return req.equalsIgnoreCase(rule);
    }

    private boolean statusMatches(String statusCode, Pattern pattern) {
        if (pattern == null) return true;
        if (!StringUtils.hasText(statusCode)) return false;
        return pattern.matcher(statusCode.trim()).matches();
    }

    private String extractStatusCode(Map<String, Object> details, AuditEvent event) {
        Object status = null;
        if (details != null && !details.isEmpty()) {
            status = details.get("status_code");
            if (status == null) status = details.get("状态码");
            if (status == null) status = details.get("status");
        }
        if (status != null && StringUtils.hasText(String.valueOf(status))) {
            return String.valueOf(status).trim();
        }
        String result = (event == null) ? null : event.getResult();
        return StringUtils.hasText(result) ? result.trim() : null;
    }

    private CompiledRule moreSpecific(CompiledRule a, CompiledRule b) {
        // Heuristic: fewer variables, fewer wildcards, longer literal length
        int av = countVars(a.raw.getUrlPattern());
        int bv = countVars(b.raw.getUrlPattern());
        if (av != bv) return av < bv ? a : b;
        int aw = countWildcards(a.raw.getUrlPattern());
        int bw = countWildcards(b.raw.getUrlPattern());
        if (aw != bw) return aw < bw ? a : b;
        int al = literalLength(a.raw.getUrlPattern());
        int bl = literalLength(b.raw.getUrlPattern());
        if (al != bl) return al > bl ? a : b;
        int ao = a.raw.getOrderValue() == null ? 0 : a.raw.getOrderValue();
        int bo = b.raw.getOrderValue() == null ? 0 : b.raw.getOrderValue();
        return ao <= bo ? a : b;
    }

    private int countVars(String p) { return StringUtils.countOccurrencesOf(p == null ? "" : p, "{"); }
    private int countWildcards(String p) {
        if (p == null) return 0; int c = 0; for (char ch : p.toCharArray()) { if (ch == '*') c++; } return c; }
    private int literalLength(String p) {
        if (p == null) return 0; int n = 0; boolean inVar = false; for (char ch : p.toCharArray()) { if (ch == '{') inVar = true; else if (ch == '}') inVar = false; else if (!inVar && ch != '*') n++; } return n; }
    private String normalizePattern(String p) { return (p == null || p.isBlank()) ? "/**" : (p.startsWith("/") ? p : "/" + p); }

    private Pattern compileStatusPattern(AuditOperationMapping mapping) {
        String regex = mapping.getStatusCodeRegex();
        if (!StringUtils.hasText(regex)) return null;
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            log.warn("Ignoring invalid status_code_regex '{}' for audit mapping id={}: {}", regex, mapping.getId(), ex.getMessage());
            return null;
        }
    }

    private boolean mappingTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND LOWER(table_name) = ?",
                Integer.class,
                "audit_operation_mapping"
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private ResolvedOperation render(CompiledRule rule, PathPattern.PathMatchInfo info, AuditEvent event, Map<String, Object> details) {
        Map<String, Object> vars = new HashMap<>();
        // path variables from pattern
        if (info != null && info.getUriVariables() != null) {
            vars.putAll(info.getUriVariables());
        }
        // event.* values
        putIfNotBlank(vars, "event.requestUri", event.getRequestUri());
        putIfNotBlank(vars, "event.httpMethod", event.getHttpMethod());
        putIfNotBlank(vars, "event.actor", event.getActor());
        putIfNotBlank(vars, "event.module", event.getModule());
        putIfNotBlank(vars, "event.resourceType", event.getResourceType());
        putIfNotBlank(vars, "event.resourceId", event.getResourceId());
        putIfNotBlank(vars, "event.result", event.getResult());
        putIfNotBlank(vars, "event.clientIp", event.getClientIp());

        Map<String, Object> safeDetails = (details == null) ? Collections.emptyMap() : details;
        for (Map.Entry<String, Object> e : safeDetails.entrySet()) {
            vars.put("details." + e.getKey(), String.valueOf(e.getValue()));
        }
        String statusCode = extractStatusCode(safeDetails, event);
        if (StringUtils.hasText(statusCode)) {
            vars.put("event.statusCode", statusCode);
        }
        // also expose Chinese keys directly for convenience
        if (safeDetails.containsKey("源表")) vars.put("源表", safeDetails.get("源表"));
        if (safeDetails.containsKey("目标ID")) vars.put("目标ID", safeDetails.get("目标ID"));
        if (safeDetails.containsKey("目标引用")) vars.put("目标引用", safeDetails.get("目标引用"));

        // paramExtractors JSON
        Map<String, String> extractors = parseExtractors(rule.raw.getParamExtractors());
        Map<String, Object> extracted = extractVars(extractors, vars);

        ResolvedOperation out = new ResolvedOperation();
        out.ruleId = rule.raw.getId();
        out.moduleName = rule.raw.getModuleName();
        out.actionType = rule.raw.getActionType();
        out.eventClass = StringUtils.hasText(rule.raw.getEventClass()) ? rule.raw.getEventClass() : null;
        out.sourceTable = renderTemplate(rule.raw.getSourceTableTemplate(), extracted);
        out.description = renderTemplate(rule.raw.getDescriptionTemplate(), extracted);
        return out;
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String val) {
        if (val != null && !val.isBlank()) map.put(key, val);
    }

    private Map<String, Object> parseDetails(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>(){});
        } catch (Exception ignore) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseExtractors(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>(){});
        } catch (Exception ex) {
            log.debug("Invalid param_extractors JSON for audit mapping: {}", ex.toString());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> extractVars(Map<String, String> rules, Map<String, Object> context) {
        Map<String, Object> out = new HashMap<>();
        out.putAll(context); // allow direct event.* / details.* usage in templates
        for (Map.Entry<String, String> e : rules.entrySet()) {
            String key = e.getKey();
            String spec = e.getValue();
            if (!StringUtils.hasText(spec)) continue;
            Object val = null;
            // Support: path.x, details.x, event.x, fixedValue:xxx
            if (spec.startsWith("path.")) {
                val = context.get(spec.substring(5));
            } else if (spec.startsWith("details.")) {
                val = context.get(spec);
            } else if (spec.startsWith("event.")) {
                val = context.get(spec);
            } else if (spec.startsWith("fixedValue:")) {
                val = spec.substring("fixedValue:".length());
            }
            if (val == null || String.valueOf(val).isBlank()) val = "未知";
            out.put(key, String.valueOf(val));
        }
        return out;
    }

    private String renderTemplate(String template, Map<String, Object> vars) {
        if (!StringUtils.hasText(template)) return "";
        String result = template;
        // Replace {var|默认} or {var}
        int guard = 0;
        while (guard++ < 200) {
            int l = result.indexOf('{');
            if (l < 0) break;
            int r = result.indexOf('}', l + 1);
            if (r < 0) break;
            String token = result.substring(l + 1, r);
            String var = token;
            String def = "未知";
            int pipe = token.indexOf('|');
            if (pipe >= 0) {
                var = token.substring(0, pipe).trim();
                def = token.substring(pipe + 1).trim();
            }
            Object v = vars.getOrDefault(var, def);
            result = result.substring(0, l) + String.valueOf(v) + result.substring(r + 1);
        }
        return result;
    }
}
