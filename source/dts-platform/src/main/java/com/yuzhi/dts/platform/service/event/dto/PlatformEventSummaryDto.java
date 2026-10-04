package com.yuzhi.dts.platform.service.event.dto;

import java.util.Map;

public record PlatformEventSummaryDto(
    long total,
    long pending,
    long sent,
    long failed,
    long skipped,
    boolean kafkaEnabled,
    String kafkaTopic,
    Map<String, Long> byDomain,
    Map<String, Long> byStatus,
    Map<String, Long> bySeverity
) {}
