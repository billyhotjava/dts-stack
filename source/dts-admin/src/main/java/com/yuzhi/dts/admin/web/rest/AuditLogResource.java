package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditEventView;
import com.yuzhi.dts.admin.service.audit.OperationMappingEngine;
import com.yuzhi.dts.admin.service.audit.OperationMappingEngine.RuleSummary;
import com.yuzhi.dts.admin.service.audit.AuditResourceDictionaryService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.errors.BadRequestAlertException;
import com.yuzhi.dts.common.audit.AuditStage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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

    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;
    private final OperationMappingEngine opMappingEngine;
    private final AuditResourceDictionaryService resourceDictionary;

    public AuditLogResource(
        AdminAuditService auditService,
        ObjectMapper objectMapper,
        OperationMappingEngine opMappingEngine,
        AuditResourceDictionaryService resourceDictionary
    ) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.opMappingEngine = opMappingEngine;
        this.resourceDictionary = resourceDictionary;
    }

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> modules() {
        LinkedHashMap<String, ModuleView> modules = collectModulesFromRules();
        List<Map<String, Object>> out = new ArrayList<>(modules.size());
        modules.values().forEach(module -> out.add(Map.of("key", module.key(), "title", module.title())));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categories() {
        LinkedHashMap<String, ModuleView> modules = collectModulesFromRules();
        LinkedHashMap<String, CategoryView> categories = collectCategoriesFromRules(modules);
        List<Map<String, Object>> out = new ArrayList<>(categories.size());
        categories.values()
            .forEach(category ->
                out.add(
                    Map.of(
                        "moduleKey",
                        category.moduleKey(),
                        "moduleTitle",
                        category.moduleTitle(),
                        "entryKey",
                        category.entryKey(),
                        "entryTitle",
                        category.entryTitle()
                    )
                )
            );
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    private LinkedHashMap<String, ModuleView> collectModulesFromRules() {
        LinkedHashMap<String, ModuleView> modules = new LinkedHashMap<>();
        List<String> canonicalModules = resourceDictionary.listModuleCategories();
        if (!canonicalModules.isEmpty()) {
            for (String moduleName : canonicalModules) {
                ensureModuleView(moduleName, modules);
            }
            return modules;
        }
        for (RuleSummary summary : opMappingEngine.describeRules()) {
            ensureModuleView(summary.getModuleName(), modules);
        }
        if (modules.isEmpty()) {
            ModuleView fallback = new ModuleView("general", "通用");
            modules.put(fallback.key(), fallback);
        }
        return modules;
    }

    private LinkedHashMap<String, CategoryView> collectCategoriesFromRules(LinkedHashMap<String, ModuleView> modules) {
        LinkedHashMap<String, CategoryView> categories = new LinkedHashMap<>();
        for (RuleSummary summary : opMappingEngine.describeRules()) {
            ModuleView module = ensureModuleView(summary.getModuleName(), modules);
            String entryTitle = safeTrim(deriveEntryTitle(summary));
            if (!StringUtils.hasText(entryTitle)) {
                continue;
            }
            String entryKey = slugify(entryTitle, module.key() + "-entry");
            String unique = module.key() + "::" + entryKey;
            categories.putIfAbsent(unique, new CategoryView(module.key(), module.title(), entryKey, entryTitle));
        }
        if (categories.isEmpty()) {
            ModuleView primary = modules.values().stream().findFirst().orElseGet(() -> ensureModuleView("通用", modules));
            String entryKey = primary.key() + "-default";
            categories.putIfAbsent(primary.key() + "::" + entryKey, new CategoryView(primary.key(), primary.title(), entryKey, "全部操作"));
        }
        return categories;
    }

    private ModuleView ensureModuleView(String moduleTitle, LinkedHashMap<String, ModuleView> modules) {
        String normalizedTitle = safeTrim(moduleTitle);
        if (!StringUtils.hasText(normalizedTitle)) {
            normalizedTitle = "通用";
        }
        for (ModuleView existing : modules.values()) {
            if (existing.title().equals(normalizedTitle)) {
                return existing;
            }
        }
        String key = slugify(normalizedTitle, "module");
        ModuleView view = new ModuleView(key, normalizedTitle);
        modules.putIfAbsent(key, view);
        return view;
    }

    private String deriveEntryTitle(RuleSummary summary) {
        if (StringUtils.hasText(summary.getActionType())) {
            return summary.getActionType();
        }
        if (StringUtils.hasText(summary.getDescriptionTemplate())) {
            return summary.getDescriptionTemplate();
        }
        if (StringUtils.hasText(summary.getSourceTableTemplate())) {
            return summary.getSourceTableTemplate();
        }
        return null;
    }

    private String resolveTableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String trimmed = key.trim();
        return resourceDictionary.resolveLabel(trimmed).orElse(trimmed);
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String slugify(String input, String prefix) {
        String trimmed = safeTrim(input);
        String effectivePrefix = StringUtils.hasText(prefix) ? prefix : "key";
        if (!StringUtils.hasText(trimmed)) {
            return effectivePrefix + "-" + "default";
        }
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKD);
        String slug = normalized
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(slug)) {
            slug = effectivePrefix + "-" + Math.abs(trimmed.hashCode());
        }
        return slug;
    }

    private record ModuleView(String key, String title) {}

    private record CategoryView(String moduleKey, String moduleTitle, String entryKey, String entryTitle) {}

    private record VisibilityScope(Set<String> allowedActors, Set<String> excludedActors) {
        VisibilityScope {
            allowedActors = allowedActors == null ? Set.of() : allowedActors;
            excludedActors = excludedActors == null ? Set.of() : excludedActors;
        }

        boolean hasAllowedActors() {
            return !allowedActors.isEmpty();
        }

        boolean hasExcludedActors() {
            return !excludedActors.isEmpty();
        }
    }

    private static final VisibilityScope UNRESTRICTED_SCOPE = new VisibilityScope(Set.of(), Set.of());

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "occurredAt,desc") String sort,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "resource", required = false) String resource,
        @RequestParam(value = "requestUri", required = false) String requestUri,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "clientIp", required = false) String clientIp
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200), parseSort(sort));
        Instant fromDate = parseInstant(from, "from");
        Instant toDate = parseInstant(to, "to");
        VisibilityScope scope = resolveVisibilityScope();
        Page<AuditEvent> pageResult = executeSearch(
            scope,
            actor,
            module,
            action,
            sourceSystem,
            eventType,
            result,
            resourceType,
            resource,
            requestUri,
            fromDate,
            toDate,
            clientIp,
            pageable
        );
        List<AuditEventView> views = pageResult.getContent().stream().map(this::toView).toList();
        Page<AuditEventView> viewPage = new PageImpl<>(views, pageable, pageResult.getTotalElements());
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", viewPage.getContent());
        payload.put("page", viewPage.getNumber());
        payload.put("size", viewPage.getSize());
        payload.put("totalElements", viewPage.getTotalElements());
        payload.put("totalPages", viewPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public void export(
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "resource", required = false) String resource,
        @RequestParam(value = "requestUri", required = false) String requestUri,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "clientIp", required = false) String clientIp,
        HttpServletResponse response
    ) throws IOException {
        Instant fromDate = parseInstant(from, "from");
        Instant toDate = parseInstant(to, "to");
        VisibilityScope scope = resolveVisibilityScope();
        List<AuditEvent> events = executeSearch(
            scope,
            actor,
            module,
            action,
            sourceSystem,
            eventType,
            result,
            resourceType,
            resource,
            requestUri,
            fromDate,
            toDate,
            clientIp,
            Pageable.unpaged()
        ).getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("id,timestamp,source,event_class,event_type,module,action,summary,operator_id,operator_name,org_code,org_name,result,resource_type,resource_id,client_ip,client_agent,http_method,request_uri,来源系统,结果中文,目标表,目标ID,操作类型,操作内容,日志类型\n");
        for (AuditEvent event : events) {
            // Derive readable fields via the same logic as list view (rule engine + fallbacks)
            AuditEventView view = toView(event);
            String sourceText = view.sourceSystemText != null ? view.sourceSystemText : mapSourceSystemText(event.getSourceSystem());
            String resultText = view.resultText != null ? view.resultText : mapResultText(event.getResult());
            java.util.Map<String, Object> details = parseDetails(event);
            String targetTable = details.getOrDefault("target_table", "").toString();
            String targetId = details.getOrDefault("target_id", "").toString();
            String opType = view.operationType != null ? view.operationType : "";
            String opContent = view.operationContent != null ? view.operationContent : "";
            String logTypeText = view.logTypeText != null ? view.logTypeText : (event.getEventClass() != null && event.getEventClass().trim().equalsIgnoreCase("SecurityEvent") ? "安全审计" : "操作审计");
            sb
                .append(event.getId()).append(',')
                .append(event.getOccurredAt()).append(',')
                .append(escape(event.getSourceSystem())).append(',')
                .append(escape(event.getEventClass())).append(',')
                .append(escape(event.getEventType())).append(',')
                .append(escape(event.getModule())).append(',')
                .append(escape(event.getAction())).append(',')
                .append(escape(event.getSummary())).append(',')
                .append(escape(event.getOperatorId())).append(',')
                .append(escape(event.getOperatorName())).append(',')
                .append(escape(event.getOrgCode())).append(',')
                .append(escape(event.getOrgName())).append(',')
                .append(escape(event.getResult())).append(',')
                .append(escape(event.getResourceType())).append(',')
                .append(escape(event.getResourceId())).append(',')
                .append(escape(event.getClientIp())).append(',')
                .append(escape(event.getClientAgent())).append(',')
                .append(escape(event.getHttpMethod())).append(',')
                .append(escape(event.getRequestUri())).append(',')
                .append(escape(sourceText)).append(',')
                .append(escape(resultText)).append(',')
                .append(escape(targetTable)).append(',')
                .append(escape(targetId)).append(',')
                .append(escape(opType)).append(',')
                .append(escape(opContent)).append(',')
                .append(escape(logTypeText))
                .append('\n');
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audits.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEventDetailView>> detail(@PathVariable Long id) {
        VisibilityScope scope = resolveVisibilityScope();
        AuditEvent event = auditService
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在"));
        ensureReadable(scope, event);
        AuditEventDetailView view = toDetailView(event);
        return ResponseEntity.ok(ApiResponse.ok(view));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> purge() {
        long removed = auditService.purgeAll();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("removed", removed)));
    }

    private Page<AuditEvent> executeSearch(
        VisibilityScope scope,
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        Pageable pageable
    ) {
        if (scope.hasAllowedActors()) {
            return auditService.searchAllowedActors(
                actor,
                module,
                action,
                sourceSystem,
                eventType,
                result,
                resourceType,
                resource,
                requestUri,
                from,
                to,
                clientIp,
                List.copyOf(scope.allowedActors()),
                pageable
            );
        }
        if (scope.hasExcludedActors()) {
            return auditService.searchExcludeActors(
                actor,
                module,
                action,
                sourceSystem,
                eventType,
                result,
                resourceType,
                resource,
                requestUri,
                from,
                to,
                clientIp,
                List.copyOf(scope.excludedActors()),
                pageable
            );
        }
        return auditService.search(
            actor,
            module,
            action,
            sourceSystem,
            eventType,
            result,
            resourceType,
            resource,
            requestUri,
            from,
            to,
            clientIp,
            pageable
        );
    }

    private VisibilityScope resolveVisibilityScope() {
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        if (login.isEmpty()) {
            return UNRESTRICTED_SCOPE;
        }
        String normalized = login.orElseThrow().trim().toLowerCase(Locale.ROOT);
        if ("authadmin".equals(normalized)) {
            return new VisibilityScope(Set.of("auditadmin"), Set.of());
        }
        if ("auditadmin".equals(normalized)) {
            return new VisibilityScope(Set.of(), Set.of("auditadmin"));
        }
        return UNRESTRICTED_SCOPE;
    }

    private void ensureReadable(VisibilityScope scope, AuditEvent event) {
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在");
        }
        String actor = Optional.ofNullable(event.getActor()).map(String::trim).orElse("").toLowerCase(Locale.ROOT);
        if (scope.hasAllowedActors() && !scope.allowedActors().contains(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该审计记录");
        }
        if (scope.hasExcludedActors() && scope.excludedActors().contains(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该审计记录");
        }
    }

    private AuditEventView toView(AuditEvent event) {
        AuditEventView view = new AuditEventView();
        view.eventId = event.getEventUuid() != null ? event.getEventUuid().toString() : null;
        view.id = event.getId();
        view.occurredAt = event.getOccurredAt();
        view.actor = event.getActor();
        view.module = event.getModule();
        view.action = event.getAction();
        view.resourceType = event.getResourceType();
        view.resourceId = event.getResourceId();
        view.clientIp = event.getClientIp();
        view.clientAgent = event.getClientAgent();
        view.httpMethod = event.getHttpMethod();
        view.result = event.getResult();
        view.operationRuleHit = Boolean.FALSE;
        if (view.result != null) {
            String r = view.result.trim().toUpperCase(java.util.Locale.ROOT);
            view.resultText = r.equals("SUCCESS") ? "成功" : (r.equals("FAILED") || r.equals("FAILURE") ? "失败" : view.result);
        }
        view.extraTags = event.getExtraTags();
        view.payloadPreview = decodePayloadPreview(event);
        // extended mapping for new audit schema
        view.sourceSystem = event.getSourceSystem();
        if (view.sourceSystem != null) {
            String s = view.sourceSystem.trim().toLowerCase(java.util.Locale.ROOT);
            view.sourceSystemText = s.equals("admin") ? "系统管理" : (s.equals("platform") ? "业务管理" : view.sourceSystem);
        }
        view.eventClass = event.getEventClass();
        view.eventType = event.getEventType();
        view.summary = event.getSummary();
        view.operatorId = event.getOperatorId();
        view.operatorName = event.getOperatorName();
        view.operatorRoles = event.getOperatorRoles();
        view.orgCode = event.getOrgCode();
        view.orgName = event.getOrgName();
        view.departmentName = view.orgName;
        // Extract minimal details for quick list rendering (requestId/target info)
        try {
            if (event.getDetails() != null && !event.getDetails().isBlank()) {
                Map<?,?> det = objectMapper.readValue(event.getDetails(), Map.class);
                Object req = det.get("请求ID");
                if (req == null) req = det.get("request_id");
                if (req != null) view.requestId = String.valueOf(req);
                Object tbl = det.get("源表");
                if (tbl == null) tbl = det.get("target_table");
                if (tbl != null) {
                    view.targetTable = String.valueOf(tbl);
                    view.targetTableLabel = resolveTableLabel(view.targetTable);
                }
                Object tid = det.get("目标ID");
                if (tid == null) tid = det.get("target_id");
                if (tid != null) view.targetId = String.valueOf(tid);
                Object tref = det.get("目标引用");
                if (tref == null) tref = det.get("target_ref");
                if (tref != null) {
                    view.targetRef = String.valueOf(tref);
                }
            }
        } catch (Exception ignore) {}

        // Rule engine: try mapping first (query-time rendering). Only uses event/requestUri/details; body/resp not persisted yet
        try {
            var mapped = opMappingEngine.resolveWithFallback(event);
            if (mapped.isPresent()) {
                var m = mapped.orElseThrow();
                if (m.ruleMatched && m.ruleId != null) {
                    view.operationRuleId = m.ruleId;
                    view.operationRuleHit = Boolean.TRUE;
                }
                if (StringUtils.hasText(m.moduleName)) {
                    view.operationModule = m.moduleName;
                    if (!StringUtils.hasText(view.module)) {
                        view.module = m.moduleName;
                    }
                }
                if (StringUtils.hasText(m.actionType) && !StringUtils.hasText(view.operationType)) {
                    view.operationType = m.actionType;
                }
                if (StringUtils.hasText(m.description) && !StringUtils.hasText(view.operationContent)) {
                    view.operationContent = m.description;
                }
                if (StringUtils.hasText(m.sourceTable)) {
                    if (!StringUtils.hasText(view.operationSourceTable)) {
                        view.operationSourceTable = m.sourceTable;
                    }
                    if (!StringUtils.hasText(view.targetTableLabel)) {
                        view.targetTableLabel = resolveTableLabel(m.sourceTable);
                    }
                    if (!StringUtils.hasText(view.targetTable)) {
                        view.targetTable = m.sourceTable;
                    }
                }
                if (StringUtils.hasText(m.eventClass)) {
                    view.operationEventClass = m.eventClass;
                    if (!StringUtils.hasText(view.eventClass)) {
                        view.eventClass = m.eventClass;
                    }
                }
            }
        } catch (Exception ignore) {}

        // Non-content derived field stays
        view.logTypeText = (view.eventClass != null && view.eventClass.trim().equalsIgnoreCase("SecurityEvent")) ? "安全审计" : "操作审计";
        return view;
    }

    private String decodePayloadPreview(AuditEvent event) {
        Object payload = decodePayload(event);
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            return map
                .entrySet()
                .stream()
                .limit(5)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        }
        if (payload instanceof Collection<?> collection) {
            return collection
                .stream()
                .limit(3)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        }
        String text = payload.toString();
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }

    private AuditEventDetailView toDetailView(AuditEvent event) {
        AuditEventView base = toView(event);
        AuditEventDetailView detail = new AuditEventDetailView();
        detail.id = base.id;
        detail.occurredAt = base.occurredAt;
        detail.actor = base.actor;
        detail.module = base.module;
        detail.action = base.action;
        detail.resourceType = base.resourceType;
        detail.resourceId = base.resourceId;
        detail.clientIp = base.clientIp;
        detail.clientAgent = base.clientAgent;
        detail.httpMethod = base.httpMethod;
        detail.result = base.result;
        detail.logTypeText = base.logTypeText;
        detail.extraTags = base.extraTags;
        detail.payloadPreview = base.payloadPreview;
        // extended
        detail.sourceSystem = base.sourceSystem;
        detail.eventClass = base.eventClass;
        detail.eventType = base.eventType;
        detail.summary = base.summary;
        detail.operatorId = base.operatorId;
        detail.operatorName = base.operatorName;
        detail.operatorRoles = base.operatorRoles;
        detail.orgCode = base.orgCode;
        detail.orgName = base.orgName;
        detail.departmentName = base.departmentName;
        detail.requestId = base.requestId;
        detail.targetTable = base.targetTable;
        detail.targetTableLabel = base.targetTableLabel;
        detail.targetId = base.targetId;
        detail.targetRef = base.targetRef;
        detail.operationType = base.operationType;
        detail.operationContent = base.operationContent;
        detail.operationRuleHit = base.operationRuleHit;
        detail.operationRuleId = base.operationRuleId;
        detail.operationModule = base.operationModule;
        detail.operationSourceTable = base.operationSourceTable;
        detail.operationEventClass = base.operationEventClass;
        detail.payload = decodePayload(event);
        // parse details json if present
        try {
            if (event.getDetails() != null && !event.getDetails().isBlank()) {
                detail.details = objectMapper.readValue(event.getDetails(), Object.class);
            }
        } catch (Exception ignore) {}
        return detail;
    }

    private Object decodePayload(AuditEvent event) {
        try {
            byte[] decrypted = auditService.decryptPayload(event);
            if (decrypted.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(decrypted, Object.class);
            } catch (Exception parseEx) {
                String fallback = new String(decrypted, StandardCharsets.UTF_8);
                return fallback;
            }
        } catch (Exception ex) {
            log.warn("Failed to decode audit payload for id {}", event.getId(), ex);
            return null;
        }
    }

    private Sort parseSort(String sortExpression) {
        if (!StringUtils.hasText(sortExpression)) {
            return Sort.by(Sort.Direction.DESC, "occurredAt");
        }
        String[] parts = sortExpression.split(",");
        if (parts.length != 2) {
            return Sort.by(Sort.Direction.DESC, "occurredAt");
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, parts[0]);
    }

    private Instant parseInstant(String value, String parameter) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestAlertException("Invalid timestamp for parameter " + parameter, "audit", "invalidTimestamp");
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String mapSourceSystemText(String source) {
        if (source == null || source.isBlank()) return "";
        String s = source.trim().toLowerCase();
        if (s.equals("admin") || s.equals("management") || s.equals("manager")) return "系统管理";
        if (s.equals("platform")) return "业务管理";
        return source;
    }

    private String mapResultText(String result) {
        if (result == null) return "";
        String r = result.trim().toUpperCase();
        if ("SUCCESS".equals(r)) return "成功";
        if ("FAILED".equals(r) || "FAILURE".equals(r)) return "失败";
        return result;
    }

    private java.util.Map<String, Object> parseDetails(AuditEvent event) {
        try {
            if (event.getDetails() == null || event.getDetails().isBlank()) return java.util.Map.of();
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<?, ?> raw = om.readValue(event.getDetails(), java.util.Map.class);
            java.util.Map<String, Object> out = new java.util.HashMap<>();
            if (raw.containsKey("源表")) out.put("target_table", raw.get("源表"));
            else if (raw.containsKey("target_table")) out.put("target_table", raw.get("target_table"));
            if (raw.containsKey("目标ID")) out.put("target_id", raw.get("目标ID"));
            else if (raw.containsKey("target_id")) out.put("target_id", raw.get("target_id"));
            return out;
        } catch (Exception ignore) {
            return java.util.Map.of();
        }
    }

    private static final class AuditEventDetailView extends AuditEventView {
        public Object payload;
        public Object details;
    }
}
