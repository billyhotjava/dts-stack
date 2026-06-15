package com.yuzhi.dts.platform.service.goldenchain.governance;

import java.util.Objects;

public record GoldenChainGovernanceGateRequest(
    String assetKey,
    GoldenChainGovernedAssetType assetType,
    String owner,
    String classification,
    boolean standardMapped,
    boolean qualityRulesPresent,
    boolean qualityPassed,
    String primaryKey,
    String grain,
    boolean metricDefinitionReady
) {
    public GoldenChainGovernanceGateRequest {
        assetKey = requireText(assetKey, "资产 key 不能为空");
        assetType = Objects.requireNonNull(assetType, "assetType must not be null");
        owner = normalize(owner);
        classification = normalize(classification);
        primaryKey = normalize(primaryKey);
        grain = normalize(grain);
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
