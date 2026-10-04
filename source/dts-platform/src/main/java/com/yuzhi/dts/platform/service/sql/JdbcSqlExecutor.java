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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Tracks in-flight JDBC Statements keyed by executionId so that {@link #cancel(UUID)} can
     * call {@link Statement#cancel()} on the executing thread's statement — not just flip a flag.
     */
    private final ConcurrentMap<UUID, Statement> activeStatements = new ConcurrentHashMap<>();

    private final InfraSecretService secretService;
    private final QueryExecutionChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;
    /**
     * Per-statement query timeout (seconds). Capped at the JDBC driver's reported resolution.
     * Set to 0 or negative to disable. Default 30 min — prevents runaway queries from pinning
     * executor threads indefinitely, which previously left {@link JdbcSqlExecutor#cancel(UUID)}
     * as the only way out and failed silently when the connection was also stuck.
     */
    private final int queryTimeoutSeconds;

    public JdbcSqlExecutor(
        InfraSecretService secretService,
        QueryExecutionChunkRepository chunkRepository,
        ObjectMapper objectMapper,
        @Value("${dts.sql.query-timeout-seconds:1800}") int queryTimeoutSeconds
    ) {
        this.secretService = secretService;
        this.chunkRepository = chunkRepository;
        this.objectMapper = objectMapper;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
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
        return execute(ds, sql, executionId, rowLimit, progressHook, cancelFlag, List.of());
    }

    /** Bound values never become SQL text. Used by governed metric queries. */
    public ExecutionResult execute(
        InfraDataSource ds, String sql, UUID executionId, int rowLimit,
        Consumer<Integer> progressHook, AtomicBoolean cancelFlag, List<Object> parameters
    ) throws SQLException {
        validateDatasource(ds);
        String password = resolvePassword(ds);

        long connectStart = System.nanoTime();
        try (Connection conn = openConnection(ds.getJdbcUrl().trim(), ds.getUsername(), password)) {
            long connectMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);

            // Critical #3: PG JDBC ignores setFetchSize() unless autoCommit=false (server-side cursor)
            boolean isPostgres = conn.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("postgres");
            if (isPostgres) {
                conn.setAutoCommit(false);
            }

            boolean persistChunks = executionId != null;
            List<ColumnMeta> columns = List.of();
            List<Map<String, Object>> inlineRows = persistChunks ? List.of() : new ArrayList<>();
            long totalRows = 0;
            boolean truncated = false;
            int chunkIndex = 0;
            long queryStart = System.nanoTime();

            // ok-flag: set to true only after the chunk loop completes normally.
            // Used in finally to decide commit vs rollback and to ensure autoCommit is restored.
            boolean ok = false;
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int index = 0; index < parameters.size(); index++) {
                        ps.setObject(index + 1, parameters.get(index));
                    }
                    ps.setFetchSize(STREAM_CHUNK_SIZE);
                    if (queryTimeoutSeconds > 0) {
                        try {
                            ps.setQueryTimeout(queryTimeoutSeconds);
                        } catch (SQLException ignored) {
                            // Some drivers don't support setQueryTimeout — non-fatal
                        }
                    }

                    // Critical #2: register statement BEFORE executeQuery so cancel() can reach it
                    if (executionId != null) {
                        activeStatements.put(executionId, ps);
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        columns = buildColumnMeta(meta, colCount);
                        List<String> headers = columns.stream().map(ColumnMeta::name).toList();

                        List<Map<String, Object>> buffer = persistChunks ? new ArrayList<>(STREAM_CHUNK_SIZE) : List.of();

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
                            if (persistChunks) {
                                buffer.add(row);
                            } else {
                                inlineRows.add(row);
                            }
                            totalRows++;

                            // Enforce rowLimit (stop early rather than after-the-fact filter)
                            if (rowLimit > 0 && totalRows >= rowLimit) {
                                truncated = rs.next(); // peek — if more rows exist, mark truncated
                                break;
                            }

                            if (persistChunks && buffer.size() >= STREAM_CHUNK_SIZE) {
                                flushChunk(executionId, chunkIndex++, buffer, totalRows, headers);
                                if (progressHook != null) {
                                    progressHook.accept((int) Math.min(totalRows, Integer.MAX_VALUE));
                                }
                                buffer.clear();
                            } else if (!persistChunks && progressHook != null && totalRows % STREAM_CHUNK_SIZE == 0) {
                                progressHook.accept((int) Math.min(totalRows, Integer.MAX_VALUE));
                            }
                        }

                        // Flush remaining
                        if (persistChunks && !buffer.isEmpty()) {
                            flushChunk(executionId, chunkIndex++, buffer, totalRows, headers);
                        }
                    }
                }
                // Chunk loop completed without exception — mark success before finally runs
                ok = true;
            } finally {
                // Critical #2: deregister statement whether execution succeeded or was canceled
                if (executionId != null) {
                    activeStatements.remove(executionId);
                }
                // Critical #3: commit on success / rollback on exception to close the server-side
                // cursor, then ALWAYS restore autoCommit so the pooled connection is not poisoned.
                if (isPostgres) {
                    try {
                        if (ok) {
                            conn.commit();
                        } else {
                            conn.rollback();
                        }
                    } catch (SQLException e) {
                        LOG.warn("txn {} failed for execution {}", ok ? "commit" : "rollback", executionId, e);
                    }
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        // If we later switch to a pooled DataSource this connection must not be
                        // returned with autoCommit=false — force-close so the pool rebuilds it.
                        LOG.error("failed to restore autoCommit for execution {} — closing connection", executionId, e);
                        try { conn.close(); } catch (SQLException ignored) {}
                    }
                }
            }

            long queryMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queryStart);
            LOG.debug(
                "JdbcSqlExecutor.execute done executionId={} rows={} truncated={} connect={}ms query={}ms",
                executionId, totalRows, truncated, connectMs, queryMs
            );
            return new ExecutionResult(columns, inlineRows, totalRows, truncated, connectMs + queryMs, chunkIndex);
        }
    }

    /**
     * Cancel the in-flight JDBC Statement for the given {@code executionId}.
     * This calls {@link Statement#cancel()} on the executing thread's statement, which causes the
     * underlying driver to interrupt the blocking {@code executeQuery()} call — unlike the
     * {@code AtomicBoolean cancelFlag} which is only checked at chunk boundaries.
     *
     * @param executionId the execution to cancel
     * @return {@code true} if a statement was found and cancel() was called; {@code false} otherwise
     */
    public boolean cancel(UUID executionId) {
        if (executionId == null) {
            return false;
        }
        Statement s = activeStatements.get(executionId);
        if (s != null) {
            try {
                s.cancel();
                LOG.debug("JdbcSqlExecutor.cancel: Statement.cancel() called for executionId={}", executionId);
                return true;
            } catch (SQLException ex) {
                LOG.warn("JdbcSqlExecutor.cancel: Statement.cancel() threw for executionId={}: {}",
                    executionId, ex.getMessage());
            }
        }
        return false;
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
        List<Map<String, Object>> rows,
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
