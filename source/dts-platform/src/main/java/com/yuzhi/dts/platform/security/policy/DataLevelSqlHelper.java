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
        return buildPredicate(columnExpression, allowedLevels, false);
    }

    /**
     * 支持数值型密级字段：
     * <ul>
     *   <li>字符串列：使用 IN (...)，兼容 PUBLIC/内部/0 等混写</li>
     *   <li>数值列：使用 {@code columnExpression <= maxRank}（0~3）</li>
     * </ul>
     */
    public static String buildPredicate(String columnExpression, Collection<DataLevel> allowedLevels, boolean numericColumn) {
        if (columnExpression == null || columnExpression.isBlank()) {
            return null;
        }
        if (allowedLevels == null || allowedLevels.isEmpty()) {
            return null;
        }
        if (numericColumn) {
            int maxRank = allowedLevels
                .stream()
                .filter(Objects::nonNull)
                .mapToInt(DataLevel::rank)
                .max()
                .orElse(-1);
            if (maxRank < 0) {
                return null;
            }
            return columnExpression + " <= " + maxRank;
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
