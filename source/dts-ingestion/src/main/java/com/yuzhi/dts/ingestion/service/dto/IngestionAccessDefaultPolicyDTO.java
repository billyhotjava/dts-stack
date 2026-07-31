package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record IngestionAccessDefaultPolicyDTO(
    String policyKey,
    Integer version,
    String status,
    JsonNode defaults,
    String checksum,
    Instant activatedAt
) {}
