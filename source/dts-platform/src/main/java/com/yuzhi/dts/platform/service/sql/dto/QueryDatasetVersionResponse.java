package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record QueryDatasetVersionResponse(
    UUID id,
    UUID datasetId,
    Integer versionNo,
    String status,
    String sqlText,
    String changeSummary,
    UUID resultSetId,
    UUID executionId,
    Instant publishedAt,
    String createdBy,
    Instant createdDate
) {}
