package com.yuzhi.dts.platform.service.services.dto;

public record DataProductMetadataDto(
    String bloodlineSummary,
    String classificationStrategy,
    String maskingStrategy,
    String latencyObjective,
    String failurePolicy
) {}
