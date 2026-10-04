package com.yuzhi.dts.platform.service.infra.dto;

import java.util.List;
import java.util.UUID;

public record ExcelImportParseResponse(
    UUID fileId,
    String batchCode,
    String sheetName,
    String csvPath,
    String csvContainerPath,
    String errorPath,
    String errorContainerPath,
    String delimiter,
    List<ExcelColumnSpecDto> columns,
    List<List<String>> preview,
    Integer rowCount,
    Integer errorCount
) {}
