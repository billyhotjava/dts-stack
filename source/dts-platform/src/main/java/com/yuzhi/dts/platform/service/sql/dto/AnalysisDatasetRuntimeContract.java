package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisDatasetRuntimeContract(
    UUID datasetId,
    int version,
    String status,
    UUID sourceDatasourceId,
    String baseSql,
    List<Map<String, Object>> dimensions,
    List<Map<String, Object>> metrics,
    List<Map<String, Object>> joins,
    List<String> policyRefs,
    String classification,
    String contractVersion,
    String contractChecksum
) {}
