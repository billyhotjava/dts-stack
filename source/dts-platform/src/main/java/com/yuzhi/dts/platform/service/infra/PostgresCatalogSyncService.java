package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
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
import java.util.UUID;
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
    private final CatalogAutoLineageService autoLineageService;
    private final CatalogSchemaDriftEventRepository schemaDriftEventRepository;
    private final SchemaDriftDetector schemaDriftDetector;

    public PostgresCatalogSyncService(
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogFeatureProperties catalogFeatureProperties,
        DataSource dataSource,
        CatalogDomainRepository domainRepository,
        CatalogAutoLineageService autoLineageService,
        CatalogSchemaDriftEventRepository schemaDriftEventRepository,
        SchemaDriftDetector schemaDriftDetector
    ) {
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.catalogFeatureProperties = catalogFeatureProperties;
        this.dataSource = dataSource;
        this.domainRepository = domainRepository;
        this.autoLineageService = autoLineageService;
        this.schemaDriftEventRepository = schemaDriftEventRepository;
        this.schemaDriftDetector = schemaDriftDetector;
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
        return synchronize(null);
    }

    public CatalogSyncResult synchronize(UUID runId) {
        if (!isFallbackActive()) {
            LOG.debug("Skipping PostgreSQL catalog sync: fallback not active");
            return CatalogSyncResult.inactive();
        }

        UUID sourceId = infraDataSourceRepository
            .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(TYPE_POSTGRES, STATUS_ACTIVE)
            .map(InfraDataSource::getId)
            .orElse(null);

        String schema = resolveSchema();
        Map<String, TableMeta> metadata;
        try {
            metadata = fetchMetadata(schema);
        } catch (Exception ex) {
            LOG.error("Failed to enumerate PostgreSQL metadata: {}", ex.getMessage(), ex);
            return CatalogSyncResult.failed(ex.getMessage());
        }

        if (metadata.isEmpty()) {
            LOG.info("PostgreSQL catalog sync completed: schema={} has no tables", schema);
            return new CatalogSyncResult(schema, 0, 0, 0, 0, 0, 0, List.of(), null);
        }

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int columnsImported = 0;
        List<String> processedTables = new ArrayList<>(metadata.size());

        CatalogDomain databaseDomain = resolveOrCreateDomain(schema);

        for (Map.Entry<String, TableMeta> entry : metadata.entrySet()) {
            String tableName = entry.getKey();
            TableMeta tableMeta = entry.getValue();
            List<ColumnMeta> columns = tableMeta != null ? tableMeta.columns() : List.of();
            processedTables.add(tableName);

            CatalogDataset dataset = (sourceId != null)
                ? datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, schema, tableName).orElseGet(CatalogDataset::new)
                : datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, tableName).orElseGet(CatalogDataset::new);
            boolean isNewDataset = dataset.getId() == null;

            dataset.setSourceId(sourceId);
            dataset.setHiveDatabase(schema);
            dataset.setHiveTable(tableName);
            dataset.setType(TYPE_POSTGRES);
            dataset.setName(defaultIfBlank(dataset.getName(), tableName));
            dataset.setClassification(defaultIfBlank(dataset.getClassification(), DEFAULT_CLASSIFICATION));
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

            List<CatalogColumnSchema> existingColumns = columnRepository.findByTable(tableSchema);
            Map<String, LegacyColumnValues> legacyColumns = existingColumns
                .stream()
                .filter(existing -> existing.getName() != null)
                .collect(
                    java.util.stream.Collectors.toMap(
                        existing -> existing.getName().trim().toLowerCase(java.util.Locale.ROOT),
                        existing ->
                            new LegacyColumnValues(
                                org.springframework.util.StringUtils.hasText(existing.getComment()) ? existing.getComment() : null,
                                org.springframework.util.StringUtils.hasText(existing.getTags()) ? existing.getTags() : null,
                                org.springframework.util.StringUtils.hasText(existing.getSensitiveTags()) ? existing.getSensitiveTags() : null
                            ),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                    )
                );
            Map<String, SchemaDriftDetector.ColumnSnapshot> beforeSnapshot = schemaDriftDetector.snapshotExisting(existingColumns);

            columnRepository.deleteByTable(tableSchema);
            if (!columns.isEmpty()) {
                List<CatalogColumnSchema> columnEntities = new ArrayList<>(columns.size());
                for (ColumnMeta column : columns) {
                    CatalogColumnSchema entity = new CatalogColumnSchema();
                    entity.setTable(tableSchema);
                    entity.setName(column.name());
                    entity.setDataType(column.dataType());
                    entity.setNullable(column.nullable());
                    String key = column.name() != null ? column.name().trim().toLowerCase(java.util.Locale.ROOT) : "";
                    LegacyColumnValues legacy = key.isEmpty() ? null : legacyColumns.getOrDefault(key, null);
                    String comment = column.comment();
                    if (!org.springframework.util.StringUtils.hasText(comment) && legacy != null) {
                        comment = legacy.comment();
                    }
                    entity.setComment(comment);
                    if (legacy != null) {
                        entity.setTags(legacy.tags());
                        entity.setSensitiveTags(legacy.sensitiveTags());
                    }
                    columnEntities.add(entity);
                }
                columnRepository.saveAll(columnEntities);
                columnsImported += columnEntities.size();
            }

            if (!beforeSnapshot.isEmpty() || !columns.isEmpty()) {
                List<SchemaDriftDetector.ColumnSnapshot> afterSnapshot = columns
                    .stream()
                    .map(col -> new SchemaDriftDetector.ColumnSnapshot(col.name(), col.dataType(), col.nullable()))
                    .toList();
                SchemaDriftDetector.DriftSummary drift = schemaDriftDetector.diff(beforeSnapshot, afterSnapshot);
                if (drift.added() > 0 || drift.removed() > 0 || drift.changed() > 0) {
                    recordSchemaDrift(runId, TYPE_POSTGRES, dataset, schema, tableName, drift);
                }
            }
            if (databaseDomain != null && dataset.getDomain() == null) {
                dataset.setDomain(databaseDomain);
                datasetRepository.save(dataset);
            }

            if (tableMeta != null && tableMeta.isView() && org.springframework.util.StringUtils.hasText(tableMeta.viewDefinition())) {
                try {
                    autoLineageService.syncAutoViewLineage(dataset, tableMeta.viewDefinition());
                } catch (Exception ex) {
                    LOG.debug("Auto lineage sync skipped for {}.{}: {}", schema, tableName, ex.getMessage());
                }
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
            datasetsUpdated,
            0,
            tablesCreated,
            columnsImported,
            processedTables,
            null
        );
    }

    private void recordSchemaDrift(
        UUID runId,
        String integration,
        CatalogDataset dataset,
        String hiveDatabase,
        String hiveTable,
        SchemaDriftDetector.DriftSummary drift
    ) {
        if (dataset == null || dataset.getId() == null || drift == null) {
            return;
        }
        if (schemaDriftEventRepository == null) {
            return;
        }
        CatalogSchemaDriftEvent event = new CatalogSchemaDriftEvent();
        event.setRunId(runId);
        event.setIntegration(integration);
        event.setDatasetId(dataset.getId());
        event.setHiveDatabase(hiveDatabase);
        event.setHiveTable(hiveTable);
        event.setAddedCount(drift.added());
        event.setRemovedCount(drift.removed());
        event.setChangedCount(drift.changed());
        event.setDetailsJson(drift.detailsJson());
        schemaDriftEventRepository.save(event);
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

    private Map<String, TableMeta> fetchMetadata(String schema) throws SQLException {
        Map<String, TableMeta> result = new LinkedHashMap<>();
        String tableSql =
            """
            SELECT table_name, table_type
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
                        String tableType = rs.getString("table_type");
                        tableType = tableType != null ? tableType.trim() : null;
                        String viewDefinition = null;
                        if ("VIEW".equalsIgnoreCase(tableType)) {
                            viewDefinition = fetchViewDefinition(connection, schema, table);
                        }
                        result.put(table.trim(), new TableMeta(table.trim(), tableType, columns, viewDefinition));
                    }
                }
            }
        }
        return result;
    }

    private List<ColumnMeta> fetchColumns(Connection connection, String schema, String table) throws SQLException {
        String columnSql =
            """
            SELECT cols.column_name,
                   cols.data_type,
                   cols.is_nullable,
                   pgd.description AS column_comment
            FROM information_schema.columns cols
            LEFT JOIN pg_catalog.pg_class c
                ON c.relname = cols.table_name
            LEFT JOIN pg_catalog.pg_namespace n
                ON n.oid = c.relnamespace
            LEFT JOIN pg_catalog.pg_description pgd
                ON pgd.objoid = c.oid AND pgd.objsubid = cols.ordinal_position
            WHERE LOWER(cols.table_schema) = LOWER(?) AND LOWER(cols.table_name) = LOWER(?)
              AND (n.nspname IS NULL OR LOWER(n.nspname) = LOWER(cols.table_schema))
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
                    String columnComment = rs.getString("column_comment");
                    columns.add(
                        new ColumnMeta(
                            columnName.trim(),
                            dataType.toLowerCase(Locale.ROOT),
                            !"NO".equalsIgnoreCase(nullable),
                            StringUtils.hasText(columnComment) ? columnComment.trim() : null
                        )
                    );
                }
            }
        }
        return columns;
    }

    private String fetchViewDefinition(Connection connection, String schema, String view) {
        String sql =
            """
            SELECT view_definition
            FROM information_schema.views
            WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)
            LIMIT 1
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, view);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString("view_definition");
                    return def != null ? def.trim() : null;
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Failed to fetch PostgreSQL view definition for {}.{}: {}", schema, view, ex.getMessage());
        }
        return null;
    }

    private record TableMeta(String tableName, String tableType, List<ColumnMeta> columns, String viewDefinition) {
        boolean isView() {
            return "VIEW".equalsIgnoreCase(tableType);
        }
    }

    private String defaultIfBlank(String current, String fallback) {
        return StringUtils.hasText(current) ? current : fallback;
    }

    private record ColumnMeta(String name, String dataType, boolean nullable, String comment) {}

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

    private record LegacyColumnValues(String comment, String tags, String sensitiveTags) {}
}
