package com.yuzhi.dts.platform.service.visualization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BiReportLinkDto(
    UUID id,
    String code,
    String title,
    String engine,
    String reportType,
    List<String> deptCodes,
    List<String> roleCodes,
    String classification,
    String url,
    boolean enabled,
    Integer sortOrder,
    UUID queryDatasetId,
    Integer queryDatasetVersion,
    String queryDatasetName,
    Instant expiresAt,
    Instant lastVisitedAt,
    String owner,
    Instant updatedAt
) {}
