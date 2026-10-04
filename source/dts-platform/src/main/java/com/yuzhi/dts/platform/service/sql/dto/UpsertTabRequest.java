package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record UpsertTabRequest(
    UUID id,           // null = create; non-null = update
    String title,
    String sqlText,
    String engine,
    UUID datasourceId,
    String schemaCtx,
    Integer cursorLine,
    Integer cursorCol,
    String selectionJson,
    UUID lastExecutionId,
    Integer sortOrder,
    Boolean active,
    Instant updatedAt  // required when id != null
) {}
