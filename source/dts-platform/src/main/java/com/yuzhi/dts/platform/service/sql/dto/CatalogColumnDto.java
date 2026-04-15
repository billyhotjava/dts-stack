package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record CatalogColumnDto(
    String name,
    String dataType,
    boolean nullable,
    String comment,
    int ordinalPosition
) implements Serializable {}
