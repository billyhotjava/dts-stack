package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainPermissionConsistencyDecision(
    boolean consistent,
    String assetKey,
    String userRef,
    List<String> blockers,
    List<String> warnings,
    String safeMessage,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainPermissionConsistencyDecision {
        assetKey = assetKey == null ? "" : assetKey.trim();
        userRef = userRef == null ? "" : userRef.trim();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        safeMessage = safeMessage == null ? "" : safeMessage.trim();
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
    }
}
