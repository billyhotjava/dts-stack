package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public HiveStatementExecutor(HiveExecutionProperties properties) {
        this.properties = properties;
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
            log.warn("Hive JDBC driver not present: {}", e.getMessage());
            for (var entry : entries) {
                results.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.FAILED, "Hive driver missing"));
            }
            return results;
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

        try (Connection connection = props.isEmpty() ? DriverManager.getConnection(jdbcUrl) : DriverManager.getConnection(jdbcUrl, props)) {
            try (Statement stmt = connection.createStatement()) {
                for (var entry : entries) {
                    String key = entry.getKey();
                    String sql = entry.getValue();
                    try {
                        stmt.execute(sql);
                        results.add(new StatementExecutionResult(key, sql, StatementExecutionResult.Status.SUCCEEDED, "OK"));
                    } catch (SQLException ex) {
                        log.warn("Hive statement execution failed: {}", ex.getMessage());
                        String reason = sanitize(ex.getMessage());
                        results.add(new StatementExecutionResult(key, sql, StatementExecutionResult.Status.FAILED, reason));
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
                    results.add(new StatementExecutionResult(entry.getKey(), entry.getValue(), StatementExecutionResult.Status.FAILED, sanitize(ex.getMessage())));
                }
            } else {
                results.replaceAll(res -> res.status() == StatementExecutionResult.Status.SUCCEEDED ? res : new StatementExecutionResult(res.key(), res.sql(), StatementExecutionResult.Status.FAILED, sanitize(ex.getMessage())));
            }
        }
        return results;
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "Execution failed";
        }
        return message.replaceAll("\n", " ").trim();
    }
}
