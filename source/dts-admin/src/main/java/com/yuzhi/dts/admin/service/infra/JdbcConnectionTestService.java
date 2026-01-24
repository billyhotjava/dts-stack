package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.JdbcConnectionTestRequest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Service
public class JdbcConnectionTestService {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionTestService.class);
    private static final Set<String> REGISTERED_DRIVERS = ConcurrentHashMap.newKeySet();

    private final JdbcDriverCatalogService driverCatalogService;

    @Value("${dts.jdbc.login-timeout-seconds:20}")
    private int loginTimeoutSeconds;

    public JdbcConnectionTestService(JdbcDriverCatalogService driverCatalogService) {
        this.driverCatalogService = driverCatalogService;
    }

    public HiveConnectionTestResult testConnection(JdbcConnectionTestRequest request) {
        Objects.requireNonNull(request, "request");
        long start = System.nanoTime();
        String url = StringUtils.trimWhitespace(request.getJdbcUrl());
        if (!StringUtils.hasText(url)) {
            return failure("JDBC URL 不能为空", 0L);
        }

        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader jdbcLoader = resolveJdbcDriverClassLoader(request.getDriverVersion());
        if (jdbcLoader != null) {
            Thread.currentThread().setContextClassLoader(jdbcLoader);
        }
        try {
            try {
                DriverManager.setLoginTimeout(Math.max(1, loginTimeoutSeconds));
            } catch (Throwable ignored) {}

            String driverClass = StringUtils.trimWhitespace(request.getDriverClass());
            if (!StringUtils.hasText(driverClass)) {
                driverClass = inferDriverClass(url);
            }

            if (StringUtils.hasText(driverClass)) {
                try {
                    Class<?> driverClazz = jdbcLoader != null
                        ? Class.forName(driverClass, true, jdbcLoader)
                        : Class.forName(driverClass);
                    registerDriverIfNeeded(driverClazz);
                } catch (Throwable ex) {
                    LOG.warn("Failed to load JDBC driver class {}: {}", driverClass, ex.getMessage());
                }
            }

            Properties props = new Properties();
            if (StringUtils.hasText(request.getUsername())) {
                props.setProperty("user", request.getUsername());
            }
            if (StringUtils.hasText(request.getPassword())) {
                props.setProperty("password", request.getPassword());
            }
            if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:") && !props.containsKey("connectTimeout")) {
                props.setProperty("connectTimeout", "5");
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
                HiveConnectionTestResult result = new HiveConnectionTestResult();
                result.setSuccess(true);
                result.setMessage("连接成功");
                result.setElapsedMillis(connectMillis);
                result.setEngineVersion(safe(meta::getDatabaseProductVersion));
                result.setDriverVersion(safe(meta::getDriverVersion));
                result.setWarnings(collectWarnings(connection.getWarnings()));
                return result;
            }
        } catch (Exception ex) {
            LOG.debug("JDBC connection test failed. url={}, driverClass={}", url, request.getDriverClass(), ex);
            return failure(sanitizeMessage(ex), elapsedMillis(start));
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    private ClassLoader resolveJdbcDriverClassLoader(String driverHint) {
        Path dir = driverCatalogService.resolveDriversDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        List<Path> jars = listDriverJars(dir);
        if (StringUtils.hasText(driverHint)) {
            Path jar = resolveDriverJar(dir, driverHint);
            if (jar == null) {
                throw new IllegalArgumentException("未找到匹配的驱动文件: " + driverHint);
            }
            jars = prioritizeJar(jars, jar);
        }
        if (jars.isEmpty()) {
            return null;
        }
        try {
            URL[] urls = jars.stream()
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(URL[]::new);
            if (urls.length == 0) {
                return null;
            }
            return new URLClassLoader(urls, getPlatformOrSystemClassLoader());
        } catch (Exception ex) {
            LOG.warn("Failed to build aggregate JDBC classloader: {}", ex.getMessage());
            return null;
        }
    }

    private List<Path> listDriverJars(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (Exception ex) {
            LOG.warn("Failed to list driver jars under {}: {}", dir, ex.getMessage());
            return List.of();
        }
    }

    private Path resolveDriverJar(Path dir, String driverHint) {
        String normalized = driverHint.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        List<Path> matches = listDriverJars(dir).stream()
            .filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".jar")) {
                    return name.equals(lower);
                }
                return name.contains(lower);
            })
            .toList();
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            LOG.warn("Multiple driver jars match '{}': {}", driverHint, matches);
        }
        return matches.get(0);
    }

    private List<Path> prioritizeJar(List<Path> jars, Path preferred) {
        if (jars == null || jars.isEmpty()) {
            return List.of(preferred);
        }
        List<Path> ordered = new ArrayList<>(jars.size() + 1);
        ordered.add(preferred);
        for (Path jar : jars) {
            if (!jar.equals(preferred)) {
                ordered.add(jar);
            }
        }
        return ordered;
    }

    private static String inferDriverClass(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) return null;
        String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (url.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (url.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (url.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (url.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (url.startsWith("jdbc:hive2:") || url.startsWith("jdbc:inceptor") || url.startsWith("jdbc:inceptor2:")) {
            return "org.apache.hive.jdbc.HiveDriver";
        }
        return null;
    }

    private void registerDriverIfNeeded(Class<?> driverClazz) throws Exception {
        if (driverClazz == null || !Driver.class.isAssignableFrom(driverClazz)) {
            return;
        }
        ClassLoader loader = driverClazz.getClassLoader();
        String driverName = driverClazz.getName();
        String key = driverName + "@" + Integer.toHexString(System.identityHashCode(loader));
        if (!REGISTERED_DRIVERS.add(key)) {
            return;
        }
        try {
            Driver driver = (Driver) driverClazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
        } catch (SQLException ex) {
            REGISTERED_DRIVERS.remove(key);
            throw ex;
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

    private static final class DriverShim implements Driver {
        private final Driver delegate;

        private DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private HiveConnectionTestResult failure(String message, long elapsedMillis) {
        HiveConnectionTestResult result = new HiveConnectionTestResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setElapsedMillis(elapsedMillis);
        result.setWarnings(List.of());
        return result;
    }

    private static ClassLoader getPlatformOrSystemClassLoader() {
        try {
            return ClassLoader.getPlatformClassLoader();
        } catch (Throwable ignored) {
            return ClassLoader.getSystemClassLoader();
        }
    }

    private String sanitizeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        StringBuilder sb = new StringBuilder();
        if (throwable instanceof SQLException se) {
            if (se.getSQLState() != null && !se.getSQLState().isBlank()) {
                sb.append("SQLState=").append(se.getSQLState()).append("; ");
            }
            if (se.getErrorCode() != 0) {
                sb.append("ErrorCode=").append(se.getErrorCode()).append("; ");
            }
        }
        sb.append(oneLine(throwable));
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && cause != throwable && depth++ < 5) {
            String causeLine = oneLine(cause);
            if (!causeLine.isBlank() && sb.indexOf(causeLine) < 0) {
                sb.append(" | Caused by: ").append(causeLine);
            }
            cause = cause.getCause();
        }
        String msg = sb.toString();
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }

    private static String oneLine(Throwable t) {
        String msg = Optional.ofNullable(t.getMessage()).orElse("");
        msg = msg.replaceAll("\\s+", " ").trim();
        if (msg.isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }
}
