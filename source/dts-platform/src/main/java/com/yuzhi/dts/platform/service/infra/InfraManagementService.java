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
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.platform.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.DataStorageRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataStorageDto;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService.JdbcSyncResult;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String TYPE_POSTGRES = "POSTGRES";
    private static final String STATUS_ACTIVE = "ACTIVE";

    public InfraManagementService(
        InfraDataSourceRepository dataSourceRepository,
        InfraDataStorageRepository storageRepository,
        InfraConnectionTestLogRepository testLogRepository,
        InfraSecretService secretService,
        InfraSecurityProperties securityProperties,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        PostgresConnectionService postgresConnectionService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.storageRepository = storageRepository;
        this.testLogRepository = testLogRepository;
        this.secretService = secretService;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.postgresConnectionService = postgresConnectionService;
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
            return sources.stream().map(this::toDto).collect(Collectors.toList());
        } catch (RuntimeException ex) {
            // If Liquibase hasn’t created infra tables yet, return empty to keep UI usable
            LOG.warn("listDataSources failed (likely missing table). Returning empty list. cause={}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public InfraDataSourceDto createDataSource(DataSourceRequest request, String username, String activeDeptHeader) {
        ensureNotInceptorManaged(request.type());
        InfraDataSource entity = new InfraDataSource();
        validateJdbcCredentials(request, null);
        applyDataSource(entity, request, username);
        ensureNotSystemManaged(entity.getType(), activeDeptHeader);
        applyOwnerDept(entity, activeDeptHeader);
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

    @Transactional
    public InfraDataSourceDto updateDataSource(UUID id, DataSourceRequest request, String username, String activeDeptHeader) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureNotInceptorManaged(request.type());
        ensureNotInceptorManaged(entity.getType());
        ensureNotSystemManaged(entity.getType(), activeDeptHeader);
        ensureDeptScopeWritable(entity, activeDeptHeader);
        validateJdbcCredentials(request, entity);
        applyDataSource(entity, request, username);
        applyOwnerDept(entity, activeDeptHeader);
        return toDto(dataSourceRepository.save(entity));
    }

    public InfraDataSource findEntity(UUID id) {
        return dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
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
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setUsername(request.username());
        entity.setDescription(request.description());
        entity.setProps(writeProps(request.props()));
        if (request.secrets() != null) {
            secretService.applySecrets(entity, request.secrets());
        }
        entity.setLastModifiedBy(username);
        entity.setCreatedBy(entity.getCreatedBy() == null ? username : entity.getCreatedBy());
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_ACTIVE);
        }
    }

    private void validateJdbcCredentials(DataSourceRequest request, InfraDataSource existing) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
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
