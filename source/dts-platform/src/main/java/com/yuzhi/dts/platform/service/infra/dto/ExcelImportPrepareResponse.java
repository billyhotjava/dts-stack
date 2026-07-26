package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExcelImportPrepareResponse(
    UUID fileId,
    String fileName,
    String batchCode,
    List<ExcelSheetInfo> sheets,
    String classification,
    UUID sealId,
    long sealVersion,
    String sealChecksum,
    Instant sealedAt
) {}
