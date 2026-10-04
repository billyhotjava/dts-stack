package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.baseline.SecurityBaselineService;
import com.yuzhi.dts.platform.service.security.baseline.dto.SecurityBaselineCheckDto;
import com.yuzhi.dts.platform.service.security.baseline.request.SecurityBaselineRemediationUpdateRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/baseline")
@Transactional
public class SecurityBaselineResource {

    private static final String INSTITUTE_PRIVILEGED_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)";

    private final SecurityBaselineService baselineService;
    private final AuditService auditService;

    public SecurityBaselineResource(SecurityBaselineService baselineService, AuditService auditService) {
        this.baselineService = baselineService;
        this.auditService = auditService;
    }

    @GetMapping("/checks")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<List<SecurityBaselineCheckDto>> listChecks() {
        List<SecurityBaselineCheckDto> checks = baselineService.listChecks();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看安全基线检查项");
        payload.put("count", checks.size());
        auditService.auditAction("SECURITY_BASELINE_CHECK_LIST", AuditStage.SUCCESS, "LIST", payload);
        return ApiResponses.ok(checks);
    }

    @PutMapping("/checks/{checkKey}")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<SecurityBaselineCheckDto> updateRemediation(
        @PathVariable String checkKey,
        @RequestBody SecurityBaselineRemediationUpdateRequest request
    ) {
        SecurityBaselineCheckDto updated = baselineService.updateRemediation(checkKey, request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新安全基线整改记录");
        payload.put("checkKey", checkKey);
        payload.put("status", request != null ? request.getStatus() : null);
        payload.put("actor", SecurityUtils.getCurrentUserLogin().orElse("system"));
        auditService.auditAction("SECURITY_BASELINE_REMEDIATION_UPDATE", AuditStage.SUCCESS, checkKey, payload);
        return ApiResponses.ok(updated);
    }

    @GetMapping("/report")
    @PreAuthorize(INSTITUTE_PRIVILEGED_EXPRESSION)
    public ApiResponse<Map<String, Object>> exportReport() {
        Map<String, Object> report = baselineService.exportReport();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "导出安全基线报告");
        payload.put("generatedAt", report.get("generatedAt"));
        auditService.auditAction("SECURITY_BASELINE_REPORT_VIEW", AuditStage.SUCCESS, "report", payload);
        return ApiResponses.ok(report);
    }
}

