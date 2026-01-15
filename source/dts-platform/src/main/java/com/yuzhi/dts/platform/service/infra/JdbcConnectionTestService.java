package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.web.rest.infra.JdbcConnectionTestRequest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JdbcConnectionTestService {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionTestService.class);

    private final HiveConnectionService hiveConnectionService;

    @Value("${dts.jdbc.login-timeout-seconds:20}")
    private int loginTimeoutSeconds;

    public JdbcConnectionTestService(HiveConnectionService hiveConnectionService) {
        this.hiveConnectionService = hiveConnectionService;
    }

    public HiveConnectionTestResult testConnection(JdbcConnectionTestRequest request) {
        Objects.requireNonNull(request, "request");
        long start = System.nanoTime();
        String url = StringUtils.trimWhitespace(request.getJdbcUrl());
        if (!StringUtils.hasText(url)) {
            return HiveConnectionTestResult.failure("JDBC URL 不能为空", 0L);
        }

        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader jdbcLoader = hiveConnectionService != null ? hiveConnectionService.getJdbcDriverLoader() : null;
        if (jdbcLoader != null) {
            Thread.currentThread().setContextClassLoader(jdbcLoader);
        }
        try {
            try {
                DriverManager.setLoginTimeout(Math.max(1, loginTimeoutSeconds));
            } catch (Throwable ignored) {}

            if (StringUtils.hasText(request.getDriverClass())) {
                try {
                    if (jdbcLoader != null) {
                        Class.forName(request.getDriverClass(), true, jdbcLoader);
                    } else {
                        Class.forName(request.getDriverClass());
                    }
                } catch (Throwable ex) {
                    LOG.warn("Failed to load JDBC driver class {}: {}", request.getDriverClass(), ex.getMessage());
                }
            }

            Properties props = new Properties();
            if (StringUtils.hasText(request.getUsername())) {
                props.setProperty("user", request.getUsername());
            }
            if (StringUtils.hasText(request.getPassword())) {
                props.setProperty("password", request.getPassword());
            }
            request.getJdbcProperties().forEach((k, v) -> {
                if (k != null && v != null) {
                    props.setProperty(k, v);
                }
            });

            long connectStart = System.nanoTime();
            try (Connection connection = props.isEmpty() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, props)) {
                long connectMillis = elapsedMillis(connectStart);
                DatabaseMetaData meta = connection.getMetaData();
                runValidationQuery(connection, request.getTestQuery());
                return HiveConnectionTestResult.success(
                    "连接成功",
                    connectMillis,
                    safe(meta::getDatabaseProductVersion),
                    safe(meta::getDriverVersion),
                    collectWarnings(connection.getWarnings())
                );
            }
        } catch (Exception ex) {
            return HiveConnectionTestResult.failure(sanitizeMessage(ex), elapsedMillis(start));
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    private void runValidationQuery(Connection connection, String testQuery) throws Exception {
        String sql = StringUtils.hasText(testQuery) ? testQuery.trim() : "SELECT 1";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private List<String> collectWarnings(SQLWarning warning) {
        List<String> warnings = new ArrayList<>();
        SQLWarning current = warning;
        int guard = 0;
        while (current != null && guard++ < 20) {
            String message = StringUtils.trimWhitespace(current.getMessage());
            if (StringUtils.hasText(message)) {
                warnings.add(message);
            }
            current = current.getNextWarning();
        }
        return warnings;
    }

    private static <T> T safe(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ignore) {
            return null;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private String sanitizeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String msg = Optional.ofNullable(throwable.getMessage()).orElse(throwable.toString());
        return msg.length() > 800 ? msg.substring(0, 800) : msg;
    }
}

