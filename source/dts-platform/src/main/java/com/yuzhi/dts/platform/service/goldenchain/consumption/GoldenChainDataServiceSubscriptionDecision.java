package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainDataServiceSubscriptionDecision(
    boolean operable,
    GoldenChainDataServiceSubscriptionStatus status,
    String serviceViewRef,
    String tokenRef,
    long callCount,
    List<String> dependencyAssetKeys,
    String serviceSummary,
    List<String> blockers,
    String safeMessage,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainDataServiceSubscriptionDecision {
        status = Objects.requireNonNull(status, "status must not be null");
        serviceViewRef = normalize(serviceViewRef);
        tokenRef = normalize(tokenRef);
        dependencyAssetKeys = dependencyAssetKeys == null ? List.of() : List.copyOf(dependencyAssetKeys);
        serviceSummary = normalize(serviceSummary);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        safeMessage = normalize(safeMessage);
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
