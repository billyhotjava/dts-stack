package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryQueryService;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryView;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryViewMapper;
import com.yuzhi.dts.admin.service.auditv2.AuditLogActionRecorder;
import com.yuzhi.dts.admin.service.auditv2.AuditResourceDictionaryService;
import com.yuzhi.dts.admin.service.auditv2.AuditSearchCriteria;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.auditv2.ModuleOption;
import com.yuzhi.dts.admin.service.auditv2.OperationMappingEngine;
import com.yuzhi.dts.admin.service.auditv2.OperationMappingEngine.RuleSummary;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.AUTH_ADMIN + "','" + AuthoritiesConstants.AUDITOR_ADMIN + "','AUTHADMIN','AUDITADMIN','AUDITOR_ADMIN','AUTH_ADMIN')"
)
public class AuditLogResource {

    private static final Logger log = LoggerFactory.getLogger(AuditLogResource.class);
    private static final Map<String, String> BUILTIN_DISPLAY_NAMES = Map.of(
        "sysadmin", "系统管理员",
        "authadmin", "授权管理员",
        "auditadmin", "安全审计员",
        "opadmin", "运维管理员"
    );

    private final AuditEntryQueryService auditQueryService;
    private final OperationMappingEngine opMappingEngine;
    private final AuditResourceDictionaryService resourceDictionary;
    private final AdminUserService adminUserService;
    private final AuditEntryViewMapper viewMapper;
    private final AuditLogActionRecorder actionRecorder;

