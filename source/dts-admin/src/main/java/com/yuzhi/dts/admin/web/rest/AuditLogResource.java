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
                    view.targetTableLabel = mapTableLabel(view.targetTable);
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

        // Derived fields for presentation
        view.logTypeText = (view.eventClass != null && view.eventClass.trim().equalsIgnoreCase("SecurityEvent")) ? "安全审计" : "操作审计";
        String method = event.getHttpMethod() == null ? "" : event.getHttpMethod().trim().toUpperCase();
        String actionRaw = event.getAction() == null ? "" : event.getAction().trim();
        String actionUpper = actionRaw.toUpperCase(java.util.Locale.ROOT);
        String actionLower = actionRaw.toLowerCase(java.util.Locale.ROOT);
        // 更稳健的登录/登出识别：
        // 1) 优先匹配登出（避免 LOGIN/OUT 等复合词干扰），支持英文与中文同义词
        if (actionUpper.contains("LOGOUT") || actionUpper.contains("SIGNOUT") || actionUpper.contains("LOGOFF")
            || actionLower.contains("登出") || actionLower.contains("退出")) {
            view.operationType = "登出";
        }
        else if (actionUpper.contains("LOGIN") || actionUpper.contains("LOG IN")
            || actionLower.contains("登录") || actionLower.contains("登入")) {
            view.operationType = "登录";
        }
        else if ("GET".equals(method)) view.operationType = "查询";
        else if ("PUT".equals(method)) view.operationType = "新增";
        else if ("POST".equals(method)) view.operationType = "修改";
        else if ("PATCH".equals(method)) view.operationType = "部分更新";
        else if ("DELETE".equals(method)) view.operationType = "删除";
        else {
            // Fallback by action code when HTTP method is unavailable (recordAction)
            if (actionUpper.contains("CREATE")) view.operationType = "新增";
            else if (actionUpper.contains("UPDATE") || actionUpper.contains("ENABLE") || actionUpper.contains("DISABLE") || actionUpper.contains("RESET")) view.operationType = "修改";
            else if (actionUpper.contains("DELETE") || actionUpper.contains("REMOVE")) view.operationType = "删除";
            else if (actionUpper.contains("VIEW") || actionUpper.contains("LIST") || actionUpper.contains("SEARCH")) view.operationType = "查询";
            else view.operationType = "操作";
        }

        String targetLabel = view.targetTableLabel != null ? view.targetTableLabel : mapTableLabel(view.resourceType);
        if (!hasChinese(targetLabel)) {
            targetLabel = null; // 禁止显示英文/数字等非中文名称
        }
        // Use approval record's 操作类型 as operationContent when applicable
        try {
            if (isApprovalEvent(event)) {
                // Extract type from details if present
                String approvalType = null;
                if (event.getDetails() != null && !event.getDetails().isBlank()) {
                    Map<?,?> det = objectMapper.readValue(event.getDetails(), Map.class);
                    Object t = det.get("type");
                    if (t == null) t = det.get("approvalType");
                    if (t != null) approvalType = String.valueOf(t);
                }
                String approvalText = mapApprovalTypeToContent(approvalType);
                if (approvalText != null && !approvalText.isBlank()) {
                    String nameSuffix = (view.targetRef != null && !view.targetRef.isBlank()) ? "（" + view.targetRef + "）" : "";
                    view.operationContent = approvalText + nameSuffix;
                    return view;
                }
            }
        } catch (Exception ignore) {}
        String nameSuffix = (hasChinese(view.targetRef)) ? "（" + view.targetRef + "）" : "";
        if (view.operationType.equals("查询")) {
            // 列表 vs 单个对象：GET 且无目标ID -> 视为“列表”查询
            boolean isList = isListQuery(event, view);
            if (isList) {
                view.operationContent = (targetLabel == null) ? "查询" : ("查询了" + targetLabel + "列表");
            } else {
                view.operationContent = (targetLabel == null) ? "查询" : ("查询了" + targetLabel + nameSuffix);
            }
        } else if (view.operationType.equals("新增")) {
            view.operationContent = (targetLabel == null) ? "新建" : ("新建了" + targetLabel + nameSuffix);
        } else if (view.operationType.equals("修改")) {
            view.operationContent = (targetLabel == null) ? "修改" : ("修改了" + targetLabel + nameSuffix);
        } else if (view.operationType.equals("部分更新")) {
            view.operationContent = (targetLabel == null) ? "部分更新" : ("部分更新了" + targetLabel + nameSuffix);
        } else if (view.operationType.equals("删除")) {
            view.operationContent = (targetLabel == null) ? "删除" : ("删除了" + targetLabel + nameSuffix);
        } else if (view.operationType.equals("登录")) {
            view.operationContent = "登录系统";
        } else if (view.operationType.equals("登出")) {
            view.operationContent = "登出系统";
        } else {
            view.operationContent = (targetLabel == null) ? "执行了操作" : ("操作了" + targetLabel + nameSuffix);
        }
        return view;
    }

    private boolean hasChinese(String s) {
        if (s == null || s.isBlank()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') return true;
        }
        return false;
    }

    private String mapTableLabel(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.equals("admin_keycloak_user") || k.equals("admin") || k.equals("admin.auth") || k.equals("user")) return "用户";
        if (k.equals("portal_menu") || k.equals("menu") || k.equals("portal.menus") || k.equals("portal-menus")) return "门户菜单";
        if (k.equals("role") || k.startsWith("role_")) return "角色";
        if (k.equals("admin_role_assignment") || k.equals("role_assignment") || k.equals("role_assignments")) return "用户角色";
        if (k.equals("approval_requests") || k.equals("approval_request") || k.equals("approvals") || k.equals("approval")) return "审批请求";
        if (k.equals("organization") || k.equals("organization_node") || k.equals("org") || k.startsWith("admin.org")) return "部门";
        if (k.equals("localization") || k.contains("localization")) return "界面语言";
        return key;
    }

    private boolean isListQuery(AuditEvent event, AuditEventView view) {
        String method = event.getHttpMethod() == null ? "" : event.getHttpMethod().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"GET".equals(method)) return false;
        // 无目标ID 视为“非单对象” -> 列表
        if (view.targetId != null && !view.targetId.isBlank()) return false;
        return true;
    }

    private boolean isApprovalEvent(AuditEvent event) {
        String action = event.getAction() == null ? "" : event.getAction().toUpperCase(java.util.Locale.ROOT);
        String rt = event.getResourceType() == null ? "" : event.getResourceType().toLowerCase(java.util.Locale.ROOT);
        if (action.contains("APPROVAL")) return true;
        if (rt.equals("approvals") || rt.equals("approval") || rt.equals("approval_requests") || rt.equals("approval_request") || rt.equals("admin_approval_item")) return true;
        return false;
    }

    private String mapApprovalTypeToContent(String type) {
        if (type == null || type.isBlank()) return null;
        String t = type.trim().toUpperCase(java.util.Locale.ROOT);
        if (t.contains("GRANT_ROLE")) return "授予角色";
        if (t.contains("REVOKE_ROLE")) return "撤销角色";
        if (t.equals("RESET_PASSWORD") || t.contains("PASSWORD")) return "重置密码";
        if (t.equals("SET_ENABLED") || t.contains("ENABLE")) return "启用/禁用用户";
        if (t.equals("SET_PERSON_LEVEL") || t.contains("PERSON_LEVEL")) return "调整人员密级";
        if (t.startsWith("CREATE_")) return "新增" + mapTableLabel(t.substring("CREATE_".length()));
        if (t.startsWith("UPDATE_")) return "修改" + mapTableLabel(t.substring("UPDATE_".length()));
        if (t.startsWith("DELETE_")) return "删除" + mapTableLabel(t.substring("DELETE_".length()));
        // Fallback generic mapping
        if (t.startsWith("CREATE")) return "新增";
        if (t.startsWith("UPDATE")) return "修改";
        if (t.startsWith("DELETE")) return "删除";
        return type;
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
