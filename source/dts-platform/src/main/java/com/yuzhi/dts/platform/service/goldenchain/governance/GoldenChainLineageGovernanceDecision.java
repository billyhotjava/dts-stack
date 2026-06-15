package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainLineageGovernanceDecision(
    boolean publishable,
    GoldenChainGovernanceState state,
    List<String> diagnostics,
    String remediation,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainLineageGovernanceDecision {
        state = Objects.requireNonNull(state, "state must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        remediation = remediation == null ? "" : remediation.trim();
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
    }
}
