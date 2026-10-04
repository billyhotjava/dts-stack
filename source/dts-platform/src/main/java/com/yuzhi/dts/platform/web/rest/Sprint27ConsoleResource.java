package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sprint27.Sprint27ConsoleService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/sprint27")
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)")
public class Sprint27ConsoleResource {

    private final Sprint27ConsoleService consoleService;
    private final AuditService auditService;

    public Sprint27ConsoleResource(Sprint27ConsoleService consoleService, AuditService auditService) {
        this.consoleService = consoleService;
        this.auditService = auditService;
    }

    @GetMapping("/elt-console")
    public ApiResponse<Map<String, Object>> eltConsole(@RequestParam(defaultValue = "7") int days, @RequestParam(defaultValue = "24") int hours) {
        Map<String, Object> data = consoleService.eltConsole(days, hours);
        audit("SPRINT27_ELT_CONSOLE_VIEW", "sprint27-elt-console", Map.of("days", days, "hours", hours));
        return ApiResponses.ok(data);
    }

    @GetMapping("/metric-operations")
    public ApiResponse<Map<String, Object>> metricOperations(
        @RequestParam(defaultValue = "168") int hours,
        @RequestParam(defaultValue = "24") int bucketHours,
        @RequestParam(required = false) String activeDept
    ) {
        Map<String, Object> data = consoleService.metricOperations(hours, bucketHours, activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hours", hours);
        payload.put("bucketHours", bucketHours);
        if (activeDept != null) payload.put("activeDept", activeDept);
        audit("SPRINT27_METRIC_OPERATIONS_VIEW", "sprint27-metric-operations", payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/events-console")
    public ApiResponse<Map<String, Object>> eventsConsole(
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String dispatchStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Map<String, Object> data = consoleService.eventsConsole(domain, eventType, status, dispatchStatus, page, size);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (domain != null) payload.put("domain", domain);
        if (eventType != null) payload.put("eventType", eventType);
        if (status != null) payload.put("status", status);
        if (dispatchStatus != null) payload.put("dispatchStatus", dispatchStatus);
        payload.put("page", page);
        payload.put("size", size);
        audit("SPRINT27_EVENTS_CONSOLE_VIEW", "sprint27-events-console", payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/audit-evidence")
    public ApiResponse<Map<String, Object>> auditEvidence() {
        Map<String, Object> data = consoleService.auditEvidence();
        audit("SPRINT27_AUDIT_EVIDENCE_VIEW", "sprint27-audit-evidence", Map.of("summary", "查看 Sprint-27 审计证据"));
        return ApiResponses.ok(data);
    }

    @GetMapping("/release-governance")
    public ApiResponse<Map<String, Object>> releaseGovernance(@RequestParam(required = false) String activeDept) {
        Map<String, Object> data = consoleService.releaseGovernance(activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看 Sprint-27 发布治理");
        if (activeDept != null) payload.put("activeDept", activeDept);
        audit("SPRINT27_RELEASE_GOVERNANCE_VIEW", "sprint27-release-governance", payload);
        return ApiResponses.ok(data);
    }

    private void audit(String actionCode, String resourceRef, Map<String, Object> payload) {
        auditService.auditAction(actionCode, AuditStage.SUCCESS, resourceRef, payload);
    }
}
