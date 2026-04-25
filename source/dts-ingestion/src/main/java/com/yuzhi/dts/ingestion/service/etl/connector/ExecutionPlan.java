package com.yuzhi.dts.ingestion.service.etl.connector;

import java.util.List;
import java.util.Map;

public record ExecutionPlan(
    String engine,
    String connectorType,
    String contractVersion,
    String jobPayloadRef,
    Map<String, Object> payload,
    List<String> secretRefs,
    CheckpointPolicy checkpointPolicy,
    Map<String, String> observabilityTags
) {
    public record CheckpointPolicy(String type, String cursorField, String updateMode) {}
}

