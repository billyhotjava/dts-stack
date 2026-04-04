package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StagingTableService {

    private static final Logger log = LoggerFactory.getLogger(StagingTableService.class);

    private static final String TABLE_PREFIX = "tmp_ingestion_";

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f\\-]{36}$");

    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("[a-zA-Z0-9_\\u4e00-\\u9fff]+");

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingTableService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a staging table with all TEXT columns plus metadata columns.
     * Table name: tmp_ingestion_{taskId with dashes removed}
     */
    public String create(UUID taskId, List<ColumnInfo> columns) {
        String taskIdStr = taskId.toString();
        if (!UUID_PATTERN.matcher(taskIdStr).matches()) {
            throw new IllegalArgumentException("Invalid taskId format");
        }

        String tableName = TABLE_PREFIX + taskIdStr.replace("-", "");

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        sql.append("_row_num SERIAL PRIMARY KEY, ");

        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeColumnName(columns.get(i).name());
            sql.append('"').append(colName).append("\" TEXT");
            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(", _errors JSONB DEFAULT '[]'::jsonb");
        sql.append(", _status VARCHAR(10) DEFAULT 'CLEAN'");
        sql.append(")");

        jdbcTemplate.execute(sql.toString());
        log.info("Created staging table: {}", tableName);
        return tableName;
    }

    /**
     * Batch insert parsed data rows using batch size of 500.
     */
    public void bulkInsert(String tableName, List<ColumnInfo> columns, List<List<String>> rows) {
        validateTableName(tableName);

        if (rows.isEmpty()) {
            return;
        }

        List<String> sanitizedNames = columns.stream()
            .map(c -> sanitizeColumnName(c.name()))
            .toList();

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < sanitizedNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append('"').append(sanitizedNames.get(i)).append('"');
        }
        sql.append(") VALUES (");
        for (int i = 0; i < sanitizedNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append('?');
        }
        sql.append(")");

        String insertSql = sql.toString();

        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, rows.size());
            List<List<String>> batch = rows.subList(start, end);

            jdbcTemplate.batchUpdate(insertSql, batch.stream()
                .map(row -> {
                    Object[] params = new Object[sanitizedNames.size()];
                    for (int i = 0; i < sanitizedNames.size(); i++) {
                        params[i] = i < row.size() ? row.get(i) : null;
                    }
                    return params;
                })
                .toList());
        }

        log.info("Inserted {} rows into {}", rows.size(), tableName);
    }

    /**
     * Paginated query. If errorsOnly=true, filter to rows with _status = 'ERROR'.
     */
    public Page<Map<String, Object>> query(String tableName, boolean errorsOnly, Pageable pageable) {
        validateTableName(tableName);

        String whereClause = errorsOnly ? " WHERE _status = 'ERROR'" : "";

        String countSql = "SELECT COUNT(*) FROM " + tableName + whereClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String dataSql = "SELECT * FROM " + tableName + whereClause
            + " ORDER BY _row_num LIMIT ? OFFSET ?";

        List<Map<String, Object>> content = jdbcTemplate.queryForList(
            dataSql, pageable.getPageSize(), pageable.getOffset());

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Update a single cell value.
     */
    public void updateCell(String tableName, int rowNum, String column, String value) {
        validateTableName(tableName);
        String safeColumn = sanitizeColumnName(column);

        String sql = "UPDATE " + tableName + " SET \"" + safeColumn + "\" = ? WHERE _row_num = ?";
        jdbcTemplate.update(sql, value, rowNum);
    }

    /**
     * Update row errors and status. Status = CLEAN if errors list is empty, ERROR otherwise.
     */
    public void updateErrors(String tableName, int rowNum, List<CellError> errors) {
        validateTableName(tableName);

        String errorsJson;
        try {
            errorsJson = objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize errors to JSON", e);
        }

        String status = errors.isEmpty() ? "CLEAN" : "ERROR";

        String sql = "UPDATE " + tableName + " SET _errors = ?::jsonb, _status = ? WHERE _row_num = ?";
        jdbcTemplate.update(sql, errorsJson, status, rowNum);
    }

    /**
     * Check if all rows are CLEAN (no ERROR status rows).
     */
    public boolean allClean(String tableName) {
        validateTableName(tableName);

        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE _status = 'ERROR'";
        Long errorCount = jdbcTemplate.queryForObject(sql, Long.class);
        return errorCount != null && errorCount == 0;
    }

    /**
     * Drop staging table. Only allowed if name starts with TABLE_PREFIX.
     */
    public void drop(String tableName) {
        validateTableName(tableName);

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        log.info("Dropped staging table: {}", tableName);
    }

    /**
     * Scheduled cleanup: every hour, find tmp_ingestion_% tables.
     * TODO: Implement TTL tracking via a metadata table to auto-drop expired staging tables.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpired() {
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE tablename LIKE ?",
            String.class,
            TABLE_PREFIX + "%");

        if (!tables.isEmpty()) {
            log.info("Found {} staging tables pending TTL check: {}", tables.size(), tables);
            // TODO: Add metadata table (staging_table_meta) with created_at column
            // to track table age and auto-drop tables older than configured TTL.
        }
    }

    /**
     * Sanitize column name: only allow safe characters, lowercase the result.
     */
    String sanitizeColumnName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_").toLowerCase();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Column name contains no valid characters: " + name);
        }
        return sanitized;
    }

    private void validateTableName(String tableName) {
        if (tableName == null || !tableName.startsWith(TABLE_PREFIX)) {
            throw new IllegalArgumentException("Invalid staging table name: " + tableName);
        }
        // After prefix, only hex characters allowed (UUID without dashes)
        String suffix = tableName.substring(TABLE_PREFIX.length());
        if (!suffix.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid staging table name suffix: " + tableName);
        }
    }
}
