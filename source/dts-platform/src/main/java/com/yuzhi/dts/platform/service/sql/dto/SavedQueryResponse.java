package com.yuzhi.dts.platform.service.sql.dto;

import java.util.UUID;

public record SavedQueryResponse(
    UUID id,
    String name,
    String description,
    String sqlText,
    UUID datasourceId,
    String datasourceName,
    String createdBy,
    String createdAt,
    String updatedAt,
    String folder
) {}
