package com.yuzhi.dts.ingestion.service.etl;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JdbcMetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcMetadataService.class);
    private static final Set<String> REGISTERED_DRIVERS = ConcurrentHashMap.newKeySet();
    /** Cache URLClassLoaders by JAR path to prevent resource leaks from repeated creation. */
    private static final ConcurrentMap<String, URLClassLoader> DRIVER_CLASSLOADER_CACHE = new ConcurrentHashMap<>();

    public record JdbcConnectionInfo(
        String jdbcUrl,
        String username,
        String password,
        String driverClass,
        String driverVersion,
        Map<String, String> jdbcProperties
    ) {}

    public record ColumnMeta(String name, int jdbcType, String typeName, Integer columnSize, Integer decimalDigits) {}

    public record TableMeta(String schema, String name, String type) {}

    public List<TableMeta> listTables(JdbcConnectionInfo info, String schemaPattern, String tablePattern, Integer limit) {
        if (info == null || !StringUtils.hasText(info.jdbcUrl())) {
            return List.of();
        }
        int max;
        if (limit == null) {
            max = 200;
        } else if (limit <= 0) {
            max = Integer.MAX_VALUE;
        } else {
            max = limit;
        }
        String schema = normalize(schemaPattern);
        String table = normalize(tablePattern);
        try (Connection connection = openConnection(info)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<TableMeta> tables = readTables(meta, schema, table, max);
            if (tables.isEmpty()) {
                String schemaUpper = schema == null ? null : schema.toUpperCase(Locale.ROOT);
                String tableUpper = table == null ? null : table.toUpperCase(Locale.ROOT);
                tables = readTables(meta, schemaUpper, tableUpper, max);
            }
            if (tables.isEmpty()) {
                String schemaLower = schema == null ? null : schema.toLowerCase(Locale.ROOT);
                String tableLower = table == null ? null : table.toLowerCase(Locale.ROOT);
                tables = readTables(meta, schemaLower, tableLower, max);
            }
            return tables;
        } catch (Exception ex) {
            LOG.warn("Failed to list tables: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<ColumnMeta> getTableColumns(JdbcConnectionInfo info, String tableName) {
        if (info == null || !StringUtils.hasText(info.jdbcUrl()) || !StringUtils.hasText(tableName)) {
            return List.of();
        }
        TableId tableId = TableId.parse(tableName);
        try (Connection connection = openConnection(info)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<ColumnMeta> columns = readColumns(meta, tableId.schema(), tableId.table());
            if (columns.isEmpty()) {
                columns = readColumns(meta, tableId.schema(), tableId.table().toUpperCase(Locale.ROOT));
            }
            if (columns.isEmpty()) {
                columns = readColumns(meta, tableId.schema(), tableId.table().toLowerCase(Locale.ROOT));
            }
            return columns;
        } catch (Exception ex) {
            LOG.warn("Failed to fetch columns for {}: {}", tableName, ex.getMessage());
            return List.of();
        }
    }

    public Connection openConnection(JdbcConnectionInfo info) throws Exception {
        if (info == null || !StringUtils.hasText(info.jdbcUrl())) {
            throw new IllegalArgumentException("JDBC url is required");
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader driverLoader = resolveDriverClassLoader(info);
        if (driverLoader != null) {
            Thread.currentThread().setContextClassLoader(driverLoader);
        }
        try {
            if (StringUtils.hasText(info.driverClass())) {
                Class<?> driverClazz = driverLoader != null
                    ? Class.forName(info.driverClass(), true, driverLoader)
                    : Class.forName(info.driverClass());
                registerDriverIfNeeded(driverClazz);
            }
            Properties props = new Properties();
            if (StringUtils.hasText(info.username())) {
                props.setProperty("user", info.username());
            }
            if (StringUtils.hasText(info.password())) {
                props.setProperty("password", info.password());
            }
            if (info.jdbcProperties() != null) {
                info.jdbcProperties().forEach((k, v) -> {
                    if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                        props.setProperty(k, v);
                    }
                });
            }
            return props.isEmpty() ? DriverManager.getConnection(info.jdbcUrl()) : DriverManager.getConnection(info.jdbcUrl(), props);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private List<ColumnMeta> readColumns(DatabaseMetaData meta, String schema, String table) throws SQLException {
        List<ColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                Integer size = rs.getInt("COLUMN_SIZE");
                Integer scale = rs.getInt("DECIMAL_DIGITS");
                if (StringUtils.hasText(name)) {
                    columns.add(new ColumnMeta(name, jdbcType, typeName, size, scale));
                }
            }
        }
        return columns;
    }

    private List<TableMeta> readTables(DatabaseMetaData meta, String schema, String table, int limit) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, schema, table, new String[] { "TABLE" })) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!StringUtils.hasText(tableName)) {
                    continue;
                }
                String tableSchema = rs.getString("TABLE_SCHEM");
                String type = rs.getString("TABLE_TYPE");
                tables.add(new TableMeta(tableSchema, tableName, type));
                if (tables.size() >= limit) {
                    break;
                }
            }
        }
        return tables;
    }

    private ClassLoader resolveDriverClassLoader(JdbcConnectionInfo info) {
        Path jar = resolveDriverJar(info);
        if (jar == null) {
            return null;
        }
        String cacheKey = jar.toAbsolutePath().toString();
        return DRIVER_CLASSLOADER_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                URL[] urls = new URL[] { jar.toUri().toURL() };
                return new URLClassLoader(urls, getPlatformOrSystemClassLoader());
            } catch (Exception ex) {
                LOG.warn("Failed to load JDBC driver jar {}: {}", jar, ex.getMessage());
                return null;
            }
        });
    }

    private Path resolveDriverJar(JdbcConnectionInfo info) {
        String driverDir = resolveDriverDir();
        if (!StringUtils.hasText(driverDir) || !Files.isDirectory(Path.of(driverDir))) {
            return null;
        }
        Path baseDir = Path.of(driverDir);
        String version = normalize(info.driverVersion());
        if (StringUtils.hasText(version)) {
            Path direct = Path.of(version);
            if (Files.exists(direct)) {
                return direct;
            }
            Path candidate = baseDir.resolve(version);
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path matched = findJarContaining(baseDir, version);
            if (matched != null) {
                return matched;
            }
        }
        String driverClass = normalize(info.driverClass());
        String key = inferDriverKey(driverClass);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String jarList = System.getenv("ADDAX_DRIVER_JARS");
        if (StringUtils.hasText(jarList)) {
            for (String jarName : jarList.split(",")) {
                String name = normalize(jarName);
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).contains(key)) {
                    Path candidate = baseDir.resolve(name);
                    if (Files.exists(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return findJarContaining(baseDir, key);
    }

    private Path findJarContaining(Path baseDir, String keyword) {
        try {
            String lower = keyword.toLowerCase(Locale.ROOT);
            try (var stream = Files.list(baseDir)) {
                return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(lower))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveDriverDir() {
        String dir = normalize(System.getenv("ADDAX_DRIVER_DIR"));
        if (StringUtils.hasText(dir)) {
            return dir;
        }
        dir = normalize(System.getenv("DTS_DRIVER_DIR"));
        if (StringUtils.hasText(dir)) {
            return dir;
        }
        String stackRoot = normalize(System.getenv("STACK_ROOT"));
        if (StringUtils.hasText(stackRoot)) {
            return stackRoot + "/services/dts-platform/drivers";
        }
        return null;
    }

    private String inferDriverKey(String driverClass) {
        if (!StringUtils.hasText(driverClass)) {
            return null;
        }
        String lower = driverClass.toLowerCase(Locale.ROOT);
        if (lower.contains("dm")) {
            return "dm";
        }
        if (lower.contains("postgresql")) {
            return "postgresql";
        }
        if (lower.contains("mysql")) {
            return "mysql";
        }
        if (lower.contains("mariadb")) {
            return "mariadb";
        }
        if (lower.contains("oracle")) {
            return "oracle";
        }
        if (lower.contains("sqlserver")) {
            return "sqlserver";
        }
        if (lower.contains("clickhouse")) {
            return "clickhouse";
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
        Driver driver = (Driver) driverClazz.getDeclaredConstructor().newInstance();
        DriverManager.registerDriver(new DriverShim(driver));
    }

    private static ClassLoader getPlatformOrSystemClassLoader() {
        try {
            return ClassLoader.getPlatformClassLoader();
        } catch (Throwable ignored) {
            return ClassLoader.getSystemClassLoader();
        }
    }

    private record TableId(String schema, String table) {
        static TableId parse(String raw) {
            if (!StringUtils.hasText(raw)) {
                return new TableId(null, raw);
            }
            String trimmed = raw.trim();
            int idx = trimmed.indexOf('.');
            if (idx > 0 && idx < trimmed.length() - 1) {
                String schema = trimmed.substring(0, idx).trim();
                String table = trimmed.substring(idx + 1).trim();
                return new TableId(schema, table);
            }
            return new TableId(null, trimmed);
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
}
