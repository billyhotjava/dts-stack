package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainGovernanceGateDecision(
    boolean publishable,
    String assetKey,
    List<String> blockers,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainGovernanceGateDecision {
        assetKey = requireText(assetKey, "资产 key 不能为空");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
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
