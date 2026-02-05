package com.yuzhi.dts.platform.service.sql.dto;

public record TableInfo(
    String schema,
    String name,
    String type,
    Long rowCount
) {}
