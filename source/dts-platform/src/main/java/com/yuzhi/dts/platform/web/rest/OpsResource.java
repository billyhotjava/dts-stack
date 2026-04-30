package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ops.OpsService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ops")
@Transactional
public class OpsResource {

    private final OpsService opsService;
    private final AuditService auditService;

    public OpsResource(OpsService opsService, AuditService auditService) {
        this.opsService = opsService;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> payload = opsService.overview();
        auditService.auditAction("OPS_OVERVIEW_READ", AuditStage.SUCCESS, "summary", null);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/instances")
    public ApiResponse<List<Map<String, Object>>> instances(
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "50") int limit
    ) {
        List<Map<String, Object>> list = opsService.listInstances(entryKey, status, keyword, limit);
        auditService.auditAction("OPS_INSTANCES_READ", AuditStage.SUCCESS, "count=" + list.size(), null);
        return ApiResponses.ok(list);
    }

    @GetMapping("/alerts")
    public ApiResponse<List<Map<String, Object>>> alerts(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = opsService.listAlerts(limit);
        auditService.auditAction("OPS_ALERTS_READ", AuditStage.SUCCESS, "count=" + list.size(), null);
        return ApiResponses.ok(list);
    }

    @GetMapping("/metrics/dev-center")
    public ApiResponse<Map<String, Object>> devCenterMetrics(
        @RequestParam(defaultValue = "7") int days,
        @RequestParam(required = false) String entryKey,
        @RequestParam(required = false) String ownerDept,
        @RequestParam(required = false) UUID artifactId,
        @RequestParam(required = false) String artifactName,
        @RequestParam(required = false) UUID planId
    ) {
        Map<String, Object> payload = opsService.devCenterMetrics(days, entryKey, ownerDept, artifactId, artifactName, planId);
        auditService.auditAction("OPS_METRICS_DEV_CENTER_READ", AuditStage.SUCCESS, "days=" + days, null);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/backfills")
    public ApiResponse<List<OpsBackfillRequest>> backfills() {
        List<OpsBackfillRequest> list = opsService.listBackfills();
        auditService.auditAction("OPS_BACKFILLS_READ", AuditStage.SUCCESS, "count=" + list.size(), null);
        return ApiResponses.ok(list);
    }

    @PostMapping("/backfills")
    public ApiResponse<OpsBackfillRequest> createBackfill(@RequestBody BackfillRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        OpsBackfillRequest saved = opsService.createBackfill(user, request.dagId(), request.dateFrom(), request.dateTo(), request.note());
        auditService.auditAction("OPS_BACKFILLS_CREATE", AuditStage.SUCCESS, saved.getId() != null ? saved.getId().toString() : "create", null);
        return ApiResponses.ok(saved);
    }

    public record BackfillRequest(String dagId, LocalDate dateFrom, LocalDate dateTo, String note) {}
}
