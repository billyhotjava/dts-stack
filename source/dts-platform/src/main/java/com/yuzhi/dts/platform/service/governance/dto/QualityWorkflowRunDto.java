package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QualityWorkflowRunDto(
    UUID id,
    UUID taskId,
    UUID datasetId,
    UUID ruleId,
    UUID retryOfId,
    Integer attemptNo,
    Integer maxRetryAttempts,
    Integer retryBackoffSeconds,
    String triggerType,
    String triggerRef,
    String status,
    Integer expectedRunCount,
    Integer completedRunCount,
    Integer passedCount,
    Integer failedCount,
    Integer dispatchFailureCount,
    Instant scheduledAt,
    Instant startedAt,
    Instant finishedAt,
    String errorCategory,
    String message,
    String contextJson,
    Instant createdDate,
    String createdBy,
    List<QualityRunDto> ruleRuns
) {}
