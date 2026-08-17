package com.yuzhi.dts.analytics.service.publication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportRegistrationCommand(
    String engine,
    String assetType,
    String assetKey,
    long assetVersion,
    String title,
    String reportType,
    String url,
    UUID queryDatasetId,
    Integer queryDatasetVersion,
    List<String> deptCodes,
    List<String> roleCodes,
    String classification,
    Instant expiresAt,
    boolean enabled
) {}
