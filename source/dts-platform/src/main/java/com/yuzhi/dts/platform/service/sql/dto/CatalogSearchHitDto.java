package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogSearchHitDto(
    String schema,
    String table,
    String column,
    String type
) {}
