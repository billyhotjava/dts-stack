package com.yuzhi.dts.platform.service.governance;

import java.util.List;
import java.util.Map;

/**
 * 质量规则 SQL 执行器抽象。
 * HiveStatementExecutor 用于湖内巡检，PgStatementExecutor 用于入湖预检。
 */
public interface QualityStatementExecutor {
    List<Map<String, Object>> executeQualityCheck(String sql, int maxRows);
}
