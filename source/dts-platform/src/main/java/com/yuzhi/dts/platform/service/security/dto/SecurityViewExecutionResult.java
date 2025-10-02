package com.yuzhi.dts.platform.service.security.dto;

import java.util.List;
import java.util.Map;

public record SecurityViewExecutionResult(
    Map<String, String> statements,
    List<StatementExecutionResult> executionResults,
    int persistedViews,
    boolean success
) {}
