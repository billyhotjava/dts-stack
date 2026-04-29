package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraSchemaDiscoverCacheRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverColumnDto;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverDriftDto;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverIndexDto;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverRequest;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverResponse;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverTableDto;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
    private static final String DEFAULT_CLASSIFICATION = SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code();
    private static final String DEFAULT_OWNER = "system";
    private static final String DEFAULT_EXPOSED_BY = "VIEW";
    private static final String LIFECYCLE_SYNCED = "SYNCED";
    private static final String LIFECYCLE_STALE = "STALE";
    private static final String STALE_MODE_MARK = "MARK";
    private static final String STALE_MODE_PURGE = "PURGE";

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
    private final InfraSchemaDiscoverCacheRepository schemaDiscoverCacheRepository;

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
        SchemaDriftDetector schemaDriftDetector,
        InfraSchemaDiscoverCacheRepository schemaDiscoverCacheRepository
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
        this.schemaDiscoverCacheRepository = schemaDiscoverCacheRepository;
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
        int datasetsMarkedStale = 0;
        int datasetsPurged = 0;
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
                    dataset.setEnabled(Boolean.TRUE);
                    dataset.setLifecycleStatus(LIFECYCLE_SYNCED);

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
                    StaleCleanupStats cleanupStats = cleanupStaleDatasets(
                        source.getId(),
                        normalizedSchema,
                        processedTablesLower,
                        resolveStaleCleanupMode(props),
                        snapshotTime
                    );
                    datasetsMarkedStale += cleanupStats.marked();
                    datasetsPurged += cleanupStats.purged();
                    datasetsRemoved += cleanupStats.totalRemoved();
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
                datasetsRemoved,
                datasetsMarkedStale,
                datasetsPurged
            );
            LOG.info(
                "JDBC catalog sync completed: source={}, schemas={}, created={}, updated={}, tablesCreated={}, columnsImported={}, removed={}, markedStale={}, purged={}, elapsedMs={}",
                source.getName(),
                schemas.size(),
                datasetsCreated,
                datasetsUpdated,
                tablesCreated,
                columnsImported,
                datasetsRemoved,
                datasetsMarkedStale,
                datasetsPurged,
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
                datasetsRemoved,
                datasetsMarkedStale,
                datasetsPurged
            );
        }
    }

    public SchemaDiscoverResponse discover(InfraDataSource source, SchemaDiscoverRequest request) {
        long startedAt = System.nanoTime();
        Instant discoveredAt = Instant.now();
        if (source == null || source.getId() == null) {
            return discoverFailure(null, null, null, elapsedMs(startedAt), "invalid-source", discoveredAt);
        }
        if (!isJdbcCatalogCandidate(source)) {
            return discoverFailure(source.getId(), source.getName(), source.getConnectorKey(), elapsedMs(startedAt), "not-a-jdbc-source", discoveredAt);
        }
        Map<String, Object> props = readProps(source.getProps());
        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = stringProp(secrets, "password");
        if (!StringUtils.hasText(password)) {
            password = stringProp(props, "password");
        }
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            return discoverFailure(source.getId(), source.getName(), source.getConnectorKey(), elapsedMs(startedAt), "missing-jdbc-url", discoveredAt);
        }

        String tablePattern = Optional
            .ofNullable(request == null ? null : request.tablePattern())
            .filter(StringUtils::hasText)
            .orElseGet(() -> Optional.ofNullable(stringProp(props, "tablePattern")).filter(StringUtils::hasText).orElse("%"));
        int maxTables = clamp(request == null ? null : request.maxTables(), 1, 500, 100);
        int sampleLimit = clamp(request == null ? null : request.sampleLimit(), 0, 200, 0);
        boolean includeColumns = request == null || request.includeColumns() == null || Boolean.TRUE.equals(request.includeColumns());
        boolean includeIndexes = request == null || request.includeIndexes() == null || Boolean.TRUE.equals(request.includeIndexes());
        boolean includeSample = sampleLimit > 0 && Boolean.TRUE.equals(request == null ? Boolean.FALSE : request.includeSample());
        boolean useCache = request == null || request.useCache() == null || Boolean.TRUE.equals(request.useCache());
        boolean forceRefresh = request != null && Boolean.TRUE.equals(request.forceRefresh());
        String cacheKey = discoverCacheKey(request, tablePattern, maxTables, sampleLimit, includeColumns, includeIndexes, includeSample);
        InfraSchemaDiscoverCache previousCache = schemaDiscoverCacheRepository
            .findFirstByDataSourceIdAndCacheKeyAndEnabledTrueOrderByRefreshedAtDesc(source.getId(), cacheKey)
            .orElse(null);
        if (useCache && !forceRefresh && previousCache != null && StringUtils.hasText(previousCache.getResponseJson())) {
            SchemaDiscoverResponse cached = parseCachedDiscoverResponse(previousCache);
            if (cached != null && "SUCCESS".equalsIgnoreCase(cached.status())) {
                return withDiscoverCache(cached, true, previousCache.getCacheKey(), driftFromCache(previousCache));
            }
        }

        try (Connection connection = openConnection(source, password, props)) {
            String databaseProduct = safe(connection.getMetaData().getDatabaseProductName());
            String databaseVersion = safe(connection.getMetaData().getDatabaseProductVersion());
            String resolvedCatalog = resolveCatalog(props, connection);
            List<String> schemas = resolveDiscoverSchemas(request, props, source, connection, resolvedCatalog);
            List<SchemaDiscoverTableDto> discoveredTables = new ArrayList<>();
            for (String schema : schemas) {
                if (discoveredTables.size() >= maxTables) {
                    break;
                }
                String normalizedSchema = normalizeSchema(schema, connection);
                List<TableMeta> tables = listTables(connection, resolvedCatalog, normalizedSchema, tablePattern);
                for (TableMeta table : tables) {
                    if (discoveredTables.size() >= maxTables) {
                        break;
                    }
                    List<ColumnMeta> columns = includeColumns
                        ? listColumns(connection, resolvedCatalog, normalizedSchema, table.tableName())
                        : List.of();
                    List<String> primaryKeys = includeColumns
                        ? listPrimaryKeys(connection, resolvedCatalog, normalizedSchema, table.tableName())
                        : List.of();
                    List<SchemaDiscoverIndexDto> indexes = includeIndexes
                        ? listIndexes(connection, resolvedCatalog, normalizedSchema, table.tableName())
                        : List.of();
                    Set<String> indexedColumns = indexColumnSet(indexes);
                    List<String> candidates = recommendIncrementalColumns(columns);
                    Set<String> candidateSet = lowerSet(candidates);
                    Set<String> pkSet = lowerSet(primaryKeys);
                    List<SchemaDiscoverColumnDto> columnDtos = columns
                        .stream()
                        .map(column ->
                            new SchemaDiscoverColumnDto(
                                column.name(),
                                column.dataType(),
                                column.nativeType(),
                                column.nullable(),
                                column.defaultValue(),
                                normalizeComment(column.comment()),
                                column.ordinalPosition(),
                                pkSet.contains(column.name().toLowerCase(Locale.ROOT)),
                                indexedColumns.contains(column.name().toLowerCase(Locale.ROOT)),
                                candidateSet.contains(column.name().toLowerCase(Locale.ROOT))
                            )
                        )
                        .toList();
                    List<Map<String, Object>> sampleRows = includeSample
                        ? sampleRows(connection, normalizedSchema, table.tableName(), sampleLimit)
                        : List.of();
                    discoveredTables.add(
                        new SchemaDiscoverTableDto(
                            normalizedSchema,
                            table.tableName(),
                            table.tableType(),
                            normalizeComment(table.remarks()),
                            table.isView(),
                            columnDtos,
                            primaryKeys,
                            indexes,
                            candidates,
                            sampleRows
                        )
                    );
                }
            }
            SchemaDiscoverResponse fresh = new SchemaDiscoverResponse(
                source.getId(),
                source.getName(),
                source.getConnectorKey(),
                databaseProduct,
                databaseVersion,
                schemas,
                discoveredTables,
                elapsedMs(startedAt),
                "SUCCESS",
                null,
                discoveredAt,
                false,
                cacheKey,
                null
            );
            SchemaDiscoverDriftDto drift = previousCache == null ? null : diffDiscoverResponses(parseCachedDiscoverResponse(previousCache), fresh);
            SchemaDiscoverResponse response = withDiscoverCache(fresh, false, cacheKey, drift);
            saveDiscoverCache(source, request, tablePattern, response);
            return response;
        } catch (Exception ex) {
            LOG.warn("Schema discover failed for datasource {}: {}", source.getId(), ex.getMessage());
            return discoverFailure(source.getId(), source.getName(), source.getConnectorKey(), elapsedMs(startedAt), truncate(ex.getMessage()), discoveredAt);
        }
    }

    private SchemaDiscoverResponse parseCachedDiscoverResponse(InfraSchemaDiscoverCache cache) {
        if (cache == null || !StringUtils.hasText(cache.getResponseJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(cache.getResponseJson(), SchemaDiscoverResponse.class);
        } catch (Exception ex) {
            LOG.debug("Failed to parse schema discover cache {}: {}", cache.getId(), ex.getMessage());
            return null;
        }
    }

    private void saveDiscoverCache(
        InfraDataSource source,
        SchemaDiscoverRequest request,
        String tablePattern,
        SchemaDiscoverResponse response
    ) {
        if (source == null || source.getId() == null || response == null || !StringUtils.hasText(response.cacheKey())) {
            return;
        }
        try {
            InfraSchemaDiscoverCache cache = schemaDiscoverCacheRepository
                .findFirstByDataSourceIdAndCacheKeyAndEnabledTrueOrderByRefreshedAtDesc(source.getId(), response.cacheKey())
                .orElseGet(InfraSchemaDiscoverCache::new);
            cache.setDataSourceId(source.getId());
            cache.setCacheKey(response.cacheKey());
            cache.setSchemaName(request == null ? null : safe(request.schema()));
            cache.setTablePattern(tablePattern);
            cache.setStatus(response.status());
            cache.setTableCount(response.tables() == null ? 0 : response.tables().size());
            cache.setColumnCount(countDiscoverColumns(response));
            SchemaDiscoverDriftDto drift = response.drift();
            cache.setDriftAddedTables(drift == null ? 0 : drift.addedTables());
            cache.setDriftRemovedTables(drift == null ? 0 : drift.removedTables());
            cache.setDriftChangedTables(drift == null ? 0 : drift.changedTables());
            cache.setDriftDetailsJson(drift == null ? null : drift.detailsJson());
            cache.setResponseJson(objectMapper.writeValueAsString(withDiscoverCache(response, false, response.cacheKey(), response.drift())));
            cache.setRefreshedAt(response.discoveredAt() == null ? Instant.now() : response.discoveredAt());
            cache.setEnabled(Boolean.TRUE);
            schemaDiscoverCacheRepository.save(cache);
        } catch (Exception ex) {
            LOG.debug("Failed to save schema discover cache for datasource {}: {}", source.getId(), ex.getMessage());
        }
    }

    private SchemaDiscoverResponse withDiscoverCache(
        SchemaDiscoverResponse source,
        boolean cached,
        String cacheKey,
        SchemaDiscoverDriftDto drift
    ) {
        if (source == null) {
            return null;
        }
        return new SchemaDiscoverResponse(
            source.dataSourceId(),
            source.dataSourceName(),
            source.connectorKey(),
            source.databaseProduct(),
            source.databaseVersion(),
            source.schemas(),
            source.tables(),
            source.elapsedMs(),
            source.status(),
            source.error(),
            source.discoveredAt(),
            cached,
            StringUtils.hasText(cacheKey) ? cacheKey : source.cacheKey(),
            drift != null ? drift : source.drift()
        );
    }

    private SchemaDiscoverDriftDto driftFromCache(InfraSchemaDiscoverCache cache) {
        if (cache == null) {
            return null;
        }
        int added = cache.getDriftAddedTables() == null ? 0 : cache.getDriftAddedTables();
        int removed = cache.getDriftRemovedTables() == null ? 0 : cache.getDriftRemovedTables();
        int changed = cache.getDriftChangedTables() == null ? 0 : cache.getDriftChangedTables();
        if (added == 0 && removed == 0 && changed == 0 && !StringUtils.hasText(cache.getDriftDetailsJson())) {
            return null;
        }
        return new SchemaDiscoverDriftDto(added, removed, changed, cache.getDriftDetailsJson());
    }

    private SchemaDiscoverDriftDto diffDiscoverResponses(SchemaDiscoverResponse before, SchemaDiscoverResponse after) {
        if (before == null || after == null) {
            return null;
        }
        Map<String, SchemaDiscoverTableDto> beforeTables = discoverTableMap(before);
        Map<String, SchemaDiscoverTableDto> afterTables = discoverTableMap(after);
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(beforeTables.keySet());
        allKeys.addAll(afterTables.keySet());
        for (String key : allKeys) {
            SchemaDiscoverTableDto oldTable = beforeTables.get(key);
            SchemaDiscoverTableDto newTable = afterTables.get(key);
            if (oldTable == null && newTable != null) {
                added.add(physicalTableName(newTable.schema(), newTable.name()));
                continue;
            }
            if (oldTable != null && newTable == null) {
                removed.add(physicalTableName(oldTable.schema(), oldTable.name()));
                continue;
            }
            List<Map<String, Object>> columnChanges = diffDiscoverColumns(oldTable, newTable);
            if (!columnChanges.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("table", physicalTableName(newTable.schema(), newTable.name()));
                entry.put("columns", columnChanges);
                changed.add(entry);
            }
        }
        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return null;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (!added.isEmpty()) {
            details.put("addedTables", added);
        }
        if (!removed.isEmpty()) {
            details.put("removedTables", removed);
        }
        if (!changed.isEmpty()) {
            details.put("changedTables", changed);
        }
        return new SchemaDiscoverDriftDto(added.size(), removed.size(), changed.size(), toJson(details));
    }

    private List<Map<String, Object>> diffDiscoverColumns(SchemaDiscoverTableDto before, SchemaDiscoverTableDto after) {
        if (before == null || after == null) {
            return List.of();
        }
        Map<String, SchemaDiscoverColumnDto> beforeColumns = discoverColumnMap(before.columns());
        Map<String, SchemaDiscoverColumnDto> afterColumns = discoverColumnMap(after.columns());
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(beforeColumns.keySet());
        allKeys.addAll(afterColumns.keySet());
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String key : allKeys) {
            SchemaDiscoverColumnDto oldCol = beforeColumns.get(key);
            SchemaDiscoverColumnDto newCol = afterColumns.get(key);
            if (oldCol == null && newCol != null) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("type", "ADDED");
                change.put("name", newCol.name());
                change.put("dataType", newCol.dataType());
                change.put("nullable", newCol.nullable());
                changes.add(change);
                continue;
            }
            if (oldCol != null && newCol == null) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("type", "REMOVED");
                change.put("name", oldCol.name());
                change.put("dataType", oldCol.dataType());
                change.put("nullable", oldCol.nullable());
                changes.add(change);
                continue;
            }
            if (oldCol == null || newCol == null) {
                continue;
            }
            boolean typeChanged = !Objects.equals(normalizeDiscoverType(oldCol.dataType()), normalizeDiscoverType(newCol.dataType()));
            boolean nullableChanged = oldCol.nullable() != newCol.nullable();
            if (typeChanged || nullableChanged) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("type", "CHANGED");
                change.put("name", newCol.name());
                Map<String, Object> beforeValues = new LinkedHashMap<>();
                beforeValues.put("dataType", oldCol.dataType());
                beforeValues.put("nullable", oldCol.nullable());
                Map<String, Object> afterValues = new LinkedHashMap<>();
                afterValues.put("dataType", newCol.dataType());
                afterValues.put("nullable", newCol.nullable());
                change.put("before", beforeValues);
                change.put("after", afterValues);
                changes.add(change);
            }
        }
        return changes;
    }

    private Map<String, SchemaDiscoverTableDto> discoverTableMap(SchemaDiscoverResponse response) {
        if (response == null || response.tables() == null || response.tables().isEmpty()) {
            return Map.of();
        }
        Map<String, SchemaDiscoverTableDto> result = new LinkedHashMap<>();
        for (SchemaDiscoverTableDto table : response.tables()) {
            if (table == null || !StringUtils.hasText(table.name())) {
                continue;
            }
            result.put((safe(table.schema()) + "." + safe(table.name())).toLowerCase(Locale.ROOT), table);
        }
        return result;
    }

    private Map<String, SchemaDiscoverColumnDto> discoverColumnMap(List<SchemaDiscoverColumnDto> columns) {
        if (columns == null || columns.isEmpty()) {
            return Map.of();
        }
        Map<String, SchemaDiscoverColumnDto> result = new LinkedHashMap<>();
        for (SchemaDiscoverColumnDto column : columns) {
            if (column == null || !StringUtils.hasText(column.name())) {
                continue;
            }
            result.put(column.name().trim().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    private int countDiscoverColumns(SchemaDiscoverResponse response) {
        if (response == null || response.tables() == null) {
            return 0;
        }
        int count = 0;
        for (SchemaDiscoverTableDto table : response.tables()) {
            count += table == null || table.columns() == null ? 0 : table.columns().size();
        }
        return count;
    }

    private String discoverCacheKey(
        SchemaDiscoverRequest request,
        String tablePattern,
        int maxTables,
        int sampleLimit,
        boolean includeColumns,
        boolean includeIndexes,
        boolean includeSample
    ) {
        String raw = String.join(
            "|",
            cachePart(request == null ? null : request.schema()),
            cachePart(tablePattern),
            String.valueOf(maxTables),
            String.valueOf(sampleLimit),
            String.valueOf(includeColumns),
            String.valueOf(includeIndexes),
            String.valueOf(includeSample)
        );
        return "schema-discover:" + sha256(raw);
    }

    private String cachePart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(raw).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception ex) {
            return UUID.nameUUIDFromBytes(String.valueOf(raw).getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    private String normalizeDiscoverType(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : null;
    }

    private String physicalTableName(String schema, String table) {
        return StringUtils.hasText(schema) ? schema.trim() + "." + table : table;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"failed-to-serialize\"}";
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
        event.setPolicyMode(CatalogSchemaDriftEvent.POLICY_REVIEW);
        event.setTicketStatus(CatalogSchemaDriftEvent.TICKET_OPEN);
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
                String defaultValue = safeResultString(rs, "COLUMN_DEF");
                Integer ordinal = safeInt(safeResultObject(rs, "ORDINAL_POSITION"));
                columns.add(new ColumnMeta(columnName, safeDataType(dataType), dataType, nullable, defaultValue, comment, ordinal));
            }
        }
        return columns;
    }

    private List<String> listPrimaryKeys(Connection connection, String catalog, String schema, String table) {
        DatabaseMetaData meta;
        try {
            meta = connection.getMetaData();
        } catch (SQLException ex) {
            return List.of();
        }
        String normalizedSchema = StringUtils.hasText(schema) ? schema : null;
        String normalizedCatalog = StringUtils.hasText(catalog) ? catalog : null;
        Map<Integer, String> keys = new TreeMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(normalizedCatalog, normalizedSchema, table)) {
            while (rs.next()) {
                String columnName = safe(rs.getString("COLUMN_NAME"));
                if (!StringUtils.hasText(columnName)) {
                    continue;
                }
                Integer seq = safeInt(safeResultObject(rs, "KEY_SEQ"));
                keys.put(seq != null ? seq : keys.size() + 1, columnName);
            }
        } catch (SQLException ex) {
            return List.of();
        }
        return List.copyOf(keys.values());
    }

    private List<SchemaDiscoverIndexDto> listIndexes(Connection connection, String catalog, String schema, String table) {
        DatabaseMetaData meta;
        try {
            meta = connection.getMetaData();
        } catch (SQLException ex) {
            return List.of();
        }
        String normalizedSchema = StringUtils.hasText(schema) ? schema : null;
        String normalizedCatalog = StringUtils.hasText(catalog) ? catalog : null;
        Map<String, IndexAccumulator> indexes = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(normalizedCatalog, normalizedSchema, table, false, false)) {
            while (rs.next()) {
                Short type = safeShort(safeResultObject(rs, "TYPE"));
                if (type != null && type.shortValue() == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String indexName = safe(rs.getString("INDEX_NAME"));
                String columnName = safe(rs.getString("COLUMN_NAME"));
                if (!StringUtils.hasText(indexName) || !StringUtils.hasText(columnName)) {
                    continue;
                }
                boolean nonUnique = Boolean.TRUE.equals(safeBoolean(safeResultObject(rs, "NON_UNIQUE")));
                Integer ordinal = safeInt(safeResultObject(rs, "ORDINAL_POSITION"));
                IndexAccumulator accumulator = indexes.computeIfAbsent(indexName, key -> new IndexAccumulator(indexName, !nonUnique));
                accumulator.addColumn(ordinal != null ? ordinal : accumulator.size() + 1, columnName);
            }
        } catch (SQLException ex) {
            return List.of();
        }
        return indexes.values().stream().map(IndexAccumulator::toDto).toList();
    }

    private List<Map<String, Object>> sampleRows(Connection connection, String schema, String table, int limit) {
        if (limit <= 0 || !StringUtils.hasText(table)) {
            return List.of();
        }
        String sql = "SELECT * FROM " + qualifiedName(connection, schema, table);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(limit);
            try (ResultSet rs = statement.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next() && rows.size() < limit) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        String label = meta.getColumnLabel(i);
                        if (!StringUtils.hasText(label)) {
                            label = meta.getColumnName(i);
                        }
                        row.put(label, maskSampleValue(label, rs.getObject(i)));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Sample rows failed for {}.{}: {}", schema, table, ex.getMessage());
        }
        return rows;
    }

    private StaleCleanupStats cleanupStaleDatasets(
        UUID sourceId,
        String schema,
        Set<String> processedTablesLower,
        String staleMode,
        Instant snapshotTime
    ) {
        if (sourceId == null || !StringUtils.hasText(schema) || processedTablesLower == null) {
            return StaleCleanupStats.empty();
        }
        List<CatalogDataset> existing = datasetRepository.findBySourceIdAndHiveDatabaseIgnoreCase(sourceId, schema);
        if (existing.isEmpty()) {
            return StaleCleanupStats.empty();
        }
        int marked = 0;
        int purged = 0;
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
            if (STALE_MODE_PURGE.equalsIgnoreCase(staleMode)) {
                purgeDataset(dataset);
                purged++;
            } else {
                markDatasetStale(dataset, snapshotTime);
                marked++;
            }
        }
        return new StaleCleanupStats(marked, purged, marked + purged);
    }

    private SchemaDiscoverResponse discoverFailure(
        UUID dataSourceId,
        String dataSourceName,
        String connectorKey,
        long elapsedMs,
        String error,
        Instant discoveredAt
    ) {
        return new SchemaDiscoverResponse(
            dataSourceId,
            dataSourceName,
            connectorKey,
            null,
            null,
            List.of(),
            List.of(),
            elapsedMs,
            "FAILED",
            error,
            discoveredAt != null ? discoveredAt : Instant.now(),
            false,
            null,
            null
        );
    }

    private List<String> resolveDiscoverSchemas(
        SchemaDiscoverRequest request,
        Map<String, Object> props,
        InfraDataSource source,
        Connection connection,
        String catalog
    ) {
        if (request != null && StringUtils.hasText(request.schema())) {
            return List.of(request.schema().trim());
        }
        List<String> schemas = resolveSchemas(props, source);
        if (schemas.isEmpty()) {
            schemas = discoverSchemas(connection, catalog, source);
        }
        if (schemas.isEmpty()) {
            schemas = List.of("");
        }
        return schemas;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int raw = value == null ? fallback : value.intValue();
        return Math.max(min, Math.min(max, raw));
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private Set<String> lowerSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                set.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private Set<String> indexColumnSet(List<SchemaDiscoverIndexDto> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return Set.of();
        }
        Set<String> columns = new LinkedHashSet<>();
        for (SchemaDiscoverIndexDto index : indexes) {
            if (index == null || index.columns() == null) {
                continue;
            }
            for (String column : index.columns()) {
                if (StringUtils.hasText(column)) {
                    columns.add(column.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private List<String> recommendIncrementalColumns(List<ColumnMeta> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<String> preferredNames = List.of(
            "updated_at",
            "update_time",
            "modified_at",
            "modify_time",
            "last_updated",
            "last_update_time",
            "gmt_modified",
            "etl_update_time",
            "ts",
            "timestamp"
        );
        List<String> candidates = new ArrayList<>();
        for (String preferred : preferredNames) {
            for (ColumnMeta column : columns) {
                String normalized = column.name() != null ? column.name().trim().toLowerCase(Locale.ROOT) : "";
                if (preferred.equals(normalized) && isIncrementalType(column)) {
                    candidates.add(column.name());
                }
            }
        }
        for (ColumnMeta column : columns) {
            String normalized = column.name() != null ? column.name().trim().toLowerCase(Locale.ROOT) : "";
            if (candidates.stream().anyMatch(item -> item.equalsIgnoreCase(column.name()))) {
                continue;
            }
            if (
                isIncrementalType(column) &&
                (normalized.contains("update") ||
                    normalized.contains("modify") ||
                    normalized.contains("modified") ||
                    normalized.endsWith("_time") ||
                    normalized.endsWith("_date"))
            ) {
                candidates.add(column.name());
            }
        }
        return candidates.size() > 5 ? candidates.subList(0, 5) : candidates;
    }

    private boolean isIncrementalType(ColumnMeta column) {
        if (column == null) {
            return false;
        }
        String type = (column.dataType() + " " + column.nativeType()).toLowerCase(Locale.ROOT);
        return type.contains("time") || type.contains("date") || type.contains("timestamp") || type.contains("datetime");
    }

    private String qualifiedName(Connection connection, String schema, String table) {
        String quotedTable = quoteIdentifier(connection, table);
        if (!StringUtils.hasText(schema)) {
            return quotedTable;
        }
        return quoteIdentifier(connection, schema) + "." + quotedTable;
    }

    private String quoteIdentifier(Connection connection, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        String quote = null;
        try {
            quote = connection.getMetaData().getIdentifierQuoteString();
        } catch (SQLException ignored) {}
        if (!StringUtils.hasText(quote)) {
            return value;
        }
        quote = quote.trim();
        if (!StringUtils.hasText(quote)) {
            return value;
        }
        return quote + value.replace(quote, quote + quote) + quote;
    }

    private Object maskSampleValue(String columnName, Object value) {
        if (value == null) {
            return null;
        }
        String normalized = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
        if (
            normalized.contains("password") ||
            normalized.contains("passwd") ||
            normalized.contains("secret") ||
            normalized.contains("token") ||
            normalized.contains("private") ||
            normalized.contains("phone") ||
            normalized.contains("mobile") ||
            normalized.contains("email") ||
            normalized.contains("id_card")
        ) {
            return "***";
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toString();
        }
        if (value instanceof byte[]) {
            return "<binary>";
        }
        return value;
    }

    private String resolveStaleCleanupMode(Map<String, Object> props) {
        if (boolProp(props, "catalogPurgeStale", false)) {
            return STALE_MODE_PURGE;
        }
        String configured = stringProp(props, "catalogCleanupMode");
        if (!StringUtils.hasText(configured)) {
            return STALE_MODE_MARK;
        }
        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        if (STALE_MODE_PURGE.equals(normalized) || STALE_MODE_MARK.equals(normalized)) {
            return normalized;
        }
        return STALE_MODE_MARK;
    }

    private void markDatasetStale(CatalogDataset dataset, Instant snapshotTime) {
        if (dataset == null || dataset.getId() == null) {
            return;
        }
        try {
            dataset.setEnabled(Boolean.FALSE);
            dataset.setLifecycleStatus(LIFECYCLE_STALE);
            dataset.setSnapshotTime(snapshotTime != null ? snapshotTime : Instant.now());
            datasetRepository.save(dataset);
        } catch (Exception ex) {
            LOG.warn("Failed to mark stale dataset {}({}): {}", dataset.getName(), dataset.getId(), ex.getMessage());
        }
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

    private Short safeShort(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.shortValue();
        }
        try {
            return Short.parseShort(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private Boolean safeBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Object safeResultObject(ResultSet rs, String column) {
        try {
            return rs.getObject(column);
        } catch (SQLException ex) {
            return null;
        }
    }

    private String safeResultString(ResultSet rs, String column) {
        try {
            return safe(rs.getString(column));
        } catch (SQLException ex) {
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

    private record ColumnMeta(
        String name,
        String dataType,
        String nativeType,
        boolean nullable,
        String defaultValue,
        String comment,
        Integer ordinalPosition
    ) {}

    private static final class IndexAccumulator {
        private final String name;
        private final boolean unique;
        private final Map<Integer, String> columns = new TreeMap<>();

        private IndexAccumulator(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        private void addColumn(int ordinal, String column) {
            if (StringUtils.hasText(column)) {
                columns.put(ordinal, column);
            }
        }

        private int size() {
            return columns.size();
        }

        private SchemaDiscoverIndexDto toDto() {
            return new SchemaDiscoverIndexDto(name, unique, List.copyOf(columns.values()));
        }
    }

    private record StaleCleanupStats(int marked, int purged, int totalRemoved) {
        private static StaleCleanupStats empty() {
            return new StaleCleanupStats(0, 0, 0);
        }
    }

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
        int datasetsRemoved,
        int datasetsMarkedStale,
        int datasetsPurged
    ) {
        public static JdbcSyncResult skipped(UUID sourceId, String reason) {
            return new JdbcSyncResult(sourceId, "SKIPPED", reason, 0L, null, null, List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public static JdbcSyncResult failed(UUID sourceId, String error) {
            return new JdbcSyncResult(sourceId, "FAILED", error, 0L, null, null, List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
