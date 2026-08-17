package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetNormalizationMigrationService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
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
@RequestMapping("/api/catalog/asset-normalization-migrations")
@PreAuthorize(
    "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "','" + AuthoritiesConstants.INST_DATA_OWNER + "')"
)
public class CatalogAssetNormalizationMigrationResource {

    private final CatalogAssetNormalizationMigrationService migrations;

    public CatalogAssetNormalizationMigrationResource(CatalogAssetNormalizationMigrationService migrations) {
        this.migrations = migrations;
    }

    @GetMapping({ "/preview", "/semantic-projection/preview" })
    public ApiResponse<CatalogAssetNormalizationMigrationService.Preview> preview(
        @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponses.ok(migrations.preview(limit));
    }

    @PostMapping({ "/apply", "/semantic-projection/apply" })
    public ApiResponse<CatalogAssetNormalizationMigrationService.ApplyResult> apply(
        @RequestBody ApplyRequest request,
        @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponses.ok(
            migrations.apply(
                request.previewHash(),
                request.limit() == null ? 100 : request.limit(),
                request.resolutions(),
                correlationId
            )
        );
    }

    @PostMapping({ "/{batchId}/rollback", "/semantic-projection/{batchId}/rollback" })
    public ApiResponse<CatalogAssetNormalizationMigrationService.RollbackResult> rollback(@PathVariable UUID batchId) {
        return ApiResponses.ok(migrations.rollback(batchId));
    }

    public record ApplyRequest(
        String previewHash,
        Integer limit,
        List<CatalogAssetNormalizationMigrationService.Resolution> resolutions
    ) {}
}
