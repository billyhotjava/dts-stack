package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Typed edit contract for the integrated ingestion flow page.
 *
 * <p>Runtime-owned fields (status, revision, DAG id and legacy graph DSL) are
 * intentionally absent so a browser cannot overwrite execution state.</p>
 */
public record IngestionTaskDesignUpdateRequest(
    @NotBlank @Size(max = 200) String taskName,
    @Size(max = 2000) String description,
    UUID sourceDataSourceId,
    @NotBlank @Size(max = 50) String sourceType,
    JsonNode sourceConfig,
    @NotBlank @Size(max = 50) String destinationType,
    JsonNode destinationConfig,
    UUID targetDatasetId,
    @NotBlank @Size(max = 50) String syncMode,
    @Size(max = 100) String syncSchedule,
    @NotNull JsonNode tableMapping,
    JsonNode syncConfig,
    boolean postIngestionQualityEnabled,
    @Size(max = 200) String qualityPolicyRef
) {}
