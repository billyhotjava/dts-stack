package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueryDatasetResponse(
    UUID id,
    String name,
    String description,
    UUID sourceDatasourceId,
    String sourceDatasourceName,
    String ownerDept,
    String status,
    String refreshStrategy,
    Integer publishedVersion,
    UUID latestExecutionId,
    UUID latestResultSetId,
    Boolean enabled,
    String createdBy,
    Instant createdDate,
    Instant lastModifiedDate,
    String semanticContractVersion,
    Integer semanticModelCount,
    List<String> semanticModelNames
) {}
