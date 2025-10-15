package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InceptorCatalogSyncService.CatalogSyncResult;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Transactional
public class PostgresCatalogSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresCatalogSyncService.class);
    private static final String TYPE_POSTGRES = "POSTGRES";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_CLASSIFICATION = "INTERNAL";
    private static final String DEFAULT_OWNER = "system";
    private static final String DEFAULT_EXPOSED_BY = "VIEW";

    private final InfraDataSourceRepository infraDataSourceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogFeatureProperties catalogFeatureProperties;
    private final DataSource dataSource;
    private final CatalogDomainRepository domainRepository;

    public PostgresCatalogSyncService(
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogFeatureProperties catalogFeatureProperties,
        DataSource dataSource,
        CatalogDomainRepository domainRepository
    ) {
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.catalogFeatureProperties = catalogFeatureProperties;
        this.dataSource = dataSource;
        this.domainRepository = domainRepository;
    }

    public boolean isFallbackActive() {
        try {
            return infraDataSourceRepository
                .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(TYPE_POSTGRES, STATUS_ACTIVE)
                .isPresent();
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            LOG.debug(
                "PostgreSQL fallback inactive because infra_data_source table is unavailable: {}",
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
            );
            return false;
        } catch (RuntimeException ex) {
            LOG.debug("Failed to probe PostgreSQL fallback: {}", ex.getMessage());
            return false;
        }
    }

    public CatalogSyncResult synchronize() {
        if (!isFallbackActive()) {
            LOG.debug("Skipping PostgreSQL catalog sync: fallback not active");
            return CatalogSyncResult.inactive();
        }

        String schema = resolveSchema();
        Map<String, List<ColumnMeta>> metadata;
        try {
            metadata = fetchMetadata(schema);
        } catch (Exception ex) {
            LOG.error("Failed to enumerate PostgreSQL metadata: {}", ex.getMessage(), ex);
            return CatalogSyncResult.failed(ex.getMessage());
        }

        if (metadata.isEmpty()) {
            LOG.info("PostgreSQL catalog sync completed: schema={} has no tables", schema);
            return new CatalogSyncResult(schema, 0, 0, 0, 0, List.of(), null);
        }

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int columnsImported = 0;
        List<String> processedTables = new ArrayList<>(metadata.size());

        CatalogDomain databaseDomain = resolveOrCreateDomain(schema);

        for (Map.Entry<String, List<ColumnMeta>> entry : metadata.entrySet()) {
            String tableName = entry.getKey();
            List<ColumnMeta> columns = entry.getValue();
            processedTables.add(tableName);

            CatalogDataset dataset = datasetRepository
                .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, tableName)
                .orElseGet(CatalogDataset::new);
            boolean isNewDataset = dataset.getId() == null;

            dataset.setHiveDatabase(schema);
            dataset.setHiveTable(tableName);
            dataset.setType(TYPE_POSTGRES);
            dataset.setName(defaultIfBlank(dataset.getName(), tableName));
            dataset.setClassification(defaultIfBlank(dataset.getClassification(), DEFAULT_CLASSIFICATION));
            dataset.setDataLevel(defaultIfBlank(dataset.getDataLevel(), "DATA_INTERNAL"));
            dataset.setOwner(defaultIfBlank(dataset.getOwner(), DEFAULT_OWNER));
            dataset.setExposedBy(defaultIfBlank(dataset.getExposedBy(), DEFAULT_EXPOSED_BY));
            if (databaseDomain != null && dataset.getDomain() == null) {
                dataset.setDomain(databaseDomain);
            }

            dataset = datasetRepository.save(dataset);
            if (isNewDataset) {
                datasetsCreated++;
            } else {
                datasetsUpdated++;
            }

            final CatalogDataset currentDataset = dataset;
            CatalogTableSchema tableSchema = tableRepository
                .findFirstByDatasetAndNameIgnoreCase(currentDataset, tableName)
                .orElseGet(() -> {
                    CatalogTableSchema schemaEntity = new CatalogTableSchema();
                    schemaEntity.setDataset(currentDataset);
                    schemaEntity.setName(tableName);
                    return schemaEntity;
                });

            boolean isNewTable = tableSchema.getId() == null;
            tableSchema.setOwner(defaultIfBlank(tableSchema.getOwner(), dataset.getOwner()));
            tableSchema.setClassification(defaultIfBlank(tableSchema.getClassification(), dataset.getClassification()));
            tableSchema = tableRepository.save(tableSchema);
            if (isNewTable) {
                tablesCreated++;
            }

            columnRepository.deleteByTable(tableSchema);
            if (!columns.isEmpty()) {
                List<CatalogColumnSchema> columnEntities = new ArrayList<>(columns.size());
                for (ColumnMeta column : columns) {
                    CatalogColumnSchema entity = new CatalogColumnSchema();
                    entity.setTable(tableSchema);
                    entity.setName(column.name());
                    entity.setDataType(column.dataType());
                    entity.setNullable(column.nullable());
                    columnEntities.add(entity);
                }
                columnRepository.saveAll(columnEntities);
                columnsImported += columnEntities.size();
            }
            if (databaseDomain != null && dataset.getDomain() == null) {
                dataset.setDomain(databaseDomain);
                datasetRepository.save(dataset);
            }
        }

        LOG.info(
            "PostgreSQL catalog sync completed: schema={}, tables={}, newDatasets={}, updatedDatasets={}, tablesCreated={}, columnsImported={}",
            schema,
            metadata.size(),
            datasetsCreated,
            datasetsUpdated,
            tablesCreated,
            columnsImported
        );
        return new CatalogSyncResult(
            schema,
            metadata.size(),
            datasetsCreated,
            tablesCreated,
            columnsImported,
            processedTables,
            null
        );
    }

    private String resolveSchema() {
        String configured = Optional
            .ofNullable(catalogFeatureProperties.getPostgresSchema())
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .orElse("public");
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(
            "SELECT schema_name FROM information_schema.schemata WHERE LOWER(schema_name) = LOWER(?) LIMIT 1"
        )) {
            stmt.setString(1, configured);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("schema_name");
                }
            }
        } catch (SQLException ex) {
            LOG.warn("Failed to resolve PostgreSQL schema '{}': {}", configured, ex.getMessage());
        }
        return configured;
    }

    private Map<String, List<ColumnMeta>> fetchMetadata(String schema) throws SQLException {
        Map<String, List<ColumnMeta>> result = new LinkedHashMap<>();
        String tableSql =
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE LOWER(table_schema) = LOWER(?) AND table_type IN ('BASE TABLE', 'VIEW')
            ORDER BY table_name
            """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(tableSql)) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (StringUtils.hasText(table)) {
                        List<ColumnMeta> columns = fetchColumns(connection, schema, table);
                        result.put(table.trim(), columns);
                    }
                }
            }
        }
        return result;
    }

    private List<ColumnMeta> fetchColumns(Connection connection, String schema, String table) throws SQLException {
        String columnSql =
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)
            ORDER BY ordinal_position
            """;
        List<ColumnMeta> columns = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(columnSql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    if (!StringUtils.hasText(columnName)) {
                        continue;
                    }
                    String dataType = defaultIfBlank(rs.getString("data_type"), "text");
                    String nullable = rs.getString("is_nullable");
                    columns.add(new ColumnMeta(columnName.trim(), dataType.toLowerCase(Locale.ROOT), !"NO".equalsIgnoreCase(nullable)));
                }
            }
        }
        return columns;
    }

    private String defaultIfBlank(String current, String fallback) {
        return StringUtils.hasText(current) ? current : fallback;
    }

    private record ColumnMeta(String name, String dataType, boolean nullable) {}

    private CatalogDomain resolveOrCreateDomain(String schema) {
        try {
            return domainRepository
                .findFirstByNameIgnoreCase(schema)
                .orElseGet(() -> {
                    CatalogDomain domain = new CatalogDomain();
                    domain.setName(schema);
                    domain.setDescription("Auto-created domain for schema " + schema);
                    return domainRepository.save(domain);
                });
        } catch (Exception ex) {
            LOG.warn("Failed to resolve/create domain for schema {}: {}", schema, ex.getMessage());
            return null;
        }
    }
}
