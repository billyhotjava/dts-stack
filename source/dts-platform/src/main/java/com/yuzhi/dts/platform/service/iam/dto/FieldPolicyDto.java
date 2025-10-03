package com.yuzhi.dts.platform.service.iam.dto;

public record FieldPolicyDto(
    String field,
    String subjectType,
    String subjectName,
    String effect
) {}
