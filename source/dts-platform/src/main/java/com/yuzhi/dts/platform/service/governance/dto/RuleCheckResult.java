package com.yuzhi.dts.platform.service.governance.dto;

import java.util.List;

public record RuleCheckResult(
    String ruleName,
    String ruleType,
    int failCount,
    List<FailingRow> sampleRows
) {
    public record FailingRow(int rowNum, String column, String actualValue, String reason) {}
}
