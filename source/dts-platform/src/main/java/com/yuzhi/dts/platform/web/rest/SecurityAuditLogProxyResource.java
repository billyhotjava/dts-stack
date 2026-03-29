package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/audit-logs")
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)")
public class SecurityAuditLogProxyResource {

    private final AdminAuditGateway adminAuditGateway;
    private final AuditService auditService;

    public SecurityAuditLogProxyResource(AdminAuditGateway adminAuditGateway, AuditService auditService) {
        this.adminAuditGateway = adminAuditGateway;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<Object> list(
        @RequestParam Map<String, String> params,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        Object data = adminAuditGateway.list(params, authorization);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看审计日志");
        payload.put("filters", params != null ? new LinkedHashMap<>(params) : Map.of());
        auditService.auditAction("SECURITY_AUDIT_LOG_LIST", AuditStage.SUCCESS, "audit-logs", payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> get(
        @PathVariable String id,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        Object data = adminAuditGateway.get(id, authorization);
        auditService.auditAction(
            "SECURITY_AUDIT_LOG_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("summary", "查看审计日志详情")
        );
        return ApiResponses.ok(data);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public void export(
        @RequestParam Map<String, String> params,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        HttpServletResponse response
    ) throws IOException {
        byte[] csv = adminAuditGateway.export(params, authorization);
        auditService.auditAction(
            "SECURITY_AUDIT_LOG_EXPORT",
            AuditStage.SUCCESS,
            "audit-logs.export",
            Map.of("summary", "导出审计日志", "filters", params != null ? new LinkedHashMap<>(params) : Map.of())
        );
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(csv);
    }
}
