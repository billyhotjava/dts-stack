package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CatalogTagCategoryDto(
    UUID id,
    String code,
    String name,
    UUID parentId,
    int sortOrder,
    boolean builtin,
    boolean enabled,
    String description,
    long tagCount,
    List<CatalogTagCategoryDto> children
) {}
