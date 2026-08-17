package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;

public record AnalysisDatasetDetail(
    AnalysisDatasetSummary dataset,
    List<Map<String, Object>> dimensions,
    List<Map<String, Object>> metrics,
    List<Map<String, Object>> joins,
    List<String> policyRefs
) {}
