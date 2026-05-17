package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;

public record CatalogAssetSourceReference(
    String sourceSystem,
    String externalId,
    String fqn,
    String sourceId,
    String resolvedBy
) {
    public static CatalogAssetSourceReference legacyDataset(CatalogDataset dataset, String resolvedBy) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        return new CatalogAssetSourceReference(
            "dts-catalog",
            dataset.getId() == null ? null : dataset.getId().toString(),
            CatalogAssetKey.dataset(dataset),
            dataset.getSourceId() == null ? null : dataset.getSourceId().toString(),
            resolvedBy
        );
    }

    public static CatalogAssetSourceReference openMetadata(OpenMetadataAssetCache asset, String resolvedBy) {
        if (asset == null) {
            throw new IllegalArgumentException("openmetadata asset is required");
        }
        return new CatalogAssetSourceReference(
            "openmetadata",
            asset.getOmEntityId(),
            asset.getFqn(),
            asset.getServiceName(),
            resolvedBy
        );
    }

    public String stableRef() {
        String system = sourceSystem == null || sourceSystem.isBlank() ? "unknown" : sourceSystem;
        String ref = externalId != null && !externalId.isBlank() ? externalId : fqn;
        return system + ":" + (ref == null || ref.isBlank() ? "unknown" : ref);
    }
}
