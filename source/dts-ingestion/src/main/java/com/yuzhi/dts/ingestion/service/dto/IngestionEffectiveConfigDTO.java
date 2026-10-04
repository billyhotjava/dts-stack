package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record IngestionEffectiveConfigDTO(
    Long taskId,
    Integer revisionNumber,
    String revisionState,
    String sourceKind,
    JsonNode effectiveConfig,
    String effectiveConfigChecksum,
    Integer defaultPolicyVersion,
    String defaultPolicyChecksum,
    String qualityPolicyRef
) {}
