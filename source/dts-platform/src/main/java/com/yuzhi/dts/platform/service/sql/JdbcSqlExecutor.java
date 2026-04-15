package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JDBC-based SQL executor used by the SQL IDE.
 * Replaces the previous Trino stub with direct DriverManager connections.
 * Supports streaming chunked persistence, row-limit enforcement, cancellation, and EXPLAIN.
 */
@Component
public class JdbcSqlExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcSqlExecutor.class);

    /** Internal chunk size when streaming rows into QueryExecutionChunk records. */
    static final int STREAM_CHUNK_SIZE = 1000;

    private final InfraSecretService secretService;
    private final QueryExecutionChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    public JdbcSqlExecutor(
        InfraSecretService secretService,
        QueryExecutionChunkRepository chunkRepository,
        ObjectMapper objectMapper
    ) {
        this.secretService = secretService;
        this.chunkRepository = chunkRepository;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Open a JDBC connection for the given datasource.
     * The caller is responsible for closing the returned Connection.
     */
    public Connection getConnection(InfraDataSource ds) throws SQLException {
        validateDatasource(ds);
        String password = resolvePassword(ds);
        return openConnection(ds.getJdbcUrl().trim(), ds.getUsername(), password);
    }

    /**
     * Execute {@code sql} against the datasource, streaming results into
     * {@link QueryExecutionChunk} rows via {@code chunkRepository}.
     *
     * @param ds            resolved datasource entity
     * @param sql           effective SQL (after validation / rewriting)
     * @param executionId   parent execution id (used as chunk.executionId)
     * @param rowLimit      maximum rows to read (hard cap, not LIMIT clause)
     * @param progressHook  called after each chunk batch with running row count (may be null)
     * @param cancelFlag    when set to {@code true} mid-execution, cancels the JDBC statement
     * @return summary of the execution
     * @throws SQLException on JDBC failure
     */
    public ExecutionResult execute(
        InfraDataSource ds,
        String sql,
        UUID executionId,
        int rowLimit,
        Consumer<Integer> progressHook,
        AtomicBoolean cancelFlag
    ) throws SQLException {
        validateDatasource(ds);
        String password = resolvePassword(ds);

        long connectStart = System.nanoTime();
        try (Connection conn = openConnection(ds.getJdbcUrl().trim(), ds.getUsername(), password)) {
            long connectMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);

            List<ColumnMeta> columns;
            long totalRows = 0;
            boolean truncated = false;
            int chunkIndex = 0;
            long queryStart = System.nanoTime();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setFetchSize(STREAM_CHUNK_SIZE);

                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    columns = buildColumnMeta(meta, colCount);
                    List<String> headers = columns.stream().map(ColumnMeta::name).toList();

                    List<Map<String, Object>> buffer = new ArrayList<>(STREAM_CHUNK_SIZE);

                    while (rs.next()) {
                        // Check cancel flag at row boundary
                        if (cancelFlag != null && cancelFlag.get()) {
                            try {
                                ps.cancel();
                            } catch (SQLException ignored) {}
                            break;
                        }

                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(headers.get(i - 1), readValue(rs, i));
                        }
                        buffer.add(row);
                        totalRows++;

                        // Enforce rowLimit (stop early rather than after-the-fact filter)
                        if (rowLimit > 0 && totalRows >= rowLimit) {
                            truncated = rs.next(); // peek — if more rows exist, mark truncated
                            break;
                        }

                        if (buffer.size() >= STREAM_CHUNK_SIZE) {
                            flushChunk(executionId, chunkIndex++, buffer, totalRows, headers);
                            if (progressHook != null) {
                                progressHook.accept((int) Math.min(totalRows, Integer.MAX_VALUE));
                            }
                            buffer.clear();
                        }
                    }

                    // Flush remaining
                    if (!buffer.isEmpty()) {
                        flushChunk(executionId, chunkIndex++, buffer, totalRows, headers);
                    }
                }
            }

            long queryMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queryStart);
            LOG.debug(
                "JdbcSqlExecutor.execute done executionId={} rows={} truncated={} connect={}ms query={}ms",
                executionId, totalRows, truncated, connectMs, queryMs
            );
            return new ExecutionResult(columns, totalRows, truncated, connectMs + queryMs, chunkIndex);
        }
    }

    /**
     * Run EXPLAIN on {@code sql}. Uses {@code EXPLAIN (FORMAT JSON)} for PostgreSQL,
     * plain {@code EXPLAIN } for other dialects.
     *
     * @return plan text (multi-line or JSON string)
     * @throws SQLException on JDBC failure
     */
    public String explain(InfraDataSource ds, String sql) throws SQLException {
        validateDatasource(ds);
        String password = resolvePassword(ds);

        String explainSql = buildExplainSql(ds, sql);

        try (
            Connection conn = openConnection(ds.getJdbcUrl().trim(), ds.getUsername(), password);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(explainSql)
        ) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    String val = rs.getString(i);
                    if (val != null) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(val);
                    }
                }
            }
            return sb.toString();
        }
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    public record ColumnMeta(String name, String typeName, boolean nullable) {}

    public record ExecutionResult(
        List<ColumnMeta> columns,
        long rowCount,
        boolean truncated,
        long elapsedMs,
        int chunkCount
    ) {}

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void validateDatasource(InfraDataSource ds) {
        if (ds == null) {
            throw new IllegalArgumentException("datasource must not be null");
        }
        if (!StringUtils.hasText(ds.getJdbcUrl())) {
            throw new IllegalArgumentException("datasource '" + ds.getName() + "' has no jdbcUrl");
        }
    }

    private String resolvePassword(InfraDataSource ds) {
        Map<String, Object> secrets = secretService.readSecrets(ds);
        Object pw = secrets.get("password");
        return pw != null ? pw.toString() : null;
    }

    private Connection openConnection(String url, String username, String password) throws SQLException {
        java.util.Properties props = new java.util.Properties();
        if (StringUtils.hasText(username)) {
            props.setProperty("user", username.trim());
        }
        if (StringUtils.hasText(password)) {
            props.setProperty("password", password);
        }
        // Short connect timeout for PostgreSQL
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            props.setProperty("connectTimeout", "15");
        }
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException ex) {
            throw new SQLException(
                "Cannot connect to datasource '" + url + "': " + ex.getMessage(),
                ex.getSQLState(),
                ex.getErrorCode(),
                ex
            );
        }
    }

    private List<ColumnMeta> buildColumnMeta(ResultSetMetaData meta, int colCount) throws SQLException {
        List<ColumnMeta> cols = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            String name = meta.getColumnLabel(i);
            if (!StringUtils.hasText(name)) {
                name = meta.getColumnName(i);
            }
            String typeName = meta.getColumnTypeName(i);
            boolean nullable = meta.isNullable(i) != ResultSetMetaData.columnNoNulls;
            cols.add(new ColumnMeta(name, typeName, nullable));
        }
        return cols;
    }

    private void flushChunk(
        UUID executionId,
        int chunkIndex,
        List<Map<String, Object>> rows,
        long totalRowsSoFar,
        List<String> headers
    ) {
        if (executionId == null || rows.isEmpty()) {
            return;
        }
        long rowEnd = totalRowsSoFar;
        long rowStart = rowEnd - rows.size();

        QueryExecutionChunk chunk = new QueryExecutionChunk();
        chunk.setExecutionId(executionId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setRowStart(rowStart);
        chunk.setRowEnd(rowEnd);
        chunk.setCreatedDate(Instant.now());
        try {
            chunk.setRowsJson(objectMapper.writeValueAsString(rows));
        } catch (Exception ex) {
            chunk.setRowsJson("[]");
        }
        chunkRepository.save(chunk);
    }

    private static String buildExplainSql(InfraDataSource ds, String sql) {
        String type = ds.getType() == null ? "" : ds.getType().trim().toUpperCase(Locale.ROOT);
        if ("POSTGRESQL".equals(type) || (ds.getJdbcUrl() != null && ds.getJdbcUrl().toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:"))) {
            return "EXPLAIN (FORMAT JSON) " + sql;
        }
        return "EXPLAIN " + sql;
    }

    static Object readValue(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), 65_536L));
        }
        if (value instanceof Blob blob) {
            return "[BLOB:" + blob.length() + "]";
        }
        return value;
    }
}
