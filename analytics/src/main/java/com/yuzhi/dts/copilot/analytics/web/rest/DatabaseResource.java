package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlert;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAlertSubscriptionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSynonymRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.JdbcDetailsResolver;
import com.yuzhi.dts.copilot.analytics.service.MetadataSyncService;
import com.yuzhi.dts.copilot.analytics.service.PlatformInfraClient;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/database")
@Transactional
public class DatabaseResource {

    private static final Logger log = LoggerFactory.getLogger(DatabaseResource.class);
    private static final DateTimeFormatter METABASE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final AnalyticsSessionService sessionService;
    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final AnalyticsAlertRepository alertRepository;
    private final AnalyticsAlertSubscriptionRepository alertSubscriptionRepository;
    private final AnalyticsSynonymRepository synonymRepository;
    private final MetadataSyncService metadataSyncService;
    private final JdbcDetailsResolver jdbcDetailsResolver;
    private final PlatformInfraClient platformInfraClient;
    private final ObjectMapper objectMapper;

    public DatabaseResource(
            AnalyticsSessionService sessionService,
            AnalyticsDatabaseRepository databaseRepository,
            AnalyticsTableRepository tableRepository,
            AnalyticsFieldRepository fieldRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            AnalyticsAlertRepository alertRepository,
            AnalyticsAlertSubscriptionRepository alertSubscriptionRepository,
            AnalyticsSynonymRepository synonymRepository,
            MetadataSyncService metadataSyncService,
            JdbcDetailsResolver jdbcDetailsResolver,
            PlatformInfraClient platformInfraClient,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.cardRepository = cardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.alertRepository = alertRepository;
        this.alertSubscriptionRepository = alertSubscriptionRepository;
        this.synonymRepository = synonymRepository;
        this.metadataSyncService = metadataSyncService;
        this.jdbcDetailsResolver = jdbcDetailsResolver;
        this.platformInfraClient = platformInfraClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        List<Map<String, Object>> data = databaseRepository.findAll().stream()
                .filter(this::isVisibleDatabase)
                .map(this::toDatabaseListItem)
                .toList();
        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        JsonNode details = request == null ? null : request.details();
        Long dataSourceId = resolveDataSourceId(details);
        UUID platformId = resolvePlatformDataSourceId(details);
        if (dataSourceId == null && platformId == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", "仅支持从数据源注册中心导入")));
        }

        PlatformInfraClient.DataSourceDetail sourceDetail = dataSourceId != null
                ? platformInfraClient.fetchDataSourceDetail(dataSourceId)
                : platformInfraClient.fetchDataSourceDetail(platformId);
        if (!StringUtils.hasText(sourceDetail.jdbcUrl())) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", "数据源缺少 JDBC URL")));
        }
        AnalyticsDatabase db = dataSourceId != null
                ? findByDataSource(dataSourceId).orElseGet(AnalyticsDatabase::new)
                : findByPlatformDataSource(platformId).orElseGet(AnalyticsDatabase::new);
        boolean isNew = db.getId() == null;

        applyDataSourceDetail(db, dataSourceId, platformId, sourceDetail, details);
        if (isNew) {
            applyNewDefaults(db);
        } else {
            applyMissingDefaults(db);
        }

