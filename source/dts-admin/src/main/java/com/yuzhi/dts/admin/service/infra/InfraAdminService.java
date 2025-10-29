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
import com.yuzhi.dts.admin.service.infra.dto.ModuleStatus;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final JdbcTemplate jdbcTemplate;
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
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        JdbcTemplate jdbcTemplate,
        @Value("${dts.infra.heartbeat.enabled:true}") boolean heartbeatEnabled,
        @Value("${dts.infra.heartbeat.timeout-ms:5000}") long heartbeatTimeoutMs
    ) {
        this.platformInfraClient = platformInfraClient;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.jdbcTemplate = jdbcTemplate;
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

    public InfraDataSourceDto createDataSource(UpsertInfraDataSourcePayload payload, String operator) {
        InfraDataSource entity = new InfraDataSource();
        entity.setId(UUID.randomUUID());
        applyPayload(entity, payload);
        applySecrets(entity, payload.getSecrets());
        Instant now = Instant.now();
        entity.setStatus(STATUS_ACTIVE);
        entity.setHeartbeatStatus(HEARTBEAT_UNKNOWN);
        entity.setHeartbeatFailureCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        dataSourceRepository.save(entity);
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
                applyPayload(existing, payload);
                applySecrets(existing, payload.getSecrets());
                existing.setUpdatedAt(Instant.now());
                dataSourceRepository.save(existing);
                updateCache(existing);
                touchLastUpdated();
                InfraDataSourceDto after = cache.get(existing.getId()).copy();
                return new DataSourceMutation(before, after);
            });
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
        ConnectivityResult probe = performConnectivityCheck(request.getJdbcUrl(), mapToObject(request.getJdbcProperties()));
        HiveConnectionTestResult result = new HiveConnectionTestResult();
        result.setSuccess(probe.success());
        result.setElapsedMillis(probe.elapsedMillis());
        result.setMessage(probe.message());
        if (!probe.success() && probe.error() != null) {
            result.getWarnings().add(probe.error());
        }
        recordTestLog(dataSourceId, result);

        if (dataSourceId != null) {
            dataSourceRepository
                .findById(dataSourceId)
                .ifPresent(entity -> {
                    Instant now = Instant.now();
                    if (probe.success()) {
                        entity.setStatus(STATUS_ACTIVE);
                        entity.setLastVerifiedAt(now);
                        entity.setLastHeartbeatAt(now);
                        entity.setHeartbeatStatus(HEARTBEAT_UP);
                        entity.setHeartbeatFailureCount(0);
                        entity.setLastError(null);
                        lastVerifiedAt.set(now);
                    } else {
                        entity.setStatus(STATUS_INACTIVE);
                        entity.setHeartbeatStatus(HEARTBEAT_DOWN);
                        entity.setHeartbeatFailureCount(incrementFailure(entity.getHeartbeatFailureCount()));
                        entity.setLastError(probe.message());
                    }
                    entity.setLastTestElapsedMillis(probe.elapsedMillis());
                    entity.setUpdatedAt(now);
                    dataSourceRepository.save(entity);
                    updateCache(entity);
                });
        }
        if (probe.success()) {
            lastTestElapsedMillis.set(result.getElapsedMillis());
        }
        touchLastUpdated();
        return result;
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
        entity.setLastVerifiedAt(now);
        entity.setLastHeartbeatAt(now);
        entity.setHeartbeatStatus(HEARTBEAT_UP);
        entity.setHeartbeatFailureCount(0);
        entity.setLastError(null);
        entity.setUpdatedAt(now);
        dataSourceRepository.save(entity);
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
        entity.setType(payload.getType());
        entity.setJdbcUrl(payload.getJdbcUrl());
        entity.setUsername(payload.getUsername());
        entity.setDescription(payload.getDescription());
        entity.setProps(payload.getProps());
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
        entity.setProps(props);
        entity.setEngineVersion(request.getEngineVersion());
        entity.setDriverVersion(request.getDriverVersion());
        entity.setLastTestElapsedMillis(request.getLastTestElapsedMillis());
        applySecrets(entity, buildPersistSecrets(request));
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

    private InfraDataSourceDto toDto(InfraDataSource entity) {
        InfraDataSourceDto dto = new InfraDataSourceDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setDescription(entity.getDescription());
        dto.setProps(entity.getProps());
        dto.setStatus(entity.getStatus());
        dto.setHasSecrets(entity.isHasSecrets() || entity.getSecureProps() != null);
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
        return cache
            .values()
            .stream()
            .filter(ds -> "INCEPTOR".equalsIgnoreCase(ds.getType()))
            .findFirst()
            .map(InfraDataSourceDto::copy);
    }

    private Optional<InfraDataSource> findFirstInceptorEntity() {
        return dataSourceRepository.findFirstByTypeIgnoreCaseOrderByUpdatedAtDesc("INCEPTOR");
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