    public AuditLogResource(
        AuditEntryQueryService auditQueryService,
        OperationMappingEngine opMappingEngine,
        AuditResourceDictionaryService resourceDictionary,
        AdminUserService adminUserService,
        AuditEntryViewMapper viewMapper,
        AuditLogActionRecorder actionRecorder
    ) {
        this.auditQueryService = auditQueryService;
        this.opMappingEngine = opMappingEngine;
        this.resourceDictionary = resourceDictionary;
        this.adminUserService = adminUserService;
        this.viewMapper = viewMapper;
        this.actionRecorder = actionRecorder;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "occurredAt,desc") String sort,
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String actionCode,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "operationGroup", required = false) String operationGroup,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String targetTable,
        @RequestParam(value = "resource", required = false) String targetId,
        @RequestParam(value = "clientIp", required = false) String clientIp,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        HttpServletRequest request
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200), parseSort(sort));
        Instant fromDate = parseInstant(from);
        Instant toDate = parseInstant(to);
        VisibilityScope scope = resolveVisibilityScope();
        AuditSearchCriteria criteria = new AuditSearchCriteria(
            actor, module, operationType, actionCode, operationGroup, sourceSystem, result,
            targetTable, targetId, clientIp, keyword, fromDate, toDate,
            scope.allowedActors(), scope.excludedActors(), false
        );
        Page<AuditEntryView> resultPage = auditQueryService.search(criteria, pageable);
        List<AuditEntryView> views = resultPage.getContent();
        Map<String, String> displayOverrides = resolveActorDisplayNames(views);
        List<Map<String, Object>> content = new ArrayList<>(views.size());
        for (AuditEntryView view : views) {
            Map<String, Object> row = viewMapper.toResponse(view, false);
            applyDisplayNameOverride(row, displayOverrides);
            content.add(row);
        }
        Map<String, Object> payload = Map.of(
            "content", content,
            "page", resultPage.getNumber(),
            "size", resultPage.getSize(),
            "totalElements", resultPage.getTotalElements(),
            "totalPages", resultPage.getTotalPages()
        );
        actionRecorder.record(ButtonCodes.AUDIT_LOG_QUERY, criteria, pageable, resultPage.getTotalElements(), content.size(), request);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable Long id) {
        VisibilityScope scope = resolveVisibilityScope();
        AuditEntryView view = auditQueryService
            .findById(id, true)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在"));
        ensureReadable(scope, view);
        Map<String, Object> body = viewMapper.toResponse(view, true);
        applyDisplayNameOverride(body, resolveActorDisplayNames(List.of(view)));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> purge() {
        long removed = auditQueryService.purgeAll();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("removed", removed)));
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public void export(
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String actionCode,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "operationGroup", required = false) String operationGroup,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String targetTable,
        @RequestParam(value = "resource", required = false) String targetId,
        @RequestParam(value = "clientIp", required = false) String clientIp,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        Instant fromDate = parseInstant(from);
        Instant toDate = parseInstant(to);
        VisibilityScope scope = resolveVisibilityScope();
        AuditSearchCriteria criteria = new AuditSearchCriteria(
            actor, module, operationType, actionCode, operationGroup, sourceSystem, result,
            targetTable, targetId, clientIp, keyword, fromDate, toDate,
            scope.allowedActors(), scope.excludedActors(), true
        );
        Page<AuditEntryView> exportPage = auditQueryService.search(criteria, Pageable.unpaged());
        List<AuditEntryView> views = exportPage.getContent();
        Map<String, String> displayOverrides = resolveActorDisplayNames(views);
        List<Map<String, Object>> records = new ArrayList<>(views.size());
        for (AuditEntryView view : views) {
            Map<String, Object> row = viewMapper.toResponse(view, true);
            applyDisplayNameOverride(row, displayOverrides);
            records.add(row);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(
            "id,occurred_at,source_system,module,action,actor,result,result_text,summary,target_table,target_id,operation_type,operation_content,client_ip,client_agent\n"
        );
        for (Map<String, Object> record : records) {
            sb
                .append(record.get("id")).append(',')
                .append(escapeCsv(record.get("occurredAt"))).append(',')
                .append(escapeCsv(record.get("sourceSystem"))).append(',')
                .append(escapeCsv(record.get("module"))).append(',')
                .append(escapeCsv(record.get("action"))).append(',')
                .append(escapeCsv(record.get("actor"))).append(',')
                .append(escapeCsv(record.get("result"))).append(',')
                .append(escapeCsv(record.get("resultText"))).append(',')
                .append(escapeCsv(record.get("summary"))).append(',')
                .append(escapeCsv(record.get("targetTable"))).append(',')
                .append(escapeCsv(record.get("targetId"))).append(',')
                .append(escapeCsv(record.get("operationType"))).append(',')
                .append(escapeCsv(record.get("operationContent"))).append(',')
                .append(escapeCsv(record.get("clientIp"))).append(',')
                .append(escapeCsv(record.get("clientAgent")))
                .append('\n');
        }
        actionRecorder.record(ButtonCodes.AUDIT_LOG_EXPORT, criteria, Pageable.unpaged(), exportPage.getTotalElements(), records.size(), request);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> modules() {
        List<ModuleOption> options = auditQueryService.listModuleOptions();
        List<Map<String, Object>> out = new ArrayList<>(options.size());
        for (ModuleOption option : options) {
            out.add(Map.of("key", option.code(), "title", option.label()));
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> groups() {
        List<RuleSummary> summaries = opMappingEngine.describeRules();
        if (summaries.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        LinkedHashMap<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (RuleSummary summary : summaries) {
            String key = resolveGroupKey(summary);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", k);
                entry.put("title", buildGroupLabel(summary));
                String module = safeTrim(summary.getModuleName());
                if (module != null) {
                    entry.put("module", module);
                }
                String groupLabel = safeTrim(summary.getGroupDisplayName());
                if (groupLabel != null) {
                    entry.put("groupDisplayName", groupLabel);
                }
                String source = safeTrim(summary.getSourceSystem());
                if (source != null) {
                    entry.put("sourceSystem", source);
                    entry.put("sourceSystemLabel", viewMapper.mapSourceSystemText(source));
                }
                return entry;
            });
        }
        return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>(grouped.values())));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categories() {
        LinkedHashMap<String, ModuleView> modules = collectModulesFromRules();
        LinkedHashMap<String, CategoryView> categories = collectCategoriesFromRules(modules);
        List<Map<String, Object>> out = new ArrayList<>(categories.size());
        categories
            .values()
            .forEach(category ->
                out.add(
                    Map.of(
                        "moduleKey", category.moduleKey(),
                        "moduleTitle", category.moduleTitle(),
                        "entryKey", category.entryKey(),
                        "entryTitle", category.entryTitle()
                    )
                )
            );
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    private void ensureReadable(VisibilityScope scope, AuditEntryView view) {
        if (scope.allowedActors().isEmpty() && scope.excludedActors().isEmpty()) {
            return;
        }
        String actor = Optional.ofNullable(view.actorId()).map(String::toLowerCase).orElse("");
        if (!scope.allowedActors().isEmpty() && !scope.allowedActors().contains(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该审计日志");
        }
        if (!scope.excludedActors().isEmpty() && scope.excludedActors().contains(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该审计日志");
        }
    }

    private Sort parseSort(String sort) {
        if (StringUtils.isBlank(sort)) {
            return Sort.by(Sort.Order.desc("occurredAt"));
        }
        try {
            String[] parts = sort.split(",");
            String property = parts[0];
            String direction = parts.length > 1 ? parts[1] : "desc";
            Sort.Order order = "asc".equalsIgnoreCase(direction)
                ? Sort.Order.asc(property)
                : Sort.Order.desc(property);
            return Sort.by(order);
        } catch (Exception ex) {
            return Sort.by(Sort.Order.desc("occurredAt"));
        }
    }

    private Instant parseInstant(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private VisibilityScope resolveVisibilityScope() {
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        String normalized = login.map(value -> value.trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean hasAuthRole = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.AUTH_ADMIN, "AUTHADMIN", "ROLE_AUTHADMIN", "AUTH_ADMIN"
        );
        boolean hasAuditRole = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.AUDITOR_ADMIN, "AUDITADMIN", "ROLE_AUDITADMIN", "AUDITOR_ADMIN"
        );
        if (hasAuthRole && !hasAuditRole) {
            return new VisibilityScope(Set.of("auditadmin"), Set.of());
        }
        if (hasAuditRole && normalized != null) {
            return new VisibilityScope(Set.of(), Set.of(normalized));
        }
        return VisibilityScope.unrestricted();
    }

    private String resolveGroupKey(RuleSummary summary) {
        String rawKey = safeTrim(summary.getOperationGroup());
        if (StringUtils.isNotBlank(rawKey)) {
            return rawKey;
        }
        String label = safeTrim(summary.getGroupDisplayName());
        if (StringUtils.isNotBlank(label)) {
            return slugify(label, "audit-group");
        }
        String module = safeTrim(summary.getModuleName());
        if (StringUtils.isNotBlank(module)) {
            return slugify(module, "audit-module");
        }
        Long id = summary.getId();
        if (id != null) {
            return "rule-" + id;
        }
        return "rule-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildGroupLabel(RuleSummary summary) {
        String module = safeTrim(summary.getModuleName());
        String groupLabel = safeTrim(summary.getGroupDisplayName());
        String sourceSystem = safeTrim(summary.getSourceSystem());
        String sourceLabel = viewMapper.mapSourceSystemText(sourceSystem);
        StringBuilder label = new StringBuilder();
        if (StringUtils.isNotBlank(sourceLabel)) {
            label.append(sourceLabel);
        }
        if (StringUtils.isNotBlank(module)) {
            if (label.length() > 0) {
                label.append(" · ");
            }
            label.append(module);
        }
        if (StringUtils.isNotBlank(groupLabel) && !Objects.equals(groupLabel, module)) {
            if (label.length() > 0) {
                label.append(" · ");
            }
            label.append(groupLabel);
        }
        if (label.length() == 0) {
            label.append("通用");
        }
        return label.toString();
    }

    private LinkedHashMap<String, ModuleView> collectModulesFromRules() {
        LinkedHashMap<String, ModuleView> modules = new LinkedHashMap<>();
        for (RuleSummary summary : opMappingEngine.describeRules()) {
            String rawModuleKey = safeTrim(summary.getModuleName());
            String moduleKey = StringUtils.isBlank(rawModuleKey) ? "general" : rawModuleKey;
            String rawModuleTitle = safeTrim(summary.getModuleName());
            String moduleTitle = StringUtils.isBlank(rawModuleTitle)
                ? resourceDictionary.resolveLabel(moduleKey).orElse(moduleKey)
                : rawModuleTitle;
            final String title = moduleTitle;
            modules.computeIfAbsent(moduleKey, k -> new ModuleView(k, title));
        }
        return modules;
    }

    private LinkedHashMap<String, CategoryView> collectCategoriesFromRules(LinkedHashMap<String, ModuleView> modules) {
        LinkedHashMap<String, CategoryView> categories = new LinkedHashMap<>();
        for (RuleSummary summary : opMappingEngine.describeRules()) {
            String rawModuleKey = safeTrim(summary.getModuleName());
            String moduleKey = StringUtils.isBlank(rawModuleKey) ? "general" : rawModuleKey;
            final String moduleKeyFinal = moduleKey;
            ModuleView module = modules.computeIfAbsent(
                moduleKeyFinal,
                k -> new ModuleView(k, resourceDictionary.resolveLabel(k).orElse(k))
            );
            String entryKey = safeTrim(summary.getModuleName()) + ":" + safeTrim(summary.getOperationGroup());
            String entryTitle = safeTrim(summary.getGroupDisplayName());
            if (StringUtils.isBlank(entryTitle)) {
                entryTitle = module.title();
            }
            categories.putIfAbsent(entryKey, new CategoryView(module.key(), module.title(), entryKey, entryTitle));
        }
        return categories;
    }

    private String slugify(String value, String fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private Map<String, String> resolveActorDisplayNames(List<AuditEntryView> views) {
        if (views == null || views.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        LinkedHashSet<String> encountered = new LinkedHashSet<>();
        for (AuditEntryView view : views) {
            if (view == null) {
                continue;
            }
            String actorId = safeTrim(view.actorId());
            if (!org.springframework.util.StringUtils.hasText(actorId)) {
                continue;
            }
            encountered.add(actorId);
            String actorName = safeTrim(view.actorName());
            String normalizedId = actorId.toLowerCase(Locale.ROOT);
            if (org.springframework.util.StringUtils.hasText(actorName) && !actorName.equals(actorId)) {
                overrides.putIfAbsent(normalizedId, actorName);
                continue;
            }
            String embeddedName = extractActorNameFromPayload(view);
            if (org.springframework.util.StringUtils.hasText(embeddedName) && !embeddedName.equals(actorId)) {
                overrides.put(normalizedId, embeddedName);
                continue;
            }
            unresolved.add(actorId);
        }
        if (!unresolved.isEmpty()) {
            try {
                Map<String, String> resolved = adminUserService.resolveDisplayNames(unresolved);
                if (resolved != null && !resolved.isEmpty()) {
                    resolved.forEach((key, value) -> {
                        if (org.springframework.util.StringUtils.hasText(key) && org.springframework.util.StringUtils.hasText(value)) {
                            overrides.putIfAbsent(key.trim().toLowerCase(Locale.ROOT), value.trim());
                        }
                    });
                }
            } catch (Exception ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to resolve platform actor display names: {}", ex.getMessage());
                }
            }
        }
        if (!encountered.isEmpty()) {
            for (String actor : encountered) {
                if (!org.springframework.util.StringUtils.hasText(actor)) {
                    continue;
                }
                String normalized = actor.trim().toLowerCase(Locale.ROOT);
                String builtin = BUILTIN_DISPLAY_NAMES.get(normalized);
                if (org.springframework.util.StringUtils.hasText(builtin)) {
                    overrides.put(normalized, builtin);
                }
            }
        }
        return overrides.isEmpty() ? Map.of() : overrides;
    }

    private void applyDisplayNameOverride(Map<String, Object> record, Map<String, String> overrides) {
        if (record == null || overrides == null || overrides.isEmpty()) {
            return;
        }
        Object actorObj = record.get("actor");
        if (actorObj == null) {
            return;
        }
        String actor = safeTrim(actorObj.toString());
        if (!org.springframework.util.StringUtils.hasText(actor)) {
            return;
        }
        String override = overrides.get(actor.toLowerCase(Locale.ROOT));
        if (!org.springframework.util.StringUtils.hasText(override)) {
            return;
        }
        record.put("actorName", override);
        record.put("operatorName", override);
    }

    private String extractActorNameFromPayload(AuditEntryView view) {
        if (view == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
            valueAsString(view.metadata(), "actorName"),
            valueAsString(view.extraAttributes(), "actorName"),
            valueAsString(view.metadata(), "targetName"),
            valueAsString(view.extraAttributes(), "targetName"),
            valueAsString(view.metadata(), "resourceName"),
            valueAsString(view.extraAttributes(), "resourceName")
        );
    }

    private String valueAsString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null && map.containsKey("payload")) {
            Object payload = map.get("payload");
            if (payload instanceof Map<?, ?> payloadMap) {
                Object nested = payloadMap.get(key);
                if (nested != null) {
                    value = nested;
                }
            }
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return null;
        }
        if ("anonymous".equalsIgnoreCase(text) || "anonymoususer".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        // CSV / Excel formula-injection guard. Cells starting with these characters are
        // interpreted as formulas by Excel and Google Sheets — prefix with a single quote
        // so the value is rendered as text (the leading quote is stripped on display).
        if (!str.isEmpty() && isFormulaTriggerChar(str.charAt(0))) {
            str = "'" + str;
        }
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return '"' + str.replace("\"", "\"\"") + '"';
        }
        return str;
    }

    private static boolean isFormulaTriggerChar(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private record ModuleView(String key, String title) {}

    private record CategoryView(String moduleKey, String moduleTitle, String entryKey, String entryTitle) {}

    private record VisibilityScope(Set<String> allowedActors, Set<String> excludedActors) {
        static VisibilityScope unrestricted() {
            return new VisibilityScope(Set.of(), Set.of());
        }
    }
}
