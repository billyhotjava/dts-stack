package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;

public record CatalogTagSeedReport(
    String packageCode,
    String packageVersion,
    boolean applied,
    int categoriesCreated,
    int tagsCreated,
    int categoriesSkipped,
    int tagsSkipped,
    List<String> installedCodes,
    List<Conflict> conflicts
) {
    public CatalogTagSeedReport {
        installedCodes = List.copyOf(installedCodes);
        conflicts = List.copyOf(conflicts);
    }

    public record Conflict(
        String itemType,
        String code,
        String reason,
        String expectedCategoryCode,
        String actualCategoryCode
    ) {}
}
