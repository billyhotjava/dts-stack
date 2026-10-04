package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record PatchTabRequest(
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
    Instant updatedAt  // optimistic lock; client's last-seen value
) {}
