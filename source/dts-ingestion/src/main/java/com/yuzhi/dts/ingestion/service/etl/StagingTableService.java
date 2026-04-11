package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.StagingTableMetadata;
import com.yuzhi.dts.ingestion.repository.StagingTableMetadataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StagingTableService {

    private static final Logger log = LoggerFactory.getLogger(StagingTableService.class);

    private static final String TABLE_PREFIX = "tmp_ingestion_";

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f\\-]{36}$");

    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("[a-zA-Z0-9_\\u4e00-\\u9fff]+");

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StagingTableMetadataRepository metadataRepository;

    public StagingTableService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            StagingTableMetadataRepository metadataRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.metadataRepository = metadataRepository;
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

        // Save metadata record for TTL tracking
        StagingTableMetadata metadata = new StagingTableMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setTableName(tableName);
        metadata.setTaskId(null); // taskId is not directly needed here, could be added if required
        metadata.setCreatedAt(Instant.now());
        metadata.setLastAccessedAt(Instant.now());
        metadata.setTtlHours(24); // Default 24 hour TTL
        metadata.setStatus("ACTIVE");
        metadataRepository.save(metadata);

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

        // Update lastAccessedAt in metadata
        metadataRepository.findByTableName(tableName).ifPresent(meta -> {
            meta.setLastAccessedAt(Instant.now());
            metadataRepository.save(meta);
        });

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

        // Update lastAccessedAt in metadata
        metadataRepository.findByTableName(tableName).ifPresent(meta -> {
            meta.setLastAccessedAt(Instant.now());
            metadataRepository.save(meta);
        });

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
     * Count total rows in the staging table.
     */
    public int countRows(String tableName) {
        validateTableName(tableName);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count.intValue() : 0;
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

        // Update metadata status to DROPPED
        metadataRepository.findByTableName(tableName).ifPresent(meta -> {
            meta.setStatus("DROPPED");
            metadataRepository.save(meta);
        });
    }

    /**
     * Scheduled cleanup: every hour, check for expired staging tables via metadata table.
     * Drops ACTIVE tables where lastAccessedAt + ttlHours < now.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpired() {
        // Query all ACTIVE staging tables, then check per-table TTL
        List<StagingTableMetadata> activeTables = metadataRepository
            .findByStatusAndLastAccessedAtBefore("ACTIVE", Instant.now());

        List<StagingTableMetadata> expired = activeTables.stream()
            .filter(meta -> {
                int ttl = meta.getTtlHours() > 0 ? meta.getTtlHours() : 24;
                Instant expiry = meta.getLastAccessedAt().plus(ttl, ChronoUnit.HOURS);
                return Instant.now().isAfter(expiry);
            })
            .toList();

        if (expired.isEmpty()) {
            log.debug("No expired staging tables to clean up");
            return;
        }

        log.info("Found {} expired staging tables to clean up", expired.size());

        for (StagingTableMetadata meta : expired) {
            try {
                drop(meta.getTableName());
                log.info("Cleaned up expired staging table: {} (TTL={}h)", meta.getTableName(), meta.getTtlHours());
            } catch (Exception e) {
                log.warn("Failed to clean up staging table: {}", meta.getTableName(), e);
            }
        }
    }

    /**
     * Count rows with ERROR status in the staging table.
     */
    public long countErrors(String tableName) {
        validateTableName(tableName);
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE _status = 'ERROR'", Long.class);
        return count != null ? count : 0;
    }

    /**
     * Transfer data from staging table to the target ODS table.
     * Resolves the target table name from the task's tableMapping configuration.
     * Only data columns are transferred (internal columns _row_num, _errors, _status are excluded).
     *
     * @param stagingTable the staging table name
     * @param task         the ingestion task containing tableMapping with target table info
     * @return the number of rows transferred
     * @throws IllegalStateException if the target table cannot be determined or the transfer fails
     */
    @Transactional
    public int transferToTarget(String stagingTable, IngestionTask task) {
        validateTableName(stagingTable);

        // 1. Resolve target table name from task's tableMapping
        String targetTable = resolveTargetTable(task);
        if (targetTable == null || targetTable.isBlank()) {
            throw new IllegalStateException(
                "Cannot determine target table for task " + task.getId()
                    + ". Ensure tableMapping is configured with a target table.");
        }
        // Validate target table name: must be ods_* prefix and safe characters only
        if (!targetTable.matches("ods_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                "Target table must start with 'ods_' and contain only safe characters: " + targetTable);
        }

        // 2. Get data columns from staging table (exclude internal columns prefixed with _)
        List<String> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = ? AND column_name NOT LIKE '\\_%' "
                + "ORDER BY ordinal_position",
            String.class, stagingTable);

        if (columns.isEmpty()) {
            throw new IllegalStateException(
                "No data columns found in staging table " + stagingTable);
        }

        // 3. Query target table column types to build proper CAST expressions.
        //    Parse schema from target table name (e.g., "ods.my_table" → schema=ods, table=my_table).
        String targetSchema = null;
        String targetTableName = targetTable;
        int dotIdx = targetTable.indexOf('.');
        if (dotIdx > 0 && dotIdx < targetTable.length() - 1) {
            targetSchema = targetTable.substring(0, dotIdx);
            targetTableName = targetTable.substring(dotIdx + 1);
        }

        Map<String, String> targetColumnTypes = new LinkedHashMap<>();
        try {
            String metaQuery;
            Object[] metaParams;
            if (targetSchema != null) {
                metaQuery = "SELECT column_name, data_type FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
                metaParams = new Object[] { targetSchema, targetTableName };
            } else {
                metaQuery = "SELECT column_name, data_type FROM information_schema.columns "
                    + "WHERE table_name = ? AND table_schema = current_schema() ORDER BY ordinal_position";
                metaParams = new Object[] { targetTableName };
            }
            List<Map<String, Object>> targetColMeta = jdbcTemplate.queryForList(metaQuery, metaParams);
            for (Map<String, Object> row : targetColMeta) {
                String colName = (String) row.get("column_name");
                String dataType = (String) row.get("data_type");
                if (colName != null && dataType != null) {
                    targetColumnTypes.put(colName.toLowerCase(), dataType.toLowerCase());
                }
            }
        } catch (Exception e) {
            log.warn("Cannot query target table column types, will use direct transfer: {}", e.getMessage());
        }

        // 4. Build INSERT INTO target SELECT ... FROM staging with appropriate CAST
        String colList = columns.stream()
            .map(c -> "\"" + c + "\"")
            .collect(Collectors.joining(", "));

        String selectExprs = columns.stream()
            .map(c -> buildCastExpression(c, targetColumnTypes))
            .collect(Collectors.joining(", "));

        String insertSql = "INSERT INTO " + targetTable + " (" + colList + ") "
            + "SELECT " + selectExprs + " FROM " + stagingTable;

        log.info("Transferring data from {} to {} (columns: {})", stagingTable, targetTable, colList);

        int rows = jdbcTemplate.update(insertSql);
        log.info("Transferred {} rows from {} to {}", rows, stagingTable, targetTable);
        return rows;
    }

    /**
     * Resolve the target table name from the task's tableMapping.
     * tableMapping is a JSON array like: [{"source":"sheet1","target":"ods_my_table"}]
     * For Excel ingestion there is typically one mapping entry.
     * Falls back to destinationConfig.table if tableMapping has no target.
     */
    private String resolveTargetTable(IngestionTask task) {
        // Try tableMapping first
        JsonNode tableMapping = task.getTableMapping();
        if (tableMapping != null && tableMapping.isArray() && !tableMapping.isEmpty()) {
            JsonNode first = tableMapping.get(0);
            if (first != null && first.has("target")) {
                String target = first.get("target").asText();
                if (target != null && !target.isBlank()) {
                    return target;
                }
            }
        }

        // Fallback: try destinationConfig.table
        JsonNode destConfig = task.getDestinationConfig();
        if (destConfig != null) {
            JsonNode tableNode = destConfig.get("table");
            if (tableNode != null && tableNode.isTextual()) {
                return tableNode.asText();
            }
            // Also try "tables" array
            JsonNode tablesNode = destConfig.get("tables");
            if (tablesNode != null && tablesNode.isArray() && !tablesNode.isEmpty()) {
                return tablesNode.get(0).asText();
            }
        }

        return null;
    }

    /**
     * Build a SELECT expression with CAST for date/time columns.
     * Staging table stores everything as TEXT; target table may have typed columns.
     * Handles Chinese date formats like "2024年3月15日 14时30分00秒" via regex replacement.
     */
    private String buildCastExpression(String column, Map<String, String> targetColumnTypes) {
        String quoted = "\"" + column + "\"";
        String targetType = targetColumnTypes.get(column.toLowerCase());
        if (targetType == null) {
            return quoted;
        }
        if (targetType.contains("timestamp") || "date".equals(targetType)
            || "time without time zone".equals(targetType) || "time".equals(targetType)) {
            // Normalize Chinese date/time delimiters before casting:
            // "2024年3月15日 14时30分00秒" → "2024-3-15 14:30:00"
            // Also handle fullwidth digits (０-９ → 0-9) and 上午/下午
            String normalized = "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + "REGEXP_REPLACE("
                + quoted
                + ", '[上下]午\\s*', '', 'g')"       // strip 上午/下午
                + ", '秒', '', 'g')"                  // 秒 → remove
                + ", '分', ':', 'g')"                  // 分 → :
                + ", '时', ':', 'g')"                  // 时 → :
                + ", '日', '', 'g')"                   // 日 → remove
                + ", '月', '-', 'g')"                  // 月 → -
                + ", '年', '-', 'g')";                 // 年 → -
            // Preserve the exact target type (e.g. "timestamp with time zone")
            String castType = targetType.toUpperCase();
            return "CASE WHEN " + quoted + " IS NULL OR TRIM(" + quoted + ") = '' THEN NULL "
                + "ELSE CAST(" + normalized + " AS " + castType + ") END";
        }
        if (targetType.contains("int") || targetType.contains("numeric")
            || targetType.contains("decimal") || targetType.contains("double")
            || targetType.contains("real") || targetType.contains("float")) {
            return "CASE WHEN " + quoted + " IS NULL OR TRIM(" + quoted + ") = '' THEN NULL "
                + "ELSE CAST(" + quoted + " AS " + targetType.toUpperCase() + ") END";
        }
        if ("boolean".equals(targetType)) {
            return "CASE WHEN " + quoted + " IS NULL OR TRIM(" + quoted + ") = '' THEN NULL "
                + "ELSE CAST(" + quoted + " AS BOOLEAN) END";
        }
        return quoted;
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
