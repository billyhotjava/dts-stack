package com.yuzhi.dts.platform.security.policy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 辅助生成基于数据密级的 SQL 条件，供各模块统一调用。
 */
public final class DataLevelSqlHelper {

    private DataLevelSqlHelper() {}

    /**
     * 根据允许访问的数据级别生成 SQL 过滤条件：
     * {@code UPPER(columnExpression) IN ('PUBLIC','DATA_PUBLIC',...)}。
     * <p>
     * 同时包含 DATA_* 与裸密级取值，兼容历史数据。
     */
    public static String buildPredicate(String columnExpression, Collection<DataLevel> allowedLevels) {
        if (columnExpression == null || columnExpression.isBlank()) {
            return null;
        }
        if (allowedLevels == null || allowedLevels.isEmpty()) {
            return null;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (DataLevel level : allowedLevels) {
            if (level == null) continue;
            tokens.addAll(level.tokens());
        }
        tokens.removeIf(Objects::isNull);
        if (tokens.isEmpty()) {
            return null;
        }
        String inClause = tokens
            .stream()
            .map(token -> "'" + token.replace("'", "''").toUpperCase() + "'")
            .collect(Collectors.joining(","));
        return "UPPER(TRIM(" + columnExpression + ")) IN (" + inClause + ")";
    }
}
