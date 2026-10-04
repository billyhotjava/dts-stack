package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.List;

public record GoldenChainOpsWorkCenterView(
    List<GoldenChainOpsInstanceSnapshot> instances,
    List<GoldenChainOpsAlertSnapshot> alerts,
    List<GoldenChainOpsBackfillSnapshot> backfills,
    List<GoldenChainOpsFailureItem> failedItems
) {
    public GoldenChainOpsWorkCenterView {
        instances = instances == null ? List.of() : List.copyOf(instances);
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
        backfills = backfills == null ? List.of() : List.copyOf(backfills);
        failedItems = failedItems == null ? List.of() : List.copyOf(failedItems);
    }
}
