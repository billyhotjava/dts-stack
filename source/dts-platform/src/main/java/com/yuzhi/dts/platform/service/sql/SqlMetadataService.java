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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
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

    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final AdminInfraClient adminInfraClient;
    private final DataSourceAccessGuard accessGuard;

    public SqlMetadataService(
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        AdminInfraClient adminInfraClient,
        DataSourceAccessGuard accessGuard
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.adminInfraClient = adminInfraClient;
        this.accessGuard = accessGuard;
    }

    /**
     * 列出数据源中的所有表。结果由 {@code sqlIdeTables} 缓存承载（TTL 5 分钟，
     * 见 {@code CacheConfiguration.buildSqlIdeMapConfig}）。依据 datasourceId 单独作 key，
     * activeDept 仅影响权限校验不影响列表内容。
     */
    @Cacheable(cacheNames = "sqlIdeTables", key = "#datasourceId")
    public List<TableInfo> listTables(UUID datasourceId, String activeDept) {
        accessGuard.assertReadable(datasourceId, activeDept);
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

        return tables;
    }

    /**
     * 列出表的列信息
     */
    @Cacheable(cacheNames = "sqlIdeColumns", key = "#datasourceId + ':' + #schema + '.' + #tableName")
    public List<Map<String, String>> listColumns(UUID datasourceId, String schema, String tableName, String activeDept) {
        accessGuard.assertReadable(datasourceId, activeDept);
        JdbcConnectionTarget target = resolveConnectionTarget(datasourceId);

        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password())) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, tableName, "%")) {
                while (rs.next()) {
                    columns.add(Map.of(
                        "name", rs.getString("COLUMN_NAME"),
                        "type", rs.getString("TYPE_NAME"),
                        "nullable", rs.getString("IS_NULLABLE")
                    ));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Failed to list columns for {}.{}: {}", schema, tableName, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "获取列信息失败: " + ex.getMessage());
        }

        return columns;
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
