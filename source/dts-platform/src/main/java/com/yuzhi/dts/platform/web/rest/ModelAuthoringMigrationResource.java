package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringMigrationService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only command boundary for reversible authoring draft metadata migration. */
@RestController
@RequestMapping("/api/modeling/model-authoring-migrations")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "','" + AuthoritiesConstants.INST_DATA_OWNER + "')"
)
public class ModelAuthoringMigrationResource {

    private final ModelAuthoringMigrationService migrations;

    public ModelAuthoringMigrationResource(ModelAuthoringMigrationService migrations) {
        this.migrations = migrations;
    }

    @GetMapping("/preview")
    public ApiResponse<ModelAuthoringMigrationService.Preview> preview(
        @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponses.ok(migrations.preview(limit));
    }

    @PostMapping("/apply")
    public ApiResponse<ModelAuthoringMigrationService.ApplyResult> apply(
        @RequestBody ApplyRequest request,
        @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponses.ok(
            migrations.apply(request.previewHash(), request.limit() == null ? 100 : request.limit(), correlationId)
        );
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResponse<ModelAuthoringMigrationService.RollbackResult> rollback(@PathVariable UUID batchId) {
        return ApiResponses.ok(migrations.rollback(batchId));
    }

    public record ApplyRequest(String previewHash, Integer limit) {}
}
