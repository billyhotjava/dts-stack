package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.List;
import java.util.Locale;

public class GoldenChainOpsImpactService {

    public GoldenChainOpsImpactSummary summarize(
        String chainKey,
        List<GoldenChainOpsInstanceSnapshot> instances,
        List<GoldenChainOpsBackfillSnapshot> backfills,
        List<String> affectedAssets,
        List<String> affectedReports,
        List<String> affectedApis
    ) {
        List<GoldenChainOpsInstanceSnapshot> chainInstances = (instances == null ? List.<GoldenChainOpsInstanceSnapshot>of() : instances)
            .stream()
            .filter(instance -> chainKey.equals(instance.chainKey()))
            .toList();
        List<GoldenChainOpsBackfillSnapshot> chainBackfills = (backfills == null ? List.<GoldenChainOpsBackfillSnapshot>of() : backfills)
            .stream()
            .filter(backfill -> chainKey.equals(backfill.chainKey()))
            .toList();
        GoldenChainOpsInstanceSnapshot latest = chainInstances.isEmpty() ? null : chainInstances.get(0);
        long failures = chainInstances.stream().filter(instance -> isFailed(instance.status())).count();
        Double mttrMinutes = computeMttrMinutes(chainInstances);
        String latestBackfillStatus = chainBackfills.isEmpty() ? "" : safeText(chainBackfills.get(0).status());
        String latestInstanceId = latest == null ? "" : latest.instanceId();
        return new GoldenChainOpsImpactSummary(
            chainKey,
            latestInstanceId,
            failures,
            mttrMinutes,
            latestBackfillStatus,
            latestInstanceId.isBlank() ? "" : "/ops/logs?chainKey=%s&runId=%s".formatted(chainKey, latestInstanceId),
            affectedAssets,
            affectedReports,
            affectedApis
        );
    }

    private Double computeMttrMinutes(List<GoldenChainOpsInstanceSnapshot> instances) {
        long failedCount = 0;
        long durationMs = 0;
        for (GoldenChainOpsInstanceSnapshot instance : instances) {
            if (isFailed(instance.status()) && instance.durationMs() != null && instance.durationMs() >= 0) {
                failedCount++;
                durationMs += instance.durationMs();
            }
        }
        if (failedCount == 0) {
            return null;
        }
        return Math.round((durationMs / 60000.0 / failedCount) * 10.0) / 10.0;
    }

    private boolean isFailed(String status) {
        String normalized = safeText(status).toUpperCase(Locale.ROOT);
        return normalized.equals("FAILED") || normalized.equals("FAIL") || normalized.equals("ERROR") || normalized.equals("TIMEOUT");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
