package com.yuzhi.dts.platform.service.goldenchain.modeling;

public record GoldenChainOdsColumnSnapshot(String name, String dataType, boolean nullable, String description) {
    public GoldenChainOdsColumnSnapshot {
        name = requireText(name, "字段名不能为空");
        dataType = requireText(dataType, "字段类型不能为空");
        description = normalize(description);
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
