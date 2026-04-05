package com.yuzhi.dts.platform.service.governance;

import java.util.List;

public record CleansingResult(
    String tableName,
    int functionsApplied,
    int columnsProcessed,
    int totalRowsAffected,
    List<FunctionResult> details
) {
    public record FunctionResult(
        String functionCode,
        String functionName,
        String columnName,
        int rowsAffected
    ) {}
}
