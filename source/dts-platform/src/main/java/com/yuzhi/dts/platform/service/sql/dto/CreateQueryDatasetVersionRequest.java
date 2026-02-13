package com.yuzhi.dts.platform.service.sql.dto;

public record CreateQueryDatasetVersionRequest(
    String sqlText,
    String changeSummary,
    String status
) {}
