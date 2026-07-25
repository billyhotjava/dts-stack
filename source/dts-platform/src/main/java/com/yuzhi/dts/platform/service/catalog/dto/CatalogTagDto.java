package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.UUID;

public record CatalogTagDto(
    UUID id,
    UUID categoryId,
    String code,
    String name,
    String color,
    boolean builtin,
    boolean enabled,
    String description,
    long usageCount
) {
    public CatalogTagDto(
        UUID id,
        UUID categoryId,
        String code,
        String name,
        String color,
        boolean builtin,
        boolean enabled,
        String description
    ) {
        this(id, categoryId, code, name, color, builtin, enabled, description, 0);
    }
}
