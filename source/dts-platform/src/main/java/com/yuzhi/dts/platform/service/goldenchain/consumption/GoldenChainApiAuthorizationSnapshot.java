package com.yuzhi.dts.platform.service.goldenchain.consumption;

public record GoldenChainApiAuthorizationSnapshot(
    String tokenRef,
    boolean approved,
    long dailyQuota,
    long callCount,
    String lastCalledAt
) {
    public GoldenChainApiAuthorizationSnapshot {
        tokenRef = normalize(tokenRef);
        if (dailyQuota < 0) {
            throw new IllegalArgumentException("dailyQuota must not be negative");
        }
        if (callCount < 0) {
            throw new IllegalArgumentException("callCount must not be negative");
        }
        lastCalledAt = normalize(lastCalledAt);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