        db = databaseRepository.save(db);
        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @GetMapping(path = "/{dbId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return databaseRepository.findById(dbId).<ResponseEntity<?>>map(db -> ResponseEntity.ok(toDatabaseGet(db, true))).orElseGet(
                () -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{dbId}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> metadata(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return databaseRepository.findById(dbId)
                .<ResponseEntity<?>>map(db -> {
                    Map<String, Object> response = new LinkedHashMap<>(toDatabaseGet(db, true));
                    response.put("tables", listTablesForDatabase(db.getId(), true));
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{dbId}/schemas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schemas(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        List<String> schemas = tableRepository.findAllByDatabaseIdOrderBySchemaNameAscNameAsc(dbId).stream()
                .filter(AnalyticsTable::isActive)
                .map(AnalyticsTable::getSchemaName)
                .filter(schema -> schema != null && !schema.isBlank())
                .distinct()
                .toList();
        return ResponseEntity.ok(schemas);
    }

    @GetMapping(path = "/{dbId}/schema/{schemaName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schemaTables(
            @PathVariable("dbId") long dbId, @PathVariable("schemaName") String schemaName, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> tables = tableRepository.findAllByDatabaseIdAndSchemaNameOrderByNameAsc(dbId, schemaName).stream()
                .filter(AnalyticsTable::isActive)
                .map(table -> toTable(table, true))
                .toList();
        return ResponseEntity.ok(tables);
    }

    @GetMapping(path = "/{dbId}/fields", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> fields(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> fields = fieldRepository.findAllByDatabaseIdOrderByTableIdAscPositionAscIdAsc(dbId).stream()
                .filter(AnalyticsField::isActive)
                .map(this::toField)
                .toList();
        return ResponseEntity.ok(fields);
    }

    @GetMapping(path = "/{dbId}/idfields", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> idfields(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> result = fieldRepository.findAllByDatabaseIdOrderByTableIdAscPositionAscIdAsc(dbId).stream()
                .filter(AnalyticsField::isActive)
                .filter(field -> {
                    String name = field.getName();
                    if (name == null) {
                        return false;
                    }
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    return lower.equals("id") || lower.endsWith("_id");
                })
                .limit(200)
                .map(this::toField)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/{dbId}/autocomplete_suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> autocompleteSuggestions(
            @PathVariable("dbId") long dbId,
            @RequestParam(name = "matchStyle", required = false) String matchStyle,
            @RequestParam(name = "query", required = false) String query,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{dbId}/card_autocomplete_suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cardAutocompleteSuggestions(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(path = "/{dbId}/sync_schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> syncSchema(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            MetadataSyncService.SyncSummary summary = metadataSyncService.syncDatabaseSchema(dbId);
            return ResponseEntity.ok(
                    Map.of(
                            "database_id",
                            summary.databaseId(),
                            "created_tables",
                            summary.createdTables(),
                            "updated_tables",
                            summary.updatedTables(),
                            "disabled_tables",
                            summary.disabledTables(),
                            "created_fields",
                            summary.createdFields(),
                            "updated_fields",
                            summary.updatedFields(),
                            "disabled_fields",
                            summary.disabledFields()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("database", e.getMessage())));
        } catch (Exception e) {
            log.error("Metadata sync failed for database {}: {}", dbId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "同步失败: " + e.getMessage()));
        }
    }

    @PostMapping(path = "/{dbId}/dismiss_spinner", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dismissSpinner(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/rescan_values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rescanValues(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/discard_values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> discardValues(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/persist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> persist(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{dbId}/unpersist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> unpersist(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        Optional<AnalyticsDatabase> existing = databaseRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsDatabase db = existing.get();
        Long dataSourceId = resolveDataSourceId(request == null ? null : request.details());
        if (dataSourceId == null) {
            dataSourceId = resolveDataSourceId(db.getDetailsJson());
        }
        UUID platformId = resolvePlatformDataSourceId(request == null ? null : request.details());
        if (dataSourceId == null && platformId == null) {
            platformId = resolvePlatformDataSourceId(db.getDetailsJson());
        }
        if (dataSourceId == null && platformId == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", "仅支持数据源注册中心连接")));
        }

        PlatformInfraClient.DataSourceDetail sourceDetail = dataSourceId != null
                ? platformInfraClient.fetchDataSourceDetail(dataSourceId)
                : platformInfraClient.fetchDataSourceDetail(platformId);
        if (!StringUtils.hasText(sourceDetail.jdbcUrl())) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", "数据源缺少 JDBC URL")));
        }
        applyDataSourceDetail(db, dataSourceId, platformId, sourceDetail, request == null ? null : request.details());
        if (request.timezone() != null) {
            db.setTimezone(request.timezone());
        }
        if (request.metadataSyncSchedule() != null) {
            db.setMetadataSyncSchedule(request.metadataSyncSchedule());
        }
        if (request.cacheFieldValuesSchedule() != null) {
            db.setCacheFieldValuesSchedule(request.cacheFieldValuesSchedule());
        }
        if (request.autoRunQueries() != null) {
            db.setAutoRunQueries(request.autoRunQueries());
        }
        if (request.isFullSync() != null) {
            db.setFullSync(request.isFullSync());
        }
        if (request.isOnDemand() != null) {
            db.setOnDemand(request.isOnDemand());
        }

        applyMissingDefaults(db);
        db = databaseRepository.save(db);
        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @DeleteMapping(path = "/{dbId}")
    public ResponseEntity<?> delete(@PathVariable("dbId") long dbId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!databaseRepository.existsById(dbId)) {
            return ResponseEntity.notFound().build();
        }
        deleteDatabaseMetadata(dbId);
        databaseRepository.deleteById(dbId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/sample_database", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addSampleDatabase(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        AnalyticsDatabase db = new AnalyticsDatabase();
        db.setName("Sample Database");
        db.setEngine("h2");
        db.setDetailsJson("{\"db\":\"file:/plugins/sample-database.db\"}");
        db.setDescription("Some example data for you to play around with as you embark on your Metabase journey.");
        db.setSample(true);
        db.setTimezone("UTC");
        db.setMetadataSyncSchedule("0 50 * * * ? *");
        db.setCacheFieldValuesSchedule("0 50 0 * * ? *");
        db.setAutoRunQueries(true);
        db.setFullSync(true);
        db.setOnDemand(false);
        db = databaseRepository.save(db);
        return ResponseEntity.ok(toDatabaseGet(db, true));
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateConnection(@RequestBody DatabaseRequest request, HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }

        Map<String, String> errors = new LinkedHashMap<>();
        JsonNode details = request == null ? null : request.details();
        if (details == null || !details.isObject()) {
            errors.put("details", "value must be a map.");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        try {
            JdbcDetailsResolver.JdbcDetails jdbcDetails = jdbcDetailsResolver.resolve(request == null ? null : request.engine(), details);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcDetails.jdbcUrl());
            if (jdbcDetails.username() != null) {
                config.setUsername(jdbcDetails.username());
            }
            if (jdbcDetails.password() != null) {
                config.setPassword(jdbcDetails.password());
            }
            config.setPoolName("analytics-validate");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(15_000);
            config.setValidationTimeout(5_000);
            config.setInitializationFailTimeout(-1);

            try (HikariDataSource dataSource = new HikariDataSource(config);
                    Connection connection = dataSource.getConnection()) {
                connection.isValid(2);
            }

            return ResponseEntity.ok(Map.of());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", e.getMessage())));
        } catch (SQLException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("details", "Connection failed: " + e.getMessage())));
        }
    }

    private Map<String, Object> toDatabaseListItem(AnalyticsDatabase db) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", db.getId());
        item.put("name", db.getName());
        item.put("engine", db.getEngine());
        item.put("database_role", db.getDatabaseRoleValue());
        item.put("description", db.getDescription());
        item.put("details", parseDetails(db.getDetailsJson()));
        item.put("settings", null);
        item.put("caveats", null);
        item.put("points_of_interest", null);
        item.put("features", List.of());
        item.put("created_at", formatTimestamp(db.getCreatedAt()));
        item.put("updated_at", formatTimestamp(db.getUpdatedAt()));
        item.put("timezone", db.getTimezone());
        item.put("auto_run_queries", db.isAutoRunQueries());
        item.put("metadata_sync_schedule", db.getMetadataSyncSchedule());
        item.put("cache_field_values_schedule", db.getCacheFieldValuesSchedule());
        item.put("cache_ttl", null);
        item.put("is_full_sync", db.isFullSync());
        item.put("is_on_demand", db.isOnDemand());
        item.put("is_sample", db.isSample());
        item.put("initial_sync_status", "complete");
        item.put("native_permissions", "write");
        item.put("options", null);
        item.put("creator_id", null);
        return item;
    }

    private Map<String, Object> toDatabaseGet(AnalyticsDatabase db, boolean canManage) {
        Map<String, Object> item = new LinkedHashMap<>(toDatabaseListItem(db));
        item.put("can-manage", canManage);
        item.put("schedules", null);
        item.remove("native_permissions");
        return item;
    }

    private Optional<AnalyticsDatabase> findByPlatformDataSource(UUID platformId) {
        if (platformId == null) {
            return Optional.empty();
        }
        return databaseRepository.findAll().stream()
                .filter(db -> platformId.equals(resolvePlatformDataSourceId(db.getDetailsJson())))
                .findFirst();
    }

    private Optional<AnalyticsDatabase> findByDataSource(Long dataSourceId) {
        if (dataSourceId == null) {
            return Optional.empty();
        }
        return databaseRepository.findAll().stream()
                .filter(db -> dataSourceId.equals(resolveDataSourceId(db.getDetailsJson())))
                .findFirst();
    }

    private boolean isVisibleDatabase(AnalyticsDatabase database) {
        return !isSystemRuntimeDatabase(database);
    }

    private boolean isSystemRuntimeDatabase(AnalyticsDatabase database) {
        if (database == null) {
            return false;
        }
        if (database.getDatabaseRole() == AnalyticsDatabaseRole.SYSTEM_RUNTIME) {
            return true;
        }
        String engine = database.getEngine() == null ? "" : database.getEngine().trim().toLowerCase(Locale.ROOT);
        if (!"postgres".equals(engine) && !"postgresql".equals(engine)) {
            return false;
        }
        Long dataSourceId = resolveDataSourceId(database.getDetailsJson());
        if (dataSourceId == null) {
            return false;
        }
        try {
            PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(dataSourceId);
            String jdbcUrl = detail.jdbcUrl();
            if (!StringUtils.hasText(jdbcUrl)) {
                return false;
            }
            return isSystemRuntimeJdbcUrl(jdbcUrl);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isSystemRuntimeJdbcUrl(String jdbcUrl) {
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        boolean localHost = normalized.startsWith("jdbc:postgresql://localhost:5432/")
                || normalized.startsWith("jdbc:postgresql://127.0.0.1:5432/")
                || normalized.startsWith("jdbc:postgresql://copilot-postgres:5432/");
        if (!localHost) {
            return false;
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex >= normalized.length() - 1) {
            return false;
        }
        String databaseName = normalized.substring(slashIndex + 1);
        int queryIndex = databaseName.indexOf('?');
        if (queryIndex >= 0) {
            databaseName = databaseName.substring(0, queryIndex);
        }
        return "garden".equals(databaseName) || "copilot".equals(databaseName);
    }

    private void applyDataSourceDetail(
            AnalyticsDatabase db,
            Long dataSourceId,
            UUID platformId,
            PlatformInfraClient.DataSourceDetail detail,
            JsonNode requestedDetails) {
        String name = StringUtils.hasText(detail.name()) ? detail.name() : "platform-" + platformId;
        String engine = resolveEngineFromType(detail.type(), detail.jdbcUrl());
        ObjectNode details = dataSourceId != null ? buildDataSourceDetailsNode(dataSourceId) : buildPlatformDetailsNode(platformId);
        copyLogicalDatasourceConfig(details, parseDetailsNode(db.getDetailsJson()));
        copyLogicalDatasourceConfig(details, requestedDetails);
        db.setName(name);
        db.setEngine(engine);
        db.setDetailsJson(details.toString());
        if (StringUtils.hasText(detail.description())) {
            db.setDescription(detail.description());
        }
        db.setDatabaseRole(resolveDatabaseRole(detail.jdbcUrl(), db.getDatabaseRole()));
        db.setSample(false);
    }

    private void applyNewDefaults(AnalyticsDatabase db) {
        db.setTimezone(ZoneId.systemDefault().getId());
        db.setMetadataSyncSchedule("0 50 * * * ? *");
        db.setCacheFieldValuesSchedule("0 50 0 * * ? *");
        db.setAutoRunQueries(true);
        db.setFullSync(true);
        db.setOnDemand(false);
        if (db.getDatabaseRole() == null) {
            db.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);
        }
    }

    private void applyMissingDefaults(AnalyticsDatabase db) {
        if (!StringUtils.hasText(db.getTimezone())) {
            db.setTimezone(ZoneId.systemDefault().getId());
        }
        if (!StringUtils.hasText(db.getMetadataSyncSchedule())) {
            db.setMetadataSyncSchedule("0 50 * * * ? *");
        }
        if (!StringUtils.hasText(db.getCacheFieldValuesSchedule())) {
            db.setCacheFieldValuesSchedule("0 50 0 * * ? *");
        }
        if (db.getDatabaseRole() == null) {
            db.setDatabaseRole(AnalyticsDatabaseRole.BUSINESS_PRIMARY);
        }
    }

    private AnalyticsDatabaseRole resolveDatabaseRole(String jdbcUrl, AnalyticsDatabaseRole currentRole) {
        if (isSystemRuntimeJdbcUrl(jdbcUrl)) {
            return AnalyticsDatabaseRole.SYSTEM_RUNTIME;
        }
        return currentRole == null ? AnalyticsDatabaseRole.BUSINESS_PRIMARY : currentRole;
    }

    private String buildPlatformDetailsJson(UUID platformId) {
        return buildPlatformDetailsNode(platformId).toString();
    }

    private ObjectNode buildPlatformDetailsNode(UUID platformId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("platformDataSourceId", platformId.toString());
        return node;
    }

    private String buildDataSourceDetailsJson(Long dataSourceId) {
        return buildDataSourceDetailsNode(dataSourceId).toString();
    }

    private ObjectNode buildDataSourceDetailsNode(Long dataSourceId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("dataSourceId", dataSourceId);
        return node;
    }

    private JsonNode parseDetailsNode(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void copyLogicalDatasourceConfig(ObjectNode target, JsonNode source) {
        if (target == null || source == null || !source.isObject()) {
            return;
        }
        copyIfPresent(target, source, "logicalSourceAliases");
        copyIfPresent(target, source, "logicalDatasourceAliases");
        copyIfPresent(target, source, "dataSourceAliases");
        copyIfPresent(target, source, "datasourceAliases");
        copyIfPresent(target, source, "sourceAliases");
        copyIfPresent(target, source, "logicalSourceAlias");
        copyIfPresent(target, source, "logicalDatasourceAlias");
        copyIfPresent(target, source, "dataSourceAlias");
        copyIfPresent(target, source, "datasourceAlias");
        copyIfPresent(target, source, "databaseAlias");
        copyIfPresent(target, source, "sourceAlias");
        copyIfPresent(target, source, "logicalDataSource");
    }

    private void copyIfPresent(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            target.set(field, value.deepCopy());
        }
    }

    private Long resolveDataSourceId(JsonNode details) {
        if (details == null || !details.isObject()) {
            return null;
        }
        JsonNode value = details.get("dataSourceId");
        if (value == null) {
            value = details.get("datasourceId");
        }
        if (value == null) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private UUID resolvePlatformDataSourceId(JsonNode details) {
        if (details == null || !details.isObject()) {
            return null;
        }
        String direct = textOf(details, "platformDataSourceId", "platform_data_source_id", "platformDataSourceID");
        if (StringUtils.hasText(direct)) {
            return parseUuid(direct);
        }
        JsonNode platform = details.get("platform");
        if (platform != null && platform.isObject()) {
            String nested = textOf(platform, "dataSourceId", "datasourceId", "id");
            if (StringUtils.hasText(nested)) {
                return parseUuid(nested);
            }
        }
        return null;
    }

    private UUID resolvePlatformDataSourceId(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            return resolvePlatformDataSourceId(node);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long resolveDataSourceId(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            return resolveDataSourceId(node);
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String textOf(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String resolveEngineFromType(String type, String jdbcUrl) {
        String normalized = normalizeType(type);
        if (!StringUtils.hasText(normalized) && StringUtils.hasText(jdbcUrl)) {
            String lowerUrl = jdbcUrl.trim().toLowerCase(java.util.Locale.ROOT);
            if (lowerUrl.startsWith("jdbc:postgresql:")) return "postgres";
            if (lowerUrl.startsWith("jdbc:mysql:")) return "mysql";
            if (lowerUrl.startsWith("jdbc:trino:")) return "trino";
            if (lowerUrl.startsWith("jdbc:oracle:")) return "oracle";
            if (lowerUrl.startsWith("jdbc:dm:")) return "dm";
        }
        if (!StringUtils.hasText(normalized)) {
            return "jdbc";
        }
        return switch (normalized) {
            case "postgresql", "postgres", "pg" -> "postgres";
            case "mysql", "mariadb" -> "mysql";
            case "trino", "presto" -> "trino";
            case "oracle" -> "oracle";
            case "dm", "dameng" -> "dm";
            default -> normalized;
        };
    }

    private String normalizeType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private String formatTimestamp(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return METABASE_TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private void deleteDatabaseMetadata(Long databaseId) {
        List<AnalyticsCard> cards = cardRepository.findAllByDatabaseIdOrderByIdAsc(databaseId);
        List<Long> cardIds = cards.stream().map(AnalyticsCard::getId).filter(Objects::nonNull).toList();
        if (!cardIds.isEmpty()) {
            List<AnalyticsAlert> alerts = alertRepository.findAllByCardIdInOrderByIdAsc(cardIds);
            List<Long> alertIds = alerts.stream().map(AnalyticsAlert::getId).filter(Objects::nonNull).toList();
            if (!alertIds.isEmpty()) {
                alertSubscriptionRepository.deleteAllByAlertIdIn(alertIds);
            }
            alertRepository.deleteAllByCardIdIn(cardIds);
            dashboardCardRepository.deleteAllByCardIdIn(cardIds);
            cardRepository.deleteAllByDatabaseId(databaseId);
        }
        fieldRepository.deleteAllByDatabaseId(databaseId);
        tableRepository.deleteAllByDatabaseId(databaseId);
        synonymRepository.deleteAllByDatabaseId(databaseId);
    }

    private List<Map<String, Object>> listTablesForDatabase(Long databaseId, boolean includeFields) {
        if (databaseId == null) {
            return List.of();
        }
        List<AnalyticsTable> tables = tableRepository.findAllByDatabaseIdOrderBySchemaNameAscNameAsc(databaseId).stream()
                .filter(AnalyticsTable::isActive)
                .toList();
        if (!includeFields) {
            return tables.stream().map(table -> toTable(table, false)).toList();
        }

        List<Long> tableIds = tables.stream().map(AnalyticsTable::getId).filter(Objects::nonNull).toList();
        Map<Long, List<AnalyticsField>> fieldsByTable = new LinkedHashMap<>();
        for (Long tableId : tableIds) {
            fieldsByTable.put(
                    tableId,
                    fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(tableId).stream()
                            .filter(AnalyticsField::isActive)
                            .toList());
        }

        return tables.stream()
                .map(table -> toTable(table, includeFields ? fieldsByTable.getOrDefault(table.getId(), List.of()) : List.of()))
                .toList();
    }

    private Map<String, Object> toTable(AnalyticsTable table, boolean includeFields) {
        return toTable(table, includeFields ? fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(table.getId()).stream()
                        .filter(AnalyticsField::isActive)
                        .toList()
                : List.of());
    }

    private Map<String, Object> toTable(AnalyticsTable table, List<AnalyticsField> fields) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", table.getId());
        json.put("db_id", table.getDatabaseId());
        json.put("schema", emptyToNull(table.getSchemaName()));
        json.put("name", table.getName());
        json.put("display_name", Optional.ofNullable(table.getDisplayName()).orElse(table.getName()));
        json.put("description", table.getDescription());
        json.put("active", table.isActive());
        json.put("visibility_type", Optional.ofNullable(table.getVisibilityType()).orElse("normal"));
        json.put("entity_type", null);
        json.put("field_order", "database");
        json.put("is_upload", false);
        json.put("created_at", formatTimestamp(table.getCreatedAt()));
        json.put("updated_at", formatTimestamp(table.getUpdatedAt()));
        json.put("points_of_interest", null);
        json.put("caveats", null);
        json.put("fields", fields.stream().map(this::toField).toList());
        return json;
    }

    private Map<String, Object> toField(AnalyticsField field) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", field.getId());
        json.put("name", field.getName());
        json.put("display_name", Optional.ofNullable(field.getDisplayName()).orElse(field.getName()));
        json.put("description", null);
        json.put("table_id", field.getTableId());
        json.put("db_id", field.getDatabaseId());
        json.put("base_type", field.getBaseType());
        json.put("effective_type", Optional.ofNullable(field.getEffectiveType()).orElse(field.getBaseType()));
        json.put("semantic_type", field.getSemanticType());
        json.put("has_field_values", "list");
        json.put("active", field.isActive());
        json.put("position", field.getPosition());
        json.put("visibility_type", Optional.ofNullable(field.getVisibilityType()).orElse("normal"));
        json.put("fingerprint", parseFingerprint(field.getFingerprintJson()));
        json.put("created_at", formatTimestamp(field.getCreatedAt()));
        json.put("updated_at", formatTimestamp(field.getUpdatedAt()));
        return json;
    }

    private Object parseFingerprint(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DatabaseRequest(
            @JsonProperty("name") String name,
            @JsonProperty("engine") String engine,
            @JsonProperty("details") JsonNode details,
            @JsonProperty("description") String description,
            @JsonProperty("is_sample") Boolean isSample,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("metadata_sync_schedule") String metadataSyncSchedule,
            @JsonProperty("cache_field_values_schedule") String cacheFieldValuesSchedule,
            @JsonProperty("auto_run_queries") Boolean autoRunQueries,
            @JsonProperty("is_full_sync") Boolean isFullSync,
            @JsonProperty("is_on_demand") Boolean isOnDemand) {}
}
