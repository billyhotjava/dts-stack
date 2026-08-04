package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import com.yuzhi.dts.platform.service.sql.dto.TableInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 获取数据源元数据（表、列等）
 */
@Service
public class SqlMetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlMetadataService.class);
    private static final String[] TABLE_TYPES = {"TABLE", "VIEW"};
    private static final String TABLE_CACHE_NAME = "sqlIdeTables";
    private static final String COLUMN_CACHE_NAME = "sqlIdeColumns";
    private static final String TABLE_CACHE_KEY_PREFIX = "metadata:tables:";
    private static final String COLUMN_CACHE_KEY_PREFIX = "metadata:columns:";

    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final AdminInfraClient adminInfraClient;
    private final DataSourceAccessGuard accessGuard;
    private final CacheManager cacheManager;

    public SqlMetadataService(
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        AdminInfraClient adminInfraClient,
        DataSourceAccessGuard accessGuard,
        CacheManager cacheManager
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.adminInfraClient = adminInfraClient;
        this.accessGuard = accessGuard;
        this.cacheManager = cacheManager;
    }

    /**
     * 列出数据源中的所有表。结果由 {@code sqlIdeTables} 缓存承载（TTL 5 分钟，
     * 见 {@code CacheConfiguration.buildSqlIdeMapConfig}）。依据元数据命名空间与 datasourceId 作 key，
     * activeDept 仅影响权限校验不影响列表内容。空结果不进入缓存，避免数据源初始化或建表前的
     * 瞬时空清单遮蔽后续创建的表。
     */
    public List<TableInfo> listTables(UUID datasourceId, String activeDept) {
        accessGuard.assertReadable(datasourceId, activeDept);
        String cacheKey = TABLE_CACHE_KEY_PREFIX + datasourceId;
        List<TableInfo> cached = getCachedList(TABLE_CACHE_NAME, cacheKey);
        if (cached != null) {
            return cached;
        }
        JdbcConnectionTarget target = resolveConnectionTarget(datasourceId);

        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password())) {
            DatabaseMetaData meta = conn.getMetaData();

            // 获取所有 schema
            List<String> schemas = new ArrayList<>();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    // 过滤系统 schema
                    if (shouldIncludeSchema(schemaName, target.type())) {
                        schemas.add(schemaName);
                    }
                }
            }

            // 如果没有 schema，尝试用 null
            if (schemas.isEmpty()) {
                schemas.add(null);
            }

            // 获取每个 schema 的表
            for (String schema : schemas) {
                try (ResultSet rs = meta.getTables(null, schema, "%", TABLE_TYPES)) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        String tableType = rs.getString("TABLE_TYPE");
                        String tableSchema = rs.getString("TABLE_SCHEM");

                        // 过滤系统表
                        if (shouldIncludeTable(tableName, tableSchema, target.type())) {
                            tables.add(new TableInfo(
                                tableSchema != null ? tableSchema : "public",
                                tableName,
                                tableType,
                                null
                            ));
                        }
                    }
                }
            }

            LOG.debug("Listed {} tables from datasource {}", tables.size(), target.name());
        } catch (SQLException ex) {
            LOG.error("Failed to list tables from datasource {}: {}", target.name(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "获取表列表失败: " + ex.getMessage());
        }

        if (!tables.isEmpty()) {
            cacheList(TABLE_CACHE_NAME, cacheKey, tables);
        }
        return tables;
    }

    /**
     * Searches JDBC metadata only. The keyword may match schema/table identity or a column name;
     * no business rows are read.
     */
    public List<TableInfo> searchTables(UUID datasourceId, String activeDept, String keyword, Integer requestedLimit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int limit = Math.max(1, Math.min(requestedLimit == null ? 20 : requestedLimit, 50));
        List<TableInfo> allTables = listTables(datasourceId, activeDept);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return allTables.stream().limit(limit).toList();
        }

        Map<String, TableInfo> tablesByIdentity = new LinkedHashMap<>();
        for (TableInfo table : allTables) {
            tablesByIdentity.put(tableIdentity(table.schema(), table.name()), table);
        }

        Set<String> matchedIdentities = new LinkedHashSet<>();
        for (TableInfo table : allTables) {
            if (tableIdentity(table.schema(), table.name()).contains(normalizedKeyword)) {
                matchedIdentities.add(tableIdentity(table.schema(), table.name()));
                if (matchedIdentities.size() >= limit) {
                    return resolveMatchedTables(matchedIdentities, tablesByIdentity);
                }
            }
        }

        JdbcConnectionTarget target = resolveConnectionTarget(datasourceId);
        try (Connection conn = DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password())) {
            DatabaseMetaData meta = conn.getMetaData();
            Set<String> schemas = new LinkedHashSet<>();
            allTables.forEach(table -> schemas.add(table.schema()));
            for (String schema : schemas) {
                try (ResultSet rs = meta.getColumns(null, schema, "%", "%")) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        if (columnName == null || !columnName.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                            continue;
                        }
                        String identity = tableIdentity(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"));
                        if (tablesByIdentity.containsKey(identity)) {
                            matchedIdentities.add(identity);
                        }
                        if (matchedIdentities.size() >= limit) {
                            return resolveMatchedTables(matchedIdentities, tablesByIdentity);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.error("Failed to search table metadata from datasource {}: {}", target.name(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "搜索表元数据失败");
        }
        return resolveMatchedTables(matchedIdentities, tablesByIdentity);
    }

    /**
     * 列出表的列信息
     */
    public List<Map<String, Object>> listColumns(UUID datasourceId, String schema, String tableName, String activeDept) {
        accessGuard.assertReadable(datasourceId, activeDept);
        String cacheKey = COLUMN_CACHE_KEY_PREFIX + datasourceId + ":" + schema + "." + tableName;
        List<Map<String, Object>> cached = getCachedList(COLUMN_CACHE_NAME, cacheKey);
        if (cached != null) {
            return cached;
        }
        JdbcConnectionTarget target = resolveConnectionTarget(datasourceId);

        List<Map<String, Object>> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password())) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("nullable", "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    column.put("description", rs.getString("REMARKS"));
                    column.put("defaultValue", rs.getString("COLUMN_DEF"));
                    column.put("autoIncrement", "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                    column.put("ordinalPosition", rs.getInt("ORDINAL_POSITION"));
                    column.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    column.put("decimalDigits", rs.getInt("DECIMAL_DIGITS"));
                    columns.add(column);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Failed to list columns for {}.{}: {}", schema, tableName, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "获取列信息失败: " + ex.getMessage());
        }

        if (!columns.isEmpty()) {
            cacheList(COLUMN_CACHE_NAME, cacheKey, columns);
        }
        return columns;
    }

    private List<TableInfo> resolveMatchedTables(Set<String> identities, Map<String, TableInfo> tablesByIdentity) {
        return identities.stream().map(tablesByIdentity::get).filter(java.util.Objects::nonNull).toList();
    }

    private String tableIdentity(String schema, String table) {
        String effectiveSchema = StringUtils.hasText(schema) ? schema : "public";
        return (effectiveSchema + "." + table).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getCachedList(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper value = cache.get(key);
        if (value == null || !(value.get() instanceof List<?> list)) {
            return null;
        }
        return (List<T>) list;
    }

    private void cacheList(String cacheName, Object key, List<?> value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private JdbcConnectionTarget resolveConnectionTarget(UUID datasourceId) {
        InfraDataSource local = dataSourceRepository.findById(datasourceId).orElse(null);
        if (local != null) {
            if (!StringUtils.hasText(local.getJdbcUrl()) || !StringUtils.hasText(local.getUsername())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源配置不完整");
            }
            Map<String, Object> secrets = secretService.readSecrets(local);
            String password = secrets.get("password") != null ? secrets.get("password").toString() : null;
            return new JdbcConnectionTarget(local.getName(), local.getType(), local.getJdbcUrl(), local.getUsername(), password);
        }

        AdminInfraClient.AdminDataLakeConfig lake = adminInfraClient
            .fetchDefaultDataLake()
            .filter(cfg -> cfg.getId() != null && cfg.getId().equals(datasourceId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));

        if (!StringUtils.hasText(lake.getJdbcUrl()) || !StringUtils.hasText(lake.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源配置不完整");
        }
        return new JdbcConnectionTarget(
            lake.getName(),
            lake.getType(),
            lake.getJdbcUrl(),
            lake.getUsername(),
            StringUtils.hasText(lake.getPassword()) ? lake.getPassword() : null
        );
    }

    private record JdbcConnectionTarget(String name, String type, String jdbcUrl, String username, String password) {}

    private boolean shouldIncludeSchema(String schemaName, String dbType) {
        if (schemaName == null) {
            return true;
        }
        String lower = schemaName.toLowerCase();

        // PostgreSQL 系统 schema
        if ("postgres".equalsIgnoreCase(dbType)) {
            return !lower.startsWith("pg_") && !lower.equals("information_schema");
        }

        // 通用过滤
        return !lower.equals("information_schema") && !lower.equals("sys");
    }

    private boolean shouldIncludeTable(String tableName, String schemaName, String dbType) {
        if (tableName == null) {
            return false;
        }
        String lower = tableName.toLowerCase();

        // 过滤 PostgreSQL 系统表
        if ("postgres".equalsIgnoreCase(dbType)) {
            if (schemaName != null && schemaName.toLowerCase().startsWith("pg_")) {
                return false;
            }
        }

        // 通用过滤
        return !lower.startsWith("pg_") && !lower.startsWith("sql_");
    }
}
