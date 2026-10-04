package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.Objects;

public record GoldenChainOpsErrorClassification(
    GoldenChainOpsErrorCategory category,
    GoldenChainOpsRetryAction retryAction,
    boolean recoverable,
    String suggestedAction
) {
    public GoldenChainOpsErrorClassification {
        category = Objects.requireNonNull(category, "category must not be null");
        retryAction = Objects.requireNonNull(retryAction, "retryAction must not be null");
        suggestedAction = suggestedAction == null ? "" : suggestedAction.trim();
    }
}
