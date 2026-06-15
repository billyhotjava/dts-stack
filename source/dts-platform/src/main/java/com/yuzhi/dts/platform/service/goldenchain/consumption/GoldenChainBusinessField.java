package com.yuzhi.dts.platform.service.goldenchain.consumption;

import java.util.Objects;

public record GoldenChainBusinessField(
    String fieldKey,
    String displayName,
    GoldenChainBusinessFieldRole role,
    String businessDefinition,
    String unit,
    String threshold,
    String refreshedAt
) {
    public GoldenChainBusinessField {
        fieldKey = requireText(fieldKey, "字段 key 不能为空");
        displayName = requireText(displayName, "业务字段名称不能为空");
        role = Objects.requireNonNull(role, "role must not be null");
        businessDefinition = requireText(businessDefinition, "业务字段说明不能为空");
        unit = normalize(unit);
        threshold = normalize(threshold);
        refreshedAt = normalize(refreshedAt);
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
