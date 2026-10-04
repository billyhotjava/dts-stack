package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class GoldenChainOpsWorkCenterService {

    public GoldenChainOpsWorkCenterView buildView(
        List<GoldenChainOpsInstanceSnapshot> instances,
        List<GoldenChainOpsAlertSnapshot> alerts,
        List<GoldenChainOpsBackfillSnapshot> backfills
    ) {
        List<GoldenChainOpsInstanceSnapshot> safeInstances = instances == null ? List.of() : List.copyOf(instances);
        List<GoldenChainOpsAlertSnapshot> safeAlerts = alerts == null ? List.of() : List.copyOf(alerts);
        List<GoldenChainOpsBackfillSnapshot> safeBackfills = backfills == null ? List.of() : List.copyOf(backfills);
        Map<String, GoldenChainOpsAlertSnapshot> alertByTask = safeAlerts
            .stream()
            .collect(Collectors.toMap(this::chainTaskKey, item -> item, (left, right) -> left));
        Map<String, GoldenChainOpsBackfillSnapshot> backfillByTask = safeBackfills
            .stream()
            .collect(Collectors.toMap(this::chainTaskKey, item -> item, (left, right) -> left));

        List<GoldenChainOpsFailureItem> failedItems = new ArrayList<>();
        for (GoldenChainOpsInstanceSnapshot instance : safeInstances) {
            if (!isFailed(instance.status())) {
                continue;
            }
            String key = chainTaskKey(instance.chainKey(), instance.taskId());
            GoldenChainOpsAlertSnapshot alert = alertByTask.get(key);
            GoldenChainOpsBackfillSnapshot backfill = backfillByTask.get(key);
            failedItems.add(
                new GoldenChainOpsFailureItem(
                    instance.chainKey(),
                    instance.taskId(),
                    instance.instanceId(),
                    alert == null ? null : alert.alertId(),
                    backfill == null ? null : backfill.backfillId(),
                    backfill == null || isOpenBackfill(backfill.status()),
                    instance.logPath()
                )
            );
        }

        return new GoldenChainOpsWorkCenterView(safeInstances, safeAlerts, safeBackfills, failedItems);
    }

    private String chainTaskKey(GoldenChainOpsAlertSnapshot alert) {
        return chainTaskKey(alert.chainKey(), alert.taskId());
    }

    private String chainTaskKey(GoldenChainOpsBackfillSnapshot backfill) {
        return chainTaskKey(backfill.chainKey(), backfill.taskId());
    }

    private String chainTaskKey(String chainKey, String taskId) {
        return safeText(chainKey) + "|" + safeText(taskId);
    }

    private boolean isFailed(String status) {
        String normalized = safeText(status).toUpperCase(Locale.ROOT);
        return normalized.equals("FAILED") || normalized.equals("FAIL") || normalized.equals("ERROR") || normalized.equals("TIMEOUT");
    }

    private boolean isOpenBackfill(String status) {
        String normalized = safeText(status).toUpperCase(Locale.ROOT);
        return normalized.equals("PENDING") || normalized.equals("SUBMITTED") || normalized.equals("RUNNING");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
