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

    // ---- public API ---------------------------------------------------------

    /**
     * Preview the effect of the SQL UPDATE without modifying data.
     * Returns estimated affected rows and sample before/after data.
     */
    @Transactional(readOnly = true)
    public SqlRepairPreview preview(String sql, int limit) {
        validateSql(sql);
        if (limit <= 0 || limit > 200) limit = 10;

        Matcher tableMatcher = VALID_UPDATE.matcher(sql);
        if (!tableMatcher.find()) {
            throw new IllegalArgumentException("无法解析表名");
        }
        String tableName = tableMatcher.group(1);

        // Extract WHERE clause (may be absent)
        String whereClause = null;
        Matcher whereMatcher = WHERE_CLAUSE.matcher(sql);
        if (whereMatcher.find()) {
            whereClause = whereMatcher.group(1).trim();
            // Remove trailing semicolons
            whereClause = whereClause.replaceAll(";\\s*$", "").trim();
        }

        // Extract SET column names for "newValues" simulation
        List<String> setColumns = parseSetColumns(sql);

        // 1. Count affected rows using the same WHERE clause
        String countSql = "SELECT count(*) FROM " + quoteIdentifier(tableName);
        if (whereClause != null && !whereClause.isEmpty()) {
            countSql += " WHERE " + whereClause;
        }
        Long affectedRows = jdbcTemplate.queryForObject(countSql, Long.class);
        if (affectedRows == null) affectedRows = 0L;

        // 2. Fetch sample "before" rows
        String selectSql = "SELECT * FROM " + quoteIdentifier(tableName);
        if (whereClause != null && !whereClause.isEmpty()) {
            selectSql += " WHERE " + whereClause;
        }
        selectSql += " LIMIT " + limit;

        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(selectSql);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            Map<String, Object> sample = new LinkedHashMap<>();
            Object rowId = rawRow.get("id");
            Map<String, Object> columnValues = new LinkedHashMap<>();
            for (var entry : rawRow.entrySet()) {
                String colName = entry.getKey();
                if ("id".equalsIgnoreCase(colName)) {
                    rowId = entry.getValue();
                }
                if (setColumns.isEmpty() || setColumns.stream().anyMatch(c -> c.equalsIgnoreCase(colName))) {
                    columnValues.put(colName, entry.getValue() == null ? null : entry.getValue().toString());
                }
            }
            sample.put("rowId", rowId);
            sample.put("columnValues", columnValues);
            samples.add(sample);
        }

        return new SqlRepairPreview(affectedRows, samples);
    }

    /**
     * Execute the SQL UPDATE and record an audit log entry.
     */
    public SqlRepairResult execute(String sql, UUID runId) {
        validateSql(sql);

        Matcher tableMatcher = VALID_UPDATE.matcher(sql);
        if (!tableMatcher.find()) {
            throw new IllegalArgumentException("无法解析表名");
        }
        String tableName = tableMatcher.group(1);
        String currentUser = SecurityUtils.getCurrentUserLogin().orElse("system");

        // Execute the UPDATE via JdbcTemplate (participates in Spring transaction)
        int affectedRows = jdbcTemplate.update(sql);

        // Record audit log (same transaction)
        GovDataEditLog logEntry = new GovDataEditLog();
        logEntry.setTableName(tableName);
        logEntry.setRowId(runId != null ? runId.toString() : "batch");
        logEntry.setColumnName(null);
        logEntry.setOldValue(null);
        logEntry.setNewValue(sql);
        logEntry.setEditType("SQL_REPAIR");
        logEntry.setEditReason("SQL修复: 影响 " + affectedRows + " 行");
        logEntry.setEditedBy(currentUser);
        logEntry.setEditedAt(Instant.now());
        GovDataEditLog saved = editLogRepository.save(logEntry);

        log.info("SQL repair executed by {}: table={}, affectedRows={}, auditId={}",
            currentUser, tableName, affectedRows, saved.getId());

        return new SqlRepairResult(affectedRows, saved.getId());
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
        List<String> columns = new ArrayList<>();
        Matcher setMatcher = SET_CLAUSE.matcher(sql);
        if (setMatcher.find()) {
            String setBody = setMatcher.group(1).trim();
            // Split on commas, extract column names (before the = sign)
            for (String assignment : setBody.split(",")) {
                String col = assignment.strip().split("\\s*=")[0].strip();
                if (!col.isEmpty()) {
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /** Double-quote an identifier to prevent SQL injection while allowing reserved words. */
    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
