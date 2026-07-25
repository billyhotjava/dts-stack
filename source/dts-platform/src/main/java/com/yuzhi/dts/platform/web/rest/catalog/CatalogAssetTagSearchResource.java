package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagReadVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRefPage;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/asset-tags")
public class CatalogAssetTagSearchResource {

    private final CatalogAssetTagService assetTagService;
    private final CatalogAssetTagReadVisibilityService readVisibility;

    public CatalogAssetTagSearchResource(
        CatalogAssetTagService assetTagService,
        CatalogAssetTagReadVisibilityService readVisibility
    ) {
        this.assetTagService = assetTagService;
        this.readVisibility = readVisibility;
    }

    @GetMapping("/search")
    public ApiResponse<AssetRefPage> search(
        @RequestParam List<UUID> tagIds,
        @RequestParam(required = false) String assetType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(
            assetTagService.searchAssets(
                tagIds,
                assetType,
                page,
                size,
                refs -> readVisibility.filterReadable(refs, activeDept)
            )
        );
    }
}
