package com.yuzhi.dts.platform.service.goldenchain.ops;

public record GoldenChainOpsFailureItem(
    String chainKey,
    String taskId,
    String instanceId,
    String alertId,
    String backfillId,
    boolean canBackfill,
    String logPath
) {}
