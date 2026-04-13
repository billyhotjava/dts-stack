package com.yuzhi.dts.platform.service.sql.dto;

public record PlanResultDto(
    PlanNodeDto root,
    String rawText,
    String engine,
    long explainTimeMs
) {}
