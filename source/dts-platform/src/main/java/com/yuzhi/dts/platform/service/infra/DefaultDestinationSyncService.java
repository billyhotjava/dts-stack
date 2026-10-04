package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DefaultDestinationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDestinationSyncService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String BIADMIN_NAME = "数仓 (biadmin)";
    private static final String SOURCE_ADMIN_DEFAULT_DATA_LAKE = "admin-default-data-lake";
    private static final String SOURCE_ADMIN_DATA_LAKE = "admin-data-lake";
    private static final UUID LEGACY_BIADMIN_DATA_SOURCE_ID = UUID.fromString(
        "a0000000-0000-0000-0000-000000000001"
    );
    private static final List<String> SENSITIVE_CONFIG_KEY_MARKERS = List.of(
        "password",
        "passwd",
        "passphrase",
        "pwd",
        "secret",
        "secureprops",
        "token",
        "credential",
        "authorization",
        "apikey",
        "accesskey",
        "privatekey",
        "clientkey",
        "sshkey",
        "keytab",
        "krb5",
        "bearer",
        "cookie"
    );
    private static final Set<String> SAFE_PERSISTED_DESTINATION_CONFIG_KEYS = Set.of(
        "database",
        "host",
        "jdbc",
        "jdbcurl",
        "port",
        "schema",
        "type",
        "url",
        "user",
        "username",
        "writer",
        "writertype"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminInfraClient adminInfraClient;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final ObjectMapper objectMapper;

    public DefaultDestinationSyncService(
        AdminInfraClient adminInfraClient,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        ObjectMapper objectMapper
    ) {
        this.adminInfraClient = adminInfraClient;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    // Before DbtConfigService.reconcileTargetWithManagedDefaultLake, which reads the mirror written here.
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    public void synchronizeManagedDefaultLakeOnStartup() {
        try {
            ensureDefaultDestination();
        } catch (RuntimeException ex) {
            LOG.warn(
                "Default lake startup synchronization failed; managed default lake remains unavailable: {}",
                ex.getClass().getSimpleName()
            );
        }
    }

    public DefaultDestinationSnapshot ensureDefaultDestination() {
        LakeSnapshot lake = resolveDefaultLake().orElse(null);
        if (lake == null) {
            return null;
        }
        if (lake.isFromAdmin()) {
            requireAdminLakeReady(lake);
        } else if (lake.isManagedDefaultMirror()) {
            throw defaultLakeUnavailable();
        }
        return buildDestinationSnapshot(lake, false);
    }

    public DefaultDestinationSnapshot ensureDestination(String dataSourceId) {
        String normalizedId = normalize(dataSourceId);
        if (!StringUtils.hasText(normalizedId)) {
            return ensureDefaultDestination();
        }
        UUID id;
        try {
            id = UUID.fromString(normalizedId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标数据源 ID 不合法: " + normalizedId);
        }
        InfraDataSource source = dataSourceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标数据源不存在: " + normalizedId));
        if (isManagedDefaultMirror(source)) {
            LakeSnapshot adminLake = adminInfraClient
                .fetchDefaultDataLake()
                .map(LakeSnapshot::fromAdmin)
                .orElseThrow(this::defaultLakeUnavailable);
            requireAdminLakeReady(adminLake);
            Map<String, Object> destinationConfig = resolveDestinationConfig(adminLake);
            String writerType = resolveWriterType(adminLake, destinationConfig);
            try {
                synchronizeManagedDefaultMirror(source, adminLake, destinationConfig, writerType);
            } catch (RuntimeException ex) {
                throw defaultLakeSyncFailed(ex);
            }
            return buildDestinationSnapshot(adminLake.withDataSourceId(normalizedId), true);
        }
        return buildDestinationSnapshot(toLocalLakeSnapshot(source), true);
    }

    private DefaultDestinationSnapshot buildDestinationSnapshot(LakeSnapshot lake, boolean selectedById) {
        if (lake == null) {
            return null;
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        if (!StringUtils.hasText(writerType)) {
            LOG.debug("Data lake missing Addax writer type; skip default destination");
            return null;
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName(), "dts-addax-destination");
        String dataSourceId = selectedById ? lake.getDataSourceId() : resolveLocalDataSourceId(lake, destinationConfig, writerType);
        applyCachedManagedPassword(lake, dataSourceId, destinationConfig);
        return new DefaultDestinationSnapshot(writerType, destinationName, destinationConfig, dataSourceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DefaultDestinationStatus checkDefaultDestinationStatus() {
        LakeSnapshot lake = resolveDefaultLake().orElse(null);
        if (lake == null) {
            return DefaultDestinationStatus.missing("未配置默认数据湖");
        }
        String adminReadinessError = adminLakeReadinessError(lake);
        if (StringUtils.hasText(adminReadinessError)) {
            return DefaultDestinationStatus.missing(adminReadinessError);
        }
        if (!lake.isFromAdmin() && lake.isManagedDefaultMirror()) {
            return DefaultDestinationStatus.missing("dts-admin 默认数据湖配置暂不可用");
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        boolean hasWriterType = StringUtils.hasText(writerType);
        boolean hasConfig = destinationConfig != null && !destinationConfig.isEmpty();
        String message = null;
        if (!hasWriterType) {
            message = "默认数据湖未配置写入器类型";
        } else if (!hasConfig) {
            message = "默认数据湖未配置写入器参数";
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName());
        String dataSourceId = resolveLocalDataSourceId(lake, destinationConfig, writerType);
        return new DefaultDestinationStatus(true, hasWriterType, hasConfig, destinationName, writerType, message, dataSourceId);
    }

    private synchronized String resolveLocalDataSourceId(
        LakeSnapshot lake,
        Map<String, Object> destinationConfig,
        String writerType
    ) {
        if (lake == null) {
            return null;
        }
        if (StringUtils.hasText(lake.getDataSourceId())) {
            return lake.getDataSourceId();
        }
        String lakeName = normalize(lake.getName());
        String lakeDestName = normalize(lake.getDestinationName());
        String lakeJdbc = firstNonEmpty(normalize(lake.getJdbcUrl()), normalize(destinationConfig == null ? null : destinationConfig.get("jdbcUrl")));
        if (!StringUtils.hasText(lakeName) && !StringUtils.hasText(lakeDestName) && !StringUtils.hasText(lakeJdbc)) {
            return null;
        }
        try {
            List<InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
            if (candidates.isEmpty()) {
                candidates = dataSourceRepository.findAll();
            }
            InfraDataSource matched = dataSourceRepository
                .findById(LEGACY_BIADMIN_DATA_SOURCE_ID)
                .filter(this::isManagedDefaultMirror)
                .orElse(null);
            if (matched == null) {
                matched = findManagedDefaultMirror(candidates, lake.getAdminDataLakeId());
            }
            if (matched == null && !candidates.isEmpty()) {
                matched = findManagedDefaultMirror(dataSourceRepository.findAll(), lake.getAdminDataLakeId());
            }
            if (matched != null && matched.getId() != null) {
                synchronizeManagedDefaultMirror(matched, lake, destinationConfig, writerType);
                return matched.getId().toString();
            }
            InfraDataSource created = createPlatformDataSource(lake, destinationConfig, writerType, lakeJdbc);
            if (created != null && created.getId() != null) {
                return created.getId().toString();
            }
        } catch (RuntimeException ex) {
            LOG.warn("Failed to synchronize managed default lake mirror: {}", ex.getClass().getSimpleName());
            throw defaultLakeSyncFailed(ex);
        }
        throw defaultLakeSyncFailed(null);
    }

    private InfraDataSource findManagedDefaultMirror(List<InfraDataSource> candidates, String adminDataLakeId) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        InfraDataSource best = null;
        int bestPriority = Integer.MIN_VALUE;
        int managedCount = 0;
        for (InfraDataSource candidate : candidates) {
            int priority = managedDefaultMirrorPriority(candidate);
            if (priority < 0) {
                continue;
            }
            managedCount++;
            Map<String, Object> props = parseMap(candidate.getProps());
            if (
                StringUtils.hasText(adminDataLakeId)
                    && adminDataLakeId.equalsIgnoreCase(normalize(props.get("adminDataLakeId")))
            ) {
                priority += 50;
            }
            if (
                priority > bestPriority
                    || (
                        priority == bestPriority
                            && best != null
                            && candidate.getId().toString().compareTo(best.getId().toString()) < 0
                    )
            ) {
                best = candidate;
                bestPriority = priority;
            }
        }
        if (managedCount > 1) {
            LOG.warn(
                "Found {} managed default lake mirrors; retaining all references and selecting source {}",
                managedCount,
                best == null ? null : best.getId()
            );
        }
        return best;
    }

    private int managedDefaultMirrorPriority(InfraDataSource source) {
        if (source == null || source.getId() == null) {
            return -1;
        }
        if (LEGACY_BIADMIN_DATA_SOURCE_ID.equals(source.getId())) {
            return 300;
        }
        Map<String, Object> props = parseMap(source.getProps());
        String marker = normalize(props.get("source"));
        if (SOURCE_ADMIN_DEFAULT_DATA_LAKE.equalsIgnoreCase(marker) && isTrue(props.get("defaultLake"))) {
            return 200;
        }
        if (SOURCE_ADMIN_DATA_LAKE.equalsIgnoreCase(marker) && isTrue(props.get("system"))) {
            return 100;
        }
        return -1;
    }

    private boolean isManagedDefaultMirror(InfraDataSource source) {
        return managedDefaultMirrorPriority(source) >= 0;
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(normalize(value));
    }

    private void synchronizeManagedDefaultMirror(
        InfraDataSource source,
        LakeSnapshot lake,
        Map<String, Object> destinationConfig,
        String writerType
    ) {
        if (source == null || lake == null) {
            return;
        }

        String expectedName = firstNonEmpty(lake.getName(), lake.getDestinationName(), BIADMIN_NAME);
        String expectedJdbcUrl = firstNonEmpty(
            normalize(lake.getJdbcUrl()),
            normalize(destinationConfig == null ? null : destinationConfig.get("jdbcUrl"))
        );
        String expectedType = inferSourceType(lake, writerType, expectedJdbcUrl);
        String expectedConnectorKey = inferConnectorKey(expectedType);
        String expectedUsername = firstNonEmpty(
            lake.getUsername(),
            normalize(destinationConfig == null ? null : destinationConfig.get("username"))
        );
        Map<String, Object> mergedProps = parseMap(source.getProps());
        mergedProps.putAll(buildDefaultLakeProps(lake, destinationConfig, writerType));
        String serializedProps = writeProps(mergedProps);
        Map<String, Object> desiredSecrets = buildDefaultLakeSecrets(lake, destinationConfig);
        Map<String, Object> currentSecrets = desiredSecrets.isEmpty()
            ? Map.of()
            : new LinkedHashMap<>(secretService.readSecrets(source));
        Map<String, Object> mergedSecrets = new LinkedHashMap<>(currentSecrets);
        mergedSecrets.putAll(desiredSecrets);
        boolean secretsChanged = !desiredSecrets.isEmpty() && !mergedSecrets.equals(currentSecrets);

        boolean connectionChanged = !Objects.equals(normalize(source.getJdbcUrl()), normalize(expectedJdbcUrl))
            || !Objects.equals(normalize(source.getUsername()), normalize(expectedUsername))
            || !Objects.equals(normalize(source.getType()), normalize(expectedType));
        boolean changed = false;
        if (StringUtils.hasText(expectedName) && !Objects.equals(source.getName(), expectedName)) {
            source.setName(expectedName);
            changed = true;
        }
        if (StringUtils.hasText(expectedJdbcUrl) && !Objects.equals(source.getJdbcUrl(), expectedJdbcUrl)) {
            source.setJdbcUrl(expectedJdbcUrl);
            changed = true;
        }
        if (StringUtils.hasText(expectedType) && !Objects.equals(source.getType(), expectedType)) {
            source.setType(expectedType);
            changed = true;
        }
        if (StringUtils.hasText(expectedConnectorKey) && !Objects.equals(source.getConnectorKey(), expectedConnectorKey)) {
            source.setConnectorKey(expectedConnectorKey);
            changed = true;
        }
        if (StringUtils.hasText(expectedUsername) && !Objects.equals(source.getUsername(), expectedUsername)) {
            source.setUsername(expectedUsername);
            changed = true;
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(normalize(source.getStatus()))) {
            source.setStatus(STATUS_ACTIVE);
            changed = true;
        }

        if (!Objects.equals(source.getProps(), serializedProps)) {
            source.setProps(serializedProps);
            changed = true;
        }

        if (secretsChanged) {
            secretService.applySecrets(source, mergedSecrets);
            changed = true;
        }

        if (connectionChanged && source.getLastVerifiedAt() != null) {
            source.setLastVerifiedAt(null);
            changed = true;
        }
        if (changed) {
            source.setLastModifiedBy("system");
            dataSourceRepository.save(source);
        }
    }

    private InfraDataSource createPlatformDataSource(
        LakeSnapshot lake,
        Map<String, Object> destinationConfig,
        String writerType,
        String jdbcUrl
    ) {
        String resolvedJdbcUrl = firstNonEmpty(jdbcUrl, normalize(destinationConfig == null ? null : destinationConfig.get("jdbcUrl")));
        if (!StringUtils.hasText(resolvedJdbcUrl)) {
            return null;
        }
        InfraDataSource source = new InfraDataSource();
        source.setName(firstNonEmpty(lake.getName(), lake.getDestinationName(), BIADMIN_NAME));
        source.setType(inferSourceType(lake, writerType, resolvedJdbcUrl));
        source.setConnectorKey(inferConnectorKey(source.getType()));
        source.setJdbcUrl(resolvedJdbcUrl);
        source.setUsername(firstNonEmpty(lake.getUsername(), normalize(destinationConfig == null ? null : destinationConfig.get("username"))));
        source.setDescription("由 dts-admin 默认湖仓自动同步");
        source.setOwnerDept(null);
        source.setStatus(STATUS_ACTIVE);
        source.setCreatedBy("system");
        source.setLastModifiedBy("system");
        source.setProps(writeProps(buildDefaultLakeProps(lake, destinationConfig, writerType)));
        Map<String, Object> secrets = buildDefaultLakeSecrets(lake, destinationConfig);
        if (!secrets.isEmpty()) {
            secretService.applySecrets(source, secrets);
        }
        return dataSourceRepository.save(source);
    }

    private String inferSourceType(LakeSnapshot lake, String writerType, String jdbcUrl) {
        String type = normalize(lake == null ? null : lake.getType());
        String marker = firstNonEmpty(writerType, type, jdbcUrl);
        String lower = marker == null ? "" : marker.toLowerCase(Locale.ROOT);
        if (lower.contains("postgres") || lower.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        if (lower.contains("mysql") || lower.contains("mariadb") || lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            return "MYSQL";
        }
        if (lower.contains("oracle") || lower.startsWith("jdbc:oracle:")) {
            return "ORACLE";
        }
        if (lower.contains("sqlserver") || lower.contains("mssql") || lower.startsWith("jdbc:sqlserver:")) {
            return "SQLSERVER";
        }
        if (lower.contains("clickhouse") || lower.startsWith("jdbc:clickhouse:")) {
            return "CLICKHOUSE";
        }
        if (lower.contains("hive") || lower.startsWith("jdbc:hive2:")) {
            return "HIVE";
        }
        if (StringUtils.hasText(type)) {
            return type.toUpperCase(Locale.ROOT);
        }
        return "JDBC";
    }

    private String inferConnectorKey(String type) {
        String normalized = normalize(type);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildDefaultLakeProps(
        LakeSnapshot lake,
        Map<String, Object> destinationConfig,
        String writerType
    ) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", SOURCE_ADMIN_DEFAULT_DATA_LAKE);
        props.put("defaultLake", true);
        putIfHasText(props, "adminDataLakeId", lake == null ? null : lake.getAdminDataLakeId());
        putIfHasText(props, "destinationName", lake == null ? null : lake.getDestinationName());
        putIfHasText(props, "destinationDefinitionId", firstNonEmpty(lake == null ? null : lake.getDestinationDefinitionId(), writerType));
        putIfHasText(props, "writerType", writerType);
        Map<String, Object> safeDestinationConfig = sanitizeConfig(destinationConfig);
        if (!safeDestinationConfig.isEmpty()) {
            props.put("destinationConfig", safeDestinationConfig);
        }
        return props;
    }

    private Map<String, Object> buildDefaultLakeSecrets(LakeSnapshot lake, Map<String, Object> destinationConfig) {
        Map<String, Object> secrets = new LinkedHashMap<>();
        String password = firstNonEmpty(
            normalize(lake == null ? null : lake.getPassword()),
            normalize(destinationConfig == null ? null : destinationConfig.get("password"))
        );
        putIfHasText(secrets, "password", password);
        return secrets;
    }

    private Map<String, Object> sanitizeConfig(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key == null || isSensitiveConfigKey(key) || !isSafePersistedDestinationConfigKey(key)) {
                return;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
            }
        });
        return safe;
    }

    private boolean isSafePersistedDestinationConfigKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SAFE_PERSISTED_DESTINATION_CONFIG_KEYS.contains(normalized);
    }

    public static boolean isSensitiveConfigKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_CONFIG_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (target != null && StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String writeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize managed default lake props", ex);
        }
    }

    private String extractJdbcDatabaseName(String jdbcUrl) {
        String text = normalize(jdbcUrl);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String sqlServerMarker = "databasename=";
        int sqlServerIndex = lower.indexOf(sqlServerMarker);
        if (sqlServerIndex >= 0) {
            String database = text.substring(sqlServerIndex + sqlServerMarker.length());
            int end = database.indexOf(';');
            return normalize(end >= 0 ? database.substring(0, end) : database);
        }
        int queryIndex = text.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? text.substring(0, queryIndex) : text;
        int slashIndex = withoutQuery.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == withoutQuery.length() - 1) {
            return null;
        }
        return normalize(withoutQuery.substring(slashIndex + 1));
    }

    private void applyCachedManagedPassword(
        LakeSnapshot lake,
        String dataSourceId,
        Map<String, Object> destinationConfig
    ) {
        if (
            lake == null
                || !lake.isFromAdmin()
                || !StringUtils.hasText(dataSourceId)
                || destinationConfig == null
                || StringUtils.hasText(normalize(destinationConfig.get("password")))
        ) {
            return;
        }
        try {
            UUID sourceId = UUID.fromString(dataSourceId);
            dataSourceRepository
                .findById(sourceId)
                .filter(this::isManagedDefaultMirror)
                .map(secretService::readSecrets)
                .map(secrets -> normalize(secrets.get("password")))
                .filter(StringUtils::hasText)
                .ifPresent(password -> destinationConfig.put("password", password));
        } catch (IllegalArgumentException ex) {
            LOG.debug("Managed default lake mirror id is invalid: {}", dataSourceId);
        }
    }

    private ResponseStatusException defaultLakeUnavailable() {
        return new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "dts-admin 默认数据湖配置暂不可用，已拒绝使用未确认的本地镜像"
        );
    }

    private ResponseStatusException defaultLakeSyncFailed(Throwable cause) {
        return new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "默认数据湖镜像同步失败，已拒绝生成不完整的入湖目标",
            cause
        );
    }

    private void requireAdminLakeReady(LakeSnapshot lake) {
        String error = adminLakeReadinessError(lake);
        if (StringUtils.hasText(error)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error);
        }
    }

    private String adminLakeReadinessError(LakeSnapshot lake) {
        if (lake == null || !lake.isFromAdmin()) {
            return null;
        }
        String status = normalize(lake.getStatus());
        if (!STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return StringUtils.hasText(status)
                ? "dts-admin 默认数据湖状态为 " + status + "，当前不可用于入湖"
                : "dts-admin 默认数据湖状态未知，当前不可用于入湖";
        }
        String heartbeatStatus = normalize(lake.getHeartbeatStatus());
        if (
            StringUtils.hasText(heartbeatStatus)
                && !List.of("UP", "HEALTHY", "OK", "ACTIVE").contains(heartbeatStatus.toUpperCase(Locale.ROOT))
        ) {
            return "dts-admin 默认数据湖心跳状态为 " + heartbeatStatus + "，当前不可用于入湖";
        }
        return null;
    }

    private Optional<LakeSnapshot> resolveDefaultLake() {
        AdminInfraClient.AdminDataLakeConfig adminLake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (adminLake != null) {
            return Optional.of(LakeSnapshot.fromAdmin(adminLake));
        }
        return resolveLocalFallbackLake();
    }

    private Optional<LakeSnapshot> resolveLocalFallbackLake() {
        try {
            List<InfraDataSource> localSources = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
            if (localSources.isEmpty()) {
                localSources = dataSourceRepository.findAll();
            }
            return localSources
                .stream()
                .filter(this::isLocalLakeCandidate)
                .max((a, b) -> Integer.compare(scoreLocalLake(a), scoreLocalLake(b)))
                .map(this::toLocalLakeSnapshot);
        } catch (RuntimeException ex) {
            LOG.debug("Failed to load local default data lake fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean isLocalLakeCandidate(InfraDataSource source) {
        if (source == null) {
            return false;
        }
        String type = normalize(source.getType());
        if (TYPE_INCEPTOR.equalsIgnoreCase(type)) {
            return false;
        }
        if (StringUtils.hasText(normalize(source.getJdbcUrl()))) {
            return true;
        }
        Map<String, Object> props = parseMap(source.getProps());
        if (props.isEmpty()) {
            return false;
        }
        return props.containsKey("destinationConfig")
            || StringUtils.hasText(normalize(props.get("destinationDefinitionId")))
            || StringUtils.hasText(normalize(props.get("writerType")))
            || StringUtils.hasText(normalize(props.get("type")));
    }

    private int scoreLocalLake(InfraDataSource source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String name = normalize(source.getName());
        String type = normalize(source.getType());
        String jdbcUrl = normalize(source.getJdbcUrl());
        String status = normalize(source.getStatus());

        if (BIADMIN_NAME.equalsIgnoreCase(name)) {
            score += 100;
        }
        if ("biadmin".equalsIgnoreCase(name)) {
            score += 80;
        }
        if ("biadmin".equalsIgnoreCase(extractJdbcDatabaseName(jdbcUrl))) {
            score += 70;
        }
        if ("postgres".equalsIgnoreCase(type) || "postgresql".equalsIgnoreCase(type)) {
            score += 50;
        } else if (StringUtils.hasText(type)) {
            score += 20;
        }
        if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
            score += 5;
        }
        if (StringUtils.hasText(jdbcUrl)) {
            score += 5;
        }
        return score;
    }

    private LakeSnapshot toLocalLakeSnapshot(InfraDataSource source) {
        Map<String, Object> props = parseMap(source.getProps());
        Map<String, Object> secrets = secretService.readSecrets(source);

        Map<String, Object> destinationConfig = new LinkedHashMap<>();
        mergeMap(destinationConfig, props.get("destinationConfig"));
        mergeMap(destinationConfig, secrets.get("destinationConfig"));

        String writerType = firstNonEmpty(
            normalize(props.get("destinationDefinitionId")),
            normalize(props.get("writerType")),
            normalize(props.get("writer")),
            normalize(props.get("type")),
            normalize(destinationConfig.get("writerType")),
            normalize(destinationConfig.get("writer")),
            normalize(destinationConfig.get("type"))
        );
        if (StringUtils.hasText(writerType)) {
            destinationConfig.putIfAbsent("writerType", writerType);
        }

        String jdbcUrl = firstNonEmpty(normalize(source.getJdbcUrl()), normalize(destinationConfig.get("jdbcUrl")));
        String username = firstNonEmpty(normalize(source.getUsername()), normalize(destinationConfig.get("username")));
        String password = firstNonEmpty(normalize(secrets.get("password")), normalize(destinationConfig.get("password")));
        if (StringUtils.hasText(jdbcUrl)) {
            destinationConfig.putIfAbsent("jdbcUrl", jdbcUrl);
        }
        if (StringUtils.hasText(username)) {
            destinationConfig.putIfAbsent("username", username);
        }
        if (StringUtils.hasText(password)) {
            destinationConfig.putIfAbsent("password", password);
        }

        String destinationName = firstNonEmpty(normalize(props.get("destinationName")), normalize(source.getName()));
        String destinationDefinitionId = firstNonEmpty(normalize(props.get("destinationDefinitionId")), writerType);

        UUID sourceId = source.getId();
        return new LakeSnapshot(
            normalize(source.getName()),
            normalize(source.getType()),
            jdbcUrl,
            username,
            password,
            destinationName,
            destinationDefinitionId,
            destinationConfig,
            sourceId == null ? null : sourceId.toString(),
            normalize(props.get("adminDataLakeId")),
            false,
            isManagedDefaultMirror(source),
            normalize(source.getStatus()),
            null
        );
    }

    private Map<String, Object> resolveDestinationConfig(LakeSnapshot lake) {
        if (lake == null || lake.getDestinationConfig() == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> config = new LinkedHashMap<>(lake.getDestinationConfig());
        String jdbcUrl = normalize(config.get("jdbcUrl"));
        if (!StringUtils.hasText(jdbcUrl)) {
            Object legacy = firstNonEmptyValue(config.get("jdbc_url"), config.get("url"), config.get("jdbc"), config.get("jdbcURL"));
            jdbcUrl = normalize(legacy);
            if (StringUtils.hasText(jdbcUrl)) {
                config.put("jdbcUrl", jdbcUrl);
            }
        }
        if (!StringUtils.hasText(jdbcUrl) && StringUtils.hasText(lake.getJdbcUrl())) {
            jdbcUrl = lake.getJdbcUrl();
            config.put("jdbcUrl", jdbcUrl);
        }
        if (!StringUtils.hasText(jdbcUrl)) {
            String host = normalize(config.get("host"));
            String database = normalize(config.get("database"));
            String port = normalize(config.get("port"));
            String writerType = normalize(
                firstNonEmpty(
                    normalize(config.get("writerType")),
                    normalize(config.get("type")),
                    lake.getDestinationDefinitionId()
                )
            );
            String built = buildJdbcUrl(writerType, host, port, database);
            if (StringUtils.hasText(built)) {
                config.put("jdbcUrl", built);
            }
        }
        if (!StringUtils.hasText(normalize(config.get("username"))) && StringUtils.hasText(lake.getUsername())) {
            config.put("username", lake.getUsername());
        }
        if (!StringUtils.hasText(normalize(config.get("password"))) && StringUtils.hasText(lake.getPassword())) {
            config.put("password", lake.getPassword());
        }
        return config;
    }

    private String resolveWriterType(LakeSnapshot lake, Map<String, Object> config) {
        String writerType = normalize(lake == null ? null : lake.getDestinationDefinitionId());
        if (!StringUtils.hasText(writerType) && config != null) {
            writerType = normalize(config.get("writerType"));
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("writer"));
            }
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("type"));
            }
        }
        if (!StringUtils.hasText(writerType) && lake != null) {
            writerType = inferWriterTypeFromLake(lake);
        }
        if (StringUtils.hasText(writerType) && config != null && !StringUtils.hasText(normalize(config.get("writerType")))) {
            config.put("writerType", writerType);
        }
        return writerType;
    }

    private String inferWriterTypeFromLake(LakeSnapshot lake) {
        String type = normalize(lake.getType());
        if (StringUtils.hasText(type)) {
            String inferred = inferWriterFromTypeString(type.toLowerCase());
            if (inferred != null) {
                return inferred;
            }
        }
        String jdbcUrl = normalize(lake.getJdbcUrl());
        if (StringUtils.hasText(jdbcUrl)) {
            String lower = jdbcUrl.toLowerCase();
            if (lower.startsWith("jdbc:postgresql:")) {
                return "postgresqlwriter";
            }
            if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                return "mysqlwriter";
            }
            if (lower.startsWith("jdbc:oracle:")) {
                return "oraclewriter";
            }
            if (lower.startsWith("jdbc:sqlserver:")) {
                return "sqlserverwriter";
            }
            if (lower.startsWith("jdbc:dm:")) {
                return "rdbmswriter";
            }
            if (lower.startsWith("jdbc:clickhouse:")) {
                return "clickhousewriter";
            }
            if (lower.startsWith("jdbc:hive2:")) {
                return "hivewriter";
            }
        }
        return null;
    }

    private String inferWriterFromTypeString(String type) {
        if (type.contains("postgres") || type.contains("pg")) {
            return "postgresqlwriter";
        }
        if (type.contains("mysql") || type.contains("mariadb")) {
            return "mysqlwriter";
        }
        if (type.contains("oracle")) {
            return "oraclewriter";
        }
        if (type.contains("sqlserver") || type.contains("mssql")) {
            return "sqlserverwriter";
        }
        if (type.contains("dm") || type.contains("dameng")) {
            return "rdbmswriter";
        }
        if (type.contains("clickhouse")) {
            return "clickhousewriter";
        }
        if (type.contains("hive")) {
            return "hivewriter";
        }
        return null;
    }

    private void mergeMap(Map<String, Object> target, Object raw) {
        if (raw == null || target == null) {
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return;
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            target.putAll(parseMap(text));
        }
    }

    private Map<String, Object> parseMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            LOG.debug("Failed to parse json map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object firstNonEmptyValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = normalize(value);
            if (StringUtils.hasText(text)) {
                return value;
            }
        }
        return null;
    }

    private String buildJdbcUrl(String writerType, String host, String port, String database) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return null;
        }
        String type = normalize(writerType);
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String lower = type.toLowerCase();
        String resolvedPort = StringUtils.hasText(port) ? port.trim() : null;
        if (lower.contains("postgres")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return "jdbc:mysql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("oracle")) {
            return "jdbc:oracle:thin:@" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ":" + database;
        }
        if (lower.contains("sqlserver") || lower.contains("mssql")) {
            return "jdbc:sqlserver://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ";databaseName=" + database;
        }
        if (lower.contains("dm")) {
            return "jdbc:dm://" + host + (resolvedPort == null ? "" : ":" + resolvedPort);
        }
        if (lower.contains("rdbms")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        return null;
    }

    private static final class LakeSnapshot {

        private final String name;
        private final String type;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String destinationName;
        private final String destinationDefinitionId;
        private final Map<String, Object> destinationConfig;
        private final String dataSourceId;
        private final String adminDataLakeId;
        private final boolean fromAdmin;
        private final boolean managedDefaultMirror;
        private final String status;
        private final String heartbeatStatus;

        private LakeSnapshot(
            String name,
            String type,
            String jdbcUrl,
            String username,
            String password,
            String destinationName,
            String destinationDefinitionId,
            Map<String, Object> destinationConfig,
            String dataSourceId,
            String adminDataLakeId,
            boolean fromAdmin,
            boolean managedDefaultMirror,
            String status,
            String heartbeatStatus
        ) {
            this.name = name;
            this.type = type;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.destinationName = destinationName;
            this.destinationDefinitionId = destinationDefinitionId;
            this.destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
            this.dataSourceId = dataSourceId;
            this.adminDataLakeId = adminDataLakeId;
            this.fromAdmin = fromAdmin;
            this.managedDefaultMirror = managedDefaultMirror;
            this.status = status;
            this.heartbeatStatus = heartbeatStatus;
        }

        private static LakeSnapshot fromAdmin(AdminInfraClient.AdminDataLakeConfig lake) {
            return new LakeSnapshot(
                lake.getName(),
                lake.getType(),
                lake.getJdbcUrl(),
                lake.getUsername(),
                lake.getPassword(),
                lake.getDestinationName(),
                lake.getDestinationDefinitionId(),
                lake.getDestinationConfig(),
                null,
                lake.getId() == null ? null : lake.getId().toString(),
                true,
                true,
                lake.getStatus(),
                lake.getHeartbeatStatus()
            );
        }

        private LakeSnapshot withDataSourceId(String resolvedDataSourceId) {
            return new LakeSnapshot(
                name,
                type,
                jdbcUrl,
                username,
                password,
                destinationName,
                destinationDefinitionId,
                destinationConfig,
                resolvedDataSourceId,
                adminDataLakeId,
                fromAdmin,
                managedDefaultMirror,
                status,
                heartbeatStatus
            );
        }

        private String getDataSourceId() {
            return dataSourceId;
        }

        private String getAdminDataLakeId() {
            return adminDataLakeId;
        }

        private boolean isFromAdmin() {
            return fromAdmin;
        }

        private boolean isManagedDefaultMirror() {
            return managedDefaultMirror;
        }

        private String getStatus() {
            return status;
        }

        private String getHeartbeatStatus() {
            return heartbeatStatus;
        }

        private String getName() {
            return name;
        }

        private String getType() {
            return type;
        }

        private String getJdbcUrl() {
            return jdbcUrl;
        }

        private String getUsername() {
            return username;
        }

        private String getPassword() {
            return password;
        }

        private String getDestinationName() {
            return destinationName;
        }

        private String getDestinationDefinitionId() {
            return destinationDefinitionId;
        }

        private Map<String, Object> getDestinationConfig() {
            return destinationConfig;
        }
    }

    public record DefaultDestinationSnapshot(
        String destinationDefinitionId,
        String destinationName,
        Map<String, Object> destinationConfig,
        String dataSourceId
    ) {
        public DefaultDestinationSnapshot(String destinationDefinitionId, String destinationName, Map<String, Object> destinationConfig) {
            this(destinationDefinitionId, destinationName, destinationConfig, null);
        }

        public DefaultDestinationSnapshot {
            destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
        }

        public boolean isEmpty() {
            return !StringUtils.hasText(destinationDefinitionId)
                && !StringUtils.hasText(destinationName)
                && (destinationConfig == null || destinationConfig.isEmpty());
        }
    }

    public record DefaultDestinationStatus(
        boolean available,
        boolean writerTypeReady,
        boolean writerConfigReady,
        String destinationName,
        String writerType,
        String message,
        String dataSourceId
    ) {
        public static DefaultDestinationStatus missing(String message) {
            return new DefaultDestinationStatus(false, false, false, null, null, message, null);
        }
    }
}
