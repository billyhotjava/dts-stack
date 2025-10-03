package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InfraDataStorageDto(
    UUID id,
    String name,
    String type,
    String location,
    String description,
    Map<String, Object> props,
    Instant createdAt,
    boolean hasSecrets
) {}
