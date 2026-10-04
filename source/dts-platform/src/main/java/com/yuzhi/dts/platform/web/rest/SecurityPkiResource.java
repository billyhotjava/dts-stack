package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.pki.SecurityPkiService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/pki")
@Transactional
public class SecurityPkiResource {

    private final SecurityPkiService pkiService;
    private final AuditService auditService;

    public SecurityPkiResource(SecurityPkiService pkiService, AuditService auditService) {
        this.pkiService = pkiService;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> result = pkiService.status();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看证书认证状态");
        payload.put("certPresent", result.get("certPresent"));
        payload.put("certVerified", result.get("certVerified"));
        payload.put("certSerial", result.get("certSerial"));
        payload.put("actor", SecurityUtils.getCurrentUserLogin().orElse("system"));
        auditService.auditAction("SECURITY_PKI_STATUS_VIEW", AuditStage.SUCCESS, "status", payload);
        return ApiResponses.ok(result);
    }

    @PutMapping("/bind")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> bind() {
        Map<String, Object> result = pkiService.bindCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "绑定证书登录");
        payload.put("certSerial", result.get("certSerial"));
        payload.put("actor", SecurityUtils.getCurrentUserLogin().orElse("system"));
        auditService.auditAction("SECURITY_PKI_BIND", AuditStage.SUCCESS, "bind", payload);
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/bind")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> unbind() {
        Map<String, Object> result = pkiService.unbindCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "解除证书绑定");
        payload.put("actor", SecurityUtils.getCurrentUserLogin().orElse("system"));
        auditService.auditAction("SECURITY_PKI_UNBIND", AuditStage.SUCCESS, "unbind", payload);
        return ApiResponses.ok(result);
    }
}
