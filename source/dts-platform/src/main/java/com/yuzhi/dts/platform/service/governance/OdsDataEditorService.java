package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDataEditLog;
import com.yuzhi.dts.platform.repository.governance.GovDataEditLogRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OdsDataEditorService {

    private static final Logger log = LoggerFactory.getLogger(OdsDataEditorService.class);

    private final DataSource dataSource;
    private final GovDataEditLogRepository editLogRepository;

    /** Only tables with ods_ prefix are allowed. */
    private static final Pattern SAFE_TABLE = Pattern.compile("^ods_[a-zA-Z0-9_]+$");

    /** Only safe SQL identifiers for column names. */
    private static final Pattern SAFE_COLUMN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** Columns that must not be modified by end users. */
    private static final Set<String> FORBIDDEN_COLUMNS = Set.of(
        "id", "source_system", "import_time", "_quality_blocked", "_quality_checked_at"
    );

    public OdsDataEditorService(DataSource dataSource, GovDataEditLogRepository editLogRepository) {
        this.dataSource = dataSource;
        this.editLogRepository = editLogRepository;
    }

    // ---- public API --------------------------------------------------------

    /**
     * Update a single ODS row. Each changed column is recorded as an audit log entry.
     */
    public void updateRow(String tableName, String rowId, Map<String, String> columns,
                          String reason, String actor) {
        validateTableName(tableName);
        validateColumns(columns.keySet());

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("没有需要更新的列");
        }

        try (Connection conn = dataSource.getConnection()) {
            // 1. Read old values for the columns being updated
            Map<String, String> oldValues = fetchOldValues(conn, tableName, rowId, columns.keySet());

            // 2. Build and execute UPDATE
            List<String> colNames = new ArrayList<>(columns.keySet());
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(quoteIdentifier(tableName));
            sql.append(" SET ");
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(quoteIdentifier(colNames.get(i))).append(" = ?");
            }
            sql.append(" WHERE id::text = ?");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < colNames.size(); i++) {
                    ps.setString(i + 1, columns.get(colNames.get(i)));
                }
                ps.setString(colNames.size() + 1, rowId);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new IllegalArgumentException("未找到 id=" + rowId + " 的行");
                }
            }

            // 3. Audit log per changed column
            Instant now = Instant.now();
            for (String col : colNames) {
                String oldVal = oldValues.get(col);
                String newVal = columns.get(col);
                if (java.util.Objects.equals(oldVal, newVal)) {
                    continue; // skip unchanged
                }
                GovDataEditLog logEntry = new GovDataEditLog();
                logEntry.setTableName(tableName);
                logEntry.setRowId(rowId);
                logEntry.setColumnName(col);
                logEntry.setOldValue(oldVal);
                logEntry.setNewValue(newVal);
                logEntry.setEditType("UPDATE");
                logEntry.setEditReason(reason);
                logEntry.setEditedBy(actor);
                logEntry.setEditedAt(now);
                editLogRepository.save(logEntry);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("更新 ODS 行失败: " + e.getMessage(), e);
        }
    }

    /**
     * Insert a new row into an ODS table. Returns the new row data including the generated id.
     */
    public Map<String, Object> insertRow(String tableName, Map<String, String> columns,
                                          String reason, String actor) {
        validateTableName(tableName);
        validateColumns(columns.keySet());

        try (Connection conn = dataSource.getConnection()) {
            // Auto-fill system columns
            Map<String, String> allCols = new LinkedHashMap<>(columns);
            allCols.put("source_system", "manual_entry");
            allCols.put("import_time", Instant.now().toString());
            allCols.put("_quality_blocked", "false");

            List<String> colNames = new ArrayList<>(allCols.keySet());
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(quoteIdentifier(tableName)).append(" (");
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(quoteIdentifier(colNames.get(i)));
            }
            sql.append(") VALUES (");
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(") RETURNING id");

            String newRowId;
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < colNames.size(); i++) {
                    ps.setString(i + 1, allCols.get(colNames.get(i)));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("INSERT 未返回 id");
                    }
                    newRowId = rs.getString(1);
                }
            }

            // Audit log per column
            Instant now = Instant.now();
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                GovDataEditLog logEntry = new GovDataEditLog();
                logEntry.setTableName(tableName);
                logEntry.setRowId(newRowId);
                logEntry.setColumnName(entry.getKey());
                logEntry.setOldValue(null);
                logEntry.setNewValue(entry.getValue());
                logEntry.setEditType("INSERT");
                logEntry.setEditReason(reason);
                logEntry.setEditedBy(actor);
                logEntry.setEditedAt(now);
                editLogRepository.save(logEntry);
            }

            // Fetch the complete new row
            return fetchRow(conn, tableName, newRowId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("新增 ODS 行失败: " + e.getMessage(), e);
        }
    }

    /**
     * List rows of an ODS table with pagination.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listRows(String tableName, int page, int size) {
        validateTableName(tableName);
        if (size <= 0 || size > 500) size = 50;
        if (page < 0) page = 0;

        try (Connection conn = dataSource.getConnection()) {
            // Count
            long totalElements;
            String countSql = "SELECT count(*) FROM " + quoteIdentifier(tableName);
            try (PreparedStatement ps = conn.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                totalElements = rs.getLong(1);
            }

            // Data
            String dataSql = "SELECT * FROM " + quoteIdentifier(tableName) + " ORDER BY id LIMIT ? OFFSET ?";
            List<Map<String, Object>> content = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                ps.setInt(1, size);
                ps.setInt(2, page * size);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        content.add(row);
                    }
                }
            }

            long totalPages = (totalElements + size - 1) / size;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", totalElements);
            result.put("totalPages", totalPages);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("查询 ODS 表数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * List column metadata for an ODS table.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> listColumns(String tableName) {
        validateTableName(tableName);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            List<Map<String, String>> columns = new ArrayList<>();
            try (ResultSet rs = dbMeta.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    Map<String, String> col = new LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", rs.getString("COLUMN_SIZE"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("remarks", rs.getString("REMARKS"));
                    columns.add(col);
                }
            }
            return columns;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("获取 ODS 表列信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * List ODS tables (tables with ods_ prefix) in the database.
     */
    @Transactional(readOnly = true)
    public List<String> listOdsTables() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = dbMeta.getTables(null, null, "ods_%", new String[] { "TABLE" })) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            return tables;
        } catch (Exception e) {
            throw new RuntimeException("获取 ODS 表列表失败: " + e.getMessage(), e);
        }
    }

    // ---- helpers ------------------------------------------------------------

    private void validateTableName(String tableName) {
        if (tableName == null || !SAFE_TABLE.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法表名: " + tableName + " (只允许 ods_ 前缀的表)");
        }
    }

    private void validateColumns(Set<String> columnNames) {
        for (String col : columnNames) {
            if (!SAFE_COLUMN.matcher(col).matches()) {
                throw new IllegalArgumentException("非法列名: " + col);
            }
            if (FORBIDDEN_COLUMNS.contains(col.toLowerCase())) {
                throw new IllegalArgumentException("禁止修改的列: " + col);
            }
        }
    }

    /** Double-quote an identifier to prevent SQL injection while allowing reserved words. */
    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private Map<String, String> fetchOldValues(Connection conn, String tableName,
                                                String rowId, Set<String> colNames) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> cols = new ArrayList<>(colNames);
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteIdentifier(cols.get(i)));
        }
        sql.append(" FROM ").append(quoteIdentifier(tableName));
        sql.append(" WHERE id::text = ?");

        Map<String, String> oldValues = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("未找到 id=" + rowId + " 的行");
                }
                for (String col : cols) {
                    Object val = rs.getObject(col);
                    oldValues.put(col, val == null ? null : val.toString());
                }
            }
        }
        return oldValues;
    }

    private Map<String, Object> fetchRow(Connection conn, String tableName, String rowId) throws Exception {
        String sql = "SELECT * FROM " + quoteIdentifier(tableName) + " WHERE id::text = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Map.of();
                }
                ResultSetMetaData meta = rs.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                return row;
            }
        }
    }
}
