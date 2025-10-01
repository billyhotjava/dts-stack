package com.yuzhi.dts.platform.service.sql.dto;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import java.util.UUID;

public record SqlStatusResponse(
    UUID executionId,
    ExecEnums.ExecStatus status,
    Long elapsedMs,
    Long rows,
    Long bytes,
    Integer queuePosition,
    String errorMessage,
    UUID resultSetId,
    PlanSnippet plan
) {}
