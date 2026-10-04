package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record TableInfo(
    String schema,
    String name,
    String type,
    Long rowCount
) implements Serializable {}
