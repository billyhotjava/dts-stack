package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InfraConnectorDto(
    UUID id,
    String connectorKey,
    String name,
    String category,
    String sourceType,
    String defaultEngine,
    String status,
    Integer displayOrder,
    String description,
    Map<String, Object> capabilities,
    Map<String, Object> configSchema,
    List<String> sensitiveFields,
    Map<String, Object> compatibility,
    ConnectorDriverBindingDto driver,
    Instant createdAt,
    Instant lastUpdatedAt
) {}
