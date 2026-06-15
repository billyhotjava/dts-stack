package com.yuzhi.dts.platform.service.goldenchain.ops;

public record GoldenChainOpsBackfillSnapshot(String backfillId, String chainKey, String taskId, String status) {
    public GoldenChainOpsBackfillSnapshot {
        backfillId = requireText(backfillId, "补数 ID 不能为空");
        chainKey = normalize(chainKey);
        taskId = normalize(taskId);
        status = normalize(status);
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
