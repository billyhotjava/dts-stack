package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.UUID;

public record ApiServiceDetailDto(
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
    ApiPolicyDto policy,
    List<ApiFieldDto> input,
    List<ApiFieldDto> output,
    ApiQuotaDto quotas,
    ApiAuditStatsDto audit,
    String latestVersion,
    java.time.Instant lastPublishedAt,
    String description
) {}
