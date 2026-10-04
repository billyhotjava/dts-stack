package com.yuzhi.dts.metrics.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MetricPackValidationResult(
    boolean valid,
    List<String> errors,
    Map<String, Object> summary,
    Instant checkedAt
) {
    public static MetricPackValidationResult valid(Map<String, Object> summary) {
        return new MetricPackValidationResult(true, List.of(), summary, Instant.now());
    }

    public static MetricPackValidationResult invalid(List<String> errors, Map<String, Object> summary) {
        return new MetricPackValidationResult(false, List.copyOf(errors), summary, Instant.now());
    }
}
