package com.yuzhi.dts.platform.service.sql.dto;

public record CreateQueryDatasetFromExecutionRequest(
    String name,
    String description,
    String refreshStrategy,
    String changeSummary
) {}
