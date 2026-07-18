package com.yuzhi.dts.platform.service.catalog;

import java.util.Locale;

public enum CatalogAssetType {
    CATALOG_DOMAIN,
    DATASET,
    DBT_MODEL,
    BI_DATASET,
    SCREEN,
    METRIC,
    METRIC_PACK,
    SEMANTIC_MODEL,
    DATA_PRODUCT,
    MODELING_SQL_MODEL,
    MODELING_PLAN,
    DATA_STANDARD,
    METADATA_STANDARD,
    GLOSSARY_TERM,
    GOV_INDICATOR,
    GOV_INDICATOR_TEMPLATE,
    QUALITY_RULE,
    SECURITY_POLICY,
    API_SERVICE,
    BACKFILL_REQUEST;

    public static CatalogAssetType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("asset type is required");
        }
        return CatalogAssetType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
