package com.yuzhi.dts.platform.service.goldenchain.governance;

import java.util.Objects;

public record GoldenChainPermissionSnapshot(
    GoldenChainConsumptionSurface surface,
    String assetKey,
    String userRef,
    boolean allowed,
    String platformPolicyHash,
    String rlsHash,
    String maskingHash,
    boolean localFallbackUsed,
    boolean breakGlass
) {
    public GoldenChainPermissionSnapshot {
        surface = Objects.requireNonNull(surface, "surface must not be null");
        assetKey = requireText(assetKey, "资产 key 不能为空");
        userRef = requireText(userRef, "用户引用不能为空");
        platformPolicyHash = normalize(platformPolicyHash);
        rlsHash = normalize(rlsHash);
        maskingHash = normalize(maskingHash);
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
