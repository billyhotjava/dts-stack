package com.yuzhi.dts.platform.service.catalog;

import java.util.Locale;

public enum CatalogAssetLifecycleStatus {
    DISCOVERED,
    PENDING_GOVERNANCE,
    DRAFT_GOVERNANCE,
    TESTING,
    ACTIVE,
    DEPRECATED,
    ARCHIVED,
    BLOCKED,
    PENDING_REVIEW;

    public static String normalizeOrDefault(String value, CatalogAssetLifecycleStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus.name();
        }
        try {
            return CatalogAssetLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')).name();
        } catch (IllegalArgumentException ignored) {
            return defaultStatus.name();
        }
    }
}
