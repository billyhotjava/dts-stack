package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainConsumptionSurface;
import java.util.List;
import java.util.Objects;

public record GoldenChainConsumptionPermissionView(
    boolean consumable,
    String assetKey,
    String userRef,
    List<GoldenChainConsumptionSurface> allowedSurfaces,
    List<GoldenChainConsumptionSurface> deniedSurfaces,
    List<GoldenChainConsumptionSurface> missingSurfaces,
    String platformPolicyHash,
    String rlsHash,
    String maskingHash,
    List<String> blockers,
    String safeMessage,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainConsumptionPermissionView {
        assetKey = normalize(assetKey);
        userRef = normalize(userRef);
        allowedSurfaces = allowedSurfaces == null ? List.of() : List.copyOf(allowedSurfaces);
        deniedSurfaces = deniedSurfaces == null ? List.of() : List.copyOf(deniedSurfaces);
        missingSurfaces = missingSurfaces == null ? List.of() : List.copyOf(missingSurfaces);
        platformPolicyHash = normalize(platformPolicyHash);
        rlsHash = normalize(rlsHash);
        maskingHash = normalize(maskingHash);
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
