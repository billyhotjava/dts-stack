package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class SqlSubqueryServiceImpl implements SqlSubqueryService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_VIEW_ROWS = 100_000;

    private final DataSource dataSource;
    private final SqlResultStreamService streamService;
    /** name → expiry */
    private final Map<String, Instant> views = new ConcurrentHashMap<>();

    public SqlSubqueryServiceImpl(DataSource dataSource, SqlResultStreamService streamService) {
        this.dataSource = dataSource;
        this.streamService = streamService;
    }

    @Override
    public TempViewDto createTempView(UUID executionId) {
        String name = "sqlide_view_" + executionId.toString().replace("-", "").substring(0, 16);
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
                // Execution not found (or no chunks) — create an empty view
                stream = java.util.stream.Stream.empty();
            }
            var iter = stream.iterator();
            if (!iter.hasNext()) {
                Instant expiry = Instant.now().plus(TTL);
                views.put(name, expiry);
                // Create empty UNLOGGED table so name is registered
                try (PreparedStatement create = conn.prepareStatement(
                        "CREATE UNLOGGED TABLE " + name + " (_placeholder text)")) {
                    create.execute();
                }
                return new TempViewDto(name, executionId, 0, expiry);
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
            views.put(name, expiry);
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
            throw new RuntimeException("invalid view name");
        }
        if (!views.containsKey(viewName)) {
            throw new RuntimeException("view not found or expired");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
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
        } catch (SQLException e) {
            throw new RuntimeException("subquery failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void dropView(String viewName) {
        if (!viewName.startsWith("sqlide_view_")) return;
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
            if (entry.getValue().isBefore(now)) {
                dropView(entry.getKey());
                dropped++;
            }
        }
        return dropped;
    }
}
