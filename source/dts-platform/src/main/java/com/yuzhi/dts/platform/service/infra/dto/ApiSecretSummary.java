package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;

public record ApiSecretSummary(
    String providerId,
    String fieldName,
    String maskedDisplay,
    String secretVersion,
    Instant rotatedAt,
    String status
) {}
