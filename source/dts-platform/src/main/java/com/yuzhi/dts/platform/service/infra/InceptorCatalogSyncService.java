package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.transaction.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

@Component
@Transactional
public class InceptorCatalogSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(InceptorCatalogSyncService.class);
    private static final String DATASET_TYPE = "INCEPTOR";
    private static final String DEFAULT_CLASSIFICATION = "INTERNAL";
    private static final String DEFAULT_OWNER = "system";
    private static final String DEFAULT_EXPOSED_BY = "VIEW";

    private final InceptorDataSourceRegistry registry;
    private final HiveConnectionService connectionService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogRowFilterRuleRepository rowFilterRepository;
    private final CatalogMaskingRuleRepository maskingRuleRepository;
    private final CatalogDatasetGrantRepository datasetGrantRepository;
    private final CatalogDatasetJobRepository datasetJobRepository;
    private final PostgresCatalogSyncService postgresCatalogSyncService;
    private final com.yuzhi.dts.platform.config.CatalogFeatureProperties catalogFeatureProperties;

    @Value("${dts.jdbc.statement-timeout-seconds:30}")
    private int statementTimeoutSeconds;

    public InceptorCatalogSyncService(
        InceptorDataSourceRegistry registry,
        HiveConnectionService connectionService,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogRowFilterRuleRepository rowFilterRepository,
        CatalogMaskingRuleRepository maskingRuleRepository,
        CatalogDatasetGrantRepository datasetGrantRepository,
        CatalogDatasetJobRepository datasetJobRepository,
        PostgresCatalogSyncService postgresCatalogSyncService,
        com.yuzhi.dts.platform.config.CatalogFeatureProperties catalogFeatureProperties
    ) {
        this.registry = registry;
        this.connectionService = connectionService;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.rowFilterRepository = rowFilterRepository;
        this.maskingRuleRepository = maskingRuleRepository;
        this.datasetGrantRepository = datasetGrantRepository;
        this.datasetJobRepository = datasetJobRepository;
        this.postgresCatalogSyncService = postgresCatalogSyncService;
        this.catalogFeatureProperties = catalogFeatureProperties;
    }

    public CatalogSyncResult synchronize() {
        if (catalogFeatureProperties != null && !catalogFeatureProperties.isInceptorSyncEnabled()) {
            LOG.info("Inceptor catalog synchronization disabled via configuration. Using PostgreSQL metadata instead.");
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                return postgresCatalogSyncService.synchronize();
            }
            return CatalogSyncResult.inactive();
        }

        Optional<InceptorDataSourceState> stateOpt = registry.getActive();
        if (stateOpt.isEmpty()) {
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.info("No active Inceptor data source. Falling back to PostgreSQL catalog sync.");
                return postgresCatalogSyncService.synchronize();
            }
            LOG.warn("Skipping catalog sync: no active Inceptor or PostgreSQL data source detected");
            return CatalogSyncResult.inactive();
        }
        InceptorDataSourceState state = stateOpt.orElseThrow();
        String database = sanitizeDatabase(state.database());

        Map<String, List<ColumnMeta>> metadata;
        try {
            metadata = fetchMetadata(state, database);
        } catch (Exception ex) {
            LOG.error("Failed to enumerate tables from Inceptor: {}", ex.getMessage(), ex);
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.warn("Falling back to PostgreSQL catalog sync due to Inceptor failure: {}", ex.getMessage());
                return postgresCatalogSyncService.synchronize();
            }
            return CatalogSyncResult.failed(ex.getMessage());
        }

        if (metadata.isEmpty()) {
            int datasetsRemoved = cleanupStaleDatasets(database, Collections.emptySet());
            LOG.info(
                "Catalog sync completed: no tables discovered in database {} (removed {} stale dataset(s))",
                database,
                datasetsRemoved
            );
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.info("Delegating to PostgreSQL catalog sync because Inceptor returned zero tables");
                return postgresCatalogSyncService.synchronize();
            }
            return new CatalogSyncResult(database, 0, 0, 0, 0, Collections.emptyList(), null);
        }

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int columnsImported = 0;
        List<String> processedTables = new ArrayList<>(metadata.size());

        for (Map.Entry<String, List<ColumnMeta>> entry : metadata.entrySet()) {
            String tableName = entry.getKey();
            List<ColumnMeta> columns = entry.getValue();
            processedTables.add(tableName);

            CatalogDataset dataset = datasetRepository
                .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(database, tableName)
                .orElseGet(CatalogDataset::new);

            boolean isNewDataset = dataset.getId() == null;

            dataset.setHiveDatabase(database);
            dataset.setHiveTable(tableName);
            dataset.setType(DATASET_TYPE);
            dataset.setName(defaultIfBlank(dataset.getName(), tableName));
            dataset.setClassification(defaultIfBlank(dataset.getClassification(), DEFAULT_CLASSIFICATION));
            dataset.setDataLevel(defaultIfBlank(dataset.getDataLevel(), "DATA_INTERNAL"));
            dataset.setOwner(defaultIfBlank(dataset.getOwner(), DEFAULT_OWNER));
            dataset.setExposedBy(defaultIfBlank(dataset.getExposedBy(), DEFAULT_EXPOSED_BY));

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
                    CatalogTableSchema schema = new CatalogTableSchema();
                    schema.setDataset(currentDataset);
                    schema.setName(tableName);
                    return schema;
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
                    entity.setComment(column.comment());
                    columnEntities.add(entity);
                }
                columnRepository.saveAll(columnEntities);
                columnsImported += columnEntities.size();
            }
        }

        LOG.info(
            "Catalog sync completed: db={}, tables={}, newDatasets={}, updatedDatasets={}, tablesCreated={}, columnsImported={}",
            database,
            metadata.size(),
            datasetsCreated,
            datasetsUpdated,
            tablesCreated,
            columnsImported
        );

        Set<String> processedLower = processedTables
            .stream()
            .filter(Objects::nonNull)
            .map(name -> name.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));
        int datasetsRemoved = cleanupStaleDatasets(database, processedLower);
        if (datasetsRemoved > 0) {
            LOG.info("Catalog sync cleanup: removed {} stale datasets in database {}", datasetsRemoved, database);
        }
        return new CatalogSyncResult(
            database,
            metadata.size(),
            datasetsCreated,
            tablesCreated,
            columnsImported,
            processedTables,
            null
        );
    }

    private Map<String, List<ColumnMeta>> fetchMetadata(InceptorDataSourceState state, String database) throws Exception {
        HiveConnectionTestRequest request = buildRequest(state);
        return connectionService.executeWithConnection(request, (connection, connectStart) -> {
            long connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
            LOG.debug("Connected to Inceptor for metadata sync in {} ms", connectMillis);

            if (StringUtils.hasText(database)) {
                try (Statement useStmt = connection.createStatement()) {
                    useStmt.execute("USE `" + database.replace("`", "``") + "`");
                }
            }

            java.util.LinkedHashSet<String> tableNames = new java.util.LinkedHashSet<>();
            try (Statement stmt = connection.createStatement()) {
                try {
                    stmt.setQueryTimeout(Math.max(1, statementTimeoutSeconds));
                } catch (Throwable ignored) {}
                collectIdentifiers(stmt, "SHOW TABLES", tableNames);
                collectIdentifiers(stmt, "SHOW VIEWS", tableNames);
            }
            List<String> tables = new ArrayList<>(tableNames);

            Map<String, List<ColumnMeta>> metadata = new LinkedHashMap<>();
            for (String table : tables) {
                List<ColumnMeta> columns = describeTable(connection, table);
                metadata.put(table, columns);
            }
            return metadata;
        });
    }

    private List<ColumnMeta> describeTable(java.sql.Connection connection, String table) {
        String sanitizedTable = table.replace("`", "``");
        String sql = "DESCRIBE `" + sanitizedTable + "`";
        List<ColumnMeta> columns = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.setQueryTimeout(Math.max(1, statementTimeoutSeconds));
            } catch (Throwable ignored) {}
            try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String columnName = rs.getString(1);
                String dataType = rs.getString(2);
                String columnComment = rs.getString(3);
                if (!StringUtils.hasText(columnName)) {
                    continue;
                }
                columnName = columnName.trim();
                if (columnName.startsWith("#")) {
                    break; // reached partition or metadata section
                }
                columns.add(new ColumnMeta(columnName, safeDataType(dataType), true, StringUtils.hasText(columnComment) ? columnComment.trim() : null));
            }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to describe table {}: {}", table, e.getMessage());
        }
        return columns;
    }

    private void collectIdentifiers(Statement stmt, String sql, java.util.LinkedHashSet<String> target) {
        if (stmt == null || target == null) {
            return;
        }
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (StringUtils.hasText(name)) {
                    target.add(name.trim());
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Hive metadata statement '{}' failed: {}", sql, ex.getMessage());
        }
    }

    private HiveConnectionTestRequest buildRequest(InceptorDataSourceState state) {
        HiveConnectionTestRequest request = new HiveConnectionTestRequest();
        request.setJdbcUrl(state.jdbcUrl());
        request.setLoginPrincipal(state.loginPrincipal());
        request.setAuthMethod(state.authMethod());
        request.setKrb5Conf(state.krb5Conf());
        request.setProxyUser(state.proxyUser());
        request.setJdbcProperties(state.jdbcProperties());
        request.setTestQuery("SELECT 1");
        if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
            request.setKeytabBase64(state.keytabBase64());
            request.setKeytabFileName(state.keytabFileName());
        } else if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            request.setPassword(state.password());
        }
        return request;
    }

    private String sanitizeDatabase(String database) {
        if (!StringUtils.hasText(database)) {
            return "default";
        }
        return database.trim();
    }

    private String defaultIfBlank(String current, String fallback) {
        return StringUtils.hasText(current) ? current : fallback;
    }

    private String safeDataType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "string";
    }

    private record ColumnMeta(String name, String dataType, boolean nullable, String comment) {}

    private int cleanupStaleDatasets(String database, Set<String> processedTablesLower) {
        List<CatalogDataset> existingDatasets = datasetRepository.findByHiveDatabaseIgnoreCase(database);
        if (existingDatasets.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (CatalogDataset dataset : existingDatasets) {
            if (dataset.getId() == null) {
                continue;
            }
            String datasetType = dataset.getType();
            if (StringUtils.hasText(datasetType) && !DATASET_TYPE.equalsIgnoreCase(datasetType)) {
                continue;
            }
            String tableName = dataset.getHiveTable();
            if (!StringUtils.hasText(tableName)) {
                continue;
            }
            if (processedTablesLower.contains(tableName.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            purgeDataset(dataset);
            removed++;
        }
        return removed;
    }

    private void purgeDataset(CatalogDataset dataset) {
        try {
            var rowFilters = rowFilterRepository.findByDataset(dataset);
            if (!rowFilters.isEmpty()) {
                rowFilterRepository.deleteAll(rowFilters);
            }
            var maskingRules = maskingRuleRepository.findByDataset(dataset);
            if (!maskingRules.isEmpty()) {
                maskingRuleRepository.deleteAll(maskingRules);
            }
            if (dataset.getId() != null) {
                datasetGrantRepository.deleteByDatasetId(dataset.getId());
            }
            datasetJobRepository.deleteByDataset(dataset);
            List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
            for (CatalogTableSchema tableSchema : tables) {
                columnRepository.deleteByTable(tableSchema);
            }
            if (!tables.isEmpty()) {
                tableRepository.deleteAll(tables);
            }
            datasetRepository.delete(dataset);
        } catch (Exception ex) {
            LOG.warn("Failed to purge stale dataset {}({}): {}", dataset.getName(), dataset.getId(), ex.getMessage());
            LOG.debug("Purge dataset stack", ex);
        }
    }

    public record CatalogSyncResult(
        String database,
        int tablesDiscovered,
        int datasetsCreated,
        int tablesCreated,
        int columnsImported,
        List<String> tableNames,
        String error
    ) {
        public static CatalogSyncResult inactive() {
            return new CatalogSyncResult(null, 0, 0, 0, 0, Collections.emptyList(), null);
        }

        public static CatalogSyncResult failed(String error) {
            return new CatalogSyncResult(null, 0, 0, 0, 0, Collections.emptyList(), error);
        }
    }
}
