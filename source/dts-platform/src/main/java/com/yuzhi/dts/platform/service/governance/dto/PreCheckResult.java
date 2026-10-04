package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record PreCheckResult(
    int totalRows,
    int passedRows,
    int failedRows,
    List<RuleCheckResult> errorsByRule
) {}
