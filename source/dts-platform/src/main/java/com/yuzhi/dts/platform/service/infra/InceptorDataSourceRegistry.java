package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient.AdminInceptorConfig;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InceptorDataSourceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(InceptorDataSourceRegistry.class);
    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final InfraDataSourceRepository repository;
    private final InfraSecretService secretService;
    private final ObjectMapper objectMapper;
    private final HiveExecutionProperties hiveExecutionProperties;
    private final AdminInfraClient adminInfraClient;
    private final DataSource dataSource;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean repositoryAvailable = new AtomicBoolean(true);
    private volatile Boolean tableAvailable;

    private volatile InceptorDataSourceState cached;

    public InceptorDataSourceRegistry(
        InfraDataSourceRepository repository,
        InfraSecretService secretService,
        ObjectMapper objectMapper,
        HiveExecutionProperties hiveExecutionProperties,
        AdminInfraClient adminInfraClient,
        DataSource dataSource
    ) {
        this.repository = repository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
        this.hiveExecutionProperties = hiveExecutionProperties;
        this.adminInfraClient = adminInfraClient;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @EventListener
    public void handlePublished(InceptorDataSourcePublishedEvent event) {
        LOG.info("Received InceptorDataSourcePublishedEvent for {}. Refreshing registry...", event.dataSource().id());
        refresh();
    }

    public Optional<InceptorDataSourceState> getActive() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(cached);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void refresh() {
        lock.writeLock().lock();
        try {
            if (!repositoryAvailable.get()) {
                if (loadFromAdmin("local repository unavailable")) {
                    return;
                }
                clearCachedState("local infra repository unavailable");
                return;
            }
            if (!isTableAvailable()) {
                repositoryAvailable.set(false);
                if (loadFromAdmin("infra_data_source table not present")) {
                    return;
                }
                LOG.info("Infra data source table not present; registry stays empty until Liquibase completes");
                clearCachedState("infra_data_source table not present");
                return;
            }
            Optional<InfraDataSource> optional = repository.findFirstByTypeIgnoreCaseAndStatusIgnoreCase(TYPE_INCEPTOR, STATUS_ACTIVE);
            if (optional.isPresent()) {
                applyEntity(optional.orElseThrow());
                return;
            }
            if (loadFromAdmin("no local Inceptor data source")) {
                return;
            }
            LOG.info("No active Inceptor data source found. Clearing runtime registry state.");
            clearCachedState("no active local or remote Inceptor data source");
        } catch (InvalidDataAccessResourceUsageException ex) {
            repositoryAvailable.set(false);
            tableAvailable = Boolean.FALSE;
            if (loadFromAdmin("infra_data_source table unavailable")) {
                return;
            }
            LOG.warn(
                "Disabling Inceptor registry refresh: infra_data_source table unavailable ({})",
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
            );
            clearCachedState("infra_data_source table unavailable");
            LOG.debug("Registry refresh failure stacktrace", ex);
        } catch (Exception ex) {
            LOG.warn("Failed to refresh Inceptor data source registry: {}", ex.getMessage());
            LOG.debug("Registry refresh failure stacktrace", ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isTableAvailable() {
        Boolean known = tableAvailable;
        if (known != null) {
            return known.booleanValue();
        }
        if (dataSource == null) {
            tableAvailable = Boolean.TRUE;
            return true;
        }
        final String sql =
            """
            SELECT 1
            FROM information_schema.tables
            WHERE lower(table_name) = 'infra_data_source'
            LIMIT 1
            """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean exists = rs.next();
                tableAvailable = Boolean.valueOf(exists);
                return exists;
            }
        } catch (SQLException ex) {
            LOG.debug("Table existence check failed: {}", ex.getMessage());
            tableAvailable = Boolean.FALSE;
            return false;
        }
    }

    private void applyEntity(InfraDataSource entity) {
        toState(entity)
            .ifPresentOrElse(
                state -> {
                    cached = state;
                    syncHiveExecutionProperties(state);
                    LOG.info(
                        "Updated Inceptor registry with data source {} (verified at {}).",
                        state.id(),
                        state.lastVerifiedAt()
                    );
                },
                () -> {
                    LOG.warn(
                        "Active Inceptor data source {} is missing required credentials. Runtime services will stay disabled.",
                        entity.getId()
                    );
                    clearCachedState("local data source missing credentials");
                }
            );
    }

    private Optional<InceptorDataSourceState> toState(InfraDataSource entity) {
        Map<String, Object> props = readProps(entity.getProps());
        Map<String, Object> secrets = secretService.readSecrets(entity);

        HiveConnectionTestRequest.AuthMethod authMethod = resolveAuthMethod(props, secrets);
        String loginPrincipal = StringUtils.hasText(entity.getUsername()) ? entity.getUsername() : stringVal(props.get("loginPrincipal"));
        String krb5Conf = stringVal(secrets.get("krb5Conf"));
        String password = stringVal(secrets.get("password"));
        String keytabBase64 = stringVal(secrets.get("keytabBase64"));
        String keytabFileName = stringVal(secrets.get("keytabFileName"));

        if (!StringUtils.hasText(entity.getJdbcUrl())) {
            LOG.warn("Inceptor data source {} has empty JDBC URL", entity.getId());
            return Optional.empty();
        }
        if (!StringUtils.hasText(loginPrincipal)) {
            LOG.warn("Inceptor data source {} missing login principal", entity.getId());
            return Optional.empty();
        }

        if (authMethod == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
            if (!StringUtils.hasText(krb5Conf) || !StringUtils.hasText(keytabBase64)) {
                LOG.warn("Inceptor data source {} missing Kerberos artifacts for keytab login", entity.getId());
                return Optional.empty();
            }
        } else if (authMethod == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            if (!StringUtils.hasText(krb5Conf) || !StringUtils.hasText(password)) {
                LOG.warn("Inceptor data source {} missing Kerberos password credentials", entity.getId());
                return Optional.empty();
            }
        } else {
            LOG.warn("Inceptor data source {} has unsupported auth method {}", entity.getId(), authMethod);
            return Optional.empty();
        }

        Map<String, String> jdbcProperties = extractStringMap(props.get("jdbcProperties"));

        InceptorDataSourceState state = new InceptorDataSourceState(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getJdbcUrl(),
            loginPrincipal,
            authMethod,
            keytabBase64,
            keytabFileName,
            password,
            krb5Conf,
            jdbcProperties,
            stringVal(props.get("proxyUser")),
            stringVal(props.get("servicePrincipal")),
            stringVal(props.get("host")),
            numberVal(props.get("port")),
            stringVal(props.get("database")),
            booleanVal(props.get("useHttpTransport")),
            stringVal(props.get("httpPath")),
            booleanVal(props.get("useSsl")),
            booleanVal(props.get("useCustomJdbc")),
            stringVal(props.get("customJdbcUrl")),
            longVal(props.get("lastTestElapsedMillis")),
            stringVal(props.get("engineVersion")),
            stringVal(props.get("driverVersion")),
            entity.getLastVerifiedAt(),
            entity.getLastModifiedDate(),
            null,
            null,
            null,
            stringVal(props.get("lastError"))
        );

        if (!state.isUsable()) {
            LOG.warn("Inceptor data source {} is not usable; required credentials incomplete", entity.getId());
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private Optional<InceptorDataSourceState> toState(AdminInceptorConfig config) {
        if (config == null) {
            return Optional.empty();
        }
        String jdbcUrl = config.getJdbcUrl();
        String loginPrincipal = config.getLoginPrincipal();
        if (!StringUtils.hasText(jdbcUrl) || !StringUtils.hasText(loginPrincipal)) {
            LOG.warn("Admin Inceptor config missing jdbcUrl or loginPrincipal");
            return Optional.empty();
        }
        HiveConnectionTestRequest.AuthMethod authMethod = HiveConnectionTestRequest.AuthMethod.KEYTAB;
        if (StringUtils.hasText(config.getAuthMethod())) {
            try {
                authMethod = HiveConnectionTestRequest.AuthMethod.valueOf(config.getAuthMethod());
            } catch (IllegalArgumentException ex) {
                LOG.warn("Unsupported auth method from admin config: {}", config.getAuthMethod());
            }
        }
        String krb5Conf = config.getKrb5Conf();
        String keytabBase64 = config.getKeytabBase64();
        String password = config.getPassword();
        if (authMethod == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
            if (!StringUtils.hasText(krb5Conf) || !StringUtils.hasText(keytabBase64)) {
                LOG.warn("Admin Inceptor config missing Kerberos artifacts for keytab login");
                return Optional.empty();
            }
        } else if (authMethod == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            if (!StringUtils.hasText(krb5Conf) || !StringUtils.hasText(password)) {
                LOG.warn("Admin Inceptor config missing password credentials");
                return Optional.empty();
            }
        }

        Map<String, String> jdbcProperties = new HashMap<>(config.getJdbcProperties());

        InceptorDataSourceState state = new InceptorDataSourceState(
            config.getId() != null ? config.getId() : UUID.randomUUID(),
            config.getName(),
            config.getDescription(),
            jdbcUrl,
            loginPrincipal,
            authMethod,
            keytabBase64,
            config.getKeytabFileName(),
            password,
            krb5Conf,
            jdbcProperties,
            config.getProxyUser(),
            config.getServicePrincipal(),
            config.getHost(),
            config.getPort(),
            config.getDatabase(),
            Boolean.TRUE.equals(config.getUseHttpTransport()),
            config.getHttpPath(),
            Boolean.TRUE.equals(config.getUseSsl()),
            Boolean.TRUE.equals(config.getUseCustomJdbc()),
            config.getCustomJdbcUrl(),
            config.getLastTestElapsedMillis(),
            config.getEngineVersion(),
            config.getDriverVersion(),
            config.getLastVerifiedAt(),
            config.getLastUpdatedAt(),
            config.getLastHeartbeatAt(),
            config.getHeartbeatStatus(),
            config.getHeartbeatFailureCount(),
            config.getLastError()
        );

        if (!state.isUsable()) {
            LOG.warn("Admin Inceptor config is not usable; required credentials incomplete");
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private void syncHiveExecutionProperties(InceptorDataSourceState state) {
        if (state == null) {
            hiveExecutionProperties.setEnabled(false);
            hiveExecutionProperties.setJdbcUrl(null);
            hiveExecutionProperties.setUsername(null);
            hiveExecutionProperties.setPassword(null);
            hiveExecutionProperties.setProperties(Collections.emptyMap());
            hiveExecutionProperties.setDefaultSchema("default");
            return;
        }
        hiveExecutionProperties.setEnabled(true);
        hiveExecutionProperties.setJdbcUrl(state.jdbcUrl());
        hiveExecutionProperties.setUsername(state.loginPrincipal());
        if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            hiveExecutionProperties.setPassword(state.password());
        } else {
            hiveExecutionProperties.setPassword(null);
        }
        Map<String, String> props = new HashMap<>(state.jdbcProperties());
        if (StringUtils.hasText(state.proxyUser())) {
            props.put("hive.server2.proxy.user", state.proxyUser());
        }
        if (StringUtils.hasText(state.servicePrincipal())) {
            props.putIfAbsent("principal", state.servicePrincipal());
        }
        hiveExecutionProperties.setProperties(props);
        if (StringUtils.hasText(state.database())) {
            hiveExecutionProperties.setDefaultSchema(state.database());
        }
    }

    private Map<String, Object> readProps(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            LOG.warn("Failed to parse data source props JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static HiveConnectionTestRequest.AuthMethod resolveAuthMethod(Map<String, Object> props, Map<String, Object> secrets) {
        Object val = secrets.getOrDefault("authMethod", props.get("authMethod"));
        if (val instanceof HiveConnectionTestRequest.AuthMethod method) {
            return method;
        }
        if (val != null) {
            try {
                return HiveConnectionTestRequest.AuthMethod.valueOf(val.toString());
            } catch (IllegalArgumentException ignored) {}
        }
        return HiveConnectionTestRequest.AuthMethod.KEYTAB;
    }

    private static Map<String, String> extractStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        map.forEach((k, v) -> {
            if (k != null) {
                out.put(k.toString(), v != null ? v.toString() : null);
            }
        });
        return out;
    }

    private void clearCachedState(String reason) {
        cached = null;
        syncHiveExecutionProperties(null);
        LOG.debug("Cleared Inceptor registry cache ({})", reason);
    }

    private boolean loadFromAdmin(String reason) {
        return adminInfraClient
            .fetchActiveInceptor()
            .flatMap(this::toState)
            .map(state -> {
                cached = state;
                syncHiveExecutionProperties(state);
                LOG.info("Loaded Inceptor configuration from admin service ({})", reason);
                return true;
            })
            .orElse(false);
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean booleanVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return StringUtils.hasText(stringVal(value)) && Boolean.parseBoolean(stringVal(value));
    }

    private static Integer numberVal(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value != null ? Long.parseLong(value.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record InceptorDataSourceState(
        UUID id,
        String name,
        String description,
        String jdbcUrl,
        String loginPrincipal,
        HiveConnectionTestRequest.AuthMethod authMethod,
        String keytabBase64,
        String keytabFileName,
        String password,
        String krb5Conf,
        Map<String, String> jdbcProperties,
        String proxyUser,
        String servicePrincipal,
        String host,
        Integer port,
        String database,
        boolean useHttpTransport,
        String httpPath,
        boolean useSsl,
        boolean useCustomJdbc,
        String customJdbcUrl,
        Long lastTestElapsedMillis,
        String engineVersion,
        String driverVersion,
        Instant lastVerifiedAt,
        Instant lastUpdatedAt,
        Instant lastHeartbeatAt,
        String heartbeatStatus,
        Integer heartbeatFailureCount,
        String lastError
    ) {
        public boolean isUsable() {
            if (!StringUtils.hasText(jdbcUrl) || !StringUtils.hasText(loginPrincipal)) {
                return false;
            }
            if (authMethod == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
                return StringUtils.hasText(keytabBase64) && StringUtils.hasText(krb5Conf);
            }
            if (authMethod == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
                return StringUtils.hasText(password) && StringUtils.hasText(krb5Conf);
            }
            return false;
        }

        public boolean isAvailable() {
            if (!isUsable()) {
                return false;
            }
            if (heartbeatStatus != null && !heartbeatStatus.isBlank()) {
                String normalized = heartbeatStatus.trim().toUpperCase(Locale.ROOT);
                if (!"UP".equals(normalized) && !"UNKNOWN".equals(normalized)) {
                    return false;
                }
            }
            return true;
        }

        public String availabilityReason() {
            if (!isUsable()) {
                return "凭据缺失或 Kerberos 配置不完整";
            }
            if (heartbeatStatus != null && !heartbeatStatus.isBlank()) {
                String normalized = heartbeatStatus.trim().toUpperCase(Locale.ROOT);
                if (!"UP".equals(normalized) && !"UNKNOWN".equals(normalized)) {
                    return "心跳状态=" + normalized;
                }
            }
            if (lastError != null && !lastError.isBlank()) {
                return lastError;
            }
            return "未知原因";
        }
    }
}
