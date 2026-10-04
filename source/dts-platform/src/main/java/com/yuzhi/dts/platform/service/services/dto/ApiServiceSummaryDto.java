package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.UUID;

public record ApiServiceSummaryDto(
    UUID id,
    String code,
    String name,
    String datasetId,
    String datasetName,
    String method,
    String path,
    String classification,
    int qps,
    int qpsLimit,
    int dailyLimit,
    String status,
    long recentCalls,
    List<Integer> sparkline
) {}
