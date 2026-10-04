package com.yuzhi.dts.analytics.service.analysis;

import java.util.List;
import java.util.UUID;

public record GovernedAnalysisDatasetContract(
    UUID datasetId,
    int version,
    String status,
    UUID sourceDatasourceId,
    String baseSql,
    List<Dimension> dimensions,
    List<Metric> metrics,
    List<Join> joins,
    List<String> policyRefs,
    String classification,
    String contractVersion,
    String contractChecksum
) {
    public record Dimension(
        String code,
        String label,
        String dataType,
        List<String> timeGrains,
        String classification,
        List<String> filterOps
    ) {}

    public record Metric(String code, String label, String aggregation, String unit, String expression) {}

    public record Join(String fromModel, String toModel, String relationship, boolean approvalRequired) {}
}
