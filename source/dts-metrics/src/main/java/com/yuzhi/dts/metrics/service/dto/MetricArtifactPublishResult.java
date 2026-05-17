package com.yuzhi.dts.metrics.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MetricArtifactPublishResult(
    boolean valid,
    List<String> errors,
    Map<String, Object> summary,
    Map<String, String> artifacts,
    List<String> warnings,
    Map<String, Object> releaseGate,
    String appliedPolicySource,
    String appliedPredicateHash,
    Instant generatedAt
) {
    public static MetricArtifactPublishResult invalid(List<String> errors, Map<String, Object> summary) {
        return new MetricArtifactPublishResult(
            false,
            List.copyOf(errors),
            Map.copyOf(summary),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            null,
            Instant.now()
        );
    }

    public static MetricArtifactPublishResult valid(
        Map<String, Object> summary,
        Map<String, String> artifacts,
        List<String> warnings,
        Map<String, Object> releaseGate,
        String appliedPolicySource,
        String appliedPredicateHash
    ) {
        return new MetricArtifactPublishResult(
            true,
            List.of(),
            Map.copyOf(summary),
            Map.copyOf(artifacts),
            List.copyOf(warnings),
            Map.copyOf(releaseGate),
            appliedPolicySource,
            appliedPredicateHash,
            Instant.now()
        );
    }
}
