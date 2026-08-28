package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/** Read model for the task-owned, single-flow ingestion designer. */
public record IngestionTaskDesignDTO(
    Long taskId,
    String taskName,
    String description,
    Integer revisionNumber,
    String revisionState,
    String operationalState,
    SourceDesign source,
    DestinationDesign destination,
    String syncMode,
    String syncSchedule,
    JsonNode tableMapping,
    JsonNode syncConfig,
    PostIngestionQuality postIngestionQuality,
    String planChecksum,
    ValidationResult validation,
    TopologyProjection topology,
    LegacyDsl legacyDsl
) {
    public record SourceDesign(UUID dataSourceId, String type, JsonNode config) {}

    public record DestinationDesign(String type, JsonNode config, DestinationAssetRef assetRef) {}

    public record DestinationAssetRef(UUID datasetId, String policyRef, String resolution) {}

    public record PostIngestionQuality(boolean enabled, String policyRef) {}

    public record ValidationResult(boolean valid, List<ValidationIssue> issues) {}

    public record ValidationIssue(String code, String field, String message) {}

    public record TopologyProjection(
        boolean readonly,
        String planChecksum,
        List<TopologyNode> nodes,
        List<TopologyEdge> edges
    ) {}

    public record TopologyNode(String id, String kind, String label, String state) {}

    public record TopologyEdge(String source, String target) {}

    public record LegacyDsl(boolean present, boolean publishable, String message) {}

    public record ScheduleCommand(Long taskId, String state, String airflowDagId) {}
}
