package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile InceptorDataSourceState cached;

    public InceptorDataSourceRegistry(
        InfraDataSourceRepository repository,
        InfraSecretService secretService,
        ObjectMapper objectMapper,
        HiveExecutionProperties hiveExecutionProperties
    ) {
        this.repository = repository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
        this.hiveExecutionProperties = hiveExecutionProperties;
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
            repository
                .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(TYPE_INCEPTOR, STATUS_ACTIVE)
                .ifPresentOrElse(
                    entity -> toState(entity)
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
                                cached = null;
                                syncHiveExecutionProperties(null);
                                LOG.warn(
                                    "Active Inceptor data source {} is missing required credentials. Runtime services will stay disabled.",
                                    entity.getId()
                                );
                            }
                        ),
                    () -> {
                        LOG.info("No active Inceptor data source found. Clearing runtime registry state.");
                        cached = null;
                        syncHiveExecutionProperties(null);
                    }
                );
        } catch (Exception ex) {
            LOG.warn("Failed to refresh Inceptor data source registry: {}", ex.getMessage());
            LOG.debug("Registry refresh failure stacktrace", ex);
        } finally {
            lock.writeLock().unlock();
        }
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
            entity.getLastModifiedDate()
        );

        if (!state.isUsable()) {
            LOG.warn("Inceptor data source {} is not usable; required credentials incomplete", entity.getId());
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
        Instant lastUpdatedAt
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
    }
}
