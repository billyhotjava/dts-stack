package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final ObjectMapper objectMapper;
    private final CatalogDomainRepository domainRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogAutoLineageService autoLineageService;

    public JdbcCatalogSyncService(
        InfraDataSourceRepository infraDataSourceRepository,
        InfraSecretService secretService,
        ObjectMapper objectMapper,
        CatalogDomainRepository domainRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogAutoLineageService autoLineageService
    ) {
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
        this.domainRepository = domainRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.autoLineageService = autoLineageService;
    }

    public List<JdbcSyncResult> synchronizeAllActive() {
        List<InfraDataSource> sources = infraDataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<JdbcSyncResult> results = new ArrayList<>();
        for (InfraDataSource source : sources) {
            if (!isJdbcCatalogCandidate(source)) {
                continue;
            }
            results.add(synchronize(source));
        }
        return results;
    }

    public JdbcSyncResult synchronize(InfraDataSource source) {
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
        if (!StringUtils.hasText(source.getJdbcUrl())) {
            return JdbcSyncResult.failed(source.getId(), "missing-jdbc-url");
        }

        List<String> schemas = resolveSchemas(props);
        if (schemas.isEmpty()) {
            schemas = List.of("");
        }

        boolean cleanupStale = boolProp(props, "catalogCleanupStale", false);
        String tablePattern = Optional.ofNullable(stringProp(props, "tablePattern")).filter(StringUtils::hasText).orElse("%");

        int datasetsCreated = 0;
        int datasetsUpdated = 0;
        int tablesCreated = 0;
        int columnsImported = 0;
        int datasetsRemoved = 0;

        long startedAt = System.nanoTime();
        try (Connection connection = openConnection(source, password, props)) {
            String dbProduct = safe(connection.getMetaData().getDatabaseProductName());
            String dbVersion = safe(connection.getMetaData().getDatabaseProductVersion());
            String resolvedCatalog = resolveCatalog(props, connection);

            CatalogDomain sourceDomain = resolveOrCreateSourceDomain(source);

            for (String schema : schemas) {
                String normalizedSchema = normalizeSchema(schema, connection);
                CatalogDomain schemaDomain = resolveOrCreateSchemaDomain(sourceDomain, source, normalizedSchema);

                Set<String> processedTablesLower = new LinkedHashSet<>();
                List<TableMeta> tables = listTables(connection, resolvedCatalog, normalizedSchema, tablePattern);
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
                    dataset.setType(StringUtils.hasText(source.getType()) ? source.getType().trim().toUpperCase(Locale.ROOT) : "JDBC");
                    dataset.setName(defaultIfBlank(dataset.getName(), tableName));
                    dataset.setClassification(defaultIfBlank(dataset.getClassification(), DEFAULT_CLASSIFICATION));
                    dataset.setOwner(defaultIfBlank(dataset.getOwner(), defaultOwner(source)));
                    dataset.setExposedBy(defaultIfBlank(dataset.getExposedBy(), DEFAULT_EXPOSED_BY));
                    if (dataset.getDomain() == null && schemaDomain != null) {
                        dataset.setDomain(schemaDomain);
                    }

                    CatalogDataset savedDataset = datasetRepository.save(dataset);
                    if (isNewDataset) {
                        datasetsCreated++;
                    } else {
                        datasetsUpdated++;
                    }

                    CatalogTableSchema tableSchema = tableRepository
                        .findFirstByDatasetAndNameIgnoreCase(savedDataset, tableName)
                        .orElseGet(() -> {
                            CatalogTableSchema schemaEntity = new CatalogTableSchema();
                            schemaEntity.setDataset(savedDataset);
                            schemaEntity.setName(tableName);
                            return schemaEntity;
                        });
                    boolean isNewTable = tableSchema.getId() == null;
                    tableSchema.setOwner(defaultIfBlank(tableSchema.getOwner(), savedDataset.getOwner()));
                    tableSchema.setClassification(defaultIfBlank(tableSchema.getClassification(), savedDataset.getClassification()));
                    tableSchema = tableRepository.save(tableSchema);
                    if (isNewTable) {
                        tablesCreated++;
                    }

                    Map<String, LegacyColumnValues> legacyColumns = columnRepository
                        .findByTable(tableSchema)
                        .stream()
                        .filter(existing -> existing.getName() != null)
                        .collect(
                            java.util.stream.Collectors.toMap(
                                existing -> existing.getName().trim().toLowerCase(Locale.ROOT),
                                existing ->
                                    new LegacyColumnValues(
                                        StringUtils.hasText(existing.getComment()) ? existing.getComment() : null,
                                        StringUtils.hasText(existing.getTags()) ? existing.getTags() : null,
                                        StringUtils.hasText(existing.getSensitiveTags()) ? existing.getSensitiveTags() : null
                                    ),
                                (left, right) -> left,
                                LinkedHashMap::new
                            )
                        );

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
                            }
                            columnEntities.add(entity);
                        }
                        columnRepository.saveAll(columnEntities);
                        columnsImported += columnEntities.size();
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
                datasetsCreated,
                datasetsUpdated,
                tablesCreated,
                columnsImported,
                datasetsRemoved
            );
        }
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

    private CatalogDomain resolveOrCreateSourceDomain(InfraDataSource source) {
        String code = "DS:" + source.getId();
        CatalogDomain existing = findDomainByCode(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        CatalogDomain domain = new CatalogDomain();
        domain.setCode(code);
        domain.setName(defaultIfBlank(source.getName(), code));
        domain.setOwner(defaultIfBlank(source.getUsername(), DEFAULT_OWNER));
        domain.setDescription(truncate(source.getDescription()));
        return domainRepository.save(domain);
    }

    private CatalogDomain resolveOrCreateSchemaDomain(CatalogDomain sourceDomain, InfraDataSource source, String schema) {
        if (sourceDomain == null || source == null || source.getId() == null) {
            return null;
        }
        String normalizedSchema = StringUtils.hasText(schema) ? schema.trim() : "default";
        String code = "DS:" + source.getId() + ":" + normalizedSchema;
        CatalogDomain existing = findDomainByCode(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        CatalogDomain domain = new CatalogDomain();
        domain.setCode(code);
        domain.setName(normalizedSchema);
        domain.setOwner(defaultIfBlank(source.getUsername(), DEFAULT_OWNER));
        domain.setDescription("Schema from " + defaultIfBlank(source.getName(), source.getId().toString()));
        domain.setParent(sourceDomain);
        return domainRepository.save(domain);
    }

    private Optional<CatalogDomain> findDomainByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        try {
            return domainRepository.findFirstByCodeIgnoreCase(code.trim());
        } catch (RuntimeException ex) {
            // Backward compatibility: older schema might not support code-based lookup.
            return Optional.empty();
        }
    }

    private List<String> resolveSchemas(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return List.of();
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
        String database = stringProp(props, "database");
        if (StringUtils.hasText(database)) {
            return List.of(database.trim());
        }
        return List.of();
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
        try {
            String current = safe(connection.getSchema());
            if (StringUtils.hasText(current)) {
                return current;
            }
        } catch (Exception ignored) {}
        return "default";
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

    private record LegacyColumnValues(String comment, String tags, String sensitiveTags) {}

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
        int datasetsCreated,
        int datasetsUpdated,
        int tablesCreated,
        int columnsImported,
        int datasetsRemoved
    ) {
        public static JdbcSyncResult skipped(UUID sourceId, String reason) {
            return new JdbcSyncResult(sourceId, "SKIPPED", reason, 0L, null, null, List.of(), 0, 0, 0, 0, 0);
        }

        public static JdbcSyncResult failed(UUID sourceId, String error) {
            return new JdbcSyncResult(sourceId, "FAILED", error, 0L, null, null, List.of(), 0, 0, 0, 0, 0);
        }
    }
}
