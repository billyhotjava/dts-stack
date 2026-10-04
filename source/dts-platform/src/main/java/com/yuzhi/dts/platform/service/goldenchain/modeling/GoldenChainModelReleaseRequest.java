package com.yuzhi.dts.platform.service.goldenchain.modeling;

import java.util.Objects;

public record GoldenChainModelReleaseRequest(
    String modelName,
    GoldenChainModelLayer layer,
    String owner,
    GoldenChainReleaseEnvironment environment,
    GoldenChainDbtRunEvidence dbtEvidence,
    GoldenChainModelGovernanceSnapshot governanceSnapshot,
    GoldenChainModelSemanticContract semanticContract
) {
    public GoldenChainModelReleaseRequest {
        modelName = requireText(modelName, "模型名称不能为空");
        layer = Objects.requireNonNull(layer, "layer must not be null");
        owner = normalize(owner);
        environment = environment == null ? GoldenChainReleaseEnvironment.PROD : environment;
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
