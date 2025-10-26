package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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
        public String operationGroup;
        public String groupDisplayName;
        public AuditOperationType operationType;
        public String operationTypeLabel;
        public String description;        // 操作内容
        public String sourceTable;        // 源表（数据库表名）
        public String eventClass;         // SecurityEvent/AuditEvent
        public boolean ruleMatched;       // true if derived from mapping rule, false if fallback
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
    public static final class RuleSummary {
        private final Long id;
        private final String moduleName;
        private final String operationGroup;
        private final String groupDisplayName;
        private final AuditOperationType operationType;
        private final String descriptionTemplate;
        private final String sourceTableTemplate;

        RuleSummary(AuditOperationMapping mapping) {
            this.id = mapping.getId();
            this.moduleName = safeTrim(mapping.getModuleName());
            this.operationGroup = effectiveGroupKey(mapping);
            this.groupDisplayName = effectiveGroupLabel(mapping);
            AuditOperationType type = AuditOperationType.from(mapping.getOperationType());
            this.operationType = type == AuditOperationType.UNKNOWN ? null : type;
            this.descriptionTemplate = safeTrim(mapping.getDescriptionTemplate());
            this.sourceTableTemplate = safeTrim(mapping.getSourceTableTemplate());
        }

        public Long getId() { return id; }
        public String getModuleName() { return moduleName; }
        public String getOperationGroup() { return operationGroup; }
        public String getGroupDisplayName() { return groupDisplayName; }
        public String getOperationType() { return operationType == null ? null : operationType.getCode(); }
        public String getOperationTypeLabel() { return operationType == null ? null : operationType.getDisplayName(); }
        public String getDescriptionTemplate() { return descriptionTemplate; }
        public String getSourceTableTemplate() { return sourceTableTemplate; }
    }

    private final AuditOperationMappingRepository repo;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditResourceDictionaryService resourceDictionary;
    private final PathPatternParser parser = new PathPatternParser();
    private volatile List<CompiledRule> rules = List.of();
    private final Map<String, PathPattern> patternCache = new ConcurrentHashMap<>();

    public OperationMappingEngine(
        AuditOperationMappingRepository repo,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate,
        AuditResourceDictionaryService resourceDictionary
    ) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.resourceDictionary = resourceDictionary;
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
            if (!sourceMatches(event.getSourceSystem(), r.raw.getSourceSystem())) continue;
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
        ResolvedOperation resolved = render(best, bestInfo, event, details);
        resolved.ruleMatched = true;
        return Optional.of(resolved);
    }

    /**
     * Resolve operation information using mapping rules; if no rule matches, derive a best-effort fallback
     * using legacy heuristics. The returned {@link ResolvedOperation} indicates whether a rule was matched.
     */
    public Optional<ResolvedOperation> resolveWithFallback(AuditEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        Optional<ResolvedOperation> rule = resolve(event);
        if (rule.isPresent()) {
            return rule;
        }
        ResolvedOperation fallback = buildFallbackOperation(event);
        return fallback == null ? Optional.empty() : Optional.of(fallback);
    }

    public List<RuleSummary> describeRules() {
        List<CompiledRule> snapshot = this.rules;
        if (snapshot.isEmpty()) {
            return List.of();
        }
        List<RuleSummary> summaries = new ArrayList<>(snapshot.size());
        for (CompiledRule rule : snapshot) {
            summaries.add(new RuleSummary(rule.raw));
        }
        return Collections.unmodifiableList(summaries);
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

    private boolean sourceMatches(String eventSource, String ruleSource) {
        if (!StringUtils.hasText(ruleSource)) {
            return true;
        }
        if (!StringUtils.hasText(eventSource)) {
            return false;
        }
        String actual = eventSource.trim().toLowerCase(Locale.ROOT);
        String[] tokens = ruleSource.split(",");
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String expected = token.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(expected) || actual.equals(expected)) {
                return true;
            }
        }
        return false;
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
        int cmp = PathPattern.SPECIFICITY_COMPARATOR.compare(a.pattern, b.pattern);
        if (cmp < 0) {
            return a;
        }
        if (cmp > 0) {
            return b;
        }
        boolean aCatchAll = isCatchAll(a.pattern);
        boolean bCatchAll = isCatchAll(b.pattern);
        if (aCatchAll != bCatchAll) {
            return aCatchAll ? b : a;
        }
        int av = countVars(a.pattern.getPatternString());
        int bv = countVars(b.pattern.getPatternString());
        if (av != bv) {
            return av < bv ? a : b;
        }
        int aw = countWildcards(a.pattern.getPatternString());
        int bw = countWildcards(b.pattern.getPatternString());
        if (aw != bw) {
            return aw < bw ? a : b;
        }
        int al = literalLength(a.pattern.getPatternString());
        int bl = literalLength(b.pattern.getPatternString());
        if (al != bl) {
            return al > bl ? a : b;
        }
        int ao = a.raw.getOrderValue() == null ? Integer.MAX_VALUE : a.raw.getOrderValue();
        int bo = b.raw.getOrderValue() == null ? Integer.MAX_VALUE : b.raw.getOrderValue();
        if (ao != bo) {
            return ao <= bo ? a : b;
        }
        long aid = a.raw.getId() == null ? Long.MAX_VALUE : a.raw.getId();
        long bid = b.raw.getId() == null ? Long.MAX_VALUE : b.raw.getId();
        return aid <= bid ? a : b;
    }

    private boolean isCatchAll(PathPattern pattern) {
        if (pattern == null) {
            return false;
        }
        String text = pattern.getPatternString();
        return "**".equals(text) || "/**".equals(text);
    }

    private int countVars(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        return StringUtils.countOccurrencesOf(pattern, "{");
    }

    private int countWildcards(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') {
                count++;
            }
        }
        return count;
    }

    private int literalLength(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        int length = 0;
        boolean inVar = false;
        for (char ch : pattern.toCharArray()) {
            if (ch == '{') {
                inVar = true;
            } else if (ch == '}') {
                inVar = false;
            } else if (!inVar && ch != '*') {
                length++;
            }
        }
        return length;
    }

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
        putIfNotBlank(vars, "event.sourceSystem", event.getSourceSystem());
        putIfNotBlank(vars, "event.actorRole", event.getActorRole());
        String actorRoleText = localizeActorRole(event.getActorRole(), event.getActor());
        if (actorRoleText != null) {
            vars.put("event.actorRoleText", actorRoleText);
            vars.put("操作者角色", actorRoleText);
        }

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
        out.operationGroup = effectiveGroupKey(rule.raw);
        out.groupDisplayName = effectiveGroupLabel(rule.raw);
        AuditOperationType opType = AuditOperationType.from(rule.raw.getOperationType());
        out.operationType = opType == AuditOperationType.UNKNOWN ? null : opType;
        out.operationTypeLabel = opType == AuditOperationType.UNKNOWN ? null : opType.getDisplayName();
        out.eventClass = StringUtils.hasText(rule.raw.getEventClass()) ? rule.raw.getEventClass() : null;
        out.sourceTable = renderTemplate(rule.raw.getSourceTableTemplate(), extracted);
        out.description = renderTemplate(rule.raw.getDescriptionTemplate(), extracted);
        return out;
    }

    private static String effectiveGroupKey(AuditOperationMapping mapping) {
        String raw = safeTrim(mapping.getOperationGroup());
        if (StringUtils.hasText(raw)) {
            return raw;
        }
        String candidate = safeTrim(mapping.getGroupDisplayName());
        if (!StringUtils.hasText(candidate)) {
            candidate = safeTrim(mapping.getModuleName());
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = mapping.getId() != null ? "rule_" + mapping.getId() : "general";
        }
        String slug = slugify(candidate);
        if (StringUtils.hasText(slug)) {
            return slug;
        }
        if (mapping.getId() != null) {
            return "rule_" + mapping.getId();
        }
        return "rule_" + UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String effectiveGroupLabel(AuditOperationMapping mapping) {
        String label = safeTrim(mapping.getGroupDisplayName());
        if (StringUtils.hasText(label)) {
            return label;
        }
        label = safeTrim(mapping.getModuleName());
        if (StringUtils.hasText(label)) {
            return label;
        }
        return "通用";
    }

    private static String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        ascii = ascii.replaceAll("[^A-Za-z0-9]+", "_");
        ascii = ascii.replaceAll("_+", "_");
        ascii = ascii.replaceAll("^_|_$", "");
        ascii = ascii.toLowerCase(Locale.ROOT);
        return ascii.isEmpty() ? null : ascii;
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

    private ResolvedOperation buildFallbackOperation(AuditEvent event) {
        FallbackContext ctx = new FallbackContext(event, parseDetails(event.getDetails()));
        String signalType = inferTypeFromSignal(ctx.actionUpper, ctx.summary);
        String type = signalType;
        if (!StringUtils.hasText(type)) {
            type = inferTypeFromActionCode(ctx.extraTags);
        }
        if (!StringUtils.hasText(type) && containsLogin(ctx.actionUpper, ctx.summary)) {
            type = "登录";
        }
        if (!StringUtils.hasText(type) && containsLogout(ctx.actionUpper, ctx.summary)) {
            type = "登出";
        }
        if (!StringUtils.hasText(type)) {
            type = inferTypeFromHttp(ctx.method, ctx, signalType);
        }

        String label = resolveResourceLabel(ctx);
        String tableName = resolveSourceTableName(ctx);
        String target = resolveTargetIndicator(ctx);
        String content = determineFallbackContent(ctx, type, label, target);
        AuditOperationType opType = AuditOperationType.from(type);
        if (opType == AuditOperationType.UNKNOWN && !StringUtils.hasText(content)) {
            return null;
        }

        ResolvedOperation resolved = new ResolvedOperation();
        resolved.ruleMatched = false;
        resolved.ruleId = null;
        String moduleCategory = resolveModuleCategory(ctx);
        String moduleName = StringUtils.hasText(moduleCategory) ? moduleCategory : (StringUtils.hasText(ctx.module) ? ctx.module : null);
        resolved.moduleName = moduleName;
        String groupLabel = StringUtils.hasText(moduleName) ? moduleName : "通用";
        String groupKey = slugify(groupLabel);
        if (!StringUtils.hasText(groupKey)) {
            groupKey = "fallback_" + UUID.nameUUIDFromBytes(groupLabel.getBytes(StandardCharsets.UTF_8));
        }
        resolved.operationGroup = groupKey;
        resolved.groupDisplayName = groupLabel;
        resolved.operationType = opType == AuditOperationType.UNKNOWN ? null : opType;
        resolved.operationTypeLabel = resolved.operationType != null ? resolved.operationType.getDisplayName() : (StringUtils.hasText(type) ? type : null);
        resolved.description = StringUtils.hasText(content) ? content : null;
        resolved.sourceTable = StringUtils.hasText(tableName) ? tableName : (StringUtils.hasText(ctx.targetTable) ? ctx.targetTable : null);
        resolved.eventClass = StringUtils.hasText(event.getEventClass()) ? event.getEventClass() : null;
        return resolved;
    }

    private String determineFallbackContent(FallbackContext ctx, String type, String label, String target) {
        if (StringUtils.hasText(ctx.summary) && shouldReuseSummary(type, ctx.summary)) {
            String text = ctx.summary;
            if (StringUtils.hasText(target)) {
                text = appendTargetIfMissing(text, target);
            }
            return text;
        }
        String effectiveLabel = StringUtils.hasText(label) ? label : "资源";
        if (StringUtils.hasText(type)) {
            String built = buildOperationContent(type, effectiveLabel, target, ctx);
            if (StringUtils.hasText(built)) {
                return built;
            }
        }
        if (StringUtils.hasText(ctx.summary)) {
            String text = ctx.summary;
            if (StringUtils.hasText(target)) {
                text = appendTargetIfMissing(text, target);
            }
            return text;
        }
        return null;
    }

    private String buildOperationContent(String type, String label, String target, FallbackContext ctx) {
        switch (type) {
            case "查询":
                if (isListQuery(ctx)) {
                    return "查询" + label + "列表";
                }
                if (StringUtils.hasText(target)) {
                    return "查看" + label + wrapTarget(target);
                }
                return "查询" + label;
            case "新增":
                if (StringUtils.hasText(target)) {
                    return "新增" + label + wrapTarget(target);
                }
                return "新增" + label;
            case "修改":
                if (StringUtils.hasText(target)) {
                    return "修改" + label + wrapTarget(target);
                }
                return "修改" + label;
            case "部分更新":
                if (StringUtils.hasText(target)) {
                    return "部分更新" + label + wrapTarget(target);
                }
                return "部分更新" + label;
            case "删除":
                if (StringUtils.hasText(target)) {
                    return "删除" + label + wrapTarget(target);
                }
                return "删除" + label;
            case "导出":
                return "导出" + label;
            case "执行":
                if (StringUtils.hasText(target)) {
                    return "执行" + label + wrapTarget(target);
                }
                return "执行" + label;
            case "审批":
                if (StringUtils.hasText(target)) {
                    return "审批" + label + wrapTarget(target);
                }
                return "处理" + label + "审批";
            case "登录":
                if (StringUtils.hasText(ctx.summary)) {
                    return ctx.summary;
                }
                return "登录系统";
            case "登出":
                if (StringUtils.hasText(ctx.summary)) {
                    return ctx.summary;
                }
                return "退出系统";
            default:
                return ctx.summary;
        }
    }

    private String resolveResourceLabel(FallbackContext ctx) {
        if (StringUtils.hasText(ctx.targetTableLabel)) {
            return ctx.targetTableLabel;
        }
        if (StringUtils.hasText(ctx.targetTable)) {
            String mapped = resolveTableLabel(ctx.targetTable);
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
        }
        if (StringUtils.hasText(ctx.resourceType)) {
            String mapped = resolveTableLabel(ctx.resourceType);
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
        }
        if (StringUtils.hasText(ctx.module)) {
            String mappedModule = resolveTableLabel(ctx.module);
            if (StringUtils.hasText(mappedModule)) {
                return mappedModule;
            }
            if (hasChinese(ctx.module)) {
                return ctx.module;
            }
        }
        return "资源";
    }

    private String resolveSourceTableName(FallbackContext ctx) {
        if (StringUtils.hasText(ctx.targetTable)) {
            return ctx.targetTable;
        }
        if (StringUtils.hasText(ctx.resourceTypeLower)) {
            return normalizeTableKey(ctx.resourceTypeLower);
        }
        if (StringUtils.hasText(ctx.module)) {
            return normalizeTableKey(ctx.module);
        }
        return null;
    }

    private String resolveTargetIndicator(FallbackContext ctx) {
        if (StringUtils.hasText(ctx.targetRef)) {
            return ctx.targetRef;
        }
        if (StringUtils.hasText(ctx.targetId)) {
            return ctx.targetId;
        }
        if (StringUtils.hasText(ctx.resourceId)) {
            return ctx.resourceId;
        }
        return null;
    }

    private boolean isListQuery(FallbackContext ctx) {
        if (!"GET".equals(ctx.method)) {
            return false;
        }
        return !StringUtils.hasText(ctx.targetId) && !StringUtils.hasText(ctx.resourceId);
    }

    private boolean containsLogin(String actionUpper, String summary) {
        if (!StringUtils.hasText(actionUpper) && !StringUtils.hasText(summary)) {
            return false;
        }
        if (StringUtils.hasText(actionUpper) && (actionUpper.contains("LOGIN") || actionUpper.contains("SIGNIN") || actionUpper.contains("AUTH"))) {
            return true;
        }
        return StringUtils.hasText(summary) && (summary.contains("登录") || summary.contains("登陆"));
    }

    private boolean containsLogout(String actionUpper, String summary) {
        if (!StringUtils.hasText(actionUpper) && !StringUtils.hasText(summary)) {
            return false;
        }
        if (StringUtils.hasText(actionUpper) && (actionUpper.contains("LOGOUT") || actionUpper.contains("SIGNOUT"))) {
            return true;
        }
        return StringUtils.hasText(summary) && (summary.contains("退出") || summary.contains("登出"));
    }

    private String inferTypeFromSignal(String actionUpper, String summary) {
        if (!StringUtils.hasText(actionUpper) && !StringUtils.hasText(summary)) return null;
        String upper = actionUpper == null ? "" : actionUpper;
        String text = summary == null ? "" : summary;
        if (upper.contains("EXPORT") || text.contains("导出")) return "导出";
        if (upper.contains("EXECUTE") || upper.contains("RUN") || text.contains("执行")) return "执行";
        if (upper.contains("DELETE") || upper.contains("REMOVE") || upper.contains("DESTROY") || text.contains("删除") || text.contains("移除")) return "删除";
        if (upper.contains("CREATE") || upper.contains("ADD") || upper.contains("REGISTER") || text.contains("新增") || text.contains("添加") || text.contains("新建")) return "新增";
        if (upper.contains("PATCH")) return "部分更新";
        if (upper.contains("UPDATE") || upper.contains("MODIFY") || upper.contains("RESET") || upper.contains("EDIT") || upper.contains("ENABLE") || upper.contains("DISABLE") || upper.contains("GRANT") || upper.contains("REVOKE") || text.contains("修改") || text.contains("更新") || text.contains("重置") || text.contains("授权") || text.contains("启用") || text.contains("禁用")) return "修改";
        if (upper.contains("APPROVE") || text.contains("审批")) return "审批";
        if (upper.contains("LIST") || upper.contains("SEARCH") || upper.contains("QUERY") || upper.contains("VIEW") || text.contains("查询") || text.contains("查看")) return "查询";
        return null;
    }

    private String inferTypeFromActionCode(String extraTagsJson) {
        if (!StringUtils.hasText(extraTagsJson)) {
            return null;
        }
        try {
            Map<?, ?> tags = objectMapper.readValue(extraTagsJson, Map.class);
            Object ac = tags.get("actionCode");
            if (ac == null) return null;
            String code = String.valueOf(ac).trim().toUpperCase(Locale.ROOT);
            if (code.contains("VIEW") || code.contains("LIST") || code.contains("SEARCH") || code.contains("EXPORT")) return "查询";
            if (code.contains("CREATE") || code.contains("ADD") || code.contains("NEW")) return "新增";
            if (code.contains("UPDATE") || code.contains("EDIT") || code.contains("RESET") || code.contains("GRANT") || code.contains("REVOKE") || code.contains("ENABLE") || code.contains("DISABLE") || code.contains("SET")) return "修改";
            if (code.contains("DELETE") || code.contains("REMOVE")) return "删除";
            if (code.contains("LOGIN")) return "登录";
            if (code.contains("LOGOUT")) return "登出";
        } catch (Exception ignore) {}
        return null;
    }

    private String inferTypeFromHttp(String method, FallbackContext ctx, String signalType) {
        if (!StringUtils.hasText(method)) {
            return likelyQuery(ctx) ? "查询" : null;
        }
        switch (method) {
            case "GET":
            case "HEAD":
            case "OPTIONS":
                return "查询";
            case "POST":
                if ("审批".equals(signalType)) return "审批";
                if (likelyQuery(ctx)) return "查询";
                return "新增";
            case "PUT":
                return "修改";
            case "PATCH":
                return "部分更新";
            case "DELETE":
                return "删除";
            default:
                return null;
        }
    }

    private boolean likelyQuery(FallbackContext ctx) {
        if (ctx.actionUpper.contains("LOGIN") || ctx.actionUpper.contains("LOGOUT") || ctx.actionUpper.contains("CREATE") || ctx.actionUpper.contains("UPDATE") || ctx.actionUpper.contains("DELETE") || ctx.actionUpper.contains("REMOVE")) {
            return false;
        }
        if (StringUtils.hasText(ctx.method)) {
            return "GET".equals(ctx.method);
        }
        String uri = ctx.requestUri == null ? "" : ctx.requestUri;
        String rt = ctx.resourceTypeLower;
        boolean knownResource = rt.contains("user") || rt.contains("role") || rt.contains("menu") || rt.contains("org") || rt.contains("approval") || rt.contains("model") || rt.contains("catalog") || rt.contains("standard");
        boolean looksApi = uri.startsWith("/api/") || uri.startsWith("/api");
        return knownResource && looksApi;
    }

    private boolean shouldReuseSummary(String type, String summary) {
        if (!StringUtils.hasText(summary)) return false;
        if (!hasChinese(summary)) return false;
        if (!StringUtils.hasText(type)) return true;
        if ("登录".equals(type) || "登出".equals(type)) return true;
        return summary.contains(type);
    }

    private boolean hasChinese(String s) {
        if (s == null || s.isBlank()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') return true;
        }
        return false;
    }

    private String wrapTarget(String target) {
        String trimmed = target == null ? "" : target.trim();
        if (trimmed.startsWith("【") && trimmed.endsWith("】")) {
            return trimmed;
        }
        return "【" + trimmed + "】";
    }

    private String appendTargetIfMissing(String text, String target) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(target)) {
            return text;
        }
        if (text.contains(target) || text.contains(wrapTarget(target))) {
            return text;
        }
        return text + wrapTarget(target);
    }

    private String resolveTableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String trimmed = key.trim();
        return resourceDictionary.resolveLabel(trimmed).orElse(trimmed);
    }

    private String normalizeTableKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String s = key.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s.isBlank() ? null : s;
    }

    private String resolveModuleCategory(FallbackContext ctx) {
        if (StringUtils.hasText(ctx.targetTable)) {
            Optional<String> category = resourceDictionary.resolveCategory(ctx.targetTable);
            if (category.isPresent()) {
                return category.orElseThrow();
            }
        }
        if (StringUtils.hasText(ctx.resourceType)) {
            Optional<String> category = resourceDictionary.resolveCategory(ctx.resourceType);
            if (category.isPresent()) {
                return category.orElseThrow();
            }
        }
        if (StringUtils.hasText(ctx.module)) {
            Optional<String> category = resourceDictionary.resolveCategory(ctx.module);
            if (category.isPresent()) {
                return category.orElseThrow();
            }
        }
        return null;
    }

    private static String coerceToString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text == null ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String val : values) {
            if (StringUtils.hasText(val)) {
                return val;
            }
        }
        return null;
    }

    private static String safeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String safeTrim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String localizeActorRole(String role, String actor) {
        String uname = actor == null ? null : actor.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(role)) {
            if ("sysadmin".equals(uname)) return "系统管理员";
            if ("authadmin".equals(uname)) return "授权管理员";
            if ("auditadmin".equals(uname) || "securityadmin".equals(uname)) return "安全审计员";
            if ("opadmin".equals(uname)) return "运维管理员";
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "ROLE_SYS_ADMIN", "SYS_ADMIN" -> "系统管理员";
            case "ROLE_AUTH_ADMIN", "AUTH_ADMIN" -> "授权管理员";
            case "ROLE_SECURITY_AUDITOR", "ROLE_AUDITOR_ADMIN", "ROLE_AUDIT_ADMIN", "SECURITY_AUDITOR", "AUDITOR_ADMIN", "AUDIT_ADMIN", "AUDITADMIN" -> "安全审计员";
            case "ROLE_OP_ADMIN", "OP_ADMIN" -> "运维管理员";
            default -> null;
        };
    }

    private final class FallbackContext {
        final Map<String, Object> details;
        final String actionUpper;
        final String summary;
        final String method;
        final String extraTags;
        final String module;
        final String resourceType;
        final String resourceTypeLower;
        final String resourceId;
        final String requestUri;
        final String targetTable;
        final String targetTableLabel;
        final String targetId;
        final String targetRef;

        FallbackContext(AuditEvent event, Map<String, Object> details) {
            this.details = details == null ? Collections.emptyMap() : details;
            this.actionUpper = safeUpper(event.getAction());
            this.summary = safeTrim(event.getSummary());
            this.method = safeUpper(event.getHttpMethod());
            this.extraTags = event.getExtraTags();
            this.module = safeTrim(event.getModule());
            this.resourceType = safeTrim(event.getResourceType());
            this.resourceTypeLower = this.resourceType == null ? "" : this.resourceType.toLowerCase(Locale.ROOT);
            this.resourceId = safeTrim(event.getResourceId());
            this.requestUri = safeTrim(event.getRequestUri());
            this.targetTable = firstNonBlank(
                coerceToString(this.details.get("源表")),
                coerceToString(this.details.get("target_table"))
            );
            this.targetTableLabel = resolveTableLabel(this.targetTable);
            this.targetId = firstNonBlank(
                coerceToString(this.details.get("目标ID")),
                coerceToString(this.details.get("target_id"))
            );
            this.targetRef = firstNonBlank(
                coerceToString(this.details.get("目标引用")),
                coerceToString(this.details.get("target_ref"))
            );
        }
    }
}
