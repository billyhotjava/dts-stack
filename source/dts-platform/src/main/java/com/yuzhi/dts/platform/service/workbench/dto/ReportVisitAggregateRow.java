package com.yuzhi.dts.platform.service.workbench.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection row for TOP-N report aggregations (Sprint-15 F1/T04).
 *
 * <p>Produced by aggregating {@code BiReportVisit} joined to
 * {@code BiReportLink}. {@code visits} is the count in the selected
 * window for DEPT/ALL aggregations, or the per-group count (typically 1
 * per group when ordered by {@code max(visitedAt)}) for the MINE
 * "most-recent" mode.
 */
public record ReportVisitAggregateRow(
    UUID reportId,
    String title,
    long visits,
    String bizDomain,
    String classification,
    Instant lastVisitedAt
) {}
