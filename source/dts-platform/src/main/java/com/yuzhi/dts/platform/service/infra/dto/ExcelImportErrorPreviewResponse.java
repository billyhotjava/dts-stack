package com.yuzhi.dts.platform.service.infra.dto;

import java.util.List;
import java.util.UUID;

public record ExcelImportErrorPreviewResponse(
    UUID fileId,
    Integer errorCount,
    Integer limit,
    List<ExcelImportErrorRow> rows
) {}
