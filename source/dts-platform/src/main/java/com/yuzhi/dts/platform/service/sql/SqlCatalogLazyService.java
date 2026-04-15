package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.sql.dto.CatalogColumnDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogDatasourceDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSchemaDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSearchHitDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogTableDto;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catalog browser service backed by live DatabaseMetaData from JDBC connections.
 * Datasources are resolved from {@code infra_data_source} — no hardcoded placeholders.
 */
@Service
@Transactional(readOnly = true)
public class SqlCatalogLazyService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlCatalogLazyService.class);

    private static final String[] TABLE_TYPES = { "TABLE", "VIEW" };
    private static final java.util.Set<String> SKIP_SCHEMAS = java.util.Set.of("pg_catalog", "information_schema");

    private final InfraDataSourceRepository datasourceRepository;
    private final JdbcSqlExecutor jdbcSqlExecutor;

    public SqlCatalogLazyService(
        InfraDataSourceRepository datasourceRepository,
        JdbcSqlExecutor jdbcSqlExecutor
    ) {
        this.datasourceRepository = datasourceRepository;
        this.jdbcSqlExecutor = jdbcSqlExecutor;
    }

    public List<CatalogDatasourceDto> listDatasources() {
        return datasourceRepository
            .findAll()
            .stream()
            .map(ds -> new CatalogDatasourceDto(
                ds.getId().toString(),
                ds.getName(),
                ds.getType() == null ? "jdbc" : ds.getType().toLowerCase(Locale.ROOT),
                ds.getName()
            ))
            .toList();
    }

    @Cacheable(cacheNames = "sqlIdeSchemas", key = "#datasourceId")
    public List<CatalogSchemaDto> listSchemas(String datasourceId) {
        InfraDataSource ds = resolveDatasource(datasourceId);
        try (Connection conn = jdbcSqlExecutor.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<CatalogSchemaDto> schemas = new ArrayList<>();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    if (!StringUtils.hasText(schema)) {
                        continue;
                    }
                    if (SKIP_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    schemas.add(new CatalogSchemaDto(schema, null));
                }
            }
            return schemas;
        } catch (SQLException ex) {
            LOG.warn("listSchemas failed datasourceId={}: {}", datasourceId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "获取 schema 列表失败: " + ex.getMessage(), ex);
        }
    }

    @Cacheable(cacheNames = "sqlIdeTables", key = "#datasourceId + ':' + #schema")
    public List<CatalogTableDto> listTables(String datasourceId, String schema) {
        InfraDataSource ds = resolveDatasource(datasourceId);
        try (Connection conn = jdbcSqlExecutor.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            String schemaPattern = StringUtils.hasText(schema) ? schema : null;
            List<CatalogTableDto> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, schemaPattern, "%", TABLE_TYPES)) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    tables.add(new CatalogTableDto(name, type, remarks, null));
                }
            }
            return tables;
        } catch (SQLException ex) {
            LOG.warn("listTables failed datasourceId={} schema={}: {}", datasourceId, schema, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "获取表列表失败: " + ex.getMessage(), ex);
        }
    }

    @Cacheable(cacheNames = "sqlIdeColumns", key = "#datasourceId + ':' + #schemaTable")
    public List<CatalogColumnDto> listColumns(String datasourceId, String schemaTable) {
        if (!StringUtils.hasText(schemaTable)) {
            return List.of();
        }
        String[] parts = schemaTable.split("\\.", 2);
        String schema = parts.length == 2 ? parts[0] : null;
        String table = parts.length == 2 ? parts[1] : parts[0];

        InfraDataSource ds = resolveDatasource(datasourceId);
        try (Connection conn = jdbcSqlExecutor.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<CatalogColumnDto> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(null, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int ordinal = rs.getInt("ORDINAL_POSITION");
                    String nullableStr = rs.getString("IS_NULLABLE");
                    boolean nullable = !"NO".equalsIgnoreCase(nullableStr);
                    String remarks = rs.getString("REMARKS");
                    columns.add(new CatalogColumnDto(name, typeName, nullable, remarks, ordinal));
                }
            }
            return columns;
        } catch (SQLException ex) {
            LOG.warn("listColumns failed datasourceId={} schemaTable={}: {}", datasourceId, schemaTable, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "获取列信息失败: " + ex.getMessage(), ex);
        }
    }

    public List<CatalogSearchHitDto> search(String datasourceId, String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        InfraDataSource ds = resolveDatasource(datasourceId);
        try (Connection conn = jdbcSqlExecutor.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<CatalogSearchHitDto> hits = new ArrayList<>();
            // Search across all schemas; getTables with null schema returns everything
            try (ResultSet rs = meta.getTables(null, null, "%", TABLE_TYPES)) {
                while (rs.next() && hits.size() < Math.max(1, limit)) {
                    String name = rs.getString("TABLE_NAME");
                    String schema = rs.getString("TABLE_SCHEM");
                    String type = rs.getString("TABLE_TYPE");
                    if (name != null && name.toLowerCase(Locale.ROOT).contains(kw)) {
                        hits.add(new CatalogSearchHitDto(schema, name, null, type));
                    }
                }
            }
            return hits;
        } catch (SQLException ex) {
            LOG.warn("catalog search failed datasourceId={} keyword={}: {}", datasourceId, keyword, ex.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private InfraDataSource resolveDatasource(String datasourceId) {
        if (!StringUtils.hasText(datasourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasourceId 不能为空");
        }
        // Try UUID lookup first; fall back to first active datasource for legacy/placeholder IDs
        try {
            UUID id = UUID.fromString(datasourceId);
            return datasourceRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在: " + datasourceId));
        } catch (IllegalArgumentException ex) {
            // Non-UUID id (e.g. "default") — fall back to the first available datasource
            return datasourceRepository
                .findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未配置任何数据源"));
        }
    }
}
