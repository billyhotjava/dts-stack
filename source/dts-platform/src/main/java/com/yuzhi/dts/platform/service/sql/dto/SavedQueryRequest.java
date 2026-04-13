package com.yuzhi.dts.platform.service.sql.dto;

import java.util.UUID;

public record SavedQueryRequest(
    String name,
    String description,
    String sqlText,
    UUID datasourceId,
    String datasourceName,
    String folder
) {}
