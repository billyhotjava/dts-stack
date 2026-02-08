package com.yuzhi.dts.platform.service.query;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import com.yuzhi.dts.platform.service.infra.PostgresCatalogSyncService;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import javax.sql.DataSource;

@Service
@Primary
public class HiveQueryGateway implements QueryGateway {

    private static final Logger LOG = LoggerFactory.getLogger(HiveQueryGateway.class);
    private static final int MAX_ROWS = 5000;
    private static final String TYPE_POSTGRES = "POSTGRES";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final HiveConnectionService connectionService;
    private final InceptorDataSourceRegistry registry;
    private final PostgresCatalogSyncService postgresCatalogSyncService;
    private final DataSource dataSource;
    private final CatalogFeatureProperties catalogFeatureProperties;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final InfraSecretService infraSecretService;
    private final AdminInfraClient adminInfraClient;

    public HiveQueryGateway(
        HiveConnectionService connectionService,
        InceptorDataSourceRegistry registry,
        PostgresCatalogSyncService postgresCatalogSyncService,
        DataSource dataSource,
        CatalogFeatureProperties catalogFeatureProperties,
        InfraDataSourceRepository infraDataSourceRepository,
        InfraSecretService infraSecretService,
        AdminInfraClient adminInfraClient
    ) {
        this.connectionService = connectionService;
        this.registry = registry;
        this.postgresCatalogSyncService = postgresCatalogSyncService;
        this.dataSource = dataSource;
        this.catalogFeatureProperties = catalogFeatureProperties;
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.infraSecretService = infraSecretService;
        this.adminInfraClient = adminInfraClient;
    }

    @Override
    public Map<String, Object> execute(String effectiveSql) {
        Optional<InceptorDataSourceState> stateOpt = registry.getActive();
        if (stateOpt.isEmpty()) {
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                return executeWithPostgres(effectiveSql);
            }
            throw new IllegalStateException("未检测到可用的数据源，请联系系统管理员");
        }
        InceptorDataSourceState state = stateOpt.orElseThrow();

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

    @Override
    public Map<String, Object> execute(String effectiveSql, UUID datasourceId) {
        if (datasourceId == null) {
            return execute(effectiveSql);
        }

        // 尝试根据 ID 查找数据源
        return infraDataSourceRepository
            .findById(datasourceId)
            .filter(ds -> StringUtils.hasText(ds.getJdbcUrl()) && StringUtils.hasText(ds.getUsername()))
            .map(ds -> {
                Map<String, Object> secrets = infraSecretService.readSecrets(ds);
                String password = secrets.get("password") != null ? secrets.get("password").toString() : null;
                return executeWithJdbcConnection(effectiveSql, ds.getJdbcUrl(), ds.getUsername(), password, ds.getName());
            })
            .orElseGet(() ->
                adminInfraClient
                    .fetchDefaultDataLake()
                    .filter(lake -> lake.getId() != null && lake.getId().equals(datasourceId))
                    .filter(lake -> StringUtils.hasText(lake.getJdbcUrl()) && StringUtils.hasText(lake.getUsername()))
                    .map(lake -> {
                        LOG.info("Using admin managed default data lake for datasource {}", datasourceId);
                        return executeWithJdbcConnection(
                            effectiveSql,
                            lake.getJdbcUrl(),
                            lake.getUsername(),
                            lake.getPassword(),
                            lake.getName()
                        );
                    })
                    .orElseGet(() -> {
                        LOG.warn("Datasource not found or incomplete: {}, falling back to default execution", datasourceId);
                        return execute(effectiveSql);
                    })
            );
    }

    private Map<String, Object> executeWithPostgres(String effectiveSql) {
        // Try to use the registered PostgreSQL datasource (e.g., biadmin) first
        return infraDataSourceRepository
            .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(TYPE_POSTGRES, STATUS_ACTIVE)
            .filter(ds -> StringUtils.hasText(ds.getJdbcUrl()) && StringUtils.hasText(ds.getUsername()))
            .map(ds -> {
                Map<String, Object> secrets = infraSecretService.readSecrets(ds);
                String password = secrets.get("password") != null ? secrets.get("password").toString() : null;
                return executeWithJdbcConnection(effectiveSql, ds.getJdbcUrl(), ds.getUsername(), password, ds.getName());
            })
            .orElseGet(() -> executeWithPlatformDataSource(effectiveSql));
    }

    private Map<String, Object> executeWithJdbcConnection(
        String effectiveSql,
        String jdbcUrl,
        String username,
        String password,
        String datasourceName
    ) {
        long connectStart = System.nanoTime();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            long connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
            long queryStart = System.nanoTime();

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
                    "database", datasourceName != null ? datasourceName : "PostgreSQL",
                    "timestamp", Instant.now()
                )
            );
            LOG.debug("PostgreSQL query executed via registered datasource. rows={}, connect={}ms, query={}ms", rows.size(), connectMillis, queryMillis);
            return result;
        } catch (SQLException e) {
            String message = resolveMessage(e);
            LOG.error("PostgreSQL query failure. datasource='{}', sql='{}', reason={}", datasourceName, effectiveSql, message, e);
            throw new IllegalStateException("PostgreSQL 查询失败: " + message, e);
        }
    }

    private Map<String, Object> executeWithPlatformDataSource(String effectiveSql) {
        long connectStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
            long queryStart = System.nanoTime();

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
                    sanitizeSchema(catalogFeatureProperties.getPostgresSchema()),
                    "timestamp",
                    Instant.now()
                )
            );
            LOG.debug("PostgreSQL query executed via platform datasource. rows={}, connect={}ms, query={}ms", rows.size(), connectMillis, queryMillis);
            return result;
        } catch (SQLException e) {
            String message = resolveMessage(e);
            LOG.error("PostgreSQL query failure. sql='{}', reason={}", effectiveSql, message, e);
            throw new IllegalStateException("PostgreSQL 查询失败: " + message, e);
        }
    }

    private String sanitizeSchema(String schema) {
        if (!StringUtils.hasText(schema)) {
            return "public";
        }
        return schema.trim();
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
        } else if (
            state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD ||
            state.authMethod() == HiveConnectionTestRequest.AuthMethod.JDBC_PASSWORD
        ) {
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
