package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record QualityScoreResult(
    int overall,
    Integer overallDelta,
    List<DimensionScore> dimensions,
    List<TrendPoint> trend
) {
    public record DimensionScore(String type, int score, Integer delta) {}
    public record TrendPoint(String date, int overall) {}
}
