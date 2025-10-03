package com.yuzhi.dts.platform.service.iam.dto;

import java.util.List;

public record DatasetPoliciesDto(
    List<ObjectPolicyDto> objectPolicies,
    List<FieldPolicyDto> fieldPolicies,
    List<RowConditionDto> rowConditions
) {}
