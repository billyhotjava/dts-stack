package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.domain.InfraDataSource;
import com.yuzhi.dts.admin.repository.InfraDataSourceRepository;
import com.yuzhi.dts.admin.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.admin.service.infra.dto.HiveAuthMethod;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import com.yuzhi.dts.admin.service.infra.dto.IntegrationStatus;
import com.yuzhi.dts.admin.service.infra.dto.JdbcConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.JdbcDriverInfo;
import com.yuzhi.dts.admin.service.infra.dto.ModuleStatus;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.PlatformDataLakeConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.PlatformDataLakeDestinationUpdateRequest;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InfraAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(InfraAdminService.class);

    private static final int MAX_LOGS = 50;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String HEARTBEAT_UP = "UP";
    private static final String HEARTBEAT_DOWN = "DOWN";
    private static final String HEARTBEAT_UNKNOWN = "UNKNOWN";
    private static final long RELOAD_RETRY_DELAY_MS = 5000L;

    private final PlatformInfraClient platformInfraClient;
    private final IngestionInfraClient ingestionInfraClient;
    private final JdbcConnectionTestService jdbcConnectionTestService;
    private final JdbcDriverCatalogService jdbcDriverCatalogService;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean heartbeatEnabled;
    private final long heartbeatTimeoutMs;

    private final ConcurrentMap<UUID, InfraDataSourceDto> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ConnectionTestLogDto> testLogs = new ConcurrentLinkedDeque<>();

    private final AtomicReference<Instant> lastVerifiedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastUpdatedAt = new AtomicReference<>();
    private final AtomicReference<Long> lastTestElapsedMillis = new AtomicReference<>();
    private final AtomicReference<Instant> lastSyncAt = new AtomicReference<>();
    private final AtomicReference<String> integrationReason = new AtomicReference<>();
    private final AtomicReference<List<String>> integrationActions = new AtomicReference<>(Collections.emptyList());
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicReference<HiveConnectionPersistRequest> lastInceptorDefinition = new AtomicReference<>();
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    public InfraAdminService(
        PlatformInfraClient platformInfraClient,
        IngestionInfraClient ingestionInfraClient,
        JdbcConnectionTestService jdbcConnectionTestService,
        JdbcDriverCatalogService jdbcDriverCatalogService,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Value("${dts.infra.heartbeat.enabled:true}") boolean heartbeatEnabled,
        @Value("${dts.infra.heartbeat.timeout-ms:5000}") long heartbeatTimeoutMs
    ) {
        this.platformInfraClient = platformInfraClient;
        this.ingestionInfraClient = ingestionInfraClient;
        this.jdbcConnectionTestService = jdbcConnectionTestService;
        this.jdbcDriverCatalogService = jdbcDriverCatalogService;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.heartbeatEnabled = heartbeatEnabled;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    @PostConstruct
    public void init() {
        scheduleReload(0L);
    }

    public void reloadCache() {
        reloadCacheInternal();
    }

    private void reloadCacheInternal() {
        if (!schemaReady.get()) {
            if (!infraDataSourceTableExists()) {
                LOG.debug("Infra data source table not yet created, delaying cache load");
                scheduleReload(RELOAD_RETRY_DELAY_MS);
                return;
            }
        }
        List<InfraDataSource> entities;
        try {
            entities = dataSourceRepository.findAll();
        } catch (DataAccessException ex) {
            LOG.debug("Infra data source repository unavailable, will retry later: {}", ex.getMessage());
            LOG.trace("Infra data source repository not ready", ex);
            schemaReady.set(false);
            scheduleReload(RELOAD_RETRY_DELAY_MS);
            return;
        }
        cache.clear();
        Instant maxUpdated = null;
        Instant maxVerified = null;
        for (InfraDataSource entity : entities) {
            InfraDataSourceDto dto = toDto(entity);
            cache.put(dto.getId(), dto);
            maxUpdated = maxInstant(maxUpdated, entity.getUpdatedAt(), entity.getCreatedAt());
            maxVerified = maxInstant(maxVerified, entity.getLastVerifiedAt());
            updateLastInceptorDefinition(entity);
        }
        if (maxUpdated != null) {
            lastUpdatedAt.set(maxUpdated);
        }
        if (maxVerified != null) {
            lastVerifiedAt.set(maxVerified);
        }
        if (schemaReady.compareAndSet(false, true)) {
            LOG.info("Infra data source schema detected, cached {} data sources", entities.size());
        } else {
            LOG.debug("Infra data source cache refreshed with {} entries", entities.size());
        }
    }

    private void scheduleReload(long delayMs) {
        CompletableFuture.runAsync(this::reloadCacheInternal, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }

    private boolean infraDataSourceTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema() and table_name = ?",
                Integer.class,
                "infra_data_source"
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            LOG.debug("Failed to inspect infra_data_source table: {}", ex.getMessage());
            LOG.trace("Infra data source table inspection failure", ex);
            return false;
        }
    }

    public List<InfraDataSourceDto> listDataSources() {
        return cache
            .values()
            .stream()
            .sorted(Comparator.comparing(InfraDataSourceDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(InfraDataSourceDto::copy)
            .collect(Collectors.toList());
    }

    public Optional<InfraDataSourceDto> findDefaultDataLake() {
        Optional<InfraDataSourceDto> preferred = cache.values().stream().filter(InfraDataSourceDto::isDefaulted).findFirst();
        if (preferred.isPresent()) {
            return preferred.map(InfraDataSourceDto::copy);
        }
        Optional<InfraDataSourceDto> fallback = cache
            .values()
            .stream()
            .filter(ds -> !"INCEPTOR".equalsIgnoreCase(ds.getType()))
            .sorted(Comparator.comparing(InfraDataSourceDto::getLastUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .findFirst();
        if (fallback.isPresent()) {
            return fallback.map(InfraDataSourceDto::copy);
        }
        List<InfraDataSourceDto> list = listDataSources();
        if (list.size() == 1) {
            return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    public Optional<InfraDataSourceDto> setDefaultDataSource(UUID id, String operator) {
        return dataSourceRepository
            .findById(id)
            .map(entity -> {
                entity.setDefaulted(true);
                entity.setUpdatedAt(Instant.now());
                dataSourceRepository.save(entity);
                clearDefaultExcept(entity.getId());
                updateCache(entity);
                touchLastUpdated();
                return cache.get(entity.getId()).copy();
            });
    }

    public InfraDataSourceDto createDataSource(UpsertInfraDataSourcePayload payload, String operator) {
        InfraDataSource entity = new InfraDataSource();
        entity.setId(UUID.randomUUID());
        ensureUniqueConnectionSignature(payload, null);
        applyPayload(entity, payload);
        applySecrets(entity, payload.getSecrets());
        Instant now = Instant.now();
        entity.setStatus(STATUS_ACTIVE);
        entity.setHeartbeatStatus(HEARTBEAT_UNKNOWN);
        entity.setHeartbeatFailureCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (payload.getDefaulted() == null) {
            entity.setDefaulted(false);
        }
        dataSourceRepository.save(entity);
        if (entity.isDefaulted()) {
            clearDefaultExcept(entity.getId());
        }
        updateCache(entity);
        touchLastUpdated();
        return cache.get(entity.getId()).copy();
    }

    public record DataSourceMutation(InfraDataSourceDto before, InfraDataSourceDto after) {}

    public Optional<DataSourceMutation> updateDataSource(UUID id, UpsertInfraDataSourcePayload payload, String operator) {
        return dataSourceRepository
            .findById(id)
            .map(existing -> {
                InfraDataSourceDto before = toDto(existing);
                ensureUniqueConnectionSignature(payload, existing);
                applyPayload(existing, payload);
                if (payload.getSecretsRaw() != null) {
                    applySecrets(existing, payload.getSecrets());
                }
                existing.setUpdatedAt(Instant.now());
                dataSourceRepository.save(existing);
                if (Boolean.TRUE.equals(payload.getDefaulted())) {
                    clearDefaultExcept(existing.getId());
                }
                updateCache(existing);
                touchLastUpdated();
                InfraDataSourceDto after = cache.get(existing.getId()).copy();
                return new DataSourceMutation(before, after);
            });
    }

    public Optional<InfraDataSourceDto> getDataSource(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return dataSourceRepository.findById(id).map(this::toDto);
    }

    public Optional<InfraDataSourceDto> deleteDataSource(UUID id) {
        return dataSourceRepository
            .findById(id)
            .map(entity -> {
                InfraDataSourceDto snapshot = toDto(entity);
                dataSourceRepository.delete(entity);
                cache.remove(id);
                touchLastUpdated();
                return snapshot;
            });
    }

    public HiveConnectionTestResult testDataSourceConnection(HiveConnectionTestRequest request, UUID dataSourceId) {
        HiveConnectionTestResult result = platformInfraClient
            .testInceptorConnection(request)
            .orElseGet(() -> {
                HiveConnectionTestResult fallback = new HiveConnectionTestResult();
                fallback.setSuccess(false);
                fallback.setElapsedMillis(0L);
                fallback.setMessage("平台 SQL 测试不可用，请检查平台服务与驱动");
                return fallback;
            });
        if (!StringUtils.hasText(result.getMessage())) {
            result.setMessage(result.isSuccess() ? "连接成功" : "连接失败");
        }
        recordTestLog(dataSourceId, result);

        if (dataSourceId != null) {
            dataSourceRepository.findById(dataSourceId).ifPresent(entity -> applyTestResult(entity, result));
        } else {
            touchLastUpdated();
        }
        return result;
    }

    public Optional<HiveConnectionTestResult> testJdbcDataSourceConnection(UUID id, JdbcConnectionTestRequest request) {
        return dataSourceRepository.findById(id).map(entity -> {
            JdbcConnectionTestRequest payload = buildJdbcTestRequest(entity, request);
            HiveConnectionTestResult result = jdbcConnectionTestService.testConnection(payload);
            if (!StringUtils.hasText(result.getMessage())) {
                result.setMessage(result.isSuccess() ? "连接成功" : "连接失败");
            }
            recordTestLog(entity.getId(), result);
            applyTestResult(entity, result);
            return result;
        });
    }

    public DataSourceMutation publishInceptor(HiveConnectionPersistRequest request, String operator) {
        InfraDataSourceDto before = findFirstInceptor().map(InfraDataSourceDto::copy).orElse(null);
        InfraDataSource entity = findFirstInceptorEntity().orElseGet(() -> {
            InfraDataSource created = new InfraDataSource();
            created.setId(UUID.randomUUID());
            created.setType("INCEPTOR");
            created.setCreatedAt(Instant.now());
            created.setStatus(STATUS_ACTIVE);
            return created;
        });
        applyPersistRequest(entity, request);
        Instant now = Instant.now();
        if (request.getDefaulted() != null) {
            entity.setDefaulted(request.getDefaulted());
        } else if (!hasDefaultDataLake()) {
            entity.setDefaulted(true);
        }
        entity.setLastVerifiedAt(now);
        entity.setLastHeartbeatAt(now);
        entity.setHeartbeatStatus(HEARTBEAT_UP);
        entity.setHeartbeatFailureCount(0);
        entity.setLastError(null);
        entity.setUpdatedAt(now);
        dataSourceRepository.save(entity);
        if (entity.isDefaulted()) {
            clearDefaultExcept(entity.getId());
        }
        updateCache(entity);
        updateLastInceptorDefinition(entity);
        lastVerifiedAt.set(now);
        if (request.getLastTestElapsedMillis() != null) {
            lastTestElapsedMillis.set(request.getLastTestElapsedMillis());
        }
        touchLastUpdated();
        lastInceptorDefinition.set(clonePersistRequest(request));
        platformInfraClient.publishInceptor(request);
        platformInfraClient.refreshInceptor();
        InfraDataSourceDto after = cache.get(entity.getId()).copy();
        return new DataSourceMutation(before, after);
    }

    public InfraFeatureFlags refreshInceptorRegistry(String operator) {
        syncInProgress.set(true);
        integrationReason.set("触发人：" + operator);
        integrationActions.set(List.of("刷新 Inceptor 注册信息"));
        platformInfraClient.refreshInceptor();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                lastSyncAt.set(Instant.now());
                syncInProgress.set(false);
            }
        });
        return computeFeatureFlags();
    }

    public InfraFeatureFlags computeFeatureFlags() {
        InfraFeatureFlags flags = new InfraFeatureFlags();
        flags.setMultiSourceEnabled(true);
        Optional<InfraDataSourceDto> inceptor = findFirstInceptor();
        boolean hasInceptor = inceptor.isPresent();
        flags.setHasActiveInceptor(hasInceptor);
        flags.setInceptorStatus(hasInceptor ? STATUS_ACTIVE : "NOT_CONFIGURED");
        flags.setSyncInProgress(syncInProgress.get());
        flags.setDefaultJdbcUrl(inceptor.map(InfraDataSourceDto::getJdbcUrl).orElse(null));
        flags.setLoginPrincipal(inceptor.map(InfraDataSourceDto::getUsername).orElse(null));
        flags.setLastVerifiedAt(formatInstant(lastVerifiedAt.get()));
        flags.setLastUpdatedAt(formatInstant(lastUpdatedAt.get()));
        flags.setDataSourceName(inceptor.map(InfraDataSourceDto::getName).orElse(null));
        flags.setDescription(inceptor.map(InfraDataSourceDto::getDescription).orElse(null));
        flags.setAuthMethod((String) inceptor.map(ds -> ds.getProps().get("authMethod")).orElse(null));
        flags.setDatabase((String) inceptor.map(ds -> ds.getProps().get("database")).orElse(null));
        flags.setProxyUser((String) inceptor.map(ds -> ds.getProps().get("proxyUser")).orElse(null));
        flags.setEngineVersion(inceptor.map(InfraDataSourceDto::getEngineVersion).orElse(null));
        flags.setDriverVersion(inceptor.map(InfraDataSourceDto::getDriverVersion).orElse(null));
        flags.setLastTestElapsedMillis(lastTestElapsedMillis.get());
        flags.setLastHeartbeatAt(inceptor.map(InfraDataSourceDto::getLastHeartbeatAt).map(Instant::toString).orElse(null));
        flags.setHeartbeatStatus(inceptor.map(InfraDataSourceDto::getHeartbeatStatus).orElse(null));
        flags.setModuleStatuses(buildModuleStatuses(hasInceptor));
        flags.setIntegrationStatus(buildIntegrationStatus());
        return flags;
    }

    public List<JdbcDriverInfo> listJdbcDrivers() {
        return jdbcDriverCatalogService.listDrivers();
    }

    public Map<String, Object> getIntegrationSettings(String service) {
        return ingestionInfraClient.getServiceSettings(service);
    }

    public Map<String, Object> updateIntegrationSettings(String service, Map<String, Object> settings) {
        return ingestionInfraClient.updateServiceSettings(service, settings);
    }

    public Map<String, Object> testIntegrationSettings(String service, Map<String, Object> settings) {
        return ingestionInfraClient.testServiceSettings(service, settings);
    }

    public Optional<PlatformInceptorConfigResponse> currentPlatformInceptorConfig() {
        InfraDataSource entity = findFirstInceptorEntity().orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        HiveConnectionPersistRequest definition = lastInceptorDefinition.get();
        if (definition == null) {
            definition = buildPersistDefinition(entity).orElse(null);
            if (definition != null) {
                lastInceptorDefinition.set(clonePersistRequest(definition));
            }
        }
        if (definition == null) {
            return Optional.empty();
        }
        InfraDataSourceDto ds = toDto(entity);
        PlatformInceptorConfigResponse resp = new PlatformInceptorConfigResponse();
        resp.setId(ds.getId());
        resp.setName(ds.getName());
        resp.setDescription(ds.getDescription());
        resp.setJdbcUrl(ds.getJdbcUrl());
        resp.setLoginPrincipal(definition.getLoginPrincipal());
        resp.setAuthMethod(definition.getAuthMethod() != null ? definition.getAuthMethod().name() : null);
        resp.setKrb5Conf(definition.getKrb5Conf());
        resp.setKeytabBase64(definition.getKeytabBase64());
        resp.setKeytabFileName(definition.getKeytabFileName());
        resp.setPassword(definition.getPassword());
        resp.setJdbcProperties(definition.getJdbcProperties());
        resp.setProxyUser(definition.getProxyUser());
        resp.setServicePrincipal(definition.getServicePrincipal());
        resp.setHost(definition.getHost());
        resp.setPort(definition.getPort());
        resp.setDatabase(definition.getDatabase());
        resp.setUseHttpTransport(definition.isUseHttpTransport());
        resp.setHttpPath(definition.getHttpPath());
        resp.setUseSsl(definition.isUseSsl());
        resp.setUseCustomJdbc(definition.isUseCustomJdbc());
        resp.setCustomJdbcUrl(definition.getCustomJdbcUrl());
        resp.setDefaulted(entity.isDefaulted());
        resp.setDestinationId(definition.getDestinationId());
        resp.setDestinationName(definition.getDestinationName());
        resp.setDestinationDefinitionId(definition.getDestinationDefinitionId());
        resp.setDestinationConfig(definition.getDestinationConfig());
        resp.setLastTestElapsedMillis(ds.getLastTestElapsedMillis());
        resp.setEngineVersion(ds.getEngineVersion());
        resp.setDriverVersion(ds.getDriverVersion());
        resp.setLastVerifiedAt(ds.getLastVerifiedAt());
        resp.setLastUpdatedAt(ds.getLastUpdatedAt());
        resp.setLastHeartbeatAt(ds.getLastHeartbeatAt());
        resp.setHeartbeatStatus(ds.getHeartbeatStatus());
        resp.setHeartbeatFailureCount(ds.getHeartbeatFailureCount());
        resp.setLastError(ds.getLastError());
        return Optional.of(resp);
    }

    public Optional<PlatformDataLakeConfigResponse> currentPlatformDefaultDataLakeConfig() {
        InfraDataSourceDto preferred = findDefaultDataLake().orElse(null);
        if (preferred == null || preferred.getId() == null) {
            return Optional.empty();
        }
        InfraDataSource entity = dataSourceRepository.findById(preferred.getId()).orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        PlatformDataLakeConfigResponse resp = new PlatformDataLakeConfigResponse();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setType(entity.getType());
        resp.setDescription(entity.getDescription());
        resp.setJdbcUrl(entity.getJdbcUrl());
        resp.setUsername(entity.getUsername());
        resp.setDefaulted(entity.isDefaulted());
        Map<String, Object> props = entity.getProps();
        Map<String, Object> secrets = secretService.readSecrets(entity);
        resp.setPassword(asString(secrets.get("password")));
        resp.setJdbcProperties(stringMap(props.get("jdbcProperties")));
        resp.setDestinationId(asString(props.get("destinationId")));
        resp.setDestinationName(asString(props.get("destinationName")));
        resp.setDestinationDefinitionId(asString(props.get("destinationDefinitionId")));
        resp.setDestinationConfig(parseDestinationConfig(secrets.get("destinationConfig")));
        resp.setStatus(entity.getStatus());
        resp.setHeartbeatStatus(entity.getHeartbeatStatus());
        resp.setHeartbeatFailureCount(entity.getHeartbeatFailureCount());
        resp.setLastError(entity.getLastError());
        resp.setLastTestElapsedMillis(entity.getLastTestElapsedMillis());
        resp.setEngineVersion(entity.getEngineVersion());
        resp.setDriverVersion(entity.getDriverVersion());
        resp.setLastVerifiedAt(entity.getLastVerifiedAt());
        resp.setLastHeartbeatAt(entity.getLastHeartbeatAt());
        return Optional.of(resp);
    }

    public Optional<PlatformDataLakeConfigResponse> updateDefaultDataLakeDestination(
        PlatformDataLakeDestinationUpdateRequest request,
        String operator
    ) {
        if (request == null) {
            return Optional.empty();
        }
        InfraDataSourceDto preferred = findDefaultDataLake().orElse(null);
        if (preferred == null || preferred.getId() == null) {
            return Optional.empty();
        }
        InfraDataSource entity = dataSourceRepository.findById(preferred.getId()).orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        Map<String, Object> props = entity.getProps();
        if (StringUtils.hasText(request.getDestinationId())) {
            props.put("destinationId", request.getDestinationId().trim());
        }
        if (StringUtils.hasText(request.getDestinationName())) {
            props.put("destinationName", request.getDestinationName().trim());
        }
        if (StringUtils.hasText(request.getDestinationDefinitionId())) {
            props.put("destinationDefinitionId", request.getDestinationDefinitionId().trim());
        }
        entity.setProps(props);

        Map<String, Object> secrets = new HashMap<>(secretService.readSecrets(entity));
        if (request.getDestinationConfig() != null && !request.getDestinationConfig().isEmpty()) {
            secrets.put("destinationConfig", request.getDestinationConfig());
        }
        secretService.applySecrets(entity, secrets);
        entity.setHasSecrets(!secrets.isEmpty());

        entity.setUpdatedAt(Instant.now());
        dataSourceRepository.save(entity);
        updateCache(entity);
        touchLastUpdated();
        return currentPlatformDefaultDataLakeConfig();
    }

    public List<ConnectionTestLogDto> recentTestLogs(UUID dataSourceId) {
        return testLogs
            .stream()
            .filter(log -> dataSourceId == null || dataSourceId.equals(log.getDataSourceId()))
            .limit(MAX_LOGS)
            .collect(Collectors.toList());
    }

    @Scheduled(initialDelayString = "${dts.infra.heartbeat.initial-delay-ms:15000}", fixedDelayString = "${dts.infra.heartbeat.interval-ms:60000}")
    public void heartbeat() {
        if (!heartbeatEnabled) {
            return;
        }
        if (!schemaReady.get()) {
            LOG.debug("Infra data source schema not yet ready, skip heartbeat cycle");
            return;
        }
        List<InfraDataSource> sources;
        try {
            sources = dataSourceRepository.findAll();
        } catch (DataAccessException ex) {
            LOG.debug("Infra data source repository unavailable, skip heartbeat: {}", ex.getMessage());
            LOG.trace("Infra data source repository not ready during heartbeat", ex);
            return;
        }
        for (InfraDataSource entity : sources) {
            try {
                ConnectivityResult probe = performConnectivityCheck(entity.getJdbcUrl(), entity.getProps());
                Instant now = Instant.now();
                if (probe.success()) {
                    entity.setHeartbeatStatus(HEARTBEAT_UP);
                    entity.setHeartbeatFailureCount(0);
                    entity.setStatus(STATUS_ACTIVE);
                    entity.setLastError(null);
                    entity.setLastHeartbeatAt(now);
                    entity.setLastTestElapsedMillis(probe.elapsedMillis());
                } else {
                    entity.setHeartbeatStatus(HEARTBEAT_DOWN);
                    entity.setHeartbeatFailureCount(incrementFailure(entity.getHeartbeatFailureCount()));
                    entity.setStatus(STATUS_INACTIVE);
                    entity.setLastError(probe.message());
                    entity.setLastHeartbeatAt(now);
                }
                entity.setUpdatedAt(now);
                dataSourceRepository.save(entity);
                updateCache(entity);
            } catch (Exception ex) {
                LOG.warn("Heartbeat check failed for data source {}: {}", entity.getName(), ex.getMessage());
            }
        }
        touchLastUpdated();
    }

    private void applyPayload(InfraDataSource entity, UpsertInfraDataSourcePayload payload) {
        entity.setName(payload.getName());
        entity.setType(normalizeDataLakeType(payload.getType()));
        entity.setJdbcUrl(payload.getJdbcUrl());
        entity.setUsername(payload.getUsername());
        entity.setDescription(payload.getDescription());
        entity.setProps(payload.getProps());
        if (payload.getDefaulted() != null) {
            entity.setDefaulted(payload.getDefaulted());
        }
    }

    private String normalizeDataLakeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "JDBC";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureUniqueConnectionSignature(UpsertInfraDataSourcePayload payload, InfraDataSource current) {
        String requestedJdbcUrl = normalizeConnectionToken(payload == null ? null : payload.getJdbcUrl());
        String requestedUsername = normalizeConnectionToken(payload == null ? null : payload.getUsername());
        if (!StringUtils.hasText(requestedJdbcUrl)) {
            return;
        }
        if (
            current != null &&
            requestedJdbcUrl.equals(normalizeConnectionToken(current.getJdbcUrl())) &&
            requestedUsername.equals(normalizeConnectionToken(current.getUsername()))
        ) {
            return;
        }
        UUID currentId = current == null ? null : current.getId();
        List<InfraDataSource> existingSources = dataSourceRepository.findAll();
        if (existingSources == null || existingSources.isEmpty()) {
            return;
        }
        for (InfraDataSource source : existingSources) {
            if (source == null || (currentId != null && currentId.equals(source.getId()))) {
                continue;
            }
            String existingJdbcUrl = normalizeConnectionToken(source.getJdbcUrl());
            if (!StringUtils.hasText(existingJdbcUrl)) {
                continue;
            }
            String existingUsername = normalizeConnectionToken(source.getUsername());
            if (requestedJdbcUrl.equals(existingJdbcUrl) && requestedUsername.equals(existingUsername)) {
                String label = StringUtils.hasText(source.getName()) ? source.getName() : source.getId().toString();
                throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源连接已存在：" + label);
            }
        }
    }

    private String normalizeConnectionToken(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return text.isEmpty() ? "" : text.toLowerCase(Locale.ROOT);
    }

    private JdbcConnectionTestRequest buildJdbcTestRequest(InfraDataSource entity, JdbcConnectionTestRequest override) {
        JdbcConnectionTestRequest request = new JdbcConnectionTestRequest();
        String jdbcUrl = StringUtils.hasText(override.getJdbcUrl()) ? override.getJdbcUrl() : entity.getJdbcUrl();
        request.setJdbcUrl(jdbcUrl);
        Map<String, Object> props = entity.getProps() != null ? entity.getProps() : Collections.emptyMap();
        Map<String, Object> secrets = secretService.readSecrets(entity);

        String username = StringUtils.hasText(override.getUsername()) ? override.getUsername() : entity.getUsername();
        request.setUsername(username);
        String password = StringUtils.hasText(override.getPassword()) ? override.getPassword() : asString(secrets.get("password"));
        request.setPassword(password);
        String driverClass = StringUtils.hasText(override.getDriverClass())
            ? override.getDriverClass()
            : asString(props.get("driverClass"));
        request.setDriverClass(driverClass);
        String driverVersion = StringUtils.hasText(override.getDriverVersion())
            ? override.getDriverVersion()
            : asString(props.get("driverVersion"));
        request.setDriverVersion(driverVersion);
        String testQuery = StringUtils.hasText(override.getTestQuery()) ? override.getTestQuery() : asString(props.get("testQuery"));
        request.setTestQuery(testQuery);
        Map<String, String> jdbcProps = override.getJdbcProperties();
        if (jdbcProps == null || jdbcProps.isEmpty()) {
            jdbcProps = stringMap(props.get("jdbcProperties"));
        }
        request.setJdbcProperties(jdbcProps);
        return request;
    }

    private void applySecrets(InfraDataSource entity, Map<String, Object> secrets) {
        Map<String, Object> normalized = secrets != null ? new HashMap<>(secrets) : Collections.emptyMap();
        secretService.applySecrets(entity, normalized);
        entity.setHasSecrets(!normalized.isEmpty());
    }

    private void applyPersistRequest(InfraDataSource entity, HiveConnectionPersistRequest request) {
        entity.setName(request.getName());
        entity.setType("INCEPTOR");
        entity.setJdbcUrl(request.getJdbcUrl());
        entity.setUsername(request.getLoginPrincipal());
        entity.setDescription(request.getDescription());
        Map<String, Object> props = new HashMap<>();
        props.put("host", request.getHost());
        props.put("port", request.getPort());
        props.put("database", request.getDatabase());
        props.put("servicePrincipal", request.getServicePrincipal());
        props.put("useHttpTransport", request.isUseHttpTransport());
        props.put("httpPath", request.getHttpPath());
        props.put("useSsl", request.isUseSsl());
        props.put("useCustomJdbc", request.isUseCustomJdbc());
        props.put("customJdbcUrl", request.getCustomJdbcUrl());
        props.put("authMethod", request.getAuthMethod() != null ? request.getAuthMethod().name() : null);
        props.put("proxyUser", request.getProxyUser());
        props.put("jdbcProperties", request.getJdbcProperties());
        String destinationId = request.getDestinationId();
        String destinationName = request.getDestinationName();
        String destinationDefinitionId = request.getDestinationDefinitionId();
        Map<String, Object> existingProps = entity.getProps();
        if (!StringUtils.hasText(destinationId)) {
            destinationId = asString(existingProps.get("destinationId"));
        }
        if (!StringUtils.hasText(destinationName)) {
            destinationName = asString(existingProps.get("destinationName"));
        }
        if (!StringUtils.hasText(destinationDefinitionId)) {
            destinationDefinitionId = asString(existingProps.get("destinationDefinitionId"));
        }
        if (StringUtils.hasText(destinationId)) {
            props.put("destinationId", destinationId);
        }
        if (StringUtils.hasText(destinationName)) {
            props.put("destinationName", destinationName);
        }
        if (StringUtils.hasText(destinationDefinitionId)) {
            props.put("destinationDefinitionId", destinationDefinitionId);
        }
        entity.setProps(props);
        entity.setEngineVersion(request.getEngineVersion());
        entity.setDriverVersion(request.getDriverVersion());
        entity.setLastTestElapsedMillis(request.getLastTestElapsedMillis());
        Map<String, Object> secrets = buildPersistSecrets(request);
        if (!secrets.containsKey("destinationConfig")) {
            Map<String, Object> existingSecrets = secretService.readSecrets(entity);
            if (existingSecrets.containsKey("destinationConfig")) {
                secrets.put("destinationConfig", existingSecrets.get("destinationConfig"));
            }
        }
        applySecrets(entity, secrets);
    }

    private Map<String, Object> buildPersistSecrets(HiveConnectionPersistRequest request) {
        Map<String, Object> secrets = new HashMap<>();
        if (StringUtils.hasText(request.getKrb5Conf())) {
            secrets.put("krb5Conf", request.getKrb5Conf());
        }
        if (StringUtils.hasText(request.getKeytabBase64())) {
            secrets.put("keytabBase64", request.getKeytabBase64());
        }
        if (StringUtils.hasText(request.getKeytabFileName())) {
            secrets.put("keytabFileName", request.getKeytabFileName());
        }
        if (StringUtils.hasText(request.getPassword())) {
            secrets.put("password", request.getPassword());
        }
        if (request.getAuthMethod() != null) {
            secrets.put("authMethod", request.getAuthMethod().name());
        }
        if (request.getJdbcProperties() != null && !request.getJdbcProperties().isEmpty()) {
            secrets.put("jdbcProperties", new HashMap<>(request.getJdbcProperties()));
        }
        if (request.getDestinationConfig() != null) {
            secrets.put("destinationConfig", new HashMap<>(request.getDestinationConfig()));
        }
        return secrets;
    }


    private void updateLastInceptorDefinition(InfraDataSource entity) {
        buildPersistDefinition(entity).ifPresent(def -> lastInceptorDefinition.set(clonePersistRequest(def)));
    }

    private Optional<HiveConnectionPersistRequest> buildPersistDefinition(InfraDataSource entity) {
        if (entity == null || !"INCEPTOR".equalsIgnoreCase(entity.getType())) {
            return Optional.empty();
        }
        HiveConnectionPersistRequest request = new HiveConnectionPersistRequest();
        request.setName(entity.getName());
        request.setDescription(entity.getDescription());
        request.setJdbcUrl(entity.getJdbcUrl());
        request.setLoginPrincipal(entity.getUsername());
        Map<String, Object> props = entity.getProps() != null ? new HashMap<>(entity.getProps()) : Collections.emptyMap();
        Map<String, Object> secrets = secretService.readSecrets(entity);

        request.setHost(asString(props.get("host")));
        Integer port = toInteger(props.get("port"));
        if (port == null) {
            port = 10000;
        }
        request.setPort(port);
        request.setDatabase(asString(props.get("database")));
        request.setServicePrincipal(asString(props.get("servicePrincipal")));
        request.setUseHttpTransport(booleanVal(props.get("useHttpTransport")));
        request.setHttpPath(asString(props.get("httpPath")));
        request.setUseSsl(booleanVal(props.get("useSsl")));
        request.setUseCustomJdbc(booleanVal(props.get("useCustomJdbc")));
        request.setCustomJdbcUrl(asString(props.get("customJdbcUrl")));
        request.setProxyUser(asString(props.getOrDefault("proxyUser", secrets.get("proxyUser"))));
        request.setEngineVersion(entity.getEngineVersion());
        request.setDriverVersion(entity.getDriverVersion());
        request.setLastTestElapsedMillis(entity.getLastTestElapsedMillis());
        request.setDefaulted(entity.isDefaulted());
        request.setDestinationId(asString(props.get("destinationId")));
        request.setDestinationName(asString(props.get("destinationName")));
        request.setDestinationDefinitionId(asString(props.get("destinationDefinitionId")));
        request.setDestinationConfig(parseDestinationConfig(secrets.get("destinationConfig")));

        String auth = asString(props.getOrDefault("authMethod", secrets.get("authMethod")));
        HiveAuthMethod authMethod = parseAuthMethod(auth);
        if (authMethod != null) {
            request.setAuthMethod(authMethod);
        }
        request.setKrb5Conf(asString(secrets.get("krb5Conf")));
        request.setKeytabBase64(asString(secrets.get("keytabBase64")));
        request.setKeytabFileName(asString(secrets.get("keytabFileName")));
        request.setPassword(asString(secrets.get("password")));
        Map<String, String> jdbcProps = stringMap(
            secrets.containsKey("jdbcProperties") ? secrets.get("jdbcProperties") : props.get("jdbcProperties")
        );
        if (!jdbcProps.isEmpty()) {
            request.setJdbcProperties(jdbcProps);
        }
        return Optional.of(request);
    }

    private void updateCache(InfraDataSource entity) {
        cache.put(entity.getId(), toDto(entity));
    }

    private void applyTestResult(InfraDataSource entity, HiveConnectionTestResult result) {
        Instant now = Instant.now();
        if (result.isSuccess()) {
            entity.setStatus(STATUS_ACTIVE);
            entity.setLastVerifiedAt(now);
            entity.setLastHeartbeatAt(now);
            entity.setHeartbeatStatus(HEARTBEAT_UP);
            entity.setHeartbeatFailureCount(0);
            entity.setLastError(null);
            lastVerifiedAt.set(now);
            lastTestElapsedMillis.set(result.getElapsedMillis());
        } else {
            entity.setStatus(STATUS_INACTIVE);
            entity.setHeartbeatStatus(HEARTBEAT_DOWN);
            entity.setHeartbeatFailureCount(incrementFailure(entity.getHeartbeatFailureCount()));
            entity.setLastError(result.getMessage());
        }
        if (StringUtils.hasText(result.getEngineVersion())) {
            entity.setEngineVersion(result.getEngineVersion());
        }
        if (StringUtils.hasText(result.getDriverVersion())) {
            entity.setDriverVersion(result.getDriverVersion());
        }
        entity.setLastTestElapsedMillis(result.getElapsedMillis());
        entity.setUpdatedAt(now);
        dataSourceRepository.save(entity);
        updateCache(entity);
        touchLastUpdated();
    }

    private void clearDefaultExcept(UUID keepId) {
        List<InfraDataSource> defaults = dataSourceRepository.findByDefaultedTrueAndIdNot(keepId);
        if (defaults.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (InfraDataSource entity : defaults) {
            entity.setDefaulted(false);
            entity.setUpdatedAt(now);
            dataSourceRepository.save(entity);
            updateCache(entity);
        }
    }

    private boolean hasDefaultDataLake() {
        return cache.values().stream().anyMatch(InfraDataSourceDto::isDefaulted);
    }

    private boolean hasWriterConfig(InfraDataSourceDto dto) {
        if (dto == null) {
            return false;
        }
        Map<String, Object> props = dto.getProps();
        String destinationDefinitionId = asString(props.get("destinationDefinitionId"));
        if (StringUtils.hasText(destinationDefinitionId)) {
            return true;
        }
        String destinationName = asString(props.get("destinationName"));
        if (StringUtils.hasText(destinationName)) {
            return true;
        }
        Map<String, Object> destinationConfig = dto.getDestinationConfig();
        if (destinationConfig != null && !destinationConfig.isEmpty()) {
            Object writer = destinationConfig.get("writerType");
            if (writer == null) {
                writer = destinationConfig.get("writer");
            }
            if (writer == null) {
                writer = destinationConfig.get("type");
            }
            return StringUtils.hasText(asString(writer));
        }
        return false;
    }

    private InfraDataSourceDto toDto(InfraDataSource entity) {
        InfraDataSourceDto dto = new InfraDataSourceDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setDescription(entity.getDescription());
        dto.setProps(entity.getProps());
        Map<String, Object> secrets = secretService.readSecrets(entity);
        dto.setDestinationConfig(parseDestinationConfig(secrets.get("destinationConfig")));
        dto.setStatus(entity.getStatus());
        dto.setHasSecrets(entity.isHasSecrets() || entity.getSecureProps() != null);
        dto.setDefaulted(entity.isDefaulted());
        dto.setEngineVersion(entity.getEngineVersion());
        dto.setDriverVersion(entity.getDriverVersion());
        dto.setLastTestElapsedMillis(entity.getLastTestElapsedMillis());
        dto.setLastVerifiedAt(entity.getLastVerifiedAt());
        dto.setLastHeartbeatAt(entity.getLastHeartbeatAt());
        dto.setHeartbeatStatus(entity.getHeartbeatStatus());
        dto.setHeartbeatFailureCount(entity.getHeartbeatFailureCount());
        dto.setLastError(entity.getLastError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setLastUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Optional<InfraDataSourceDto> findFirstInceptor() {
        Optional<InfraDataSourceDto> preferred = cache
            .values()
            .stream()
            .filter(ds -> "INCEPTOR".equalsIgnoreCase(ds.getType()) && ds.isDefaulted())
            .findFirst();
        if (preferred.isPresent()) {
            return preferred.map(InfraDataSourceDto::copy);
        }
        return cache
            .values()
            .stream()
            .filter(ds -> "INCEPTOR".equalsIgnoreCase(ds.getType()))
            .findFirst()
            .map(InfraDataSourceDto::copy);
    }

    private Optional<InfraDataSource> findFirstInceptorEntity() {
        return dataSourceRepository
            .findFirstByTypeIgnoreCaseAndDefaultedTrueOrderByUpdatedAtDesc("INCEPTOR")
            .or(() -> dataSourceRepository.findFirstByTypeIgnoreCaseOrderByUpdatedAtDesc("INCEPTOR"));
    }

    private HiveAuthMethod parseAuthMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return HiveAuthMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean booleanVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value != null) {
            String str = value.toString().trim();
            if (str.isEmpty()) {
                return false;
            }
            return str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes") || str.equals("1");
        }
        return false;
    }

    private Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        map.forEach((k, v) -> {
            if (k != null) {
                result.put(k.toString(), v != null ? v.toString() : null);
            }
        });
        return result;
    }

    private Map<String, Object> parseDestinationConfig(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(k.toString(), v);
                }
            });
            return result;
        }
        if (raw instanceof String str && StringUtils.hasText(str)) {
            try {
                return objectMapper.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                LOG.debug("Failed to parse destination config: {}", ex.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    private void recordTestLog(UUID dataSourceId, HiveConnectionTestResult result) {
        ConnectionTestLogDto log = new ConnectionTestLogDto();
        log.setId(UUID.randomUUID());
        log.setDataSourceId(dataSourceId);
        log.setResult(result.isSuccess() ? "SUCCESS" : "FAILURE");
        log.setMessage(result.getMessage());
        log.setElapsedMs(result.getElapsedMillis());
        log.setCreatedAt(Instant.now());
        testLogs.addFirst(log);
        while (testLogs.size() > MAX_LOGS) {
            testLogs.pollLast();
        }
    }

    private ConnectivityResult performConnectivityCheck(String jdbcUrl, Map<String, Object> props) {
        HostPort hostPort = resolveHostPort(jdbcUrl, props);
        if (!hostPort.valid()) {
            return new ConnectivityResult(false, 0L, "无法解析数据源主机或端口", "hostPort=missing");
        }
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostPort.host(), hostPort.port()), Math.toIntExact(heartbeatTimeoutMs));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return new ConnectivityResult(true, elapsed, "连接成功", null);
        } catch (Exception ex) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String message = String.format(Locale.ROOT, "连接 %s:%d 失败", hostPort.host(), hostPort.port());
            return new ConnectivityResult(false, elapsed, message, ex.getMessage());
        }
    }

    private HostPort resolveHostPort(String jdbcUrl, Map<String, Object> props) {
        String host = asString(props != null ? props.get("host") : null);
        Integer port = toInteger(props != null ? props.get("port") : null);
        if (!StringUtils.hasText(host) || port == null || port <= 0) {
            if (StringUtils.hasText(jdbcUrl)) {
                String normalized = jdbcUrl.trim();
                int start = normalized.indexOf("//");
                if (start >= 0) {
                    String tail = normalized.substring(start + 2);
                    String hostPart = tail.split("[/;]")[0];
                    if (hostPart.contains(",")) {
                        hostPart = hostPart.split(",")[0];
                    }
                    int colon = hostPart.indexOf(':');
                    if (colon >= 0) {
                        host = hostPart.substring(0, colon);
                        port = toInteger(hostPart.substring(colon + 1));
                    } else {
                        host = hostPart;
                    }
                }
            }
        }
        if (port == null || port <= 0) {
            port = 10000;
        }
        return new HostPort(host, port);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str.trim();
        }
        return value.toString().trim();
    }

    private Integer toInteger(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.intValue();
            }
            String str = value.toString().trim();
            if (str.isEmpty()) {
                return null;
            }
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer incrementFailure(Integer current) {
        int next = current == null ? 1 : Math.min(current + 1, 1000);
        return next;
    }

    private IntegrationStatus buildIntegrationStatus() {
        IntegrationStatus status = new IntegrationStatus();
        status.setLastSyncAt(formatInstant(lastSyncAt.get()));
        status.setReason(integrationReason.get());
        status.setActions(integrationActions.get());
        status.setCatalogDatasetCount(cache.size());
        return status;
    }

    private List<ModuleStatus> buildModuleStatuses(boolean hasInceptor) {
        String updated = formatInstant(lastUpdatedAt.get());
        List<ModuleStatus> list = new ArrayList<>();
        list.add(new ModuleStatus("catalog", hasInceptor ? STATUS_ACTIVE : "INACTIVE", hasInceptor ? "Inceptor 数据源已启用" : "尚未配置 Inceptor", updated));
        list.add(new ModuleStatus("governance", "AVAILABLE", "治理模块依赖管理端配置", updated));
        list.add(new ModuleStatus("development", "AVAILABLE", "数据开发模块待接入", updated));
        return list;
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private Instant maxInstant(Instant base, Instant candidate) {
        return maxInstant(base, candidate, null);
    }

    private Instant maxInstant(Instant base, Instant a, Instant b) {
        Instant candidate = a != null ? a : b;
        if (candidate == null) {
            return base;
        }
        if (base == null || candidate.isAfter(base)) {
            return candidate;
        }
        return base;
    }

    private Map<String, Object> mapToObject(Map<String, String> source) {
        if (source == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(source);
    }

    private HiveConnectionPersistRequest clonePersistRequest(HiveConnectionPersistRequest source) {
        HiveConnectionPersistRequest target = new HiveConnectionPersistRequest();
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setServicePrincipal(source.getServicePrincipal());
        target.setHost(source.getHost());
        target.setPort(source.getPort());
        target.setDatabase(source.getDatabase());
        target.setUseHttpTransport(source.isUseHttpTransport());
        target.setHttpPath(source.getHttpPath());
        target.setUseSsl(source.isUseSsl());
        target.setUseCustomJdbc(source.isUseCustomJdbc());
        target.setCustomJdbcUrl(source.getCustomJdbcUrl());
        target.setLastTestElapsedMillis(source.getLastTestElapsedMillis());
        target.setEngineVersion(source.getEngineVersion());
        target.setDriverVersion(source.getDriverVersion());
        target.setJdbcUrl(source.getJdbcUrl());
        target.setLoginPrincipal(source.getLoginPrincipal());
        target.setAuthMethod(source.getAuthMethod());
        target.setKrb5Conf(source.getKrb5Conf());
        target.setKeytabBase64(source.getKeytabBase64());
        target.setKeytabFileName(source.getKeytabFileName());
        target.setPassword(source.getPassword());
        target.setProxyUser(source.getProxyUser());
        target.setTestQuery(source.getTestQuery());
        target.setRemarks(source.getRemarks());
        if (source.getJdbcProperties() != null) {
            target.setJdbcProperties(new HashMap<>(source.getJdbcProperties()));
        }
        return target;
    }

    private void touchLastUpdated() {
        lastUpdatedAt.set(Instant.now());
    }

    private record HostPort(String host, Integer port) {
        boolean valid() {
            return StringUtils.hasText(host) && port != null && port > 0;
        }
    }

    private record ConnectivityResult(boolean success, long elapsedMillis, String message, String error) {}
}
