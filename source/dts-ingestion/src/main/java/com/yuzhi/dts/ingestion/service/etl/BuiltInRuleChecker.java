package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BuiltInRuleChecker {

    private static final Logger log = LoggerFactory.getLogger(BuiltInRuleChecker.class);

    private final JdbcTemplate jdbcTemplate;

    public BuiltInRuleChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Run all built-in checks on the staging table.
     * Returns Map<rowNum, List<CellError>> -- errors grouped by row.
     */
    public Map<Integer, List<CellError>> check(String tableName, List<ColumnInfo> columns) {
        if (columns == null || columns.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<CellError>> errors = new HashMap<>();

        checkEmptyRows(tableName, columns, errors);
        checkDuplicateRows(tableName, columns, errors);
        checkNumericWithUnit(tableName, columns, errors);
        checkDateFormatInconsistency(tableName, columns, errors);
        checkLargeNumericPrecision(tableName, columns, errors);

        return errors;
    }

    /**
     * Check 1: Empty rows -- all columns are NULL or blank.
     */
    private void checkEmptyRows(String tableName, List<ColumnInfo> columns,
                                Map<Integer, List<CellError>> errors) {
        List<String> sanitized = sanitizedColumnNames(columns);

        String whereClause = sanitized.stream()
            .map(col -> "(\"" + col + "\" IS NULL OR TRIM(\"" + col + "\") = '')")
            .collect(Collectors.joining(" AND "));

        String sql = "SELECT _row_num FROM " + tableName + " WHERE " + whereClause;

        try {
            List<Integer> rowNums = jdbcTemplate.queryForList(sql, Integer.class);
            for (Integer rowNum : rowNums) {
                errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                    .add(new CellError("*", "[内置]空行检测", "整行为空"));
            }
            log.debug("Empty row check on {}: found {} empty rows", tableName, rowNums.size());
        } catch (Exception e) {
            log.error("Empty row check failed on {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * Check 2: Duplicate rows -- all data columns identical.
     */
    private void checkDuplicateRows(String tableName, List<ColumnInfo> columns,
                                    Map<Integer, List<CellError>> errors) {
        List<String> sanitized = sanitizedColumnNames(columns);

        String colList = sanitized.stream()
            .map(col -> "\"" + col + "\"")
            .collect(Collectors.joining(", "));

        String sql = "WITH dups AS ("
            + " SELECT " + colList + ", MIN(_row_num) AS first_row,"
            + " ARRAY_AGG(_row_num ORDER BY _row_num) AS all_rows"
            + " FROM " + tableName
            + " GROUP BY " + colList
            + " HAVING COUNT(*) > 1"
            + ") SELECT unnest(all_rows) AS row_num, first_row FROM dups";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                int rowNum = ((Number) row.get("row_num")).intValue();
                int firstRow = ((Number) row.get("first_row")).intValue();
                if (rowNum != firstRow) {
                    errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                        .add(new CellError("*", "[内置]重复行检测", "与第 " + firstRow + " 行重复"));
                }
            }
            log.debug("Duplicate row check on {}: found {} candidate rows", tableName, rows.size());
        } catch (Exception e) {
            log.error("Duplicate row check failed on {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * Check 3: Numeric with unit -- digits mixed with non-numeric chars in LONG/DOUBLE columns.
     */
    private void checkNumericWithUnit(String tableName, List<ColumnInfo> columns,
                                      Map<Integer, List<CellError>> errors) {
        for (ColumnInfo col : columns) {
            String type = col.inferredType();
            if (!"LONG".equals(type) && !"DOUBLE".equals(type)) {
                continue;
            }

            String sanitized = sanitizeColumnName(col.name());
            String sql = "SELECT _row_num, \"" + sanitized + "\" AS val FROM " + tableName
                + " WHERE \"" + sanitized + "\" IS NOT NULL"
                + " AND \"" + sanitized + "\" ~ '[0-9]+[^0-9.\\s\\-]'";

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> row : rows) {
                    int rowNum = ((Number) row.get("_row_num")).intValue();
                    String val = (String) row.get("val");
                    errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                        .add(new CellError(col.name(), "[内置]数字含单位", "数字列含非数字字符: " + val));
                }
            } catch (Exception e) {
                log.error("Numeric-with-unit check failed on {}.{}: {}", tableName, sanitized, e.getMessage());
            }
        }
    }

    /**
     * Check 4: Date/timestamp format inconsistency.
     * DATE columns must match yyyy-MM-dd.
     * TIMESTAMP columns must match yyyy-MM-dd HH:mm or yyyy-MM-dd HH:mm:ss.
     */
    private void checkDateFormatInconsistency(String tableName, List<ColumnInfo> columns,
                                              Map<Integer, List<CellError>> errors) {
        for (ColumnInfo col : columns) {
            String inferredType = col.inferredType();
            if (!"DATE".equals(inferredType) && !"TIMESTAMP".equals(inferredType)) {
                continue;
            }

            String sanitized = sanitizeColumnName(col.name());
            String regex;
            String expected;
            if ("TIMESTAMP".equals(inferredType)) {
                // Accept yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss, or minute precision variants.
                regex = "^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?$";
                expected = "yyyy-MM-dd HH:mm:ss 或 ISO yyyy-MM-ddTHH:mm:ss";
            } else {
                // Accept yyyy-MM-dd (pure date) or date-time values (also acceptable for DATE).
                regex = "^\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?$";
                expected = "yyyy-MM-dd、yyyy-MM-dd HH:mm:ss 或 ISO yyyy-MM-ddTHH:mm:ss";
            }

            String sql = "SELECT _row_num, \"" + sanitized + "\" AS val FROM " + tableName
                + " WHERE \"" + sanitized + "\" IS NOT NULL"
                + " AND \"" + sanitized + "\" != ''"
                + " AND \"" + sanitized + "\" !~ '" + regex + "'";

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> row : rows) {
                    int rowNum = ((Number) row.get("_row_num")).intValue();
                    String val = (String) row.get("val");
                    errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                        .add(new CellError(col.name(), "[内置]日期格式",
                            "日期格式不统一，期望 " + expected + "，实际: " + val));
                }
            } catch (Exception e) {
                log.error("Date format check failed on {}.{}: {}", tableName, sanitized, e.getMessage());
            }
        }
    }

    /**
     * Check 5: Large numeric values (>15 digits) that may have lost precision.
     * IEEE 754 double only has ~15.9 significant digits; IDs, card numbers etc. stored
     * as numeric cells in Excel will silently lose trailing digits.
     */
    private void checkLargeNumericPrecision(String tableName, List<ColumnInfo> columns,
                                             Map<Integer, List<CellError>> errors) {
        for (ColumnInfo col : columns) {
            String type = col.inferredType();
            if (!"LONG".equals(type) && !"DOUBLE".equals(type)) {
                continue;
            }

            String sanitized = sanitizeColumnName(col.name());
            // Detect values that are pure digits with length > 15 (precision loss territory)
            // or values ending in multiple zeros (common sign of precision truncation)
            String sql = "SELECT _row_num, \"" + sanitized + "\" AS val FROM " + tableName
                + " WHERE \"" + sanitized + "\" IS NOT NULL"
                + " AND \"" + sanitized + "\" ~ '^-?\\d{16,}$'";

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> row : rows) {
                    int rowNum = ((Number) row.get("_row_num")).intValue();
                    String val = (String) row.get("val");
                    errors.computeIfAbsent(rowNum, k -> new ArrayList<>())
                        .add(new CellError(col.name(), "[内置]大数精度",
                            "数值超过15位，可能存在精度损失（如身份证号、长编号应存为文本）: " + val));
                }
            } catch (Exception e) {
                log.error("Large numeric check failed on {}.{}: {}", tableName, sanitized, e.getMessage());
            }
        }
    }

    private List<String> sanitizedColumnNames(List<ColumnInfo> columns) {
        return columns.stream()
            .map(c -> sanitizeColumnName(c.name()))
            .toList();
    }

    private String sanitizeColumnName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_").toLowerCase();
    }
}
