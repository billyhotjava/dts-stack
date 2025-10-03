package com.yuzhi.dts.platform.service.iam.dto;

import java.time.Instant;

public record ObjectPolicyDto(
    String subjectType,
    String subjectId,
    String subjectName,
    String effect,
    Instant validFrom,
    Instant validTo,
    String source
) {}
