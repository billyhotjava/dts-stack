package com.yuzhi.dts.platform.service.iam.dto;

public record RowConditionDto(
    String subjectType,
    String subjectName,
    String expression,
    String description
) {}
