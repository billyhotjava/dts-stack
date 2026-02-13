package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Transactional
public class JdbcCatalogSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcCatalogSyncService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String DEFAULT_CLASSIFICATION = "INTERNAL";
    private static final String DEFAULT_OWNER = "system";
    private static final String DEFAULT_EXPOSED_BY = "VIEW";

    private final InfraDataSourceRepository infraDataSourceRepository;
    private final InfraSecretService secretService;
    private final HiveConnectionService hiveConnectionService;
    private final ObjectMapper objectMapper;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogAutoLineageService autoLineageService;
    private final CatalogSchemaDriftEventRepository schemaDriftEventRepository;
    private final SchemaDriftDetector schemaDriftDetector;

    public JdbcCatalogSyncService(
        InfraDataSourceRepository infraDataSourceRepository,
        InfraSecretService secretService,
        HiveConnectionService hiveConnectionService,
        ObjectMapper objectMapper,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogAutoLineageService autoLineageService,
        CatalogSchemaDriftEventRepository schemaDriftEventRepository,
        SchemaDriftDetector schemaDriftDetector
    ) {
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.secretService = secretService;
        this.hiveConnectionService = hiveConnectionService;
        this.objectMapper = objectMapper;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.autoLineageService = autoLineageService;
        this.schemaDriftEventRepository = schemaDriftEventRepository;
        this.schemaDriftDetector = schemaDriftDetector;
    }

    public List<JdbcSyncResult> synchronizeAllActive() {
        return synchronizeAllActive(null);
    }

    public List<JdbcSyncResult> synchronizeAllActive(UUID runId) {
        List<InfraDataSource> sources = infraDataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<JdbcSyncResult> results = new ArrayList<>();
        for (InfraDataSource source : sources) {
            if (!isJdbcCatalogCandidate(source)) {
                continue;
            }
            results.add(synchronize(source, runId));
        }
        return results;
    }

    public JdbcSyncResult synchronize(InfraDataSource source) {
        return synchronize(source, null);
    }

    public JdbcSyncResult synchronize(InfraDataSource source, UUID runId) {
        return synchronize(source, runId, null);
    }

    public JdbcSyncResult synchronize(InfraDataSource source, UUID runId, Boolean cleanupStaleOverride) {
        if (source == null || source.getId() == null) {
            return JdbcSyncResult.failed(null, "invalid-source");
        }
        if (!isJdbcCatalogCandidate(source)) {
            return JdbcSyncResult.skipped(source.getId(), "not-a-jdbc-catalog-source");
        }

        Map<String, Object> props = readProps(source.getProps());
        if (boolProp(props, "catalogSyncDisabled", false)) {
            return JdbcSyncResult.skipped(source.getId(), "disabled-by-props");
        }

        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = stringProp(secrets, "password");
        if (!StringUtils.hasText(password)) {
            // Fallback for virtual/admin-provided sources where secure props are not persisted locally.
            password = stringProp(props, "password");
        }
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            return JdbcSyncResult.failed(source.getId(), "missing-jdbc-url");
        }

        List<String> schemas = resolveSchemas(props, source);

        boolean cleanupStale = cleanupStaleOverride != null
            ? cleanupStaleOverride.booleanValue()
            : boolProp(props, "catalogCleanupStale", true);
        String tablePattern = Optional.ofNullable(stringProp(props, "tablePattern")).filter(StringUtils::hasText).orElse("%");

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int tablesDiscovered = 0;
        int columnsImported = 0;
        int datasetsRemoved = 0;
        Instant snapshotTime = Instant.now();

        long startedAt = System.nanoTime();
        try (Connection connection = openConnection(source, password, props)) {
            String dbProduct = safe(connection.getMetaData().getDatabaseProductName());
            String dbVersion = safe(connection.getMetaData().getDatabaseProductVersion());
            String resolvedCatalog = resolveCatalog(props, connection);
            if (schemas.isEmpty()) {
                schemas = discoverSchemas(connection, resolvedCatalog, source);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Discovered schemas for source {}: {}", source.getName(), schemas);
                }
            }
            if (schemas.isEmpty()) {
                schemas = List.of("");
            }

            for (String schema : schemas) {
                String normalizedSchema = normalizeSchema(schema, connection);

                Set<String> processedTablesLower = new LinkedHashSet<>();
                List<TableMeta> tables = listTables(connection, resolvedCatalog, normalizedSchema, tablePattern);
                tablesDiscovered += tables.size();
                for (TableMeta table : tables) {
                    String tableName = table.tableName();
                    processedTablesLower.add(tableName.toLowerCase(Locale.ROOT));

                    CatalogDataset dataset = datasetRepository
                        .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(source.getId(), normalizedSchema, tableName)
                        .orElseGet(CatalogDataset::new);
                    boolean isNewDataset = dataset.getId() == null;

                    dataset.setSourceId(source.getId());
                    dataset.setHiveDatabase(normalizedSchema);
                    dataset.setHiveTable(tableName);
                    dataset.setSnapshotTime(snapshotTime);
                    dataset.setType(StringUtils.hasText(source.getType()) ? source.getType().trim().toUpperCase(Locale.ROOT) : "JDBC");
                    dataset.setName(defaultIfBlank(dataset.getName(), tableName));
                    dataset.setClassification(defaultIfBlank(dataset.getClassification(), DEFAULT_CLASSIFICATION));
                    dataset.setOwner(defaultIfBlank(dataset.getOwner(), defaultOwner(source)));
                    dataset.setExposedBy(defaultIfBlank(dataset.getExposedBy(), DEFAULT_EXPOSED_BY));

                    CatalogDataset savedDataset = datasetRepository.save(dataset);
                    if (isNewDataset) {
                        datasetsCreated++;
                    } else {
                        datasetsUpdated++;
                    }

                    CatalogTableSchema tableSchema = tableRepository
                        .findFirstByDatasetAndNameIgnoreCase(savedDataset, tableName)
                        .orElseGet(CatalogTableSchema::new);
                    boolean isNewTable = tableSchema.getId() == null;
                    tableSchema.setDataset(savedDataset);
                    tableSchema.setName(tableName);
                    tableSchema.setOwner(defaultIfBlank(tableSchema.getOwner(), savedDataset.getOwner()));
                    tableSchema.setClassification(defaultIfBlank(tableSchema.getClassification(), savedDataset.getClassification()));
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
                                existing -> existing.getName().trim().toLowerCase(Locale.ROOT),
                                existing ->
                                    new LegacyColumnValues(
                                        StringUtils.hasText(existing.getComment()) ? existing.getComment() : null,
                                        StringUtils.hasText(existing.getTags()) ? existing.getTags() : null,
                                        StringUtils.hasText(existing.getSensitiveTags()) ? existing.getSensitiveTags() : null,
                                        StringUtils.hasText(existing.getStatus()) ? existing.getStatus() : null,
                                        existing.getStandardId(),
                                        StringUtils.hasText(existing.getStandardRule()) ? existing.getStandardRule() : null,
                                        StringUtils.hasText(existing.getStandardMismatchReason())
                                            ? existing.getStandardMismatchReason()
                                            : null
                                    ),
                                (left, right) -> left,
                                LinkedHashMap::new
                            )
                        );
                    Map<String, SchemaDriftDetector.ColumnSnapshot> beforeSnapshot = schemaDriftDetector.snapshotExisting(existingColumns);

                    List<ColumnMeta> columns = listColumns(connection, resolvedCatalog, normalizedSchema, tableName);
                    columnRepository.deleteByTable(tableSchema);
                    if (!columns.isEmpty()) {
                        List<CatalogColumnSchema> columnEntities = new ArrayList<>(columns.size());
                        for (ColumnMeta column : columns) {
                            CatalogColumnSchema entity = new CatalogColumnSchema();
                            entity.setTable(tableSchema);
                            entity.setName(column.name());
                            entity.setDataType(column.dataType());
                            entity.setNullable(column.nullable());
                            String comment = column.comment();
                            if (!StringUtils.hasText(comment)) {
                                LegacyColumnValues legacy = legacyColumns.getOrDefault(column.name().toLowerCase(Locale.ROOT), null);
                                comment = legacy != null ? legacy.comment() : null;
                            }
                            entity.setComment(normalizeComment(comment));
                            LegacyColumnValues legacy = legacyColumns.getOrDefault(column.name().toLowerCase(Locale.ROOT), null);
                        if (legacy != null) {
                            entity.setTags(legacy.tags());
                            entity.setSensitiveTags(legacy.sensitiveTags());
                            if (StringUtils.hasText(legacy.status())) {
                                entity.setStatus(legacy.status());
                                }
                                if (legacy.standardId() != null) {
                                    entity.setStandardId(legacy.standardId());
                                }
                                if (StringUtils.hasText(legacy.standardRule())) {
                                    entity.setStandardRule(legacy.standardRule());
                                }
                                if (StringUtils.hasText(legacy.standardMismatchReason())) {
                                    entity.setStandardMismatchReason(legacy.standardMismatchReason());
                            }
                        }
                        if (!StringUtils.hasText(entity.getStatus())) {
                            entity.setStatus("ACTIVE");
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
                            recordSchemaDrift(runId, "JDBC", savedDataset, normalizedSchema, tableName, drift);
                        }
                    }

                    if (table.isView()) {
                        String viewDefinition = fetchViewDefinition(connection, dbProduct, resolvedCatalog, normalizedSchema, table.tableName());
                        if (StringUtils.hasText(viewDefinition)) {
                            try {
                                autoLineageService.syncAutoViewLineage(savedDataset, viewDefinition);
                            } catch (Exception ex) {
                                LOG.debug("Auto lineage sync skipped for {}.{}: {}", normalizedSchema, table.tableName(), ex.getMessage());
                            }
                        }
                    }
                }

                if (cleanupStale) {
                    datasetsRemoved += cleanupStaleDatasets(source.getId(), normalizedSchema, processedTablesLower);
                }
            }

            long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            JdbcSyncResult result = new JdbcSyncResult(
                source.getId(),
                "SUCCESS",
                null,
                elapsedMs,
                dbProduct,
                dbVersion,
                schemas,
                tablesDiscovered,
                datasetsCreated,
                datasetsUpdated,
                tablesCreated,
                columnsImported,
                datasetsRemoved
            );
            LOG.info(
                "JDBC catalog sync completed: source={}, schemas={}, created={}, updated={}, tablesCreated={}, columnsImported={}, removed={}, elapsedMs={}",
                source.getName(),
                schemas.size(),
                datasetsCreated,
                datasetsUpdated,
                tablesCreated,
                columnsImported,
                datasetsRemoved,
                elapsedMs
            );
            return result;
        } catch (Exception ex) {
            long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOG.warn("JDBC catalog sync failed: source={} cause={}", source.getName(), ex.getMessage());
            return new JdbcSyncResult(
                source.getId(),
                "FAILED",
                truncate(ex.getMessage()),
                elapsedMs,
                null,
                null,
                schemas,
                tablesDiscovered,
                datasetsCreated,
                datasetsUpdated,
                tablesCreated,
                columnsImported,
                datasetsRemoved
            );
        }
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

    private boolean isJdbcCatalogCandidate(InfraDataSource source) {
        if (source == null) {
            return false;
        }
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            return false;
        }
        if (!StringUtils.hasText(source.getType())) {
            return true;
        }
        String normalized = source.getType().trim().toUpperCase(Locale.ROOT);
        if (TYPE_INCEPTOR.equals(normalized)) {
            return false;
        }
        Map<String, Object> props = readProps(source.getProps());
        String managedBy = stringProp(props, "managedBy");
        if ("PLATFORM".equalsIgnoreCase(managedBy)) {
            return false;
        }
        return true;
    }

    private Connection openConnection(InfraDataSource source, String password, Map<String, Object> props) throws SQLException {
        String url = source.getJdbcUrl().trim();
        String username = StringUtils.hasText(source.getUsername()) ? source.getUsername().trim() : null;

        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader jdbcLoader = hiveConnectionService != null ? hiveConnectionService.getJdbcDriverLoader() : null;
        if (jdbcLoader != null) {
            Thread.currentThread().setContextClassLoader(jdbcLoader);
        }
        try {
            Object driverClass = props.get("driverClass");
            if (driverClass == null) {
                driverClass = props.get("driver_class");
            }
            if (driverClass != null && StringUtils.hasText(String.valueOf(driverClass))) {
                try {
                    String cn = String.valueOf(driverClass).trim();
                    if (jdbcLoader != null) {
                        Class.forName(cn, true, jdbcLoader);
                    } else {
                        Class.forName(cn);
                    }
                } catch (Throwable ex) {
                    LOG.warn("Failed to load JDBC driver class {} for {}: {}", driverClass, url, ex.getMessage());
                }
            }
        java.util.Properties jdbcProps = new java.util.Properties();
        if (StringUtils.hasText(username)) {
            jdbcProps.setProperty("user", username);
        }
        if (StringUtils.hasText(password)) {
            jdbcProps.setProperty("password", password);
        }
        Object jdbcProperties = props.get("jdbcProperties");
        if (jdbcProperties instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String k = entry.getKey() != null ? entry.getKey().toString() : null;
                String v = entry.getValue() != null ? entry.getValue().toString() : null;
                if (StringUtils.hasText(k) && v != null) {
                    jdbcProps.setProperty(k.trim(), v);
                }
            }
        }
            return DriverManager.getConnection(url, jdbcProps);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    private List<TableMeta> listTables(Connection connection, String catalog, String schema, String tablePattern) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<TableMeta> tables = new ArrayList<>();
        String normalizedSchema = StringUtils.hasText(schema) ? schema : null;
        String normalizedCatalog = StringUtils.hasText(catalog) ? catalog : null;
        try (ResultSet rs = meta.getTables(normalizedCatalog, normalizedSchema, tablePattern, new String[] { "TABLE", "VIEW" })) {
            while (rs.next()) {
                String tableName = safe(rs.getString("TABLE_NAME"));
                if (!StringUtils.hasText(tableName)) {
                    continue;
                }
                String tableType = safe(rs.getString("TABLE_TYPE"));
                String remarks = safe(rs.getString("REMARKS"));
                tables.add(new TableMeta(tableName, tableType, remarks));
            }
        }
        return tables;
    }

    private List<ColumnMeta> listColumns(Connection connection, String catalog, String schema, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<ColumnMeta> columns = new ArrayList<>();
        String normalizedSchema = StringUtils.hasText(schema) ? schema : null;
        String normalizedCatalog = StringUtils.hasText(catalog) ? catalog : null;
        try (ResultSet rs = meta.getColumns(normalizedCatalog, normalizedSchema, table, "%")) {
            while (rs.next()) {
                String columnName = safe(rs.getString("COLUMN_NAME"));
                if (!StringUtils.hasText(columnName)) {
                    continue;
                }
                String dataType = safe(rs.getString("TYPE_NAME"));
                Integer nullableValue = safeInt(rs.getObject("NULLABLE"));
                boolean nullable = nullableValue == null || nullableValue.intValue() == DatabaseMetaData.columnNullable;
                String comment = safe(rs.getString("REMARKS"));
                columns.add(new ColumnMeta(columnName, safeDataType(dataType), nullable, comment));
            }
        }
        return columns;
    }

    private int cleanupStaleDatasets(UUID sourceId, String schema, Set<String> processedTablesLower) {
        if (sourceId == null || !StringUtils.hasText(schema) || processedTablesLower == null) {
            return 0;
        }
        List<CatalogDataset> existing = datasetRepository.findBySourceIdAndHiveDatabaseIgnoreCase(sourceId, schema);
        if (existing.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (CatalogDataset dataset : existing) {
            if (dataset.getId() == null) {
                continue;
            }
            if (!Objects.equals(sourceId, dataset.getSourceId())) {
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
        }
    }

    private List<String> resolveSchemas(Map<String, Object> props, InfraDataSource source) {
        if (props == null || props.isEmpty()) {
            return extractSchemasFromJdbcUrl(source != null ? source.getJdbcUrl() : null);
        }
        Object schemas = props.get("schemas");
        if (schemas instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String value = item != null ? item.toString().trim() : "";
                if (StringUtils.hasText(value)) {
                    out.add(value);
                }
            }
            return out;
        }
        String schema = stringProp(props, "schema");
        if (StringUtils.hasText(schema)) {
            return List.of(schema.trim());
        }
        List<String> fromJdbcProps = extractSchemasFromJdbcProps(props);
        if (!fromJdbcProps.isEmpty()) {
            return fromJdbcProps;
        }
        List<String> fromJdbcUrl = extractSchemasFromJdbcUrl(source != null ? source.getJdbcUrl() : null);
        if (!fromJdbcUrl.isEmpty()) {
            return fromJdbcUrl;
        }
        String database = stringProp(props, "database");
        if (StringUtils.hasText(database)) {
            return List.of(database.trim());
        }
        return List.of();
    }

    private List<String> extractSchemasFromJdbcProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return List.of();
        }
        Object jdbcProperties = props.get("jdbcProperties");
        if (!(jdbcProperties instanceof Map<?, ?> map)) {
            return List.of();
        }
        String schemaValue = lookupSchemaValue(map);
        return splitSchemas(schemaValue);
    }

    private String lookupSchemaValue(Map<?, ?> map) {
        for (String key : List.of("currentSchema", "current_schema", "schema", "searchpath", "search_path")) {
            Object value = map.get(key);
            if (value == null) {
                value = map.get(key.toLowerCase(Locale.ROOT));
            }
            if (value != null) {
                String text = value.toString().trim();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private List<String> extractSchemasFromJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return List.of();
        }
        int idx = jdbcUrl.indexOf('?');
        if (idx < 0 || idx == jdbcUrl.length() - 1) {
            return List.of();
        }
        String query = jdbcUrl.substring(idx + 1);
        String[] pairs = query.split("[&;]");
        for (String pair : pairs) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = pair.substring(eq + 1).trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (key.equals("currentschema") || key.equals("current_schema") || key.equals("schema") || key.equals("searchpath") || key.equals("search_path")) {
                return splitSchemas(urlDecode(value));
            }
        }
        return List.of();
    }

    private List<String> discoverSchemas(Connection connection, String catalog, InfraDataSource source) {
        if (connection == null) {
            return List.of();
        }
        String product = databaseProduct(connection);
        DatabaseMetaData meta;
        try {
            meta = connection.getMetaData();
        } catch (SQLException ex) {
            return List.of();
        }
        String currentCatalog = safeCatalog(connection);
        if (product.contains("mysql") || product.contains("mariadb")) {
            String database = StringUtils.hasText(currentCatalog) ? currentCatalog : parseDatabaseName(source);
            if (StringUtils.hasText(database)) {
                return List.of(database);
            }
            return filterSchemas(listCatalogs(meta), product);
        }
        List<String> schemas = listSchemas(meta, catalog);
        if (schemas.isEmpty()) {
            schemas = listSchemas(meta, null);
        }
        if (schemas.isEmpty() && StringUtils.hasText(currentCatalog)) {
            schemas = List.of(currentCatalog);
        }
        return filterSchemas(schemas, product);
    }

    private List<String> listSchemas(DatabaseMetaData meta, String catalog) {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = meta.getSchemas(catalog, null)) {
            while (rs.next()) {
                String name = safe(rs.getString("TABLE_SCHEM"));
                if (StringUtils.hasText(name)) {
                    schemas.add(name.trim());
                }
            }
        } catch (SQLException ignored) {
            return List.of();
        }
        return schemas;
    }

    private List<String> listCatalogs(DatabaseMetaData meta) {
        List<String> catalogs = new ArrayList<>();
        try (ResultSet rs = meta.getCatalogs()) {
            while (rs.next()) {
                String name = safe(rs.getString("TABLE_CAT"));
                if (StringUtils.hasText(name)) {
                    catalogs.add(name.trim());
                }
            }
        } catch (SQLException ignored) {
            return List.of();
        }
        return catalogs;
    }

    private List<String> filterSchemas(List<String> schemas, String product) {
        if (schemas == null || schemas.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String schema : schemas) {
            if (!StringUtils.hasText(schema)) {
                continue;
            }
            String normalized = schema.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.startsWith("pg_") || lower.startsWith("pg_toast")) {
                continue;
            }
            if (lower.equals("information_schema") || lower.equals("pg_catalog") || lower.equals("mysql") || lower.equals("performance_schema") || lower.equals("sys")) {
                continue;
            }
            if (lower.equals("sys") || lower.equals("system") || lower.equals("sysdba") || lower.equals("public")) {
                if (product.contains("dm") || product.contains("oracle")) {
                    continue;
                }
            }
            out.add(normalized);
        }
        return out;
    }

    private String parseDatabaseName(InfraDataSource source) {
        if (source == null || !StringUtils.hasText(source.getJdbcUrl())) {
            return null;
        }
        String url = source.getJdbcUrl().trim();
        int slash = url.indexOf("://");
        if (slash < 0) {
            return null;
        }
        int path = url.indexOf('/', slash + 3);
        if (path < 0 || path == url.length() - 1) {
            return null;
        }
        String tail = url.substring(path + 1);
        int query = tail.indexOf('?');
        String database = query >= 0 ? tail.substring(0, query) : tail;
        database = database.trim();
        return StringUtils.hasText(database) ? database : null;
    }

    private String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private List<String> splitSchemas(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String[] parts = value.split("[,;]");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part != null ? part.trim() : "";
            if (StringUtils.hasText(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private String resolveCatalog(Map<String, Object> props, Connection connection) {
        String configured = stringProp(props, "catalog");
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        try {
            return safe(connection.getCatalog());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeSchema(String schema, Connection connection) {
        if (StringUtils.hasText(schema)) {
            return schema.trim();
        }
        String current = nullSafeSchema(connection);
        if (StringUtils.hasText(current)) {
            return current;
        }
        String product = databaseProduct(connection);
        if (product.contains("postgres")) {
            return "public";
        }
        String catalog = safeCatalog(connection);
        if (StringUtils.hasText(catalog)) {
            return catalog;
        }
        return "default";
    }

    private String databaseProduct(Connection connection) {
        if (connection == null) {
            return "";
        }
        try {
            String name = safe(connection.getMetaData().getDatabaseProductName());
            return name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String nullSafeSchema(Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            return safe(connection.getSchema());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeCatalog(Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            return safe(connection.getCatalog());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchViewDefinition(Connection connection, String databaseProduct, String catalog, String schema, String view) {
        if (connection == null || !StringUtils.hasText(view)) {
            return null;
        }
        String product = databaseProduct != null ? databaseProduct.trim().toLowerCase(Locale.ROOT) : "";
        if (product.contains("postgres")) {
            return fetchViewDefinitionPostgres(connection, schema, view);
        }
        if (product.contains("oracle") || product.contains("dm")) {
            return fetchViewDefinitionOracleLike(connection, schema, view);
        }
        return fetchViewDefinitionInformationSchema(connection, schema, view);
    }

    private String fetchViewDefinitionPostgres(Connection connection, String schema, String view) {
        String sql =
            """
            SELECT view_definition
            FROM information_schema.views
            WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)
            LIMIT 1
            """;
        return fetchSingleSql(connection, sql, schema, view, "view_definition");
    }

    private String fetchViewDefinitionInformationSchema(Connection connection, String schema, String view) {
        String sql =
            """
            SELECT view_definition
            FROM information_schema.views
            WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)
            """;
        return fetchSingleSql(connection, sql, schema, view, "view_definition");
    }

    private String fetchViewDefinitionOracleLike(Connection connection, String schema, String view) {
        String normalizedView = view.trim().toUpperCase(Locale.ROOT);
        String sqlUser = "SELECT TEXT AS view_definition FROM USER_VIEWS WHERE VIEW_NAME = ?";
        String def = fetchSingleSql(connection, sqlUser, normalizedView, "view_definition");
        if (StringUtils.hasText(def)) {
            return def;
        }
        if (StringUtils.hasText(schema)) {
            String sqlAll = "SELECT TEXT AS view_definition FROM ALL_VIEWS WHERE VIEW_NAME = ? AND OWNER = ?";
            def = fetchSingleSql(connection, sqlAll, normalizedView, schema.trim().toUpperCase(Locale.ROOT), "view_definition");
            if (StringUtils.hasText(def)) {
                return def;
            }
        }
        return null;
    }

    private String fetchSingleSql(Connection connection, String sql, String arg1, String column) {
        return fetchSingleSql(connection, sql, arg1, null, column);
    }

    private String fetchSingleSql(Connection connection, String sql, String arg1, String arg2, String column) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, arg1);
            if (arg2 != null) {
                stmt.setString(2, arg2);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(column);
                    return def != null ? def.trim() : null;
                }
            }
        } catch (SQLException ex) {
            return null;
        }
        return null;
    }

    private String defaultOwner(InfraDataSource source) {
        if (source == null) {
            return DEFAULT_OWNER;
        }
        if (StringUtils.hasText(source.getUsername())) {
            return source.getUsername().trim();
        }
        return DEFAULT_OWNER;
    }

    private Map<String, Object> readProps(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private String stringProp(Map<String, Object> map, String key) {
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text != null ? text.trim() : null;
    }

    private boolean boolProp(Map<String, Object> map, String key, boolean fallback) {
        if (map == null || !StringUtils.hasText(key)) {
            return fallback;
        }
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b.booleanValue();
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        return Boolean.parseBoolean(text.trim());
    }

    private Integer safeInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value != null ? value.trim() : null;
    }

    private String defaultIfBlank(String current, String fallback) {
        return StringUtils.hasText(current) ? current : fallback;
    }

    private String safeDataType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "string";
    }

    private String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if ("null".equalsIgnoreCase(trimmed) || "\\n".equalsIgnoreCase(trimmed) || "n/a".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) : trimmed;
    }

    private record LegacyColumnValues(
        String comment,
        String tags,
        String sensitiveTags,
        String status,
        java.util.UUID standardId,
        String standardRule,
        String standardMismatchReason
    ) {}

    private record TableMeta(String tableName, String tableType, String remarks) {
        boolean isView() {
            if (!StringUtils.hasText(tableType)) {
                return false;
            }
            return tableType.trim().toUpperCase(Locale.ROOT).contains("VIEW");
        }
    }

    private record ColumnMeta(String name, String dataType, boolean nullable, String comment) {}

    public record JdbcSyncResult(
        UUID sourceId,
        String status,
        String error,
        long elapsedMs,
        String databaseProduct,
        String databaseVersion,
        List<String> schemas,
        int tablesDiscovered,
        int datasetsCreated,
        int datasetsUpdated,
        int tablesCreated,
        int columnsImported,
        int datasetsRemoved
    ) {
        public static JdbcSyncResult skipped(UUID sourceId, String reason) {
            return new JdbcSyncResult(sourceId, "SKIPPED", reason, 0L, null, null, List.of(), 0, 0, 0, 0, 0, 0);
        }

        public static JdbcSyncResult failed(UUID sourceId, String error) {
            return new JdbcSyncResult(sourceId, "FAILED", error, 0L, null, null, List.of(), 0, 0, 0, 0, 0, 0);
        }
    }
}
