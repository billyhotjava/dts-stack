package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditEventView;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.errors.BadRequestAlertException;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.AUTH_ADMIN + "','" + AuthoritiesConstants.AUDITOR_ADMIN + "')")
public class AuditLogResource {

    private static final Logger log = LoggerFactory.getLogger(AuditLogResource.class);

    private final AdminAuditService auditService;
    private final AuditActionCatalog actionCatalog;
    private final ObjectMapper objectMapper;

    public AuditLogResource(AdminAuditService auditService, ObjectMapper objectMapper, AuditActionCatalog actionCatalog) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.actionCatalog = actionCatalog;
    }

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> modules() {
        // Build unique module list from audit action catalog
        Map<String, String> unique = new java.util.LinkedHashMap<>();
        for (AuditActionDefinition def : actionCatalog.listAll()) {
            if (def.getModuleKey() != null && def.getModuleTitle() != null) {
                unique.putIfAbsent(def.getModuleKey(), def.getModuleTitle());
            }
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        unique.forEach((key, title) -> out.add(java.util.Map.of("key", key, "title", title)));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categories() {
        // Build a flat, de-duplicated list of (moduleKey, moduleTitle, entryKey, entryTitle)
        Map<String, String> moduleTitles = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (AuditActionDefinition def : actionCatalog.listAll()) {
            String moduleKey = def.getModuleKey();
            String moduleTitle = def.getModuleTitle();
            String entryKey = def.getEntryKey();
            String entryTitle = def.getEntryTitle();
            if (moduleKey == null || entryKey == null) continue;
            moduleTitles.putIfAbsent(moduleKey, moduleTitle);
            String key = moduleKey + "::" + entryKey;
            if (uniq.add(key)) {
                out.add(java.util.Map.of(
                    "moduleKey", moduleKey,
                    "moduleTitle", moduleTitles.getOrDefault(moduleKey, moduleKey),
                    "entryKey", entryKey,
                    "entryTitle", entryTitle != null ? entryTitle : entryKey
                ));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

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

        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isSysAdmin = hasAuthority(auth, AuthoritiesConstants.SYS_ADMIN);
        boolean isAuthAdmin = hasAuthority(auth, AuthoritiesConstants.AUTH_ADMIN);
        boolean isAuditAdmin = hasAuthority(auth, AuthoritiesConstants.AUDITOR_ADMIN);

        Page<AuditEvent> pageResult;
        if (isSysAdmin) {
            // sysadmin：不可查看任何审计记录
            pageResult = new PageImpl<>(java.util.List.of(), pageable, 0);
        } else if (isAuthAdmin) {
            java.util.List<String> allowed = java.util.List.of(
                AuthoritiesConstants.SYS_ADMIN,
                AuthoritiesConstants.AUDITOR_ADMIN
            );
            String current = SecurityUtils.getCurrentUserLogin().orElse(null);
            java.util.List<String> excludedActors = current != null ? java.util.List.of(current.toLowerCase()) : java.util.List.of();
            pageResult = (excludedActors.isEmpty()) ? auditService.searchAllowedRoles(
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
                allowed,
                pageable
            ) : auditService.searchAllowedRolesExcludeActors(
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
                allowed,
                excludedActors,
                pageable
            );
        } else if (isAuditAdmin) {
            String current = SecurityUtils.getCurrentUserLogin().orElse(null);
            java.util.List<String> excludedActors = current != null ? java.util.List.of(current.toLowerCase()) : java.util.List.of();
            pageResult = (excludedActors.isEmpty()) ? auditService.searchExcludeRoles(
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
                java.util.List.of(AuthoritiesConstants.AUDITOR_ADMIN),
                pageable
            ) : auditService.searchExcludeRolesExcludeActors(
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
                java.util.List.of(AuthoritiesConstants.AUDITOR_ADMIN),
                excludedActors,
                pageable
            );
        } else {
            pageResult = auditService.search(
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
        }
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
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isSysAdmin = hasAuthority(auth, AuthoritiesConstants.SYS_ADMIN);
        boolean isAuthAdmin = hasAuthority(auth, AuthoritiesConstants.AUTH_ADMIN);
        boolean isAuditAdmin = hasAuthority(auth, AuthoritiesConstants.AUDITOR_ADMIN);

        List<AuditEvent> events;
        if (isSysAdmin) {
            // sysadmin：不可导出任何审计记录
            events = java.util.List.of();
        } else if (isAuthAdmin) {
            String current = SecurityUtils.getCurrentUserLogin().orElse(null);
            java.util.List<String> excludedActors = current != null ? java.util.List.of(current.toLowerCase()) : java.util.List.of();
            events = excludedActors.isEmpty()
                ? auditService.findAllForExportAllowedRoles(
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
                    java.util.List.of(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
                )
                : auditService.findAllForExportAllowedRolesExcludeActors(
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
                    java.util.List.of(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN),
                    excludedActors
                );
        } else if (isAuditAdmin) {
            String current = SecurityUtils.getCurrentUserLogin().orElse(null);
            java.util.List<String> excludedActors = current != null ? java.util.List.of(current.toLowerCase()) : java.util.List.of();
            events = excludedActors.isEmpty()
                ? auditService.findAllForExportExcludeRoles(
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
                    java.util.List.of(AuthoritiesConstants.AUDITOR_ADMIN)
                )
                : auditService.findAllForExportExcludeRolesExcludeActors(
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
                    java.util.List.of(AuthoritiesConstants.AUDITOR_ADMIN),
                    excludedActors
                );
        } else {
            events = auditService.findAllForExport(
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
                clientIp
            );
        }
        StringBuilder sb = new StringBuilder();
        sb.append("id,timestamp,source,event_class,event_type,module,action,summary,operator_id,operator_name,org_code,org_name,result,resource_type,resource_id,client_ip,client_agent,http_method,request_uri,来源系统,结果中文,目标表,目标ID\n");
        for (AuditEvent event : events) {
            String sourceText = mapSourceSystemText(event.getSourceSystem());
            String resultText = mapResultText(event.getResult());
            java.util.Map<String, Object> details = parseDetails(event);
            String targetTable = details.getOrDefault("target_table", "").toString();
            String targetId = details.getOrDefault("target_id", "").toString();
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
                .append(escape(targetId))
                .append('\n');
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audits.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEventDetailView>> detail(@PathVariable Long id) {
        AuditEvent event = auditService
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在"));
        AuditEventDetailView view = toDetailView(event);
        return ResponseEntity.ok(ApiResponse.ok(view));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> purge() {
        long removed = auditService.purgeAll();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.recordAction(actor, "ADMIN_AUDIT_PURGE", AuditStage.SUCCESS, "audit", Map.of("removed", removed));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("removed", removed)));
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
            view.sourceSystemText = s.equals("admin") ? "管理端" : (s.equals("platform") ? "业务端" : view.sourceSystem);
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
                Object req = det.get("request_id");
                if (req != null) view.requestId = String.valueOf(req);
                Object tbl = det.get("target_table");
                if (tbl != null) {
                    view.targetTable = String.valueOf(tbl);
                    view.targetTableLabel = mapTableLabel(view.targetTable);
                }
                Object tid = det.get("target_id");
                if (tid != null) view.targetId = String.valueOf(tid);
                Object tref = det.get("target_ref");
                if (tref != null) {
                    view.targetRef = String.valueOf(tref);
                } else {
                    // Synthesize when missing: "<table>+<id>"
                    if (view.targetTable != null && view.targetId != null) {
                        view.targetRef = view.targetTable + "+" + view.targetId;
                    }
                }
            }
        } catch (Exception ignore) {}
        return view;
    }

    private String mapTableLabel(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.equals("admin_keycloak_user") || k.equals("admin") || k.equals("admin.auth") || k.equals("user")) return "用户";
        if (k.equals("portal_menu") || k.equals("menu") || k.equals("portal.menus") || k.equals("portal-menus")) return "门户菜单";
        return key;
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
        if (s.equals("admin") || s.equals("management") || s.equals("manager")) return "管理端";
        if (s.equals("platform")) return "业务端";
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
            if (raw.containsKey("target_table")) out.put("target_table", raw.get("target_table"));
            if (raw.containsKey("target_id")) out.put("target_id", raw.get("target_id"));
            return out;
        } catch (Exception ignore) {
            return java.util.Map.of();
        }
    }

    private boolean hasAuthority(org.springframework.security.core.Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (org.springframework.security.core.GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }

    private static final class AuditEventDetailView extends AuditEventView {
        public Object payload;
        public Object details;
    }
}
