package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record CatalogDatasourceDto(
    String id,
    String name,
    String engine,
    String label
) implements Serializable {}
