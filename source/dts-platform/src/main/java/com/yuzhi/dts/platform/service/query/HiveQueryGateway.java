package com.yuzhi.dts.platform.service.query;

import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
public class HiveQueryGateway implements QueryGateway {

    private static final Logger LOG = LoggerFactory.getLogger(HiveQueryGateway.class);
    private static final int MAX_ROWS = 5000;

    private final HiveConnectionService connectionService;
    private final InceptorDataSourceRegistry registry;

    public HiveQueryGateway(HiveConnectionService connectionService, InceptorDataSourceRegistry registry) {
        this.connectionService = connectionService;
        this.registry = registry;
    }

    @Override
    public Map<String, Object> execute(String effectiveSql) {
        InceptorDataSourceState state = registry
            .getActive()
            .orElseThrow(() -> new IllegalStateException("未检测到可用的 Inceptor 数据源，联系系统管理员"));

        HiveConnectionTestRequest request = buildRequest(state);
        try {
            return connectionService.executeWithConnection(request, (connection, connectStart) -> {
                long connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
                long queryStart = System.nanoTime();

                if (StringUtils.hasText(state.database())) {
                    try (Statement schemaStmt = connection.createStatement()) {
                        applySchema(schemaStmt, state.database());
                    }
                }

                List<String> headers = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                try (Statement stmt = connection.createStatement()) {
                    stmt.setMaxRows(MAX_ROWS);
                    stmt.setFetchSize(2000);
                    try (ResultSet rs = stmt.executeQuery(effectiveSql)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            headers.add(meta.getColumnLabel(i));
                        }
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(headers.get(i - 1), readValue(rs, i));
                            }
                            rows.add(row);
                        }
                    }
                }

                long queryMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queryStart);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("headers", headers);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("connectMillis", connectMillis);
                result.put("queryMillis", queryMillis);
                result.put("effectiveSql", effectiveSql);
                result.put(
                    "executionContext",
                    Map.of(
                        "database",
                        state.database(),
                        "loginPrincipal",
                        state.loginPrincipal(),
                        "timestamp",
                        Instant.now()
                    )
                );
                LOG.debug("Hive query executed. rows={}, connect={}ms, query={}ms", rows.size(), connectMillis, queryMillis);
                return result;
            });
        } catch (Exception e) {
            String message = resolveMessage(e);
            LOG.error("Hive query failure. sql='{}', reason={}", effectiveSql, message, e);
            throw new IllegalStateException("Hive 查询失败: " + message, e);
        }
    }

    private HiveConnectionTestRequest buildRequest(InceptorDataSourceState state) {
        HiveConnectionTestRequest request = new HiveConnectionTestRequest();
        request.setJdbcUrl(state.jdbcUrl());
        request.setLoginPrincipal(state.loginPrincipal());
        request.setAuthMethod(state.authMethod());
        request.setKrb5Conf(state.krb5Conf());
        request.setProxyUser(state.proxyUser());
        request.setJdbcProperties(state.jdbcProperties());
        request.setTestQuery("SELECT 1");
        if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
            request.setKeytabBase64(state.keytabBase64());
            request.setKeytabFileName(state.keytabFileName());
        } else if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            request.setPassword(state.password());
        }
        return request;
    }

    private void applySchema(Statement stmt, String schema) throws SQLException {
        if (!StringUtils.hasText(schema)) {
            return;
        }
        String sanitized = schema.replace("`", "``");
        stmt.execute("USE `" + sanitized + "`");
    }

    private Object readValue(ResultSet rs, int index) throws SQLException {
        Object value = rs.getObject(index);
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
        }
        if (value instanceof Blob blob) {
            return blob.getBytes(1, (int) Math.min(blob.length(), 1_048_576));
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        return value;
    }

    private String resolveMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable current = throwable;
        String lastNonBlank = null;
        int depth = 0;
        while (current != null && depth < 10) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                lastNonBlank = message.trim();
            }
            current = current.getCause();
            depth++;
        }
        return lastNonBlank != null ? lastNonBlank : throwable.getClass().getSimpleName();
    }
}
