package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.Report;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService.ExitGateView;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService.MigrationExecution;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService.UsageReport;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/migrations/legacy-objects")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)")
public class LegacyObjectMigrationResource {

    private final LegacyObjectMigrationService service;
    private final AuditService audit;

    public LegacyObjectMigrationResource(LegacyObjectMigrationService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/dry-run")
    public ApiResponse<Report> dryRun(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId
    ) {
        Report report = service.dryRun(tenant(tenantId));
        audit.auditAction(
            "LEGACY_OBJECT_MIGRATION_DRY_RUN",
            AuditStage.SUCCESS,
            report.batchId(),
            Map.of("checksum", report.checksum(), "objects", report.decisions().size())
        );
        return ApiResponses.ok(report);
    }

    @PostMapping("/{batchId}/execute")
    public ApiResponse<MigrationExecution> execute(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
        @PathVariable String batchId,
        @RequestBody ExecuteRequest request
    ) {
        MigrationExecution result = service.execute(
            tenant(tenantId),
            batchId,
            request == null ? null : request.expectedChecksum(),
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );
        audit.auditAction(
            "LEGACY_OBJECT_MIGRATION_EXECUTE",
            AuditStage.SUCCESS,
            batchId,
            Map.of("checksum", result.checksum(), "status", result.status(), "replayed", result.replayed())
        );
        return ApiResponses.ok(result);
    }

    @GetMapping("/usage")
    public ApiResponse<UsageReport> usage(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
        @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponses.ok(service.usageReport(tenant(tenantId), days));
    }

    @GetMapping("/exit-gate")
    public ApiResponse<ExitGateView> exitGate(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
        @RequestParam(defaultValue = "30") int observationDays
    ) {
        ExitGateView result = service.exitGate(tenant(tenantId), observationDays);
        audit.auditAction(
            "LEGACY_OBJECT_PHYSICAL_EXIT_GATE_VIEW",
            AuditStage.SUCCESS,
            result.batchId() == null ? "no-batch" : result.batchId(),
            Map.of("decision", result.decision(), "blockers", result.blockers().size())
        );
        return ApiResponses.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .badRequest()
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "MIGRATION_REQUEST_INVALID", null));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> conflict(IllegalStateException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "MIGRATION_GATE_BLOCKED", null));
    }

    private String tenant(String requested) {
        return requested == null || requested.isBlank() ? service.defaultTenantId() : requested.trim();
    }

    public record ExecuteRequest(String expectedChecksum) {}
}
