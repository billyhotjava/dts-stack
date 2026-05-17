package com.yuzhi.dts.platform.service.catalog;

import java.util.Locale;

public enum CatalogAssetType {
    DATASET,
    DBT_MODEL,
    BI_DATASET,
    SCREEN,
    METRIC,
    SEMANTIC_MODEL,
    DATA_PRODUCT;

    public static CatalogAssetType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("asset type is required");
        }
        return CatalogAssetType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
