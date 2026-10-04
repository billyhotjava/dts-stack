package com.yuzhi.dts.platform.service.infra.dto;

import java.util.UUID;
import java.util.Map;

public record ExcelImportParseRequest(
    UUID fileId,
    String sheetName,
    Integer sheetIndex,
    Integer headerRow,
    Integer dataStartRow,
    String delimiter,
    Integer previewLimit,
    Boolean skipErrors,
    Boolean fillMerged,
    String dateFormat,
    Map<String, String> fieldClassifications,
    Boolean sealClassification
) {}
