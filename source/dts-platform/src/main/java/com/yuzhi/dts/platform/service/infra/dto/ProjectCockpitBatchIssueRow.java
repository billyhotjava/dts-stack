package com.yuzhi.dts.platform.service.infra.dto;

public record ProjectCockpitBatchIssueRow(
    Integer rowIndex,
    String severity,
    String issueCode,
    String message,
    String projectNo,
    String subsystem,
    String nodeTask,
    String planDate,
    String completionStatus,
    String riskLevel
) {}
