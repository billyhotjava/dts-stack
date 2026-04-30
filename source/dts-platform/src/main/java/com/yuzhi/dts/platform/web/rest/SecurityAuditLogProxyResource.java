package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Browser-facing proxy that forwards audit log queries to dts-admin.
 * <p>
 * The browser uses the BFF cookie session ({@code portal_session}); after Sprint-22 the
 * {@code Authorization} header is no longer set client-side. The proxy re-extracts the KC
 * access token from the same cookie and forwards it as a bearer to admin so that admin's
 * existing {@code @PreAuthorize} flow keeps working without any new identity-impersonation
 * surface.
 */
@RestController
@RequestMapping("/api/security/audit-logs")
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)")
public class SecurityAuditLogProxyResource {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogProxyResource.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuditGateway adminAuditGateway;
    private final AuditService auditService;
    private final PortalSessionCookieService portalSessionCookieService;

    public SecurityAuditLogProxyResource(
        AdminAuditGateway adminAuditGateway,
        AuditService auditService,
        PortalSessionCookieService portalSessionCookieService
    ) {
        this.adminAuditGateway = adminAuditGateway;
        this.auditService = auditService;
        this.portalSessionCookieService = portalSessionCookieService;
    }

    @GetMapping
    public ApiResponse<Object> list(@RequestParam Map<String, String> params, HttpServletRequest request) {
        String bearer = resolveUserBearer(request);
        Object data = adminAuditGateway.list(params, bearer);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看审计日志");
        payload.put("filters", params != null ? new LinkedHashMap<>(params) : Map.of());
        auditService.auditAction("SECURITY_AUDIT_LOG_LIST", AuditStage.SUCCESS, "audit-logs", payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> get(@PathVariable String id, HttpServletRequest request) {
        String bearer = resolveUserBearer(request);
        Object data = adminAuditGateway.get(id, bearer);
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
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        String bearer = resolveUserBearer(request);
        byte[] csv = adminAuditGateway.export(params, bearer);
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

    /**
     * Resolves the user's KC access token from the BFF portal session cookie and wraps it as a
     * Bearer header value. Falls back to a legacy Authorization header for backward compatibility
     * during rollout.
     */
    private String resolveUserBearer(HttpServletRequest request) {
        String cookieToken = portalSessionCookieService != null
            ? portalSessionCookieService.resolvePortalSessionToken(request)
            : null;
        if (StringUtils.hasText(cookieToken)) {
            return BEARER_PREFIX + cookieToken;
        }
        String header = request != null ? request.getHeader(HttpHeaders.AUTHORIZATION) : null;
        if (StringUtils.hasText(header)) {
            log.debug("Falling back to Authorization header for audit log proxy");
            return header;
        }
        log.warn("Audit log proxy could not resolve a user token from cookie or header");
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户会话");
    }
}
