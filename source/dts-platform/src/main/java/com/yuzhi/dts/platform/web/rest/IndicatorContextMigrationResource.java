package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.governance.IndicatorContextMigrationService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/indicator-context-migrations")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "','" + AuthoritiesConstants.INST_DATA_OWNER + "')"
)
public class IndicatorContextMigrationResource {

    private final IndicatorContextMigrationService migrations;

    public IndicatorContextMigrationResource(IndicatorContextMigrationService migrations) {
        this.migrations = migrations;
    }

    @GetMapping("/preview")
    public ApiResponse<IndicatorContextMigrationService.Preview> preview(
        @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponses.ok(migrations.preview(limit));
    }

    @PostMapping("/apply")
    public ApiResponse<IndicatorContextMigrationService.ApplyResult> apply(@RequestBody ApplyRequest request) {
        return ApiResponses.ok(migrations.apply(request.previewHash(), request.limit() == null ? 100 : request.limit()));
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResponse<IndicatorContextMigrationService.RollbackResult> rollback(@PathVariable UUID batchId) {
        return ApiResponses.ok(migrations.rollback(batchId));
    }

    public record ApplyRequest(String previewHash, Integer limit) {}
}
