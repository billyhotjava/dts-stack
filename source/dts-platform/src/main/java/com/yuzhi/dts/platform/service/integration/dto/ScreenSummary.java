package com.yuzhi.dts.platform.service.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Sprint-17 / F1 — Platform-side mirror of dts-analytics
 * {@code ScreenSummaryDto}, populated by the reconcile job.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so a future
 * field added on the dts-bi side won't break this client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScreenSummary(
    Long id,
    String name,
    String description,
    String classification,
    String ownerDeptCode,
    boolean archived,
    Instant updatedAt
) {}
