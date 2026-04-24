package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlSubqueryServiceImpl implements SqlSubqueryService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlSubqueryServiceImpl.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_VIEW_ROWS = 100_000;
    /**
     * Sweep period for expired temp-view cleanup.
     * <p>Picked to be < TTL/3 so an expired view never lingers more than ~10 min after TTL.
     */
    private static final long CLEANUP_FIXED_DELAY_MS = 10L * 60L * 1000L;
    /**
     * Sweep period for orphan-table reconciliation. Drops {@code sqlide_view_*} UNLOGGED
     * tables whose name no longer appears in the in-memory registry — these are typically
     * left over from process crashes or from a previous instance that owned the schema.
     */
    private static final long ORPHAN_SWEEP_FIXED_DELAY_MS = 60L * 60L * 1000L;

    private record ViewState(String ownerLogin, Instant expiresAt) {}

    private final DataSource dataSource;
    private final SqlResultStreamService streamService;
    /** name → ViewState (owner + expiry) */
    private final Map<String, ViewState> views = new ConcurrentHashMap<>();

    public SqlSubqueryServiceImpl(DataSource dataSource, SqlResultStreamService streamService) {
        this.dataSource = dataSource;
        this.streamService = streamService;
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    }

    @Override
    public TempViewDto createTempView(UUID executionId) {
        // Fix 3: use full 32-hex UUID — no truncation, no collision risk
        String name = "sqlide_view_" + executionId.toString().replace("-", "");
        String ownerLogin = currentUser();
        long count = 0;
        try (Connection conn = dataSource.getConnection()) {
            // Drop existing table with same name first
            try (PreparedStatement drop = conn.prepareStatement("DROP TABLE IF EXISTS " + name)) {
                drop.execute();
            }
            // Use UNLOGGED (not TEMP) because Hikari pool means session changes between requests
            java.util.stream.Stream<java.util.Map<String, Object>> stream;
            try {
                stream = streamService.streamRange(executionId, 0, MAX_VIEW_ROWS);
            } catch (Exception e) {
                // Execution not found (or no chunks) — treat as empty result
                stream = java.util.stream.Stream.empty();
            }
            var iter = stream.iterator();
            // Fix 4: empty result — return DTO with rowCount=0, no table created, no map entry
            if (!iter.hasNext()) {
                return new TempViewDto(name, executionId, 0, Instant.now().plus(TTL));
            }
            Map<String, Object> firstRow = iter.next();
            List<String> cols = new ArrayList<>(firstRow.keySet());
            StringBuilder ddl = new StringBuilder("CREATE UNLOGGED TABLE " + name + " (");
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) ddl.append(", ");
                ddl.append('"').append(cols.get(i).replace("\"", "")).append("\" text");
            }
            ddl.append(")");
            try (PreparedStatement create = conn.prepareStatement(ddl.toString())) {
                create.execute();
            }
            // Insert rows in batches
            String placeholders = String.join(",", cols.stream().map(c -> "?").toList());
            String insertSql = "INSERT INTO " + name + " VALUES (" + placeholders + ")";
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                count = insertOne(ins, firstRow, cols);
                while (iter.hasNext()) {
                    count += insertOne(ins, iter.next(), cols);
                }
                ins.executeBatch();
            }
            Instant expiry = Instant.now().plus(TTL);
            // Fix 1/2: store owner with expiry
            views.put(name, new ViewState(ownerLogin, expiry));
            return new TempViewDto(name, executionId, count, expiry);
        } catch (SQLException e) {
            throw new RuntimeException("temp view create failed", e);
        }
    }

    private long insertOne(PreparedStatement ps, Map<String, Object> row, List<String> cols) throws SQLException {
        for (int i = 0; i < cols.size(); i++) {
            Object v = row.get(cols.get(i));
            ps.setObject(i + 1, v == null ? null : String.valueOf(v));
        }
        ps.addBatch();
        return 1;
    }

    @Override
    public Map<String, Object> executeOnView(String viewName, String sql) {
        if (!viewName.startsWith("sqlide_view_")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "view not found or expired");
        }
        // Fix 1/2: ownership check — return 404 to avoid existence leak
        String currentLogin = currentUser();
        ViewState state = views.get(viewName);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "view not found or expired");
        }
        if (!state.ownerLogin().equals(currentLogin)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "view not found or expired");
        }
        try (Connection conn = dataSource.getConnection()) {
            // Fix 1: enforce read-only at connection level before any SQL runs
            conn.setReadOnly(true);
            try (Statement s = conn.createStatement()) {
                s.execute("SET TRANSACTION READ ONLY");
            }
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> headers = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) headers.add(md.getColumnLabel(i));
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) r.put(headers.get(i - 1), rs.getObject(i));
                    rows.add(r);
                    if (rows.size() >= MAX_VIEW_ROWS) break;
                }
                return Map.of("columns", headers, "rows", rows);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("subquery failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void dropView(String viewName) {
        if (!viewName.startsWith("sqlide_view_")) return;
        // Fix 2: ownership check — silently no-op if not owner or not found (don't leak existence)
        String currentLogin = currentUser();
        ViewState state = views.get(viewName);
        if (state == null) return;
        if (!state.ownerLogin().equals(currentLogin)) return;
        dropViewInternal(viewName);
    }

    /** Bypasses ownership check — used only by the scheduler cleanup path. */
    private void dropViewInternal(String viewName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DROP TABLE IF EXISTS " + viewName)) {
            ps.execute();
            views.remove(viewName);
        } catch (SQLException ignored) { /* best effort */ }
    }

    @Override
    public int cleanupExpired() {
        Instant now = Instant.now();
        int dropped = 0;
        for (var entry : List.copyOf(views.entrySet())) {
            if (entry.getValue().expiresAt().isBefore(now)) {
                dropViewInternal(entry.getKey());
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Periodic sweep of expired temp views. Runs at a fixed delay so that even a busy
     * IDE tenant cannot accumulate more than a few extra minutes of stale UNLOGGED tables
     * after their 30-minute TTL elapses.
     */
    @Scheduled(fixedDelay = CLEANUP_FIXED_DELAY_MS, initialDelay = CLEANUP_FIXED_DELAY_MS)
    public void scheduledCleanupExpired() {
        try {
            int dropped = cleanupExpired();
            if (dropped > 0) {
                LOG.info("[sql-ide] expired temp-view cleanup dropped {} table(s)", dropped);
            }
        } catch (Exception ex) {
            LOG.warn("[sql-ide] expired temp-view cleanup failed: {}", ex.getMessage());
        }
    }

    /**
     * Reconciles the in-memory registry against the database every hour: drops any
     * {@code sqlide_view_*} UNLOGGED table that has no entry in {@link #views}. This
     * catches tables left behind by a previous JVM instance (process crash, redeploy)
     * that the live registry does not know about.
     */
    @Scheduled(fixedDelay = ORPHAN_SWEEP_FIXED_DELAY_MS, initialDelay = ORPHAN_SWEEP_FIXED_DELAY_MS)
    public void scheduledOrphanSweep() {
        List<String> orphans = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tablename FROM pg_tables WHERE tablename LIKE 'sqlide_view_%'"
            ); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !views.containsKey(name)) {
                        orphans.add(name);
                    }
                }
            }
        } catch (SQLException ex) {
            // Probably running on a non-PostgreSQL backend; skip orphan sweep silently.
            LOG.debug("[sql-ide] orphan sweep skipped: {}", ex.getMessage());
            return;
        }
        for (String name : orphans) {
            dropViewInternal(name);
        }
        if (!orphans.isEmpty()) {
            LOG.info("[sql-ide] orphan temp-view sweep dropped {} table(s)", orphans.size());
        }
    }
}
