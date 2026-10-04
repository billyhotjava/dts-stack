package com.yuzhi.dts.analytics.web.rest.internal.dto;

import java.time.Instant;

/**
 * Sprint-17 / F1 — minimal shape returned by
 * {@code GET /api/internal/screens} to dts-platform reconcile.
 * Trimmed subset of {@link com.yuzhi.dts.analytics.domain.AnalyticsScreen}:
 * we only expose the fields needed to populate the platform-side
 * {@code BiReportLink} mirror (id, label, classification, owner dept,
 * archived flag, last-updated timestamp).
 */
public record ScreenSummaryDto(
    Long id,
    String name,
    String description,
    String classification,
    String ownerDeptCode,
    boolean archived,
    Instant updatedAt
) {}
