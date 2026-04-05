package com.yuzhi.dts.platform.service.governance;

import java.util.List;

/**
 * dbt 指标生成结果。
 */
public record GenerationResult(
    int totalIndicators,
    List<String> generatedFiles,
    String compileStatus,
    String runStatus,
    List<String> warnings
) {}
