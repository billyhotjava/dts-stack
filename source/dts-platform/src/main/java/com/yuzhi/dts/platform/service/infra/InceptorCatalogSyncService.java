package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftConsumerReferenceReadPort;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.transaction.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
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
import java.util.UUID;
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
    private static final String HARVEST_STATUS_SYNCED = "SYNCED";
    private static final String HARVEST_STATUS_STALE = "STALE";
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
    private final CatalogAutoLineageService autoLineageService;
    private final CatalogSchemaDriftEventRepository schemaDriftEventRepository;
    private final SchemaDriftDetector schemaDriftDetector;
    private final CatalogColumnSyncService columnSyncService;
    private final SchemaDriftConsumerReferenceReadPort driftConsumerReferences;

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
        com.yuzhi.dts.platform.config.CatalogFeatureProperties catalogFeatureProperties,
        CatalogAutoLineageService autoLineageService,
        CatalogSchemaDriftEventRepository schemaDriftEventRepository,
        SchemaDriftDetector schemaDriftDetector,
        CatalogColumnSyncService columnSyncService,
        SchemaDriftConsumerReferenceReadPort driftConsumerReferences
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
        this.autoLineageService = autoLineageService;
        this.schemaDriftEventRepository = schemaDriftEventRepository;
        this.schemaDriftDetector = schemaDriftDetector;
        this.columnSyncService = columnSyncService;
        this.driftConsumerReferences = driftConsumerReferences;
    }

    public CatalogSyncResult synchronize() {
        return synchronize(null);
    }

    public CatalogSyncResult synchronize(UUID runId) {
        if (catalogFeatureProperties != null && !catalogFeatureProperties.isInceptorSyncEnabled()) {
            LOG.info("Inceptor catalog synchronization disabled via configuration. Using PostgreSQL metadata instead.");
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                return postgresCatalogSyncService.synchronize(runId);
            }
            return CatalogSyncResult.inactive();
        }

        Optional<InceptorDataSourceState> stateOpt = registry.getActive();
        if (stateOpt.isEmpty()) {
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.info("No active Inceptor data source. Falling back to PostgreSQL catalog sync.");
                return postgresCatalogSyncService.synchronize(runId);
            }
            LOG.warn("Skipping catalog sync: no active Inceptor or PostgreSQL data source detected");
            return CatalogSyncResult.inactive();
        }
        InceptorDataSourceState state = stateOpt.orElseThrow();
        UUID sourceId = state.id();
        String database = sanitizeDatabase(state.database());
        Instant snapshotTime = Instant.now();

        Map<String, TableMeta> metadata;
        try {
            metadata = fetchMetadata(state, database);
        } catch (Exception ex) {
            if (isKerberosUnavailable(ex)) {
                LOG.warn(
                    "Skipping Inceptor catalog synchronization because Kerberos authentication failed (KDC unreachable): {}",
                    ex.getMessage()
                );
                if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                    LOG.warn("Falling back to PostgreSQL catalog sync because Kerberos authentication is unavailable");
                    return postgresCatalogSyncService.synchronize(runId);
                }
                return CatalogSyncResult.inactive();
            }
            LOG.error("Failed to enumerate tables from Inceptor: {}", ex.getMessage(), ex);
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.warn("Falling back to PostgreSQL catalog sync due to Inceptor failure: {}", ex.getMessage());
                return postgresCatalogSyncService.synchronize(runId);
            }
            return CatalogSyncResult.failed(ex.getMessage());
        }

        if (metadata.isEmpty()) {
            int datasetsRemoved = cleanupStaleDatasets(sourceId, database, Collections.emptySet());
            LOG.info(
                "Catalog sync completed: no tables discovered in database {} (marked {} dataset(s) stale)",
                database,
                datasetsRemoved
            );
            if (postgresCatalogSyncService != null && postgresCatalogSyncService.isFallbackActive()) {
                LOG.info("Delegating to PostgreSQL catalog sync because Inceptor returned zero tables");
                return postgresCatalogSyncService.synchronize(runId);
            }
            return new CatalogSyncResult(database, 0, 0, 0, datasetsRemoved, 0, 0, Collections.emptyList(), null);
        }

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int columnsImported = 0;
        List<String> processedTables = new ArrayList<>(metadata.size());

        for (Map.Entry<String, TableMeta> entry : metadata.entrySet()) {
            String tableName = entry.getKey();
            TableMeta tableMeta = entry.getValue();
            List<ColumnMeta> columns = tableMeta != null ? tableMeta.columns() : List.of();
            processedTables.add(tableName);

            CatalogDataset dataset = (sourceId != null)
                ? datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, database, tableName).orElseGet(CatalogDataset::new)
                : datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(database, tableName).orElseGet(CatalogDataset::new);

            boolean isNewDataset = dataset.getId() == null;

            dataset.setSourceId(sourceId);
            dataset.setHiveDatabase(database);
            dataset.setHiveTable(tableName);
            dataset.setSnapshotTime(snapshotTime);
            dataset.setType(DATASET_TYPE);
            dataset.setName(defaultIfBlank(dataset.getName(), tableName));
            dataset.setOwner(defaultIfBlank(dataset.getOwner(), DEFAULT_OWNER));
            dataset.setExposedBy(defaultIfBlank(dataset.getExposedBy(), DEFAULT_EXPOSED_BY));
            dataset.setHarvestStatus(HARVEST_STATUS_SYNCED);

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
            if (StringUtils.hasText(dataset.getClassification())) {
                tableSchema.setClassification(defaultIfBlank(tableSchema.getClassification(), dataset.getClassification()));
            }
            tableSchema = tableRepository.save(tableSchema);
            if (isNewTable) {
                tablesCreated++;
            }

            List<CatalogColumnSchema> existingColumns = columnRepository.findByTable(tableSchema);
            Map<String, SchemaDriftDetector.ColumnSnapshot> beforeSnapshot = schemaDriftDetector.snapshotExisting(existingColumns);
            columnsImported += columnSyncService.synchronizeSnapshot(
                tableSchema,
                columns
                    .stream()
                    .map(column ->
                        new CatalogColumnSyncService.ColumnSpec(
                            column.name(),
                            column.dataType(),
                            column.nullable(),
                            column.comment(),
                            null,
                            null,
                            null,
                            null
                        )
                    )
                    .toList()
            );

            if (!beforeSnapshot.isEmpty() || !columns.isEmpty()) {
                List<SchemaDriftDetector.ColumnSnapshot> afterSnapshot = columns
                    .stream()
                    .map(col -> new SchemaDriftDetector.ColumnSnapshot(col.name(), col.dataType(), col.nullable()))
                    .toList();
                Set<String> referencedFields = driftConsumerReferences
                    .findCurrentReferencedFields(tableSchema.getId(), dataset.getSourceId(), database, tableName)
                    .orElse(null);
                SchemaDriftDetector.DriftSummary drift = schemaDriftDetector.diff(
                    beforeSnapshot,
                    afterSnapshot,
                    referencedFields
                );
                if (drift.added() > 0 || drift.removed() > 0 || drift.changed() > 0) {
                    recordSchemaDrift(runId, "INCEPTOR", dataset, database, tableName, drift);
                }
            }

            if (tableMeta != null && tableMeta.isView() && StringUtils.hasText(tableMeta.viewDefinition())) {
                try {
                    autoLineageService.syncAutoViewLineage(dataset, tableMeta.viewDefinition());
                } catch (Exception ex) {
                    LOG.debug("Auto lineage sync skipped for {}.{}: {}", database, tableName, ex.getMessage());
                }
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
        int datasetsRemoved = cleanupStaleDatasets(sourceId, database, processedLower);
        if (datasetsRemoved > 0) {
            LOG.info("Catalog sync cleanup: marked {} datasets stale in database {}", datasetsRemoved, database);
        }
        return new CatalogSyncResult(
            database,
            metadata.size(),
            datasetsCreated,
            datasetsUpdated,
            datasetsRemoved,
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
        event.setPolicyMode(CatalogSchemaDriftEvent.POLICY_REVIEW);
        event.setTicketStatus(CatalogSchemaDriftEvent.TICKET_OPEN);
        schemaDriftEventRepository.save(event);
    }

    private Map<String, TableMeta> fetchMetadata(InceptorDataSourceState state, String database) throws Exception {
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
            java.util.LinkedHashSet<String> viewNames = new java.util.LinkedHashSet<>();
            try (Statement stmt = connection.createStatement()) {
                try {
                    stmt.setQueryTimeout(Math.max(1, statementTimeoutSeconds));
                } catch (Throwable ignored) {}
                collectIdentifiers(stmt, "SHOW TABLES", tableNames);
                collectIdentifiers(stmt, "SHOW VIEWS", viewNames);
            }
            tableNames.addAll(viewNames);
            List<String> tables = new ArrayList<>(tableNames);
            Set<String> viewLower = viewNames
                .stream()
                .filter(StringUtils::hasText)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

            Map<String, TableMeta> metadata = new LinkedHashMap<>();
            for (String table : tables) {
                List<ColumnMeta> columns = describeTable(connection, table);
                boolean isView = StringUtils.hasText(table) && viewLower.contains(table.trim().toLowerCase(Locale.ROOT));
                String viewDefinition = isView ? fetchViewDefinition(connection, table) : null;
                metadata.put(table, new TableMeta(table, columns, isView, viewDefinition));
            }
            return metadata;
        });
    }

    private String fetchViewDefinition(java.sql.Connection connection, String table) {
        if (connection == null || !StringUtils.hasText(table)) {
            return null;
        }
        String sanitizedTable = table.replace("`", "``");
        String sql = "SHOW CREATE TABLE `" + sanitizedTable + "`";
        StringBuilder out = new StringBuilder();
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.setQueryTimeout(Math.max(1, statementTimeoutSeconds));
            } catch (Throwable ignored) {}
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String part = rs.getString(1);
                    if (part != null) {
                        if (out.length() > 0) {
                            out.append('\n');
                        }
                        out.append(part);
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Failed to fetch view definition for {}: {}", table, ex.getMessage());
            return null;
        }
        String text = out.toString().trim();
        return StringUtils.hasText(text) ? text : null;
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
                columns.add(
                    new ColumnMeta(
                        columnName,
                        safeDataType(dataType),
                        true,
                        normalizeComment(columnComment)
                    )
                );
            }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to describe table {}: {}", table, e.getMessage());
        }
        if (columns.stream().anyMatch(col -> !StringUtils.hasText(col.comment()))) {
            Map<String, String> ddlComments = HiveColumnCommentResolver.fetchColumnComments(connection, table, statementTimeoutSeconds);
            if (!ddlComments.isEmpty()) {
                List<ColumnMeta> enriched = new ArrayList<>(columns.size());
                for (ColumnMeta col : columns) {
                    String comment = col.comment();
                    if (!StringUtils.hasText(comment)) {
                        comment = ddlComments.getOrDefault(col.name().toLowerCase(Locale.ROOT), null);
                    }
                    enriched.add(new ColumnMeta(col.name(), col.dataType(), col.nullable(), normalizeComment(comment)));
                }
                columns = enriched;
            }
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
        } else if (
            state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD ||
            state.authMethod() == HiveConnectionTestRequest.AuthMethod.JDBC_PASSWORD
        ) {
            request.setPassword(state.password());
        }
        return request;
    }

    private boolean isKerberosUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof javax.security.auth.login.LoginException && containsConnectionRefused(current)) {
                return true;
            }
            current = current.getCause();
        }
        return containsConnectionRefused(throwable);
    }

    private boolean containsConnectionRefused(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.net.ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        if (throwable != null && throwable.getMessage() != null) {
            String message = throwable.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("connection refused");
        }
        return false;
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

    private String normalizeComment(String raw) {
        return HiveColumnCommentResolver.normalizeComment(raw);
    }

    private record ColumnMeta(String name, String dataType, boolean nullable, String comment) {}

    private record TableMeta(String tableName, List<ColumnMeta> columns, boolean isView, String viewDefinition) {}

    private int cleanupStaleDatasets(UUID sourceId, String database, Set<String> processedTablesLower) {
        List<CatalogDataset> existingDatasets = (sourceId != null)
            ? datasetRepository.findBySourceIdAndHiveDatabaseIgnoreCase(sourceId, database)
            : datasetRepository.findByHiveDatabaseIgnoreCase(database);
        if (existingDatasets.isEmpty()) {
            return 0;
        }
        int markedStale = 0;
        for (CatalogDataset dataset : existingDatasets) {
            if (dataset.getId() == null) {
                continue;
            }
            String datasetType = dataset.getType();
            if (StringUtils.hasText(datasetType) && !DATASET_TYPE.equalsIgnoreCase(datasetType)) {
                continue;
            }
            if (sourceId != null && dataset.getSourceId() != null && !Objects.equals(dataset.getSourceId(), sourceId)) {
                continue;
            }
            String tableName = dataset.getHiveTable();
            if (!StringUtils.hasText(tableName)) {
                continue;
            }
            if (processedTablesLower.contains(tableName.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!HARVEST_STATUS_STALE.equalsIgnoreCase(dataset.getHarvestStatus())) {
                dataset.setHarvestStatus(HARVEST_STATUS_STALE);
                datasetRepository.save(dataset);
                markedStale++;
            }
        }
        return markedStale;
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
        int datasetsUpdated,
        int datasetsRemoved,
        int tablesCreated,
        int columnsImported,
        List<String> tableNames,
        String error
    ) {
        public static CatalogSyncResult inactive() {
            return new CatalogSyncResult(null, 0, 0, 0, 0, 0, 0, Collections.emptyList(), null);
        }

        public static CatalogSyncResult failed(String error) {
            return new CatalogSyncResult(null, 0, 0, 0, 0, 0, 0, Collections.emptyList(), error);
        }
    }
}
