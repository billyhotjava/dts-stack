package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryQueryService;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryView;
import com.yuzhi.dts.admin.service.auditv2.AuditEntryTargetView;
import com.yuzhi.dts.admin.service.auditv2.AuditSearchCriteria;
import com.yuzhi.dts.admin.service.auditv2.ModuleOption;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.AuditResourceDictionaryService;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.OperationMappingEngine;
import com.yuzhi.dts.admin.service.auditv2.OperationMappingEngine.RuleSummary;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.net.IpAddressUtils;
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
    private static final Set<String> DETAIL_KEYS_TO_HIDE = Set.of("attributes", "actionDisplay", "target");

    private final AuditEntryQueryService auditQueryService;
    private final OperationMappingEngine opMappingEngine;
    private final AuditResourceDictionaryService resourceDictionary;
    private final AuditV2Service auditV2Service;
    private final AdminUserService adminUserService;
    private final ObjectMapper objectMapper;
    public AuditLogResource(
        AuditEntryQueryService auditQueryService,
        OperationMappingEngine opMappingEngine,
        AuditResourceDictionaryService resourceDictionary,
        AuditV2Service auditV2Service,
        AdminUserService adminUserService,
        ObjectMapper objectMapper
    ) {
        this.auditQueryService = auditQueryService;
        this.opMappingEngine = opMappingEngine;
        this.resourceDictionary = resourceDictionary;
        this.auditV2Service = auditV2Service;
        this.adminUserService = adminUserService;
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
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
            actor,
            module,
            operationType,
            actionCode,
            operationGroup,
            sourceSystem,
            result,
            targetTable,
            targetId,
            clientIp,
            keyword,
            fromDate,
            toDate,
            scope.allowedActors(),
            scope.excludedActors(),
            false
        );
        Page<AuditEntryView> resultPage = auditQueryService.search(criteria, pageable);
        List<AuditEntryView> views = resultPage.getContent();
        Map<String, String> displayOverrides = resolvePlatformDisplayNames(views);
        List<Map<String, Object>> content = new ArrayList<>(views.size());
        for (AuditEntryView view : views) {
            Map<String, Object> row = toResponse(view, false);
            applyDisplayNameOverride(row, displayOverrides);
            content.add(row);
        }
        Map<String, Object> payload = Map.of(
            "content",
            content,
            "page",
            resultPage.getNumber(),
            "size",
            resultPage.getSize(),
            "totalElements",
            resultPage.getTotalElements(),
            "totalPages",
            resultPage.getTotalPages()
        );
        recordAuditLogAction(
            ButtonCodes.AUDIT_LOG_QUERY,
            criteria,
            pageable,
            resultPage.getTotalElements(),
            content.size(),
            request
        );
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable Long id) {
        VisibilityScope scope = resolveVisibilityScope();
        AuditEntryView view = auditQueryService
            .findById(id, true)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在"));
        ensureReadable(scope, view);
        Map<String, Object> body = toResponse(view, true);
        applyDisplayNameOverride(body, resolvePlatformDisplayNames(List.of(view)));
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
            actor,
            module,
            operationType,
            actionCode,
            operationGroup,
            sourceSystem,
            result,
            targetTable,
            targetId,
            clientIp,
            keyword,
            fromDate,
            toDate,
            scope.allowedActors(),
            scope.excludedActors(),
            true
        );
        Page<AuditEntryView> exportPage = auditQueryService.search(criteria, Pageable.unpaged());
        List<AuditEntryView> views = exportPage.getContent();
        Map<String, String> displayOverrides = resolvePlatformDisplayNames(views);
        List<Map<String, Object>> records = new ArrayList<>(views.size());
        for (AuditEntryView view : views) {
            Map<String, Object> row = toResponse(view, true);
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
        recordAuditLogAction(
            ButtonCodes.AUDIT_LOG_EXPORT,
            criteria,
            Pageable.unpaged(),
            exportPage.getTotalElements(),
            records.size(),
            request
        );
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
                    entry.put("sourceSystemLabel", mapSourceSystemText(source));
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

    private Map<String, Object> toResponse(AuditEntryView view, boolean includeDetails) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", view.id());
        map.put("occurredAt", view.occurredAt() != null ? view.occurredAt().toString() : null);
        map.put("sourceSystem", view.sourceSystem());
        map.put("sourceSystemText", mapSourceSystemText(view.sourceSystem()));
        String moduleKey = StringUtils.defaultIfBlank(view.moduleKey(), "general");
        String moduleLabel = StringUtils.isNotBlank(view.moduleName())
            ? view.moduleName()
            : resourceDictionary.resolveLabel(moduleKey).orElse(moduleKey);
        map.put("module", moduleLabel);
        map.put("moduleKey", moduleKey);
        map.put("buttonCode", view.buttonCode());
        map.put("action", StringUtils.defaultIfBlank(view.operationName(), view.operationCode()));
        map.put("operationCode", view.operationCode());
        AuditOperationKind kind = view.operationKind();
        String normalizedCode = normalizeOperationTypeCode(view);
        map.put("operationTypeCode", normalizedCode);
        map.put("operationType", mapOperationTypeLabel(normalizedCode));
        map.put("operationTypeRaw", kind != null ? kind.displayName() : null);
        map.put("operationContent", StringUtils.defaultIfBlank(view.summary(), view.operationName()));
        map.put("summary", view.summary());
        map.put("operationGroup", view.operationGroup());
        map.put("result", view.result());
        map.put("resultText", view.resultLabel());
        map.put("logTypeText", mapLogType(view.sourceSystem()));
        map.put("eventClass", "AUDIT_ENTRY");
        map.put("eventType", view.operationKind() != null ? view.operationKind().code() : "OTHER");

        map.put("actor", view.actorId());
        map.put("actorName", view.actorName());
        map.put("actorRoles", view.actorRoles());
        map.put("actorRole", view.actorRoles().isEmpty() ? null : view.actorRoles().get(0));
        map.put("operatorId", view.actorId());
        map.put("operatorName", view.actorName());
        map.put("operatorRoles", toJson(view.actorRoles()));
        map.put("orgCode", null);
        map.put("orgName", null);
        map.put("departmentName", null);

        map.put("clientIp", view.clientIp());
        map.put("clientAgent", view.clientAgent());
        map.put("requestUri", view.requestUri());
        map.put("httpMethod", view.httpMethod());

        map.put("changeRequestRef", view.changeRequestRef());

        List<String> targetIds = new ArrayList<>();
        Map<String, String> targetLabels = new LinkedHashMap<>();
        String targetTable = null;
        for (AuditEntryTargetView target : view.targets()) {
            if (target == null) {
                continue;
            }
            String table = safeTrim(target.table());
            String id = safeTrim(target.id());
            String label = safeTrim(target.label());
            if (targetTable == null && StringUtils.isNotBlank(table)) {
                targetTable = table;
            }
            if (StringUtils.isNotBlank(id)) {
                targetIds.add(id);
                targetLabels.put(id, StringUtils.defaultIfBlank(label, id));
            }
        }
        map.put("resourceType", targetTable);
        map.put("resourceId", targetIds.isEmpty() ? null : targetIds.get(0));
        map.put("targetTable", targetTable);
        map.put("targetId", targetIds.isEmpty() ? null : targetIds.get(0));
        map.put("targetIds", targetIds);
        map.put("targetLabels", targetLabels.isEmpty() ? Map.of() : targetLabels);
        if (StringUtils.isNotBlank(targetTable)) {
            map.put("targetTableLabel", resourceDictionary.resolveLabel(targetTable).orElse(targetTable));
        }

        map.put("metadata", view.metadata());
        map.put("extraAttributes", view.extraAttributes());

        Map<String, Object> detailPayload;
        if (includeDetails) {
            detailPayload = new LinkedHashMap<>();
            if (view.details() != null) {
                view
                    .details()
                    .forEach((key, value) -> {
                        if (!DETAIL_KEYS_TO_HIDE.contains(key)) {
                            detailPayload.put(key, value);
                        }
                    });
            }
            if (!view.metadata().isEmpty()) {
                detailPayload.putIfAbsent("metadata", view.metadata());
            }
            if (StringUtils.isNotBlank(targetTable)) {
                detailPayload.putIfAbsent("targetTable", targetTable);
                detailPayload.putIfAbsent(
                    "targetTableLabel",
                    resourceDictionary.resolveLabel(targetTable).orElse(targetTable)
                );
            }
            if (!targetIds.isEmpty()) {
                detailPayload.putIfAbsent("targetIds", targetIds);
            }
        } else {
            detailPayload = Map.of();
        }
        map.put("details", detailPayload);
        map.put("payload", detailPayload);

        Object requestId = includeDetails ? detailPayload.getOrDefault("requestId", detailPayload.get("request_id")) : null;
        if (requestId != null) {
            map.put("requestId", requestId);
        }
        Object approvalSummary = includeDetails ? detailPayload.get("approvalSummary") : null;
        if (approvalSummary != null) {
            map.put("approvalSummary", approvalSummary);
        }

        return map;
    }

    private void recordAuditLogAction(
        String buttonCode,
        AuditSearchCriteria criteria,
        Pageable pageable,
        long totalElements,
        int returnedCount,
        HttpServletRequest request
    ) {
        if (auditV2Service == null) {
            return;
        }
        String actor = SecurityUtils.getCurrentAuditableLogin();
        AuditActionRequest.Builder builder = AuditActionRequest
            .builder(actor, buttonCode)
            .actorName(actor)
            .actorRoles(SecurityUtils.getCurrentUserAuthorities())
            .summary(ButtonCodes.AUDIT_LOG_EXPORT.equals(buttonCode) ? "导出审计日志" : "查询审计日志")
            .result(AuditResultStatus.SUCCESS)
            .allowEmptyTargets();
        if ("system".equalsIgnoreCase(actor)) {
            builder.allowSystemActor();
        }
        if (request != null) {
            builder.client(clientIp(request), request.getHeader("User-Agent"));
            builder.request(request.getRequestURI(), request.getMethod());
        } else {
            builder.request(
                ButtonCodes.AUDIT_LOG_EXPORT.equals(buttonCode) ? "/api/audit-logs/export" : "/api/audit-logs",
                "GET"
            );
        }
        Map<String, Object> detail = buildAuditLogDetail(buttonCode, criteria, pageable, totalElements, returnedCount);
        if (!detail.isEmpty()) {
            builder.detail("detail", detail);
        }
        builder.metadata("totalElements", totalElements);
        builder.metadata("returnedCount", returnedCount);
        builder.metadata("export", ButtonCodes.AUDIT_LOG_EXPORT.equals(buttonCode));
        try {
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit log action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private Map<String, Object> buildAuditLogDetail(
        String buttonCode,
        AuditSearchCriteria criteria,
        Pageable pageable,
        long totalElements,
        int returnedCount
    ) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> filters = buildFilterDetail(criteria);
        if (!filters.isEmpty()) {
            detail.put("filters", filters);
        }
        Map<String, Object> pagination = buildPaginationDetail(pageable, returnedCount, totalElements);
        if (!pagination.isEmpty()) {
            detail.put("pagination", pagination);
        }
        detail.put("returnedCount", returnedCount);
        detail.put("totalElements", totalElements);
        detail.put("actionType", ButtonCodes.AUDIT_LOG_EXPORT.equals(buttonCode) ? "export" : "query");
        return detail;
    }

    private Map<String, Object> buildFilterDetail(AuditSearchCriteria criteria) {
        LinkedHashMap<String, Object> filters = new LinkedHashMap<>();
        if (criteria == null) {
            return filters;
        }
        putIfHasText(filters, "actor", criteria.actor());
        putIfHasText(filters, "module", criteria.module());
        putIfHasText(filters, "operationType", criteria.operationKind());
        putIfHasText(filters, "actionCode", criteria.action());
        putIfHasText(filters, "operationGroup", criteria.operationGroup());
        putIfHasText(filters, "sourceSystem", criteria.sourceSystem());
        putIfHasText(filters, "result", criteria.result());
        putIfHasText(filters, "targetTable", criteria.targetTable());
        putIfHasText(filters, "targetId", criteria.targetId());
        putIfHasText(filters, "clientIp", criteria.clientIp());
        putIfHasText(filters, "keyword", criteria.keyword());
        if (criteria.from() != null) {
            filters.put("from", formatInstant(criteria.from()));
        }
        if (criteria.to() != null) {
            filters.put("to", formatInstant(criteria.to()));
        }
        return filters;
    }

    private Map<String, Object> buildPaginationDetail(Pageable pageable, int returnedCount, long totalElements) {
        LinkedHashMap<String, Object> info = new LinkedHashMap<>();
        info.put("returnedCount", returnedCount);
        info.put("totalElements", totalElements);
        if (pageable == null) {
            info.put("paged", false);
            return info;
        }
        boolean paged = pageable.isPaged();
        info.put("paged", paged);
        if (paged) {
            info.put("page", pageable.getPageNumber());
            info.put("size", pageable.getPageSize());
        }
        if (pageable.getSort() != null && pageable.getSort().isSorted()) {
            info.put("sort", pageable.getSort().toString());
        }
        return info;
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-Forwarded-For");
        String xfip = StringUtils.isNotBlank(header) ? header.split(",")[0].trim() : null;
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        return IpAddressUtils.resolveClientIp(xfip, realIp, remote);
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

    private String mapSourceSystemText(String sourceSystem) {
        if (StringUtils.isBlank(sourceSystem)) {
            return "系统管理";
        }
        return switch (sourceSystem.trim().toLowerCase(Locale.ROOT)) {
            case "platform" -> "业务管理";
            default -> "系统管理";
        };
    }

    private String mapResultText(String result) {
        if (StringUtils.isBlank(result)) {
            return "成功";
        }
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "FAILED", "FAIL", "ERROR" -> "失败";
            case "PENDING" -> "处理中";
            default -> "成功";
        };
    }

    private String mapLogType(String sourceSystem) {
        return "platform".equalsIgnoreCase(StringUtils.trimToEmpty(sourceSystem)) ? "业务端审计" : "管理端审计";
    }

    private String normalizeOperationTypeCode(AuditEntryView view) {
        AuditOperationKind kind = view.operationKind();
        if (kind != null && kind != AuditOperationKind.OTHER) {
            return kind.code();
        }
        String candidate = StringUtils.firstNonBlank(
            extractOperationToken(view),
            view.operationCode(),
            view.operationName(),
            view.summary()
        );
        AuditOperationType type = AuditOperationType.from(candidate);
        if (type == AuditOperationType.UNKNOWN) {
            type = AuditOperationType.READ;
        }
        return type.getCode();
    }

    private String mapOperationTypeLabel(String normalizedCode) {
        AuditOperationType type = AuditOperationType.from(normalizedCode);
        if (type == AuditOperationType.UNKNOWN) {
            type = AuditOperationType.READ;
        }
        return type.getDisplayName();
    }

    private VisibilityScope resolveVisibilityScope() {
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        if (login.isEmpty()) {
            return VisibilityScope.unrestricted();
        }
        String normalized = login.orElseThrow().trim().toLowerCase(Locale.ROOT);
        if ("authadmin".equals(normalized)) {
            return new VisibilityScope(Set.of("auditadmin"), Set.of());
        }
        if ("auditadmin".equals(normalized)) {
            return new VisibilityScope(Set.of(), Set.of("auditadmin"));
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
        String sourceLabel = mapSourceSystemText(sourceSystem);
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

    private String extractOperationToken(AuditEntryView view) {
        if (view == null) {
            return null;
        }
        String direct = StringUtils.firstNonBlank(
            extractFromMapLike(view.extraAttributes(), "operationType", "operation_type"),
            extractFromMapLike(view.metadata(), "operationType", "operation_type"),
            extractFromMapLike(view.details(), "operationType", "operation_type")
        );
        if (StringUtils.isNotBlank(direct)) {
            return direct;
        }
        Object payload = view.details() != null ? view.details().get("payload") : null;
        if (payload instanceof Map<?, ?> map) {
            return extractFromMapLike(map, "operationType", "operation_type");
        }
        return null;
    }

    private String extractFromMapLike(Object source, String... keys) {
        if (!(source instanceof Map<?, ?> map) || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = map.get(key);
            if (value == null) {
                value = map.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null) {
                value = map.get(key.toUpperCase(Locale.ROOT));
            }
            if (value != null) {
                String text = safeTrim(value.toString());
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
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

    private Map<String, String> resolvePlatformDisplayNames(List<AuditEntryView> views) {
        if (views == null || views.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (AuditEntryView view : views) {
            if (view == null) {
                continue;
            }
            String source = safeTrim(view.sourceSystem());
            if (source == null || !"platform".equalsIgnoreCase(source)) {
                continue;
            }
            String actorId = safeTrim(view.actorId());
            if (!org.springframework.util.StringUtils.hasText(actorId)) {
                continue;
            }
            String actorName = safeTrim(view.actorName());
            if (org.springframework.util.StringUtils.hasText(actorName) && !actorName.equals(actorId)) {
                continue;
            }
            usernames.add(actorId);
        }
        if (usernames.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, String> resolved = adminUserService.resolveDisplayNames(usernames);
            if (resolved == null || resolved.isEmpty()) {
                return Map.of();
            }
            Map<String, String> normalized = new LinkedHashMap<>();
            resolved.forEach((key, value) -> {
                if (org.springframework.util.StringUtils.hasText(key) && org.springframework.util.StringUtils.hasText(value)) {
                    normalized.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
                }
            });
            return normalized;
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve platform actor display names: {}", ex.getMessage());
            }
            return Map.of();
        }
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

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"")) {
            return '"' + str.replace("\"", "\"\"") + '"';
        }
        return str;
    }

    private record ModuleView(String key, String title) {}

    private record CategoryView(String moduleKey, String moduleTitle, String entryKey, String entryTitle) {}

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            return value.toString();
        }
    }

    private record VisibilityScope(Set<String> allowedActors, Set<String> excludedActors) {
        static VisibilityScope unrestricted() {
            return new VisibilityScope(Set.of(), Set.of());
        }
    }
}
