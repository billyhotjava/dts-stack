package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.UUID;

public record ResultMetaDto(
    UUID executionId,
    UUID resultSetId,
    long rowCount,
    int chunkCount,
    List<ColumnMetaDto> columns
) {}
