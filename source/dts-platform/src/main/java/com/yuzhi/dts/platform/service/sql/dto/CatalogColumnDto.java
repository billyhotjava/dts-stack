package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogColumnDto(
    String name,
    String dataType,
    boolean nullable,
    String comment,
    int ordinalPosition
) {}
