package com.yuzhi.dts.platform.service.sql.dto;

public record SqlCatalogRequest(
    String datasource,
    String catalog,
    String schema,
    String search
) {}
