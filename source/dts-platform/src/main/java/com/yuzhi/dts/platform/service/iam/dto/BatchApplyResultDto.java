package com.yuzhi.dts.platform.service.iam.dto;

import java.time.Instant;

public record BatchApplyResultDto(boolean ok, Instant appliedAt) {}
