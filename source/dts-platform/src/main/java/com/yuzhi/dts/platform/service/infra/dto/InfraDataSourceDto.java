package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InfraDataSourceDto(
    UUID id,
    String name,
    String type,
    String jdbcUrl,
    String username,
    String description,
    Map<String, Object> props,
    Instant createdAt,
    Instant lastVerifiedAt,
    String status,
    boolean hasSecrets
) {}
