package com.yuzhi.dts.platform.service.catalog;

public record CatalogAssetIdentity(
    CatalogAssetType type,
    String assetKey,
    String assetId,
    String sourceRef
) {
    public CatalogAssetIdentity {
        if (type == null) {
            throw new IllegalArgumentException("asset type is required");
        }
        if (assetKey == null || assetKey.isBlank()) {
            throw new IllegalArgumentException("asset key is required");
        }
    }

    public String grantAssetType() {
        return type.name();
    }

    public String grantAssetId() {
        return assetId != null && !assetId.isBlank() ? assetId : assetKey;
    }
}
