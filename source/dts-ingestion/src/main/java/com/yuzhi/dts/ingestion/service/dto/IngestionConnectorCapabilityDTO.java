package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IngestionConnectorCapabilityDTO(
    String connectorType,
    List<String> capabilities,
    String connectorVersion,
    Map<String, Object> constraints,
    Boolean enabled,
    Instant updatedAt
) {}
