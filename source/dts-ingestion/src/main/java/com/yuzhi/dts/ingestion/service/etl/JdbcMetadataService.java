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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    public record ColumnMeta(
        String name,
        int jdbcType,
        String typeName,
        Integer columnSize,
        Integer decimalDigits,
        Boolean nullable,
        String defaultValue,
        String comment,
        Integer ordinalPosition
    ) {
        public ColumnMeta(String name, int jdbcType, String typeName, Integer columnSize, Integer decimalDigits) {
            this(name, jdbcType, typeName, columnSize, decimalDigits, null, null, null, null);
        }

        public ColumnMeta withName(String newName) {
            return new ColumnMeta(
                newName,
                jdbcType,
                typeName,
                columnSize,
                decimalDigits,
                nullable,
                defaultValue,
                comment,
                ordinalPosition
            );
        }
    }

    public record TableMeta(String schema, String name, String type, String comment) {
        public TableMeta(String schema, String name, String type) {
            this(schema, name, type, null);
        }
    }

    public record IndexMeta(String name, boolean unique, List<String> columns) {}

    public static final class MetadataDiscoveryException extends RuntimeException {

        private final String code;

        private MetadataDiscoveryException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public static MetadataDiscoveryException authenticationFailure(Throwable cause) {
            return new MetadataDiscoveryException(
                "JDBC_METADATA_AUTH_FAILED",
                "数据库认证失败，请检查用户名、密码及来源 IP 授权",
                cause
            );
        }

        public static MetadataDiscoveryException connectionFailure(Throwable cause) {
            return new MetadataDiscoveryException(
                "JDBC_METADATA_CONNECTION_FAILED",
                "数据库连接失败，请检查地址、端口、网络及数据库服务状态",
                cause
            );
        }

        public String getCode() {
            return code;
        }
    }

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
            if (!StringUtils.hasText(schema) && usesCatalogForDatabase(meta)) {
                schema = normalize(connection.getCatalog());
            }
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
            throw metadataFailure("列举表", ex);
        }
    }

    private static MetadataDiscoveryException metadataFailure(String operation, Exception error) {
        if (error instanceof MetadataDiscoveryException failure) {
            return failure;
        }
        SQLException sqlException = findSqlException(error);
        MetadataDiscoveryException failure;
        if (isDriverLoadingFailure(error)) {
            failure = new MetadataDiscoveryException(
                "JDBC_METADATA_DRIVER_FAILED",
                "数据库驱动加载失败，请检查接入服务的驱动配置与驱动文件",
                error
            );
        } else if (isAuthenticationFailure(sqlException)) {
            failure = MetadataDiscoveryException.authenticationFailure(error);
        } else if (sqlException != null && StringUtils.hasText(sqlException.getSQLState())
            && !sqlException.getSQLState().startsWith("08")) {
            failure = new MetadataDiscoveryException(
                "JDBC_METADATA_READ_FAILED",
                "数据库元数据读取失败（" + operation + "），请检查数据库、表名与元数据访问权限",
                error
            );
        } else {
            failure = MetadataDiscoveryException.connectionFailure(error);
        }
        LOG.warn(
            "JDBC metadata failure operation={} code={} sqlState={} vendorCode={} exceptionType={}",
            operation,
            failure.getCode(),
            sqlException == null ? "unknown" : sqlException.getSQLState(),
            sqlException == null ? 0 : sqlException.getErrorCode(),
            error.getClass().getSimpleName()
        );
        return failure;
    }

    private static boolean isDriverLoadingFailure(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof ReflectiveOperationException
                || (current instanceof SQLException && current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("no suitable driver"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static SQLException findSqlException(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isAuthenticationFailure(SQLException error) {
        if (error == null) {
            return false;
        }
        String sqlState = error.getSQLState();
        return (sqlState != null && sqlState.startsWith("28")) || error.getErrorCode() == 1045;
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
            throw metadataFailure("读取字段", ex);
        }
    }

    public List<String> getPrimaryKeyColumns(JdbcConnectionInfo info, String tableName) {
        if (info == null || !StringUtils.hasText(info.jdbcUrl()) || !StringUtils.hasText(tableName)) {
            return List.of();
        }
        TableId tableId = TableId.parse(tableName);
        try (Connection connection = openConnection(info)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<String> columns = readPrimaryKeyColumns(meta, tableId.schema(), tableId.table());
            if (columns.isEmpty()) {
                columns = readPrimaryKeyColumns(meta, tableId.schema(), tableId.table().toUpperCase(Locale.ROOT));
            }
            if (columns.isEmpty()) {
                columns = readPrimaryKeyColumns(meta, tableId.schema(), tableId.table().toLowerCase(Locale.ROOT));
            }
            return columns;
        } catch (Exception ex) {
            LOG.warn("Failed to fetch primary key for {}: {}", tableName, ex.getMessage());
            return List.of();
        }
    }

    public List<IndexMeta> getTableIndexes(JdbcConnectionInfo info, String tableName) {
        if (info == null || !StringUtils.hasText(info.jdbcUrl()) || !StringUtils.hasText(tableName)) {
            return List.of();
        }
        TableId tableId = TableId.parse(tableName);
        try (Connection connection = openConnection(info)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<IndexMeta> indexes = readIndexes(meta, tableId.schema(), tableId.table());
            if (indexes.isEmpty()) {
                indexes = readIndexes(meta, tableId.schema(), tableId.table().toUpperCase(Locale.ROOT));
            }
            if (indexes.isEmpty()) {
                indexes = readIndexes(meta, tableId.schema(), tableId.table().toLowerCase(Locale.ROOT));
            }
            return indexes;
        } catch (Exception ex) {
            LOG.warn("Failed to fetch indexes for {}: {}", tableName, ex.getMessage());
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
        MetadataScope scope = metadataScope(meta, schema);
        try (ResultSet rs = meta.getColumns(scope.catalog(), scope.schema(), table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                Integer size = rs.getInt("COLUMN_SIZE");
                Integer scale = rs.getInt("DECIMAL_DIGITS");
                int nullableValue = rs.getInt("NULLABLE");
                Boolean nullable = rs.wasNull() ? null : nullableValue == DatabaseMetaData.columnNullable;
                String defaultValue = rs.getString("COLUMN_DEF");
                String comment = rs.getString("REMARKS");
                Integer ordinal = rs.getInt("ORDINAL_POSITION");
                if (rs.wasNull()) {
                    ordinal = null;
                }
                if (StringUtils.hasText(name)) {
                    columns.add(new ColumnMeta(name, jdbcType, typeName, size, scale, nullable, defaultValue, comment, ordinal));
                }
            }
        }
        return columns;
    }

    private List<String> readPrimaryKeyColumns(DatabaseMetaData meta, String schema, String table) throws SQLException {
        List<KeyColumn> columns = new ArrayList<>();
        MetadataScope scope = metadataScope(meta, schema);
        try (ResultSet rs = meta.getPrimaryKeys(scope.catalog(), scope.schema(), table)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                short seq = rs.getShort("KEY_SEQ");
                if (StringUtils.hasText(column)) {
                    columns.add(new KeyColumn(seq, column));
                }
            }
        }
        return columns.stream()
            .sorted(Comparator.comparingInt(KeyColumn::sequence))
            .map(KeyColumn::name)
            .toList();
    }

    private List<IndexMeta> readIndexes(DatabaseMetaData meta, String schema, String table) throws SQLException {
        Map<String, IndexBuilder> builders = new LinkedHashMap<>();
        MetadataScope scope = metadataScope(meta, schema);
        try (ResultSet rs = meta.getIndexInfo(scope.catalog(), scope.schema(), table, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (!StringUtils.hasText(indexName) || !StringUtils.hasText(columnName)) {
                    continue;
                }
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                short ordinal = rs.getShort("ORDINAL_POSITION");
                IndexBuilder builder = builders.computeIfAbsent(indexName, key -> new IndexBuilder(indexName, !nonUnique));
                builder.columns.add(new KeyColumn(ordinal, columnName));
            }
        }
        return builders.values().stream()
            .map(IndexBuilder::toMeta)
            .filter(index -> !index.columns().isEmpty())
            .toList();
    }

    private record KeyColumn(int sequence, String name) {}

    private static final class IndexBuilder {
        private final String name;
        private final boolean unique;
        private final List<KeyColumn> columns = new ArrayList<>();

        private IndexBuilder(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        private IndexMeta toMeta() {
            return new IndexMeta(
                name,
                unique,
                columns.stream()
                    .sorted(Comparator.comparingInt(KeyColumn::sequence))
                    .map(KeyColumn::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    .stream()
                    .toList()
            );
        }
    }

    private List<TableMeta> readTables(DatabaseMetaData meta, String schema, String table, int limit) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();
        MetadataScope scope = metadataScope(meta, schema);
        try (ResultSet rs = meta.getTables(scope.catalog(), scope.schema(), table, new String[] { "TABLE" })) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!StringUtils.hasText(tableName)) {
                    continue;
                }
                String tableSchema = rs.getString("TABLE_SCHEM");
                if (!StringUtils.hasText(tableSchema)) {
                    tableSchema = rs.getString("TABLE_CAT");
                }
                String type = rs.getString("TABLE_TYPE");
                tables.add(new TableMeta(tableSchema, tableName, type, normalize(rs.getString("REMARKS"))));
                if (tables.size() >= limit) {
                    break;
                }
            }
        }
        return tables;
    }

    private record MetadataScope(String catalog, String schema) {}

    private MetadataScope metadataScope(DatabaseMetaData meta, String database) throws SQLException {
        if (usesCatalogForDatabase(meta)) {
            String catalog = normalize(database);
            if (!StringUtils.hasText(catalog)) {
                catalog = normalize(meta.getConnection().getCatalog());
            }
            return new MetadataScope(catalog, null);
        }
        return new MetadataScope(null, database);
    }

    private boolean usesCatalogForDatabase(DatabaseMetaData meta) throws SQLException {
        String productName = normalize(meta.getDatabaseProductName());
        if (!StringUtils.hasText(productName)) {
            return false;
        }
        String normalized = productName.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
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
