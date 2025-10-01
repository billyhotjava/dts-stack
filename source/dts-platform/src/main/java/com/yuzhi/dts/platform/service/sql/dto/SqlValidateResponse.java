package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;

public record SqlValidateResponse(
    boolean executable,
    String rewrittenSql,
    SqlSummary summary,
    List<SqlViolation> violations,
    List<String> warnings,
    PlanSnippet plan,
    SqlLimitInfo limitInfo
) {}
