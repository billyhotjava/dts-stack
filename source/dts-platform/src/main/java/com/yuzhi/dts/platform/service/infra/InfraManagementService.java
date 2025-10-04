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
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.DataStorageRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataStorageDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    public InfraManagementService(
        InfraDataSourceRepository dataSourceRepository,
        InfraDataStorageRepository storageRepository,
        InfraConnectionTestLogRepository testLogRepository,
        InfraSecretService secretService,
        InfraSecurityProperties securityProperties,
        ObjectMapper objectMapper
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.storageRepository = storageRepository;
        this.testLogRepository = testLogRepository;
        this.secretService = secretService;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public List<InfraDataSourceDto> listDataSources() {
        return dataSourceRepository
            .findAll()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public InfraDataSourceDto createDataSource(DataSourceRequest request, String username) {
        InfraDataSource entity = new InfraDataSource();
        applyDataSource(entity, request, username);
        return toDto(dataSourceRepository.save(entity));
    }

    @Transactional
    public InfraDataSourceDto updateDataSource(UUID id, DataSourceRequest request, String username) {
        InfraDataSource entity = dataSourceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
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
