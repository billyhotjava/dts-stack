package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.Map;

public record CatalogLineageFailureReport(
    List<CatalogLineageFailureItem> content,
    Map<String, Long> severityCounts,
    Map<String, Long> reasonCounts,
    long inspected,
    long skipped,
    long totalCandidates,
    int page,
    int size,
    String sourceReport,
    String metadataSource
) {}
