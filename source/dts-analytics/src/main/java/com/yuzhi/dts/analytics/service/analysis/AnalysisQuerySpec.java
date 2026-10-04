package com.yuzhi.dts.analytics.service.analysis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisQuerySpec(
    String apiVersion,
    DatasetRef dataset,
    List<DimensionSelection> dimensions,
    List<MetricSelection> metrics,
    List<DerivedMetric> derivedMetrics,
    List<FilterSelection> filters,
    TimeRange timeRange,
    List<OrderSelection> orderBy,
    Integer limit,
    Visualization visualization
) {
    public record DatasetRef(UUID id, int version, String contractVersion, String checksum) {}

    public record DimensionSelection(String field, String alias) {}

    public record MetricSelection(String code, String alias) {}

    public record DerivedMetric(String code, String expression, String format) {}

    public record FilterSelection(String field, String op, List<Object> values, Boolean required) {}

    public record TimeRange(String field, String start, String end, String grain) {}

    public record OrderSelection(String field, String direction) {}

    public record Visualization(String type, Map<String, Object> settings) {}
}
