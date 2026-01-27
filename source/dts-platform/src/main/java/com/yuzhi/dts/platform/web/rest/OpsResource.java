package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ops.OpsService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        auditService.audit("READ", "ops.overview", "summary");
        return ApiResponses.ok(payload);
    }

    @GetMapping("/instances")
    public ApiResponse<List<Map<String, Object>>> instances(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "50") int limit
    ) {
        List<Map<String, Object>> list = opsService.listInstances(status, keyword, limit);
        auditService.audit("READ", "ops.instances", "count=" + list.size());
        return ApiResponses.ok(list);
    }

    @GetMapping("/alerts")
    public ApiResponse<List<Map<String, Object>>> alerts(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = opsService.listAlerts(limit);
        auditService.audit("READ", "ops.alerts", "count=" + list.size());
        return ApiResponses.ok(list);
    }

    @GetMapping("/backfills")
    public ApiResponse<List<OpsBackfillRequest>> backfills() {
        List<OpsBackfillRequest> list = opsService.listBackfills();
        auditService.audit("READ", "ops.backfills", "count=" + list.size());
        return ApiResponses.ok(list);
    }

    @PostMapping("/backfills")
    public ApiResponse<OpsBackfillRequest> createBackfill(@RequestBody BackfillRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        OpsBackfillRequest saved = opsService.createBackfill(user, request.dagId(), request.dateFrom(), request.dateTo(), request.note());
        auditService.audit("CREATE", "ops.backfills", saved.getId() != null ? saved.getId().toString() : "create");
        return ApiResponses.ok(saved);
    }

    public record BackfillRequest(String dagId, LocalDate dateFrom, LocalDate dateTo, String note) {}
}
