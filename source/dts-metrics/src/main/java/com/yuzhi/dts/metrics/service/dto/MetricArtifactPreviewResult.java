package com.yuzhi.dts.metrics.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MetricArtifactPreviewResult(
    boolean valid,
    List<String> errors,
    Map<String, Object> summary,
    Map<String, String> artifacts,
    List<String> warnings,
    Instant generatedAt
) {
    public static MetricArtifactPreviewResult invalid(List<String> errors, Map<String, Object> summary) {
        return new MetricArtifactPreviewResult(false, List.copyOf(errors), Map.copyOf(summary), Map.of(), List.of(), Instant.now());
    }

    public static MetricArtifactPreviewResult valid(
        Map<String, Object> summary,
        Map<String, String> artifacts,
        List<String> warnings
    ) {
        return new MetricArtifactPreviewResult(true, List.of(), Map.copyOf(summary), Map.copyOf(artifacts), List.copyOf(warnings), Instant.now());
    }
}
