package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InfraDataSourceDetailDto(
    UUID id,
    String name,
    String type,
    String jdbcUrl,
    String username,
    String description,
    String ownerDept,
    Map<String, Object> props,
    Map<String, Object> secrets,
    String status,
    Instant lastVerifiedAt
) {}
