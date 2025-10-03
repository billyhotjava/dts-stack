package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;

public record ApiMetricsDto(
    List<SeriesPoint> series,
    List<DistributionSlice> levelDistribution,
    List<RecentCall> recentCalls
) {

    public record SeriesPoint(long timestamp, long calls, int qps) {}

    public record DistributionSlice(String label, long value) {}

    public record RecentCall(String user, String level, long rowCount, String policy) {}
}
