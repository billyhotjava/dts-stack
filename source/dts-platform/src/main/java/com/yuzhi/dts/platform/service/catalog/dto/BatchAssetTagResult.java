package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;

public record BatchAssetTagResult(int assetCount, int created, int skipped, List<AssetResult> results) {
    public BatchAssetTagResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record AssetResult(String assetType, String assetKey, int created, int skipped) {}
}
