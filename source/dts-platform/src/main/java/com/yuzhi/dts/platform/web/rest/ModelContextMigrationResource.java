package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelContextMigrationService;
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

@RestController
@RequestMapping("/api/modeling/model-context-migrations")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "','" + AuthoritiesConstants.INST_DATA_OWNER + "')"
)
public class ModelContextMigrationResource {

    private final ModelContextMigrationService migrations;

    public ModelContextMigrationResource(ModelContextMigrationService migrations) {
        this.migrations = migrations;
    }

    @GetMapping("/preview")
    public ApiResponse<ModelContextMigrationService.Preview> preview(
        @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponses.ok(migrations.preview(limit));
    }

    @PostMapping("/apply")
    public ApiResponse<ModelContextMigrationService.ApplyResult> apply(
        @RequestBody ApplyRequest request,
        @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponses.ok(
            migrations.apply(request.previewHash(), request.limit() == null ? 100 : request.limit(), correlationId)
        );
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResponse<ModelContextMigrationService.RollbackResult> rollback(@PathVariable UUID batchId) {
        return ApiResponses.ok(migrations.rollback(batchId));
    }

    public record ApplyRequest(String previewHash, Integer limit) {}
}
