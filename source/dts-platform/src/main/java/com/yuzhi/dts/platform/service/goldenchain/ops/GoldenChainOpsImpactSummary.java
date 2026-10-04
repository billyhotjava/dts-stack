package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.List;

public record GoldenChainOpsImpactSummary(
    String chainKey,
    String latestInstanceId,
    long failureCount,
    Double mttrMinutes,
    String backfillStatus,
    String openLogRoute,
    List<String> affectedAssets,
    List<String> affectedReports,
    List<String> affectedApis
) {
    public GoldenChainOpsImpactSummary {
        chainKey = requireText(chainKey, "链路 key 不能为空");
        latestInstanceId = latestInstanceId == null ? "" : latestInstanceId.trim();
        backfillStatus = backfillStatus == null ? "" : backfillStatus.trim();
        openLogRoute = openLogRoute == null ? "" : openLogRoute.trim();
        affectedAssets = affectedAssets == null ? List.of() : List.copyOf(affectedAssets);
        affectedReports = affectedReports == null ? List.of() : List.copyOf(affectedReports);
        affectedApis = affectedApis == null ? List.of() : List.copyOf(affectedApis);
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
