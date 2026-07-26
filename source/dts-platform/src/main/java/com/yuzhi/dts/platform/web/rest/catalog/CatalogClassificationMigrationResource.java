package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.DryRunCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.FreezeCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.MigrationItemView;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.MigrationRunView;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.ReconciliationView;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationMigrationService.WriteFreezeView;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/classification-migrations")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN')")
public class CatalogClassificationMigrationResource {

    private final CatalogClassificationMigrationService migrationService;

    public CatalogClassificationMigrationResource(
        CatalogClassificationMigrationService migrationService
    ) {
        this.migrationService = migrationService;
    }

    @PostMapping("/dry-run")
    public ApiResponse<MigrationRunView> dryRun(@RequestBody DryRunCommand command) {
        return ApiResponses.ok(migrationService.dryRun(command, actor()));
    }

    @GetMapping("/{runId}")
    public ApiResponse<MigrationRunView> get(@PathVariable UUID runId) {
        return ApiResponses.ok(migrationService.get(runId));
    }

    @GetMapping("/{runId}/items")
    public ApiResponse<List<MigrationItemView>> items(
        @PathVariable UUID runId,
        @RequestParam(required = false) String decision,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return ApiResponses.ok(migrationService.items(runId, decision, limit));
    }

    @PostMapping("/{runId}/apply")
    public ApiResponse<MigrationRunView> apply(
        @PathVariable UUID runId,
        @RequestParam(defaultValue = "0") int batchSize
    ) {
        return ApiResponses.ok(migrationService.applyBatch(runId, batchSize, actor()));
    }

    @PostMapping("/{runId}/pause")
    public ApiResponse<MigrationRunView> pause(@PathVariable UUID runId) {
        return ApiResponses.ok(migrationService.pause(runId, actor()));
    }

    @PostMapping("/{runId}/resume")
    public ApiResponse<MigrationRunView> resume(@PathVariable UUID runId) {
        return ApiResponses.ok(migrationService.resume(runId, actor()));
    }

    @GetMapping("/{runId}/reconciliation")
    public ApiResponse<ReconciliationView> reconcile(@PathVariable UUID runId) {
        return ApiResponses.ok(migrationService.reconcile(runId));
    }

    @PostMapping("/{runId}/freeze")
    public ApiResponse<List<WriteFreezeView>> freeze(
        @PathVariable UUID runId,
        @RequestBody(required = false) FreezeCommand command
    ) {
        FreezeCommand effective = command == null ? new FreezeCommand(List.of(), null) : command;
        return ApiResponses.ok(
            migrationService.freezeLegacyWrites(
                runId,
                effective.sourceTables(),
                effective.reason(),
                actor()
            )
        );
    }

    @GetMapping("/write-freezes")
    public ApiResponse<List<WriteFreezeView>> writeFreezes() {
        return ApiResponses.ok(migrationService.writeFreezes());
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<ApiResponse<Object>> handleMigrationRejected(RuntimeException exception) {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    "CLASSIFICATION_MIGRATION_REJECTED",
                    null
                )
            );
    }

    private String actor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
