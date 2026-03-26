package com.yuzhi.dts.analytics.service;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUploadTableService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUploadTableService.class);

    private static final String SCHEMA = "biadmin";
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_TABLE_NAME_LENGTH = 40;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, String> TYPE_MAPPING = Map.of(
            "number", "DOUBLE PRECISION",
            "date", "DATE",
            "boolean", "BOOLEAN");

    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;
    private final MetadataSyncService metadataSyncService;

    public DatabaseUploadTableService(
            ExternalDatabaseDataSourceRegistry dataSourceRegistry,
            MetadataSyncService metadataSyncService) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.metadataSyncService = metadataSyncService;
    }

    /**
     * Creates a table in the biadmin schema with the given columns and rows,
     * then syncs metadata so the table appears in the analytics catalog.
     *
     * @return the actual table name created (without schema prefix)
     */
    public UploadResult uploadTable(long databaseId, String tableName, List<ColumnDef> columns, List<List<Object>> rows)
            throws SQLException {
        String sanitized = sanitizeTableName(tableName);
        String fullTableName = SCHEMA + ".\"" + sanitized + "\"";

        HikariDataSource dataSource = dataSourceRegistry.get(databaseId);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                createSchemaIfNotExists(conn);
                dropTableIfExists(conn, sanitized);
                createTable(conn, sanitized, columns);
                int rowCount = insertRows(conn, fullTableName, columns, rows);
                addTableComment(conn, fullTableName, tableName);
                conn.commit();

                log.info("Uploaded table {} with {} rows to database {}", fullTableName, rowCount, databaseId);

                // Sync metadata so the new table appears in the analytics catalog
                metadataSyncService.syncDatabaseSchema(databaseId);

                return new UploadResult(sanitized, SCHEMA, rowCount);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void createSchemaIfNotExists(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        }
    }

    private void dropTableIfExists(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + SCHEMA + ".\"" + tableName + "\"");
        }
    }

    private void createTable(Connection conn, String tableName, List<ColumnDef> columns) throws SQLException {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(SCHEMA).append(".\"").append(tableName).append("\" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                ddl.append(", ");
            }
            ColumnDef col = columns.get(i);
            String sqlType = TYPE_MAPPING.getOrDefault(
                    col.type() == null ? "" : col.type().toLowerCase(Locale.ROOT),
                    "TEXT");
            ddl.append("\"").append(col.name()).append("\" ").append(sqlType);
        }
        ddl.append(")");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }
    }

    private int insertRows(Connection conn, String fullTableName, List<ColumnDef> columns, List<List<Object>> rows)
            throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(fullTableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("\"").append(columns.get(i).name()).append("\"");
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        int totalInserted = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int r = 0; r < rows.size(); r++) {
                List<Object> row = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    Object raw = c < row.size() ? row.get(c) : null;
                    Object value = coerceValue(raw, columns.get(c).type());
                    ps.setObject(c + 1, value);
                }
                ps.addBatch();
                totalInserted++;

                if (totalInserted % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            // Execute remaining batch
            if (totalInserted % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
        }
        return totalInserted;
    }

    private void addTableComment(Connection conn, String fullTableName, String originalName) throws SQLException {
        String comment = "Uploaded table: " + originalName;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("COMMENT ON TABLE " + fullTableName + " IS '" + comment.replace("'", "''") + "'");
        }
    }

    static String sanitizeTableName(String name) {
        if (name == null || name.isBlank()) {
            name = "untitled";
        }
        String sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "_");
        // Remove leading/trailing underscores and collapse consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (sanitized.isEmpty()) {
            sanitized = "untitled";
        }
        if (sanitized.length() > MAX_TABLE_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TABLE_NAME_LENGTH);
            // Don't end with underscore after truncation
            sanitized = sanitized.replaceAll("_$", "");
        }
        String datePrefix = "upload_" + LocalDate.now().format(DATE_FMT) + "_";
        return datePrefix + sanitized;
    }

    /**
     * Coerce a raw JSON value (String/Number/Boolean/null) to the Java type
     * that matches the declared column type, so PreparedStatement.setObject
     * sends the correct PG wire type.
     */
    private static Object coerceValue(Object raw, String colType) {
        if (raw == null) return null;
        String str = String.valueOf(raw).trim();
        if (str.isEmpty() || "null".equalsIgnoreCase(str)) return null;

        String type = (colType == null) ? "text" : colType.toLowerCase(Locale.ROOT);
        try {
            return switch (type) {
                case "number" -> {
                    // Handle integers sent as "45350.0" or "45350"
                    yield Double.parseDouble(str);
                }
                case "date" -> {
                    // Excel serial date number → convert to java.sql.Date
                    if (str.matches("^\\d+(\\.\\d+)?$")) {
                        // Excel epoch: 1900-01-01 = serial 1 (with the famous leap year bug)
                        long serial = Math.round(Double.parseDouble(str));
                        java.time.LocalDate date = java.time.LocalDate.of(1899, 12, 30).plusDays(serial);
                        yield java.sql.Date.valueOf(date);
                    }
                    // ISO or slash-separated date string
                    yield java.sql.Date.valueOf(str.replace("/", "-"));
                }
                case "boolean" -> {
                    yield "true".equalsIgnoreCase(str) || "是".equals(str) || "1".equals(str);
                }
                default -> str;
            };
        } catch (Exception e) {
            // If conversion fails, fall back to null for typed columns, or raw string for text
            return "text".equals(type) ? str : null;
        }
    }

    public record ColumnDef(String name, String displayName, String type) {}

    public record UploadResult(String tableName, String schema, int rowCount) {}
}
