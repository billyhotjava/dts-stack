package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HiveStatementExecutor {

    private static final Logger log = LoggerFactory.getLogger(HiveStatementExecutor.class);
    private static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

    private final HiveExecutionProperties properties;
    private final HiveConnectionService driverSource;
    private final InceptorDataSourceRegistry inceptorRegistry;

    public HiveStatementExecutor(
        HiveExecutionProperties properties,
        HiveConnectionService driverSource,
        InceptorDataSourceRegistry inceptorRegistry
    ) {
        this.properties = properties;
        this.driverSource = driverSource;
        this.inceptorRegistry = inceptorRegistry;
    }

    public List<StatementExecutionResult> execute(Map<String, String> statements, String schemaHint) {
        if (statements == null || statements.isEmpty()) {
            return new ArrayList<>();
        }
        List<Entry<String, String>> entries = statements.entrySet().stream().toList();

        InceptorDataSourceState state = inceptorRegistry.getActive().orElse(null);
        if (state != null) {
            return executeWithInceptor(state, entries, schemaHint);
        }
        return executeWithLegacyProperties(entries, schemaHint);
    }

    private List<StatementExecutionResult> executeWithInceptor(InceptorDataSourceState state, List<Entry<String, String>> entries, String schemaHint) {
        HiveConnectionTestRequest request = buildRequest(state);
        try {
            return driverSource.executeWithConnection(request, (connection, connectStart) -> {
                List<StatementExecutionResult> results = new ArrayList<>();
                try {
                    runStatementsOnConnection(connection, entries, schemaHint, properties.isTolerant(), results);
                } catch (SQLException ex) {
                    log.error("Hive connection/statement failure: {}", ex.getMessage());
                    handleSqlFailure(entries, results, ex);
                }
                return results;
            });
        } catch (Exception ex) {
            log.error("Hive connection/statement failure: {}", ex.getMessage());
            return buildFailureResults(entries, sanitize(ex.getMessage()), errorCode(ex));
        }
    }

    private List<StatementExecutionResult> executeWithLegacyProperties(List<Entry<String, String>> entries, String schemaHint) {
        List<StatementExecutionResult> results = new ArrayList<>();
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getJdbcUrl())) {
            log.info("Hive execution disabled. Statements will be marked as skipped.");
            for (Entry<String, String> entry : entries) {
                results.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.SKIPPED, "Hive execution disabled"));
            }
            return results;
        }

        try {
            Class.forName(HIVE_DRIVER);
        } catch (ClassNotFoundException e) {
            log.debug("Hive JDBC driver not on classpath; relying on externally loaded driver");
        }

        Properties props = new Properties();
        if (properties.getUsername() != null) {
            props.setProperty("user", properties.getUsername());
        }
        if (properties.getPassword() != null) {
            props.setProperty("password", properties.getPassword());
        }
        properties.getProperties().forEach(props::setProperty);

        String jdbcUrl = properties.getJdbcUrl();
        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader ext = driverSource != null ? driverSource.getJdbcDriverLoader() : null;
        if (ext != null) {
            try {
                Thread.currentThread().setContextClassLoader(ext);
            } catch (SecurityException ignored) {}
        }
        try (Connection connection = props.isEmpty() ? DriverManager.getConnection(jdbcUrl) : DriverManager.getConnection(jdbcUrl, props)) {
            try {
                runStatementsOnConnection(connection, entries, schemaHint, properties.isTolerant(), results);
            } catch (SQLException ex) {
                log.error("Hive connection/statement failure: {}", ex.getMessage());
                handleSqlFailure(entries, results, ex);
            }
        } catch (SQLException ex) {
            log.error("Hive connection/statement failure: {}", ex.getMessage());
            results = buildFailureResults(entries, sanitize(ex.getMessage()), resolveErrorCode(ex));
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
        return results;
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

    private void runStatementsOnConnection(
        Connection connection,
        List<Entry<String, String>> entries,
        String schemaHint,
        boolean tolerant,
        List<StatementExecutionResult> results
    ) throws SQLException {
        if (StringUtils.hasText(schemaHint)) {
            applySchemaHint(connection, schemaHint);
        }
        try (Statement stmt = connection.createStatement()) {
            for (Entry<String, String> entry : entries) {
                String key = entry.getKey();
                String sql = entry.getValue();
                try {
                    stmt.execute(sql);
                    results.add(new StatementExecutionResult(key, sql, StatementExecutionResult.Status.SUCCEEDED, "OK"));
                } catch (SQLException ex) {
                    String code = resolveErrorCode(ex);
                    String reason = sanitize(ex.getMessage());
                    log.warn("Hive statement execution failed [{}]: {}", code, reason);
                    results.add(new StatementExecutionResult(key, sql, StatementExecutionResult.Status.FAILED, reason, code));
                    if (!tolerant) {
                        throw ex;
                    }
                }
            }
        }
    }

    private void applySchemaHint(Connection connection, String schemaHint) throws SQLException {
        if (!StringUtils.hasText(schemaHint)) {
            return;
        }
        String sanitized = schemaHint.replace("`", "``");
        String sql = "USE `" + sanitized + "`";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void handleSqlFailure(List<Entry<String, String>> entries, List<StatementExecutionResult> results, SQLException ex) {
        if (results.isEmpty()) {
            results.addAll(buildFailureResults(entries, sanitize(ex.getMessage()), resolveErrorCode(ex)));
        } else {
            String reason = sanitize(ex.getMessage());
            String code = resolveErrorCode(ex);
            results.replaceAll(res -> res.status() == StatementExecutionResult.Status.SUCCEEDED
                ? res
                : new StatementExecutionResult(res.key(), res.sql(), StatementExecutionResult.Status.FAILED, reason, code));
        }
    }

    private List<StatementExecutionResult> buildFailureResults(List<Entry<String, String>> entries, String reason, String code) {
        List<StatementExecutionResult> failures = new ArrayList<>(entries.size());
        for (Entry<String, String> entry : entries) {
            failures.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.FAILED, reason, code));
        }
        return failures;
    }

    private String errorCode(Throwable throwable) {
        if (throwable instanceof SQLException sql) {
            return resolveErrorCode(sql);
        }
        return throwable != null ? throwable.getClass().getSimpleName() : "UNKNOWN";
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "Execution failed";
        }
        return message.replaceAll("\n", " ").trim();
    }

    private String resolveErrorCode(SQLException ex) {
        if (ex instanceof SQLTimeoutException) {
            return "TIMEOUT";
        }
        if (ex instanceof SQLSyntaxErrorException) {
            return "SYNTAX";
        }
        if (ex instanceof SQLIntegrityConstraintViolationException) {
            return "CONSTRAINT";
        }
        String sqlState = ex.getSQLState();
        if (StringUtils.hasText(sqlState)) {
            return sqlState;
        }
        return ex.getClass().getSimpleName();
    }
}
