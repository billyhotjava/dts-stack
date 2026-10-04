package com.yuzhi.dts.platform.service.goldenchain.ops;

public record GoldenChainOpsInstanceSnapshot(
    String instanceId,
    String chainKey,
    String taskId,
    String taskName,
    String status,
    Long durationMs,
    String logPath
) {
    public GoldenChainOpsInstanceSnapshot {
        instanceId = requireText(instanceId, "实例 ID 不能为空");
        chainKey = normalize(chainKey);
        taskId = normalize(taskId);
        taskName = normalize(taskName);
        status = normalize(status);
        logPath = normalize(logPath);
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
