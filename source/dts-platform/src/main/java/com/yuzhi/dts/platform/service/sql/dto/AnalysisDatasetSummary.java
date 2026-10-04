package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisDatasetSummary(
    UUID datasetId,
    int version,
    String name,
    String description,
    String ownerDept,
    UUID sourceDatasourceId,
    String sourceDatasourceName,
    String warehouseLayer,
    String classification,
    String refreshStrategy,
    String semanticContractVersion,
    List<String> semanticModelNames,
    String contractChecksum,
    Instant updatedAt
) {}
