package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.UUID;

public record InfraJdbcDriverDto(
    UUID id,
    String fileName,
    String filePath,
    String driverClass,
    String version,
    String jdkSpec,
    Instant createdAt,
    Instant lastUpdatedAt,
    boolean missing
) {}
