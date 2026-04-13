package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogDatasourceDto(
    String id,
    String name,
    String engine,
    String label
) {}
