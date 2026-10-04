package com.yuzhi.dts.platform.service.sql.dto;

import java.util.UUID;

public record CreateTabRequest(
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
    Boolean active
) {}
