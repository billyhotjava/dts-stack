package com.yuzhi.dts.platform.service.goldenchain.governance;

import java.util.Objects;

public record GoldenChainLineageEvidence(
    GoldenChainLineageSource source,
    String assetKey,
    String sourceRef,
    String targetRef,
    String evidenceRef,
    boolean parsed,
    String failureReason
) {
    public GoldenChainLineageEvidence {
        source = Objects.requireNonNull(source, "source must not be null");
        assetKey = requireText(assetKey, "资产 key 不能为空");
        sourceRef = normalize(sourceRef);
        targetRef = normalize(targetRef);
        evidenceRef = normalize(evidenceRef);
        failureReason = normalize(failureReason);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
