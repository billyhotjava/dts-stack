package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;

public record AnalysisDatasetPage(
    List<AnalysisDatasetSummary> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
