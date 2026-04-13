package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.UUID;

public record ResultMetaDto(
    UUID executionId,
    String status,
    List<ColumnMetaDto> columns,
    long totalRows,
    boolean truncated,
    Long elapsedMs,
    Long bytesProcessed
) {}
