package com.yuzhi.dts.platform.service.goldenchain.governance;

import java.util.List;
import java.util.Objects;

public record GoldenChainAssetIdentityResolution(
    GoldenChainGovernanceState state,
    String assetKey,
    List<String> diagnostics
) {
    public GoldenChainAssetIdentityResolution {
        state = Objects.requireNonNull(state, "state must not be null");
        assetKey = assetKey == null ? "" : assetKey.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
