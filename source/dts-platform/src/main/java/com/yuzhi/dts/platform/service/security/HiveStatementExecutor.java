package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
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
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HiveStatementExecutor {

    private static final Logger log = LoggerFactory.getLogger(HiveStatementExecutor.class);
    private static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

    private final HiveExecutionProperties properties;
    private final HiveConnectionService driverSource;

    public HiveStatementExecutor(HiveExecutionProperties properties, HiveConnectionService driverSource) {
        this.properties = properties;
        this.driverSource = driverSource;
    }

    public List<StatementExecutionResult> execute(Map<String, String> statements, String schemaHint) {
        List<StatementExecutionResult> results = new ArrayList<>();
        if (statements == null || statements.isEmpty()) {
            return results;
        }
        var entries = statements.entrySet().stream().toList();

        if (!properties.isEnabled() || !StringUtils.hasText(properties.getJdbcUrl())) {
            log.info("Hive execution disabled. Statements will be marked as skipped.");
            for (var entry : entries) {
                results.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.SKIPPED, "Hive execution disabled"));
            }
            return results;
        }

        try {
            Class.forName(HIVE_DRIVER);
        } catch (ClassNotFoundException e) {
            // When using vendor driver loaded externally, the class won't be on the app classpath;
            // rely on DriverManager's registered drivers from external loader.
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
        if (StringUtils.hasText(schemaHint) && !jdbcUrl.contains("/")) {
            jdbcUrl = jdbcUrl + "/" + schemaHint;
        }

        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader ext = (driverSource == null) ? null : driverSource.getJdbcDriverLoader();
        if (ext != null) {
            Thread.currentThread().setContextClassLoader(ext);
        }
        try (Connection connection = props.isEmpty() ? DriverManager.getConnection(jdbcUrl) : DriverManager.getConnection(jdbcUrl, props)) {
            try (Statement stmt = connection.createStatement()) {
                for (var entry : entries) {
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
                        if (!properties.isTolerant()) {
                            throw ex;
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            log.error("Hive connection/statement failure: {}", ex.getMessage());
            if (results.isEmpty()) {
                for (var entry : entries) {
                    results.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.FAILED, sanitize(ex.getMessage()), resolveErrorCode(ex)));
                }
            } else {
                String reason = sanitize(ex.getMessage());
                String code = resolveErrorCode(ex);
                results.replaceAll(res -> res.status() == StatementExecutionResult.Status.SUCCEEDED ? res : new StatementExecutionResult(res.key(), res.sql(), StatementExecutionResult.Status.FAILED, reason, code));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
        return results;
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
