package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDataEditLog;
import com.yuzhi.dts.platform.repository.governance.GovDataEditLogRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SqlRepairService {

    private static final Logger log = LoggerFactory.getLogger(SqlRepairService.class);

    /** Only UPDATE on ods_* tables with a SET clause are allowed. */
    private static final Pattern VALID_UPDATE = Pattern.compile(
        "^\\s*UPDATE\\s+(ods_\\w+)\\s+SET\\s+", Pattern.CASE_INSENSITIVE);

    /** Dangerous SQL keywords that must never appear in the statement. */
    private static final Pattern FORBIDDEN = Pattern.compile(
        "\\b(DELETE|DROP|TRUNCATE|ALTER|CREATE|INSERT\\s+INTO|SELECT\\s+INTO"
        + "|COPY|GRANT|REVOKE|LISTEN|NOTIFY|DO\\s*\\$|EXECUTE|CALL"
        + "|pg_read_file|pg_write_file|lo_import|lo_export)\\b",
        Pattern.CASE_INSENSITIVE);

    /** Extract the WHERE clause from an UPDATE statement (everything after the last WHERE). */
    private static final Pattern WHERE_CLAUSE = Pattern.compile(
        "\\bWHERE\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Extract the SET clause columns: between SET and WHERE (or end of string). */
    private static final Pattern SET_CLAUSE = Pattern.compile(
        "\\bSET\\s+(.+?)(?:\\bWHERE\\b|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final JdbcTemplate jdbcTemplate;
    private final GovDataEditLogRepository editLogRepository;

    public SqlRepairService(JdbcTemplate jdbcTemplate, GovDataEditLogRepository editLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.editLogRepository = editLogRepository;
    }

    // ---- DTOs ---------------------------------------------------------------

    public record SqlRepairPreview(
        long affectedRows,
        List<Map<String, Object>> samples
    ) {}

    public record SqlRepairResult(
        int affectedRows,
        UUID auditLogId
    ) {}

    private record SetAssignment(String columnName, String columnExpression, String valueExpression) {}

    // ---- public API ---------------------------------------------------------

    /**
     * Preview the effect of the SQL UPDATE without modifying data.
     * Returns estimated affected rows and sample before/after data.
     */
    @Transactional(readOnly = true)
    public SqlRepairPreview preview(String sql, int limit) {
        throw new UnsupportedOperationException("质量 SQL 修复预览暂未开放");
    }

    /**
     * Execute the SQL UPDATE and record an audit log entry.
     */
    public SqlRepairResult execute(String sql, UUID runId) {
        throw new UnsupportedOperationException("质量 SQL 修复执行暂未开放");
    }

    // ---- validation ---------------------------------------------------------

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 语句不能为空");
        }

        // Strip trailing semicolons for validation
        String trimmed = sql.strip().replaceAll(";\\s*$", "");

        // Must match UPDATE ods_xxx SET pattern
        if (!VALID_UPDATE.matcher(trimmed).find()) {
            throw new IllegalArgumentException("仅允许 UPDATE ods_* 表的 SET 语句");
        }

        // Must not contain forbidden keywords
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的操作（DELETE/DROP/TRUNCATE/ALTER/CREATE/INSERT INTO/SELECT INTO）");
        }

        // Reject multiple statements (semicolons in the middle)
        String withoutStrings = trimmed.replaceAll("'[^']*'", "''"); // neutralize string literals
        if (withoutStrings.contains(";")) {
            throw new IllegalArgumentException("不允许多条 SQL 语句");
        }

        // Reject subqueries that could be exploited
        // Allow simple IN (...) but reject SELECT inside parentheses
        if (Pattern.compile("\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            throw new IllegalArgumentException("不允许包含子查询的 SQL");
        }
    }

    // ---- helpers ------------------------------------------------------------

    private List<String> parseSetColumns(String sql) {
        return parseSetAssignments(sql).stream().map(SetAssignment::columnName).toList();
    }

    private List<SetAssignment> parseSetAssignments(String sql) {
        List<SetAssignment> assignments = new ArrayList<>();
        Matcher setMatcher = SET_CLAUSE.matcher(sql);
        if (setMatcher.find()) {
            String setBody = setMatcher.group(1).trim();
            for (String assignment : splitAssignments(setBody)) {
                int equals = assignment.indexOf('=');
                if (equals <= 0 || equals >= assignment.length() - 1) {
                    continue;
                }
                String rawColumn = assignment.substring(0, equals).strip();
                String valueExpression = assignment.substring(equals + 1).strip();
                String columnName = normalizeColumnName(rawColumn);
                if (!columnName.isEmpty() && !valueExpression.isEmpty()) {
                    assignments.add(new SetAssignment(columnName, rawColumn, valueExpression));
                }
            }
        }
        return assignments;
    }

    private List<String> splitAssignments(String setBody) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < setBody.length(); i++) {
            char ch = setBody.charAt(i);
            if (ch == '\'') {
                if (inString && i + 1 < setBody.length() && setBody.charAt(i + 1) == '\'') {
                    current.append(ch).append(setBody.charAt(i + 1));
                    i++;
                    continue;
                }
                inString = !inString;
                current.append(ch);
                continue;
            }
            if (!inString) {
                if (ch == '(') depth++;
                if (ch == ')' && depth > 0) depth--;
                if (ch == ',' && depth == 0) {
                    parts.add(current.toString().strip());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }

    private String normalizeColumnName(String rawColumn) {
        String column = rawColumn.strip();
        if (column.startsWith("\"") && column.endsWith("\"") && column.length() >= 2) {
            column = column.substring(1, column.length() - 1).replace("\"\"", "\"");
        }
        int dot = column.lastIndexOf('.');
        if (dot >= 0 && dot < column.length() - 1) {
            column = column.substring(dot + 1);
        }
        return column;
    }

    /** Double-quote an identifier to prevent SQL injection while allowing reserved words. */
    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
