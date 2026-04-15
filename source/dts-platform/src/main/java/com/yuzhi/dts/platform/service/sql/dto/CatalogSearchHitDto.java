package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record CatalogSearchHitDto(
    String schema,
    String table,
    String column,
    String type
) implements Serializable {}
