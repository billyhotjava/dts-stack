package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record SqlIdeTabDto(
    UUID id,
    String title,
    String sqlText,
    String engine,
    UUID datasourceId,
    String schemaCtx,
    Integer cursorLine,
    Integer cursorCol,
    String selectionJson,
    UUID lastExecutionId,
    int sortOrder,
    boolean active,
    Instant updatedAt
) {}
