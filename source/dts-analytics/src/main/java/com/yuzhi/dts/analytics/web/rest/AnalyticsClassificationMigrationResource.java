package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.service.AnalyticsClassificationMigrationService;
import com.yuzhi.dts.analytics.service.AnalyticsClassificationMigrationService.ItemView;
import com.yuzhi.dts.analytics.service.AnalyticsClassificationMigrationService.RunView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/classification-migrations")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN')")
public class AnalyticsClassificationMigrationResource {

    private final AnalyticsClassificationMigrationService migrationService;

    public AnalyticsClassificationMigrationResource(
        AnalyticsClassificationMigrationService migrationService
    ) {
        this.migrationService = migrationService;
    }

    @PostMapping("/dry-run")
    public RunView dryRun(@RequestBody DryRunRequest request) {
        return migrationService.dryRun(
            request.idempotencyKey(),
            request.batchSize() == null ? 200 : request.batchSize(),
            actor()
        );
    }

    @GetMapping("/{runId}")
    public RunView get(@PathVariable UUID runId) {
        return migrationService.get(runId);
    }

    @GetMapping("/{runId}/items")
    public List<ItemView> items(
        @PathVariable UUID runId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return migrationService.items(runId, limit);
    }

    @PostMapping("/{runId}/apply")
    public RunView apply(
        @PathVariable UUID runId,
        @RequestParam(defaultValue = "200") int batchSize
    ) {
        return migrationService.applyBatch(runId, batchSize, actor());
    }

    @PostMapping("/{runId}/pause")
    public RunView pause(@PathVariable UUID runId) {
        return migrationService.pause(runId);
    }

    @PostMapping("/{runId}/resume")
    public RunView resume(@PathVariable UUID runId) {
        return migrationService.resume(runId);
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
            ? "system"
            : authentication.getName();
    }

    public record DryRunRequest(String idempotencyKey, Integer batchSize) {}
}
