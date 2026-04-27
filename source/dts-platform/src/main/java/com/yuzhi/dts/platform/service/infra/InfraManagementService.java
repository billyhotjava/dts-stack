package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.domain.service.InfraDataStorage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.repository.service.InfraConnectionTestLogRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataStorageRepository;
import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.platform.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.DataStorageRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataStorageDto;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService.JdbcSyncResult;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.yuzhi.dts.common.audit.AuditStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InfraManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(InfraManagementService.class);

    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraDataStorageRepository storageRepository;
    private final InfraConnectionTestLogRepository testLogRepository;
    private final InfraSecretService secretService;
    private final InfraSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostgresConnectionService postgresConnectionService;
    private final IngestionServiceClient ingestionServiceClient;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final JdbcCatalogSyncService jdbcCatalogSyncService;
    private final InfraCatalogSyncRunRepository syncRunRepository;
    private final AuditService auditService;
    private final AdminInfraClient adminInfraClient;

    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String TYPE_POSTGRES = "POSTGRES";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String SOURCE_ADMIN_DATA_LAKE = "admin-data-lake";

    private static final String DEFAULT_FILE_SCHEMA = "ods";
    private static final String DATASET_TYPE_FILE = "file";
    private static final String SETTINGS_KEY_CATALOG_SYNC = "catalogSyncOnDataSource";
    private static final String SETTINGS_SERVICE_PLATFORM = "platform";
    private static final String AUDIT_ACTION_CATALOG_SYNC = "FOUNDATION_DATASOURCE_CATALOG_SYNC";
    private static final Duration ADMIN_DATA_LAKE_LOOKUP_TIMEOUT = Duration.ofMillis(800);
    private static final int LOCAL_DEFAULT_LAKE_SCORE_THRESHOLD = 70;
    private static final java.util.Set<String> JDBC_TYPES = java.util.Set.of(
        "jdbc",
        "postgres",
        "postgresql",
        "mysql",
        "mariadb",
        "oracle",
        "dm",
        "sqlserver",
        "clickhouse",
        "hive",
        "inceptor",
        "db2"
    );

    private final ApiSecretMetadataService apiSecretMetadataService;

    public InfraManagementService(
        InfraDataSourceRepository dataSourceRepository,
        InfraDataStorageRepository storageRepository,
        InfraConnectionTestLogRepository testLogRepository,
        InfraSecretService secretService,
        InfraSecurityProperties securityProperties,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        PostgresConnectionService postgresConnectionService,
        IngestionServiceClient ingestionServiceClient,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSyncService columnSyncService,
        JdbcCatalogSyncService jdbcCatalogSyncService,
        InfraCatalogSyncRunRepository syncRunRepository,
        AuditService auditService,
        AdminInfraClient adminInfraClient,
        ApiSecretMetadataService apiSecretMetadataService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.storageRepository = storageRepository;
        this.testLogRepository = testLogRepository;
        this.secretService = secretService;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.postgresConnectionService = postgresConnectionService;
        this.ingestionServiceClient = ingestionServiceClient;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnSyncService = columnSyncService;
        this.jdbcCatalogSyncService = jdbcCatalogSyncService;
        this.syncRunRepository = syncRunRepository;
        this.auditService = auditService;
        this.adminInfraClient = adminInfraClient;
        this.apiSecretMetadataService = apiSecretMetadataService;
    }

    public List<InfraDataSourceDto> listDataSources(String activeDeptHeader) {
        try {
            boolean isMaintainer = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
            boolean canViewAll = isInstituteMaintainer();
            List<InfraDataSource> sources = (canViewAll || isMaintainer)
                ? dataSourceRepository.findAll()
                : dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);

            // Dept maintainers: only see global sources + their own department sources
            if (!canViewAll && isMaintainer) {
                String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
                sources = sources
                    .stream()
                    .filter(ds -> {
                        String owner = normalizeDept(ds.getOwnerDept());
                        return owner.isEmpty() || (!dept.isEmpty() && owner.equalsIgnoreCase(dept));
                    })
                    .toList();
            }
            List<InfraDataSourceDto> result = new java.util.ArrayList<>(sources.stream().map(this::toDto).toList());
            mergeAdminDataLake(result);
            return result;
        } catch (RuntimeException ex) {
            // If Liquibase hasn't created infra tables yet, return empty to keep UI usable
            LOG.warn("listDataSources failed (likely missing table). Returning empty list. cause={}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Merge the default data lake from admin into the data source list.
     * If the data lake's id or jdbcUrl already matches a local record, skip it.
     */
    private void mergeAdminDataLake(List<InfraDataSourceDto> result) {
        try {
            AdminInfraClient.AdminDataLakeConfig lake = fetchDefaultDataLakeForList().orElse(null);
            if (lake == null || lake.getId() == null) {
                return;
            }
            // Check if already present by id or jdbcUrl
            for (InfraDataSourceDto dto : result) {
                if (lake.getId().equals(dto.id())) {
                    return;
                }
                if (StringUtils.hasText(lake.getJdbcUrl()) && lake.getJdbcUrl().equals(dto.jdbcUrl())) {
                    return;
                }
            }
            removeLocalFallbackCandidate(result);
            InfraDataSourceDto dto = toDto(lake);
            result.add(0, dto);
        } catch (Exception ex) {
            LOG.debug("Failed to merge admin data lake into data source list: {}", ex.getMessage());
        }
    }

    private void removeLocalFallbackCandidate(List<InfraDataSourceDto> result) {
        result
            .stream()
            .filter(this::isLocalLakeCandidate)
            .max(Comparator.comparingInt(this::scoreLocalLake))
            .filter(candidate -> scoreLocalLake(candidate) >= LOCAL_DEFAULT_LAKE_SCORE_THRESHOLD)
            .ifPresent(result::remove);
    }

    private boolean isLocalLakeCandidate(InfraDataSourceDto dto) {
        if (dto == null) {
            return false;
        }
        Map<String, Object> props = dto.props() == null ? Map.of() : dto.props();
        if (SOURCE_ADMIN_DATA_LAKE.equalsIgnoreCase(normalize(props.get("source")))) {
            return false;
        }
        String type = normalizeLower(dto.type());
        if (TYPE_INCEPTOR.equalsIgnoreCase(type)) {
            return false;
        }
        if (StringUtils.hasText(normalize(dto.jdbcUrl())) && (JDBC_TYPES.contains(type) || !StringUtils.hasText(type))) {
            return true;
        }
        return props.containsKey("destinationConfig")
            || StringUtils.hasText(normalize(props.get("destinationDefinitionId")))
            || StringUtils.hasText(normalize(props.get("writerType")))
            || StringUtils.hasText(normalize(props.get("type")));
    }

    private int scoreLocalLake(InfraDataSourceDto dto) {
        if (dto == null) {
            return Integer.MIN_VALUE;
        }
        int score = DataSourceScorer.scoreDataSource(dto.name(), dto.jdbcUrl(), dto.type(), dto.status());
        // Context-specific bonuses: Chinese name hints and description hints
        String name = normalizeLower(dto.name());
        String description = normalizeLower(dto.description());
        if (StringUtils.hasText(name) && (name.contains("默认数据湖") || name.contains("默认湖") || name.contains("数仓"))) {
            score += 40;
        }
        if (StringUtils.hasText(name) && name.contains("平台") && (name.contains("postgres") || name.contains("postgresql"))) {
            score += 40;
        }
        if (StringUtils.hasText(description) && (description.contains("临时数据源") || description.contains("平台自用"))) {
            score += 30;
        }
        return score;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeLower(Object value) {
        String text = normalize(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private Optional<AdminInfraClient.AdminDataLakeConfig> fetchDefaultDataLakeForList() {
        try {
            return CompletableFuture
                .supplyAsync(adminInfraClient::fetchDefaultDataLake)
                .completeOnTimeout(Optional.empty(), ADMIN_DATA_LAKE_LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> Optional.empty())
                .join();
        } catch (Exception ex) {
            LOG.debug("Failed to fetch admin default data lake for list view: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public InfraDataSourceDto createDataSource(DataSourceRequest request, String username, String activeDeptHeader) {
        ensureNotInceptorManaged(request.type());
        InfraDataSource entity = new InfraDataSource();
        validateRequest(request, null);
        applyDataSource(entity, request, username);
        ensureNotSystemManaged(entity.getType(), activeDeptHeader);
        applyOwnerDept(entity, activeDeptHeader);
        InfraDataSource saved = dataSourceRepository.save(entity);
        syncCatalogIfEnabled(saved, request, username);
        return toDto(saved);
    }

    @Transactional
    public InfraDataSourceDto publishInceptorDataSource(HiveConnectionPersistRequest request, String username) {
        InfraDataSource entity = dataSourceRepository.findFirstByTypeIgnoreCase(TYPE_INCEPTOR).orElseGet(InfraDataSource::new);
        applyInceptorDataSource(entity, request, username);
        InfraDataSource saved = dataSourceRepository.save(entity);
        purgeDuplicateInceptorRows(saved.getId());
        long elapsed = request.getLastTestElapsedMillis() != null ? request.getLastTestElapsedMillis() : 0L;
        HiveConnectionTestResult auditResult = HiveConnectionTestResult.success(
            "连接已发布",
            elapsed,
            request.getEngineVersion(),
            request.getDriverVersion(),
            List.of()
        );
        recordConnectionTest(saved.getId(), request, auditResult, username);
        InfraDataSourceDto dto = toDto(saved);
        eventPublisher.publishEvent(new InceptorDataSourcePublishedEvent(dto));
        return dto;
    }

    @Transactional
    public InfraDataSourceDto activatePlatformPostgres(String username) {
        PostgresConnectionService.PostgresConnectionResult result = postgresConnectionService.verifyPlatformDatabase();
        InfraDataSource entity = dataSourceRepository.findFirstByTypeIgnoreCase(TYPE_POSTGRES).orElseGet(InfraDataSource::new);
        entity.setName(StringUtils.hasText(entity.getName()) ? entity.getName() : "平台 PostgreSQL");
        entity.setDescription("Hive 不可用时的临时数据源，指向平台自用的 PostgreSQL。");
        entity.setType(TYPE_POSTGRES);
        entity.setJdbcUrl(result.jdbcUrl());
        entity.setUsername(result.username());
        entity.setProps(writeProps(result.props()));
        secretService.applySecrets(entity, result.secrets());
        entity.setLastVerifiedAt(Instant.now());
        entity.setStatus(STATUS_ACTIVE);
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
        InfraDataSource saved = dataSourceRepository.save(entity);
        HiveConnectionTestResult auditResult = HiveConnectionTestResult.success(
            "PostgreSQL 连接已激活",
            result.elapsedMillis(),
            result.databaseVersion(),
            result.driverVersion(),
            List.of()
        );
        recordConnectionTest(
            saved.getId(),
            Map.of(
                "type",
                TYPE_POSTGRES,
                "jdbcUrl",
                result.jdbcUrl(),
                "username",
                result.username()
            ),
            auditResult,
            username
        );
        return toDto(saved);
    }

    public record DataSourceUpdateImpact(
        InfraDataSourceDto dataSource,
        boolean connectionChanged,
        int affectedTasks,
        int changeLogCreated,
        List<Long> taskIds
    ) {}

    @Transactional
    public DataSourceUpdateImpact updateDataSourceWithImpact(
        UUID id,
        DataSourceRequest request,
        String username,
        String activeDeptHeader
    ) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureNotInceptorManaged(request.type());
        ensureNotInceptorManaged(entity.getType());
        ensureNotSystemManaged(entity.getType(), activeDeptHeader);
        ensureDeptScopeWritable(entity, activeDeptHeader);
        validateRequest(request, entity);
        String beforeType = entity.getType();
        String beforeJdbcUrl = entity.getJdbcUrl();
        String beforeUsername = entity.getUsername();
        Map<String, Object> beforeProps = readProps(entity.getProps());
        Map<String, Object> beforeSecrets = secretService.readSecrets(entity);
        applyDataSource(entity, request, username);
        applyOwnerDept(entity, activeDeptHeader);
        InfraDataSource saved = dataSourceRepository.save(entity);
        syncCatalogIfEnabled(saved, request, username);
        Map<String, Object> afterProps = readProps(saved.getProps());
        Map<String, Object> afterSecrets = secretService.readSecrets(saved);
        ConnectionChange change = buildConnectionChange(
            beforeType,
            beforeJdbcUrl,
            beforeUsername,
            beforeProps,
            beforeSecrets,
            saved.getType(),
            saved.getJdbcUrl(),
            saved.getUsername(),
            afterProps,
            afterSecrets
        );
        ImpactResult impact = change.changed()
            ? notifyIngestionTasks(saved, change, username)
            : ImpactResult.empty();
        return new DataSourceUpdateImpact(toDto(saved), change.changed(), impact.affected(), impact.created(), impact.taskIds());
    }

    @Transactional
    public InfraDataSourceDto updateDataSource(UUID id, DataSourceRequest request, String username, String activeDeptHeader) {
        return updateDataSourceWithImpact(id, request, username, activeDeptHeader).dataSource();
    }

    public InfraDataSource findEntity(UUID id) {
        return dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public InfraDataSourceDto getDataSource(UUID id, String activeDeptHeader) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElse(null);
        if (entity != null) {
            ensureDeptScopeReadable(entity, activeDeptHeader);
            return toDto(entity);
        }
        return findAdminDataLakeById(id)
            .map(this::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在: " + id));
    }

    @Transactional(readOnly = true)
    public com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto getDataSourceDetail(UUID id) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElse(null);
        if (entity != null) {
            Map<String, Object> props = readProps(entity.getProps());
            boolean apiType = ApiDataSourceSupport.isApiType(entity.getType());
            // API data sources never expose plaintext secrets via the detail endpoint;
            // callers see the masked summary derived from sidecar metadata instead.
            Map<String, Object> secrets = apiType ? Map.of() : secretService.readSecrets(entity);
            List<com.yuzhi.dts.platform.service.infra.dto.ApiSecretSummary> summaries = apiType
                ? apiSecretMetadataService.toSummaries(apiSecretMetadataService.readFromProps(props))
                : List.of();
            return new com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getJdbcUrl(),
                entity.getUsername(),
                entity.getDescription(),
                entity.getOwnerDept(),
                props,
                secrets,
                summaries,
                entity.getStatus(),
                entity.getLastVerifiedAt()
            );
        }
        return findAdminDataLakeById(id)
            .map(this::toDetailDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在: " + id));
    }

    @Transactional
    public void updateJdbcSyncResult(UUID id, JdbcSyncResult result) {
        if (id == null || result == null) {
            return;
        }
        InfraDataSource entity = dataSourceRepository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }
        Map<String, Object> props = readProps(entity.getProps());
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("status", result.status());
        sync.put("error", result.error());
        sync.put("elapsedMs", result.elapsedMs());
        sync.put("databaseProduct", result.databaseProduct());
        sync.put("databaseVersion", result.databaseVersion());
        sync.put("schemas", result.schemas());
        sync.put("tablesDiscovered", result.tablesDiscovered());
        sync.put("datasetsCreated", result.datasetsCreated());
        sync.put("datasetsUpdated", result.datasetsUpdated());
        sync.put("datasetsRemoved", result.datasetsRemoved());
        sync.put("datasetsMarkedStale", result.datasetsMarkedStale());
        sync.put("datasetsPurged", result.datasetsPurged());
        sync.put("tablesCreated", result.tablesCreated());
        sync.put("columnsImported", result.columnsImported());
        sync.put("lastSyncAt", Instant.now().toString());
        props.put("sync", sync);
        props.put("tableCount", Math.max(result.tablesDiscovered(), 0));
        entity.setProps(writeProps(props));
        dataSourceRepository.save(entity);
    }

    @Transactional
    public void deleteDataSource(UUID id, String activeDeptHeader) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureNotSystemManaged(entity.getType(), activeDeptHeader);
        ensureDeptScopeWritable(entity, activeDeptHeader);
        dataSourceRepository.delete(entity);
    }

    public List<InfraDataStorageDto> listDataStorages() {
        return storageRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public InfraDataStorageDto createDataStorage(DataStorageRequest request, String username) {
        InfraDataStorage entity = new InfraDataStorage();
        applyDataStorage(entity, request, username);
        return toDto(storageRepository.save(entity));
    }

    @Transactional
    public InfraDataStorageDto updateDataStorage(UUID id, DataStorageRequest request, String username) {
        InfraDataStorage entity = storageRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        applyDataStorage(entity, request, username);
        return toDto(storageRepository.save(entity));
    }

    @Transactional
    public void deleteDataStorage(UUID id) {
        storageRepository.deleteById(id);
    }

    public List<ConnectionTestLogDto> recentTestLogs(UUID dataSourceId) {
        try {
            List<InfraConnectionTestLog> logs = dataSourceId != null
                ? testLogRepository.findTop20ByDataSourceIdOrderByCreatedDateDesc(dataSourceId)
                : testLogRepository.findTop20ByOrderByCreatedDateDesc();
            return logs
                .stream()
                .map(log -> new ConnectionTestLogDto(log.getId(), log.getDataSourceId(), log.getResult(), log.getMessage(), log.getElapsedMs(), log.getCreatedDate()))
                .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            // If the infra_connection_test_log table doesn't exist yet (older DB before migration),
            // do not fail the whole request. Log and return an empty list.
            LOG.warn("recentTestLogs failed (likely missing table). Returning empty list. cause={}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public void recordConnectionTest(UUID dataSourceId, Object payload, HiveConnectionTestResult result, String username) {
        try {
            InfraConnectionTestLog log = new InfraConnectionTestLog();
            log.setDataSourceId(dataSourceId);
            log.setResult(result.success() ? "SUCCESS" : "FAILED");
            log.setMessage(result.message());
            log.setElapsedMs((int) result.elapsedMillis());
            log.setRequestPayload(objectMapper.writeValueAsString(redactPayload(payload)));
            log.setCreatedBy(username);
            log.setCreatedDate(Instant.now());
            testLogRepository.save(log);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to persist connection test log: {}", e.getMessage());
        }
    }

    @Transactional
    public void markDataSourceVerified(UUID dataSourceId) {
        dataSourceRepository.findById(dataSourceId).ifPresent(entity -> {
            entity.setLastVerifiedAt(Instant.now());
            dataSourceRepository.save(entity);
        });
    }

    private Object redactPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof HiveConnectionTestRequest request) {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("jdbcUrl", request.getJdbcUrl());
            safe.put("loginPrincipal", request.getLoginPrincipal());
            safe.put("authMethod", request.getAuthMethod() != null ? request.getAuthMethod().name() : null);
            safe.put("proxyUser", request.getProxyUser());
            safe.put("testQuery", request.getTestQuery());
            safe.put("jdbcProperties", request.getJdbcProperties());
            safe.put("remarks", request.getRemarks());
            safe.put("krb5Provided", StringUtils.hasText(request.getKrb5Conf()));
            safe.put("keytabProvided", StringUtils.hasText(request.getKeytabBase64()));
            safe.put("passwordProvided", StringUtils.hasText(request.getPassword()));
            return safe;
        }
        if (payload instanceof com.yuzhi.dts.platform.web.rest.infra.JdbcConnectionTestRequest request) {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("jdbcUrl", request.getJdbcUrl());
            safe.put("driverClass", request.getDriverClass());
            safe.put("driverVersion", request.getDriverVersion());
            safe.put("username", request.getUsername());
            safe.put("passwordProvided", StringUtils.hasText(request.getPassword()));
            return safe;
        }
        if (payload instanceof DataSourceRequest request) {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("name", request.name());
            safe.put("type", request.type());
            safe.put("jdbcUrl", request.jdbcUrl());
            safe.put("username", request.username());
            safe.put("description", request.description());
            safe.put("props", request.props());
            safe.put("secretsProvided", request.secrets() != null && !request.secrets().isEmpty());
            return safe;
        }
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k == null) {
                    return;
                }
                String key = String.valueOf(k);
                String normalized = key.toLowerCase(Locale.ROOT);
                if (
                    normalized.contains("password") ||
                    normalized.contains("keytab") ||
                    normalized.contains("krb5") ||
                    normalized.contains("secret")
                ) {
                    safe.put(key, "***");
                } else {
                    safe.put(key, v);
                }
            });
            return safe;
        }
        return payload;
    }

    public boolean isMultiSourceEnabled() {
        return securityProperties.isMultiSourceEnabled();
    }

    private void applyDataSource(InfraDataSource entity, DataSourceRequest request, String username) {
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setJdbcUrl(ApiDataSourceSupport.isApiType(request.type()) ? null : request.jdbcUrl());
        entity.setUsername(request.username());
        entity.setDescription(request.description());
        Map<String, Object> props = request.props() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.props());
        boolean apiType = ApiDataSourceSupport.isApiType(request.type());
        if (apiType) {
            props = ApiDataSourceSupport.normalizeProps(props);
            ApiSecretMetadata previousMetadata = apiSecretMetadataService.readFromProps(readProps(entity.getProps()));
            ApiSecretMetadata nextMetadata = apiSecretMetadataService.computeMetadata(
                ApiDataSourceSupport.extractAuthProviderId(props),
                request.secrets(),
                previousMetadata
            );
            apiSecretMetadataService.writeIntoProps(props, nextMetadata);
        } else if (isJdbcRequest(request)) {
            String readerType = extractReaderType(props);
            if (!StringUtils.hasText(readerType)) {
                String resolved = resolveJdbcReaderType(request, props);
                if (StringUtils.hasText(resolved)) {
                    props.put("readerType", resolved);
                }
            }
        }
        entity.setProps(writeProps(props));
        if (request.secrets() != null) {
            secretService.applySecrets(entity, request.secrets());
        }
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_ACTIVE);
        }
    }

    private String resolveJdbcReaderType(DataSourceRequest request, Map<String, Object> props) {
        String type = normalizeType(request == null ? null : request.type());
        if (StringUtils.hasText(type)) {
            if (type.contains("dm")) {
                return "rdbmsreader";
            }
            if (type.contains("postgres")) {
                return "postgresqlreader";
            }
            if (type.contains("mysql") || type.contains("mariadb")) {
                return "mysqlreader";
            }
            if (type.contains("oracle")) {
                return "oraclereader";
            }
            if (type.contains("sqlserver") || type.contains("mssql")) {
                return "sqlserverreader";
            }
            if (type.contains("clickhouse")) {
                return "clickhousereader";
            }
            if (type.contains("hive")) {
                return "hivereader";
            }
            if (type.contains("db2")) {
                return "db2reader";
            }
            if (type.contains("sqlite")) {
                return "sqlitereader";
            }
            if (type.contains("jdbc")) {
                return "rdbmsreader";
            }
        }
        String jdbcUrl = normalizeText(request == null ? null : request.jdbcUrl());
        if (!StringUtils.hasText(jdbcUrl) && props != null) {
            Object raw = props.get("jdbcUrl");
            if (raw != null) {
                jdbcUrl = normalizeText(raw);
            }
        }
        if (StringUtils.hasText(jdbcUrl)) {
            String lower = jdbcUrl.toLowerCase(Locale.ROOT);
            if (lower.startsWith("jdbc:dm:")) {
                return "rdbmsreader";
            }
            if (lower.startsWith("jdbc:postgresql:")) {
                return "postgresqlreader";
            }
            if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                return "mysqlreader";
            }
            if (lower.startsWith("jdbc:oracle:")) {
                return "oraclereader";
            }
            if (lower.startsWith("jdbc:sqlserver:")) {
                return "sqlserverreader";
            }
            if (lower.startsWith("jdbc:clickhouse:")) {
                return "clickhousereader";
            }
            if (lower.startsWith("jdbc:hive2:")) {
                return "hivereader";
            }
            if (lower.startsWith("jdbc:db2:")) {
                return "db2reader";
            }
            if (lower.startsWith("jdbc:sqlite:")) {
                return "sqlitereader";
            }
        }
        return "rdbmsreader";
    }

    private record ConnectionChange(
        boolean changed,
        List<String> fields,
        Map<String, Object> before,
        Map<String, Object> after
    ) {}

    private record ImpactResult(int affected, int created, List<Long> taskIds) {
        static ImpactResult empty() {
            return new ImpactResult(0, 0, List.of());
        }
    }

    private ConnectionChange buildConnectionChange(
        String beforeType,
        String beforeJdbcUrl,
        String beforeUsername,
        Map<String, Object> beforeProps,
        Map<String, Object> beforeSecrets,
        String afterType,
        String afterJdbcUrl,
        String afterUsername,
        Map<String, Object> afterProps,
        Map<String, Object> afterSecrets
    ) {
        Map<String, Object> beforeSig = buildConnectionSignature(beforeType, beforeJdbcUrl, beforeUsername, beforeProps, beforeSecrets);
        Map<String, Object> afterSig = buildConnectionSignature(afterType, afterJdbcUrl, afterUsername, afterProps, afterSecrets);
        List<String> changedFields = new java.util.ArrayList<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(beforeSig.keySet());
        keys.addAll(afterSig.keySet());
        keys.remove("passwordPresent");
        for (String key : keys) {
            if (!Objects.equals(beforeSig.get(key), afterSig.get(key))) {
                changedFields.add(key);
            }
        }
        String beforePassword = extractPassword(beforeSecrets);
        String afterPassword = extractPassword(afterSecrets);
        if (!Objects.equals(beforePassword, afterPassword)) {
            changedFields.add("password");
        }
        boolean changed = !changedFields.isEmpty();
        return new ConnectionChange(changed, List.copyOf(changedFields), beforeSig, afterSig);
    }

    private Map<String, Object> buildConnectionSignature(
        String type,
        String jdbcUrl,
        String username,
        Map<String, Object> props,
        Map<String, Object> secrets
    ) {
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("type", normalizeText(type));
        signature.put("jdbcUrl", normalizeText(jdbcUrl));
        signature.put("username", normalizeText(username));
        signature.put("host", normalizeText(props.get("host")));
        signature.put("port", normalizeNumber(props.get("port")));
        signature.put("database", normalizeText(props.get("database")));
        signature.put("schema", normalizeText(props.get("schema")));
        signature.put("driverClass", normalizeText(props.get("driverClass")));
        signature.put("driverVersion", normalizeText(props.get("driverVersion")));
        signature.put("jdbcProperties", normalizeStringMap(props.get("jdbcProperties")));
        signature.put("readerType", normalizeText(extractReaderType(props)));
        signature.put("format", normalizeText(props.get("format")));
        signature.put("passwordPresent", StringUtils.hasText(extractPassword(secrets)));
        return signature;
    }

    private Object normalizeNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ex) {
            return normalizeText(value);
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, String> normalizeStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        java.util.Map<String, String> normalized = new java.util.TreeMap<>();
        map.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            String key = k.toString();
            normalized.put(key, v == null ? null : v.toString());
        });
        return normalized;
    }

    private ImpactResult notifyIngestionTasks(InfraDataSource dataSource, ConnectionChange change, String operator) {
        if (dataSource == null || dataSource.getId() == null) {
            return ImpactResult.empty();
        }
        if (!ingestionServiceClient.isEnabled()) {
            return ImpactResult.empty();
        }
        ApiResponse<Object> response = ingestionServiceClient.listTasksBySource(dataSource.getId(), false);
        List<IngestionTaskSummary> tasks = extractTaskSummaries(response == null ? null : response.getData());
        if (tasks.isEmpty()) {
            return ImpactResult.empty();
        }
        int created = 0;
        List<Long> taskIds = new java.util.ArrayList<>();
        for (IngestionTaskSummary task : tasks) {
            if (task.id() == null) {
                continue;
            }
            taskIds.add(task.id());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("dataSourceId", dataSource.getId().toString());
            detail.put("dataSourceName", dataSource.getName());
            detail.put("changedFields", change.fields());
            detail.put("before", change.before());
            detail.put("after", change.after());
            detail.put("operator", operator);
            detail.put("timestamp", Instant.now().toString());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.id());
            payload.put("taskName", task.name());
            payload.put("changeType", "CONN_PARAM");
            payload.put("summary", "数据源连接已更新，请确认入湖任务");
            payload.put("detail", toJson(detail));
            payload.put("riskLevel", "M");
            payload.put("status", "PENDING");
            ApiResponse<Map<String, Object>> result = ingestionServiceClient.createChangeLog(payload);
            if (result != null && result.getStatus() >= 200 && result.getStatus() < 300) {
                created++;
            }
        }
        if (!tasks.isEmpty()) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_IMPACT",
                AuditStage.SUCCESS,
                dataSource.getId().toString(),
                Map.of(
                    "summary",
                    "数据源连接变更影响入湖任务",
                    "dataSourceId",
                    dataSource.getId().toString(),
                    "dataSourceName",
                    dataSource.getName(),
                    "affectedTasks",
                    tasks.size(),
                    "changeLogCreated",
                    created,
                    "taskIds",
                    taskIds,
                    "operator",
                    operator
                )
            );
        }
        return new ImpactResult(tasks.size(), created, List.copyOf(taskIds));
    }

    private record IngestionTaskSummary(Long id, String name, String status) {}

    private List<IngestionTaskSummary> extractTaskSummaries(Object data) {
        if (data == null) {
            return List.of();
        }
        Object payload = data;
        if (payload instanceof Map<?, ?> map && map.containsKey("content")) {
            payload = map.get("content");
        }
        if (!(payload instanceof List<?> list)) {
            return List.of();
        }
        List<IngestionTaskSummary> tasks = new java.util.ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> item) {
                Long id = parseLong(item.get("id"));
                String name = normalizeText(item.get("name"));
                String status = normalizeText(item.get("status"));
                tasks.add(new IngestionTaskSummary(id, name, status));
            }
        }
        return tasks;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return value.toString();
        }
    }

    private void validateRequest(DataSourceRequest request, InfraDataSource existing) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (ApiDataSourceSupport.isApiType(request.type())) {
            ApiDataSourceSupport.validateRequest(request);
            return;
        }
        if (isJdbcRequest(request)) {
            if (!StringUtils.hasText(request.jdbcUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JDBC 地址不能为空");
            }
            if (!StringUtils.hasText(request.username())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
            }
            String password = extractPassword(request.secrets());
            if (StringUtils.hasText(password)) {
                return;
            }
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
            }
            Map<String, Object> secrets = secretService.readSecrets(existing);
            String existingPassword = extractPassword(secrets);
            if (!StringUtils.hasText(existingPassword)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
            }
            return;
        }
        String readerType = extractReaderType(request.props());
        if (!StringUtils.hasText(readerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 Reader 类型");
        }
    }

    private String extractPassword(Map<String, Object> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return null;
        }
        Object value = secrets.get("password");
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isJdbcRequest(DataSourceRequest request) {
        if (request == null) {
            return false;
        }
        if (StringUtils.hasText(request.jdbcUrl())) {
            return true;
        }
        String type = normalizeType(request.type());
        return JDBC_TYPES.contains(type);
    }

    private String extractReaderType(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        Object direct = props.get("readerType");
        if (direct == null) {
            direct = props.get("reader");
        }
        if (direct instanceof Map<?, ?> map) {
            Object inner = map.get("type");
            if (inner != null) {
                return inner.toString().trim();
            }
        }
        if (direct != null) {
            String text = direct.toString().trim();
            return text.isEmpty() ? null : text;
        }
        Object fallback = props.get("type");
        if (fallback != null) {
            String text = fallback.toString().trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private void applyInceptorDataSource(InfraDataSource entity, HiveConnectionPersistRequest request, String username) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setType(TYPE_INCEPTOR);
        entity.setJdbcUrl(request.getJdbcUrl());
        entity.setUsername(request.getLoginPrincipal());
        entity.setOwnerDept(null); // institute/global
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
        entity.setLastVerifiedAt(Instant.now());
        entity.setStatus(STATUS_ACTIVE);
        entity.setProps(writeProps(buildInceptorProps(request)));
        secretService.applySecrets(entity, buildInceptorSecrets(request));
    }

    private void purgeDuplicateInceptorRows(UUID keepId) {
        dataSourceRepository
            .findByTypeIgnoreCase(TYPE_INCEPTOR)
            .stream()
            .filter(ds -> !Objects.equals(ds.getId(), keepId))
            .forEach(dataSourceRepository::delete);
    }

    private void ensureNotInceptorManaged(String type) {
        if (!StringUtils.hasText(type)) {
            return;
        }
        if (TYPE_INCEPTOR.equalsIgnoreCase(type)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Inceptor 数据源由专用流程管理，请使用 Hive 测试与发布功能"
            );
        }
    }

    private void ensureNotSystemManaged(String type, String activeDeptHeader) {
        if (!StringUtils.hasText(type)) {
            return;
        }
        if (TYPE_POSTGRES.equalsIgnoreCase(type) && !isInstituteMaintainer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限维护系统数据源");
        }
        if (!isInstituteMaintainer()) {
            String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
            if (dept.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少部门上下文，请先选择部门范围");
            }
        }
    }

    private void applyOwnerDept(InfraDataSource entity, String activeDeptHeader) {
        if (entity == null) return;
        if (isInstituteMaintainer()) {
            // Do not override existing department binding on updates.
            // For new records, allow optionally binding to current department context.
            if (entity.getId() == null && !StringUtils.hasText(entity.getOwnerDept())) {
                String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
                entity.setOwnerDept(dept.isEmpty() ? null : dept);
            }
            return;
        }
        String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
        if (dept.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少部门上下文，请先选择部门范围");
        }
        entity.setOwnerDept(dept);
    }

    private void ensureDeptScopeWritable(InfraDataSource entity, String activeDeptHeader) {
        if (entity == null) return;
        if (isInstituteMaintainer()) return;

        String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
        if (dept.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少部门上下文，请先选择部门范围");
        }
        String owner = normalizeDept(entity.getOwnerDept());
        if (owner.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限维护所级数据源");
        }
        if (!owner.equalsIgnoreCase(dept)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限维护其他部门的数据源");
        }
    }

    private void ensureDeptScopeReadable(InfraDataSource entity, String activeDeptHeader) {
        if (entity == null) return;
        if (isInstituteMaintainer()) return;

        String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
        String owner = normalizeDept(entity.getOwnerDept());
        if (dept.isEmpty() || owner.isEmpty()) {
            return;
        }
        if (!owner.equalsIgnoreCase(dept)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限查看其他部门的数据源");
        }
    }

    private boolean isInstituteMaintainer() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
    }

    private String normalizeDept(String dept) {
        if (!StringUtils.hasText(dept)) {
            return "";
        }
        return DepartmentUtils.normalize(dept);
    }

    private String resolveActiveDept(String activeDeptHeader) {
        String candidate = StringUtils.hasText(activeDeptHeader) ? activeDeptHeader.trim() : null;
        if (StringUtils.hasText(candidate)) {
            return candidate;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("dept_code");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
                v = token.getToken().getClaims().get("deptCode");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
                v = token.getToken().getClaims().get("department");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute("dept_code");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
                v = principal.getAttribute("deptCode");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
                v = principal.getAttribute("department");
                if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> buildInceptorProps(HiveConnectionPersistRequest request) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("servicePrincipal", request.getServicePrincipal());
        props.put("host", request.getHost());
        props.put("port", request.getPort());
        props.put("database", request.getDatabase());
        props.put("useHttpTransport", Boolean.TRUE.equals(request.getUseHttpTransport()));
        if (StringUtils.hasText(request.getHttpPath())) {
            props.put("httpPath", request.getHttpPath());
        }
        props.put("useSsl", Boolean.TRUE.equals(request.getUseSsl()));
        props.put("useCustomJdbc", Boolean.TRUE.equals(request.getUseCustomJdbc()));
        if (StringUtils.hasText(request.getCustomJdbcUrl())) {
            props.put("customJdbcUrl", request.getCustomJdbcUrl());
        }
        if (StringUtils.hasText(request.getProxyUser())) {
            props.put("proxyUser", request.getProxyUser());
        }
        if (StringUtils.hasText(request.getTestQuery())) {
            props.put("testQuery", request.getTestQuery());
        }
        if (request.getJdbcProperties() != null && !request.getJdbcProperties().isEmpty()) {
            props.put("jdbcProperties", request.getJdbcProperties());
        }
        props.put("authMethod", request.getAuthMethod().name());
        props.put("loginPrincipal", request.getLoginPrincipal());
        if (request.getLastTestElapsedMillis() != null && request.getLastTestElapsedMillis() > 0) {
            props.put("lastTestElapsedMillis", request.getLastTestElapsedMillis());
        }
        if (StringUtils.hasText(request.getEngineVersion())) {
            props.put("engineVersion", request.getEngineVersion());
        }
        if (StringUtils.hasText(request.getDriverVersion())) {
            props.put("driverVersion", request.getDriverVersion());
        }
        return props;
    }

    private Map<String, Object> buildInceptorSecrets(HiveConnectionPersistRequest request) {
        Map<String, Object> secrets = new LinkedHashMap<>();
        secrets.put("authMethod", request.getAuthMethod().name());
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
            secrets.put("keytabBase64", request.getKeytabBase64());
            secrets.put("keytabFileName", request.getKeytabFileName());
        }
        if (
            (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD ||
                request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.JDBC_PASSWORD) &&
            StringUtils.hasText(request.getPassword())
        ) {
            secrets.put("password", request.getPassword());
        }
        if (request.getAuthMethod() != HiveConnectionTestRequest.AuthMethod.JDBC_PASSWORD && StringUtils.hasText(request.getKrb5Conf())) {
            secrets.put("krb5Conf", request.getKrb5Conf());
        }
        return secrets;
    }

    private void applyDataStorage(InfraDataStorage entity, DataStorageRequest request, String username) {
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setLocation(request.location());
        entity.setDescription(request.description());
        entity.setProps(writeProps(request.props()));
        if (request.secrets() != null) {
            secretService.applySecrets(entity, request.secrets());
        }
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
    }

    private InfraDataSourceDto toDto(InfraDataSource entity) {
        Map<String, Object> props = readProps(entity.getProps());
        return new InfraDataSourceDto(
            entity.getId(),
            entity.getName(),
            entity.getType(),
            entity.getJdbcUrl(),
            entity.getUsername(),
            entity.getDescription(),
            entity.getOwnerDept(),
            props,
            entity.getCreatedDate(),
            entity.getLastModifiedDate(),
            entity.getLastVerifiedAt(),
            entity.getStatus(),
            entity.getSecureProps() != null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private InfraDataSourceDto toDto(AdminInfraClient.AdminDataLakeConfig lake) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", SOURCE_ADMIN_DATA_LAKE);
        props.put("defaulted", Boolean.TRUE.equals(lake.getDefaulted()));
        if (lake.getJdbcProperties() != null && !lake.getJdbcProperties().isEmpty()) {
            props.putAll(lake.getJdbcProperties());
        }
        if (StringUtils.hasText(lake.getEngineVersion())) {
            props.put("engineVersion", lake.getEngineVersion());
        }
        if (StringUtils.hasText(lake.getDriverVersion())) {
            props.put("driverVersion", lake.getDriverVersion());
        }
        return new InfraDataSourceDto(
            lake.getId(),
            StringUtils.hasText(lake.getName()) ? lake.getName() : "默认数据湖",
            StringUtils.hasText(lake.getType()) ? lake.getType() : "DATA_LAKE",
            lake.getJdbcUrl(),
            lake.getUsername(),
            StringUtils.hasText(lake.getDescription()) ? lake.getDescription() : "由管理员在系统管理中配置的默认数据湖",
            null,
            props,
            null,
            null,
            lake.getLastVerifiedAt(),
            StringUtils.hasText(lake.getStatus()) ? lake.getStatus() : STATUS_ACTIVE,
            StringUtils.hasText(lake.getPassword()),
            lake.getEngineVersion(),
            lake.getDriverVersion(),
            lake.getLastTestElapsedMillis(),
            lake.getLastHeartbeatAt(),
            lake.getHeartbeatStatus(),
            lake.getHeartbeatFailureCount(),
            lake.getLastError()
        );
    }

    private com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto toDetailDto(AdminInfraClient.AdminDataLakeConfig lake) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", SOURCE_ADMIN_DATA_LAKE);
        props.put("defaulted", Boolean.TRUE.equals(lake.getDefaulted()));
        if (lake.getJdbcProperties() != null && !lake.getJdbcProperties().isEmpty()) {
            props.putAll(lake.getJdbcProperties());
        }
        if (StringUtils.hasText(lake.getEngineVersion())) {
            props.put("engineVersion", lake.getEngineVersion());
        }
        if (StringUtils.hasText(lake.getDriverVersion())) {
            props.put("driverVersion", lake.getDriverVersion());
        }
        Map<String, Object> secrets = new LinkedHashMap<>();
        if (StringUtils.hasText(lake.getPassword())) {
            secrets.put("password", lake.getPassword());
        }
        return new com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto(
            lake.getId(),
            StringUtils.hasText(lake.getName()) ? lake.getName() : "默认数据湖",
            StringUtils.hasText(lake.getType()) ? lake.getType() : "DATA_LAKE",
            lake.getJdbcUrl(),
            lake.getUsername(),
            StringUtils.hasText(lake.getDescription()) ? lake.getDescription() : "由管理员在系统管理中配置的默认数据湖",
            null,
            props,
            secrets,
            List.of(),
            StringUtils.hasText(lake.getStatus()) ? lake.getStatus() : STATUS_ACTIVE,
            lake.getLastVerifiedAt()
        );
    }

    private Optional<AdminInfraClient.AdminDataLakeConfig> findAdminDataLakeById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return adminInfraClient.fetchDefaultDataLake().filter(lake -> id.equals(lake.getId()));
    }

    private InfraDataStorageDto toDto(InfraDataStorage entity) {
        Map<String, Object> props = readProps(entity.getProps());
        return new InfraDataStorageDto(
            entity.getId(),
            entity.getName(),
            entity.getType(),
            entity.getLocation(),
            entity.getDescription(),
            props,
            entity.getCreatedDate(),
            entity.getSecureProps() != null
        );
    }

    private void syncCatalogIfEnabled(InfraDataSource dataSource, DataSourceRequest request, String operator) {
        if (dataSource == null || request == null) {
            return;
        }
        boolean enabled = shouldSyncCatalogOnDataSource();
        if (ApiDataSourceSupport.isApiType(request.type())) {
            auditCatalogSync(
                dataSource,
                operator,
                new CatalogSyncSummary(enabled, true, "api-preview-required", null, null, null, 0, false),
                AuditStage.SUCCESS,
                null
            );
            return;
        }
        if (isJdbcRequest(request)) {
            if (!enabled) {
                auditJdbcCatalogSync(
                    dataSource,
                    operator,
                    JdbcSyncResult.skipped(dataSource.getId(), "disabled"),
                    AuditStage.SUCCESS,
                    null
                );
                return;
            }
            try {
                JdbcSyncResult result = jdbcCatalogSyncService.synchronize(dataSource);
                updateJdbcSyncResult(dataSource.getId(), result);
                recordJdbcSyncRun(dataSource, result, "datasource-auto");
                AuditStage stage = "FAILED".equalsIgnoreCase(result.status()) ? AuditStage.FAIL : AuditStage.SUCCESS;
                auditJdbcCatalogSync(dataSource, operator, result, stage, result.error());
            } catch (Exception ex) {
                LOG.warn("[catalog-sync] jdbc sync failed for {}: {}", dataSource.getId(), ex.getMessage());
                recordJdbcSyncRun(dataSource, JdbcSyncResult.failed(dataSource.getId(), ex.getMessage()), "datasource-auto");
                auditJdbcCatalogSync(
                    dataSource,
                    operator,
                    JdbcSyncResult.failed(dataSource.getId(), ex.getMessage()),
                    AuditStage.FAIL,
                    ex.getMessage()
                );
            }
            return;
        }
        if (!enabled) {
            auditCatalogSync(dataSource, operator, new CatalogSyncSummary(false, true, "disabled", null, null, null, 0, false), AuditStage.SUCCESS, null);
            return;
        }
        try {
            CatalogSyncSummary summary = syncFileSourceCatalog(dataSource, request, operator);
            auditCatalogSync(dataSource, operator, summary, AuditStage.SUCCESS, null);
        } catch (Exception ex) {
            LOG.warn("[catalog-sync] failed to sync datasource {}: {}", dataSource.getId(), ex.getMessage());
            auditCatalogSync(
                dataSource,
                operator,
                new CatalogSyncSummary(true, false, "failed", null, null, null, 0, false),
                AuditStage.FAIL,
                ex.getMessage()
            );
        }
    }

    private boolean shouldSyncCatalogOnDataSource() {
        Map<String, Object> settings = ingestionServiceClient.getInfraSettings(SETTINGS_SERVICE_PLATFORM);
        if (settings == null || settings.isEmpty()) {
            return true;
        }
        Object raw = settings.get(SETTINGS_KEY_CATALOG_SYNC);
        if (raw == null) {
            return true;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String text = raw.toString().trim();
        return text.isEmpty() || Boolean.parseBoolean(text);
    }

    private CatalogSyncSummary syncFileSourceCatalog(InfraDataSource dataSource, DataSourceRequest request, String operator) {
        Map<String, Object> props = request.props() == null || request.props().isEmpty()
            ? readProps(dataSource.getProps())
            : request.props();
        List<ColumnSpec> specs = resolveColumnSpecsFromProps(props);
        String schema = DEFAULT_FILE_SCHEMA;
        String desiredTable = normalizeFileTableName(dataSource.getName());
        String finalTable = desiredTable;
        boolean adjusted = false;

        CatalogDataset dataset = datasetRepository
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(dataSource.getId(), schema, desiredTable)
            .orElse(null);
        if (dataset == null) {
            List<CatalogDataset> existing = datasetRepository.findBySourceIdAndHiveDatabaseIgnoreCase(dataSource.getId(), schema);
            if (!existing.isEmpty()) {
                dataset = existing.get(0);
            }
        }

        if (dataset == null) {
            finalTable = ensureUniqueFileTable(schema, desiredTable, dataSource.getId());
            adjusted = !finalTable.equalsIgnoreCase(desiredTable);
            dataset = new CatalogDataset();
        } else {
            String currentTable = dataset.getHiveTable();
            if (!StringUtils.hasText(currentTable) || !currentTable.equalsIgnoreCase(desiredTable)) {
                finalTable = ensureUniqueFileTable(schema, desiredTable, dataSource.getId());
                adjusted = !finalTable.equalsIgnoreCase(desiredTable);
            } else {
                finalTable = currentTable;
            }
        }

        dataset.setName(finalTable);
        dataset.setType(DATASET_TYPE_FILE);
        dataset.setSourceId(dataSource.getId());
        dataset.setHiveDatabase(schema);
        dataset.setHiveTable(finalTable);
        if (!StringUtils.hasText(dataset.getWarehouseLayer())) {
            dataset.setWarehouseLayer("ODS");
        }
        dataset.setDescription(dataSource.getDescription());
        if (!StringUtils.hasText(dataset.getOwner()) && StringUtils.hasText(operator)) {
            dataset.setOwner(operator);
        }
        if (!StringUtils.hasText(dataset.getOwnerDept()) && StringUtils.hasText(dataSource.getOwnerDept())) {
            dataset.setOwnerDept(dataSource.getOwnerDept());
        }
        CatalogDataset savedDataset = datasetRepository.save(dataset);

        CatalogTableSchema table = tableRepository
            .findFirstByDatasetAndNameIgnoreCase(savedDataset, finalTable)
            .orElse(null);
        if (table == null) {
            List<CatalogTableSchema> tables = tableRepository.findByDataset(savedDataset);
            if (tables.size() == 1) {
                table = tables.get(0);
            } else {
                table = new CatalogTableSchema();
                table.setDataset(savedDataset);
            }
        }
        table.setName(finalTable);
        if (!StringUtils.hasText(table.getOwner()) && StringUtils.hasText(operator)) {
            table.setOwner(operator);
        }
        CatalogTableSchema savedTable = tableRepository.save(table);

        int columnCount = 0;
        if (!specs.isEmpty()) {
            columnCount = columnSyncService.upsertColumns(savedTable, specs, CatalogColumnSyncService.STATUS_DRAFT);
        }
        return new CatalogSyncSummary(false, false, null, schema, finalTable, savedDataset.getId(), columnCount, adjusted);
    }

    private void auditCatalogSync(
        InfraDataSource dataSource,
        String operator,
        CatalogSyncSummary summary,
        AuditStage stage,
        String error
    ) {
        if (dataSource == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "同步数据源字段到元数据");
        meta.put("dataSourceId", dataSource.getId() != null ? dataSource.getId().toString() : null);
        meta.put("dataSourceName", dataSource.getName());
        meta.put("dataSourceType", dataSource.getType());
        meta.put("operator", operator);
        if (summary != null) {
            meta.put("syncEnabled", summary.syncEnabled());
            meta.put("skipped", summary.skipped());
            meta.put("skipReason", summary.skipReason());
            meta.put("schema", summary.schema());
            meta.put("table", summary.table());
            meta.put("datasetId", summary.datasetId() != null ? summary.datasetId().toString() : null);
            meta.put("columnCount", summary.columnCount());
            meta.put("nameAdjusted", summary.nameAdjusted());
        }
        if (StringUtils.hasText(error)) {
            meta.put("error", error);
        }
        auditService.auditAction(
            AUDIT_ACTION_CATALOG_SYNC,
            stage,
            dataSource.getId() != null ? dataSource.getId().toString() : "catalog-sync",
            meta
        );
    }

    private void auditJdbcCatalogSync(
        InfraDataSource dataSource,
        String operator,
        JdbcSyncResult result,
        AuditStage stage,
        String error
    ) {
        if (dataSource == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "同步 JDBC 数据源元数据");
        meta.put("dataSourceId", dataSource.getId() != null ? dataSource.getId().toString() : null);
        meta.put("dataSourceName", dataSource.getName());
        meta.put("dataSourceType", dataSource.getType());
        meta.put("operator", operator);
        if (result != null) {
            meta.put("status", result.status());
            meta.put("schemas", result.schemas());
            meta.put("tablesDiscovered", result.tablesDiscovered());
            meta.put("datasetsCreated", result.datasetsCreated());
            meta.put("datasetsUpdated", result.datasetsUpdated());
            meta.put("datasetsRemoved", result.datasetsRemoved());
            meta.put("datasetsMarkedStale", result.datasetsMarkedStale());
            meta.put("datasetsPurged", result.datasetsPurged());
            meta.put("tablesCreated", result.tablesCreated());
            meta.put("columnsImported", result.columnsImported());
            meta.put("databaseProduct", result.databaseProduct());
            meta.put("databaseVersion", result.databaseVersion());
            meta.put("elapsedMs", result.elapsedMs());
        }
        if (StringUtils.hasText(error)) {
            meta.put("error", error);
        }
        auditService.auditAction(
            AUDIT_ACTION_CATALOG_SYNC,
            stage,
            dataSource.getId() != null ? dataSource.getId().toString() : "catalog-sync",
            meta
        );
    }

    private void recordJdbcSyncRun(InfraDataSource dataSource, JdbcSyncResult result, String reason) {
        if (dataSource == null || dataSource.getId() == null) {
            return;
        }
        try {
            InfraCatalogSyncRun run = new InfraCatalogSyncRun();
            run.setIntegration("JDBC");
            run.setReason(StringUtils.hasText(reason) ? reason : "datasource-auto");
            run.setStartedAt(Instant.now());
            run.setFinishedAt(Instant.now());
            run.setCatalogDatasetCountBefore(safeDatasetCount());
            run.setCatalogDatasetCountAfter(safeDatasetCount());
            if (result != null) {
                run.setDatasetsCreated(result.datasetsCreated());
                run.setDatasetsUpdated(result.datasetsUpdated());
                run.setDatasetsRemoved(result.datasetsRemoved());
                run.setTablesCreated(result.tablesCreated());
                run.setColumnsImported(result.columnsImported());
                run.setError(result.error());
                run.setStatus("FAILED".equalsIgnoreCase(result.status()) ? "FAILED" : "SUCCESS");
                run.setDetailsJson(writeProps(Map.of("single", true, "result", result)));
            } else {
                run.setStatus("FAILED");
                run.setError("empty-result");
            }
            syncRunRepository.save(run);
        } catch (Exception ex) {
            LOG.debug("[catalog-sync] failed to record jdbc sync run: {}", ex.getMessage());
        }
    }

    private long safeDatasetCount() {
        try {
            return datasetRepository.count();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private record CatalogSyncSummary(
        boolean syncEnabled,
        boolean skipped,
        String skipReason,
        String schema,
        String table,
        UUID datasetId,
        int columnCount,
        boolean nameAdjusted
    ) {}

    private List<ColumnSpec> resolveColumnSpecsFromProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return List.of();
        }
        Map<String, Object> readerConfig = readMap(props.get("readerConfig"));
        Object columnRaw = readerConfig.get("column");
        if (columnRaw == null) {
            columnRaw = readerConfig.get("columns");
        }
        return extractColumnSpecs(columnRaw);
    }

    private List<ColumnSpec> extractColumnSpecs(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<ColumnSpec> specs = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                ColumnSpec spec = toColumnSpec(item);
                if (spec != null) {
                    specs.add(spec);
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            ColumnSpec spec = toColumnSpec(map);
            if (spec != null) {
                specs.add(spec);
            }
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            for (String name : text.split(",")) {
                if (!StringUtils.hasText(name)) continue;
                specs.add(new ColumnSpec(name.trim(), "string", null, null, null, null, null, null));
            }
        }
        return specs;
    }

    private ColumnSpec toColumnSpec(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof String text) {
            String name = text.trim();
            return StringUtils.hasText(name) ? new ColumnSpec(name, "string", null, null, null, null, null, null) : null;
        }
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> values = readMap(map);
            String name = normalizeField(values.get("name"));
            if (!StringUtils.hasText(name)) {
                name = normalizeField(values.get("column"));
            }
            if (!StringUtils.hasText(name)) {
                name = normalizeField(values.get("field"));
            }
            if (!StringUtils.hasText(name)) {
                return null;
            }
            String dataType = normalizeField(values.get("type"));
            if (!StringUtils.hasText(dataType)) {
                dataType = normalizeField(values.get("dataType"));
            }
            if (!StringUtils.hasText(dataType)) {
                dataType = "string";
            }
            return new ColumnSpec(name, dataType, null, null, null, null, null, null);
        }
        return null;
    }

    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        return Map.of();
    }

    private String normalizeField(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeFileTableName(String name) {
        String base = StringUtils.hasText(name) ? name.trim().toLowerCase(Locale.ROOT) : "file_source";
        String sanitized = base.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "file_source";
        }
        int max = 60;
        if (sanitized.length() > max) {
            sanitized = sanitized.substring(0, max);
        }
        return sanitized;
    }

    private String ensureUniqueFileTable(String schema, String base, UUID sourceId) {
        String candidate = base;
        int suffix = 1;
        while (candidate.length() > 0) {
            java.util.Optional<CatalogDataset> existing = datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
            CatalogDataset dataset = existing.orElseThrow();
            if (sourceId != null && sourceId.equals(dataset.getSourceId())) {
                return candidate;
            }
            String tail = "_" + suffix;
            String trimmed = base;
            if (trimmed.length() + tail.length() > 60) {
                trimmed = trimmed.substring(0, Math.max(1, 60 - tail.length()));
            }
            candidate = trimmed + tail;
            suffix++;
        }
        return base;
    }

    private String writeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize props", e);
        }
    }

    private Map<String, Object> readProps(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse props JSON: {}", e.getMessage());
            return Map.of("raw", json);
        }
    }
}
