package com.yuzhi.dts.platform.service.development.dto;

import java.time.Instant;
import java.util.UUID;

public record ScriptRunResponse(
    UUID id,
    UUID scriptId,
    Integer versionNo,
    String executionId,
    String status,
    String failureType,
    String errorMessage,
    String logText,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs,
    String triggeredBy,
    Instant createdDate
) {}
