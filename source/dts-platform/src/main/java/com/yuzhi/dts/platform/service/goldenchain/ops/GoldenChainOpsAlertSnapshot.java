package com.yuzhi.dts.platform.service.goldenchain.ops;

public record GoldenChainOpsAlertSnapshot(
    String alertId,
    String chainKey,
    String taskId,
    String type,
    String severity,
    String message
) {
    public GoldenChainOpsAlertSnapshot {
        alertId = requireText(alertId, "告警 ID 不能为空");
        chainKey = normalize(chainKey);
        taskId = normalize(taskId);
        type = normalize(type);
        severity = normalize(severity);
        message = normalize(message);
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
