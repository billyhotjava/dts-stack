package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record QualityDashboardDto(
    int ruleCount,
    int coveredDatasets,
    int totalDatasets,
    int todayPassed,
    int todayFailed,
    long pendingFixRows,
    List<TrendPoint> trend7d,
    List<FailingDataset> topFailingDatasets,
    List<RecentRun> recentFailedRuns
) {
    public record TrendPoint(String date, double passRate) {}
    public record FailingDataset(String name, long failingRows) {}
    public record RecentRun(String ruleName, String dataset, String time, String status) {}
}
