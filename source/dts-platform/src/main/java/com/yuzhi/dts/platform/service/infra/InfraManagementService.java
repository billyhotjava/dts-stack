package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.InfraSecurityProperties;
import com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.domain.service.InfraDataStorage;
import com.yuzhi.dts.platform.repository.service.InfraConnectionTestLogRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataStorageRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.platform.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.DataStorageRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataStorageDto;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
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

    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String STATUS_ACTIVE = "ACTIVE";

    public InfraManagementService(
        InfraDataSourceRepository dataSourceRepository,
        InfraDataStorageRepository storageRepository,
        InfraConnectionTestLogRepository testLogRepository,
        InfraSecretService secretService,
        InfraSecurityProperties securityProperties,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.storageRepository = storageRepository;
        this.testLogRepository = testLogRepository;
        this.secretService = secretService;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public List<InfraDataSourceDto> listDataSources() {
        boolean canViewAll = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.CATALOG_ADMIN,
            AuthoritiesConstants.OP_ADMIN
        );
        List<InfraDataSource> sources = canViewAll
            ? dataSourceRepository.findAll()
            : dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        return sources.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public InfraDataSourceDto createDataSource(DataSourceRequest request, String username) {
        ensureNotInceptorManaged(request.type());
        InfraDataSource entity = new InfraDataSource();
        applyDataSource(entity, request, username);
        return toDto(dataSourceRepository.save(entity));
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
    public InfraDataSourceDto updateDataSource(UUID id, DataSourceRequest request, String username) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureNotInceptorManaged(request.type());
        ensureNotInceptorManaged(entity.getType());
        applyDataSource(entity, request, username);
        return toDto(dataSourceRepository.save(entity));
    }

    @Transactional
    public void deleteDataSource(UUID id) {
        dataSourceRepository.deleteById(id);
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
            log.setResult(result.success() ? "SUCCESS" : "FAILURE");
            log.setMessage(result.message());
            log.setElapsedMs((int) result.elapsedMillis());
            log.setRequestPayload(objectMapper.writeValueAsString(payload));
            log.setCreatedBy(username);
            log.setCreatedDate(Instant.now());
            testLogRepository.save(log);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to persist connection test log: {}", e.getMessage());
        }
    }

    public boolean isMultiSourceEnabled() {
        return securityProperties.isMultiSourceEnabled();
    }

    private void applyDataSource(InfraDataSource entity, DataSourceRequest request, String username) {
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setUsername(request.username());
        entity.setDescription(request.description());
        entity.setProps(writeProps(request.props()));
        secretService.applySecrets(entity, request.secrets());
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_ACTIVE);
        }
    }

    private void applyInceptorDataSource(InfraDataSource entity, HiveConnectionPersistRequest request, String username) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setType(TYPE_INCEPTOR);
        entity.setJdbcUrl(request.getJdbcUrl());
        entity.setUsername(request.getLoginPrincipal());
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
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD && StringUtils.hasText(request.getPassword())) {
            secrets.put("password", request.getPassword());
        }
        if (StringUtils.hasText(request.getKrb5Conf())) {
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
        secretService.applySecrets(entity, request.secrets());
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
            props,
            entity.getCreatedDate(),
            entity.getLastVerifiedAt(),
            entity.getStatus(),
            entity.getSecureProps() != null
        );
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
