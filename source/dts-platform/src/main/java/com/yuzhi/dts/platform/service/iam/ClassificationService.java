package com.yuzhi.dts.platform.service.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog;
import com.yuzhi.dts.platform.domain.iam.IamUserClassification;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.iam.IamClassificationSyncLogRepository;
import com.yuzhi.dts.platform.repository.iam.IamUserClassificationRepository;
import com.yuzhi.dts.platform.service.iam.dto.DatasetClassificationDto;
import com.yuzhi.dts.platform.service.iam.dto.SyncFailureDto;
import com.yuzhi.dts.platform.service.iam.dto.SyncStatusDto;
import com.yuzhi.dts.platform.service.iam.dto.UserClassificationDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ClassificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationService.class);

    private final IamUserClassificationRepository userClassificationRepository;
    private final IamClassificationSyncLogRepository syncLogRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public ClassificationService(
        IamUserClassificationRepository userClassificationRepository,
        IamClassificationSyncLogRepository syncLogRepository,
        CatalogDatasetRepository datasetRepository,
        ObjectMapper objectMapper
    ) {
        this.userClassificationRepository = userClassificationRepository;
        this.syncLogRepository = syncLogRepository;
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
    }

    public List<UserClassificationDto> searchUsers(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();
        return userClassificationRepository
            .findTop20ByUsernameIgnoreCaseContainingOrDisplayNameIgnoreCaseContaining(kw, kw)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public UserClassificationDto refreshUser(UUID id) {
        IamUserClassification entity = userClassificationRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        entity.setSyncedAt(Instant.now());
        return toDto(userClassificationRepository.save(entity));
    }

    public List<DatasetClassificationDto> datasets() {
        return datasetRepository
            .findAll()
            .stream()
            .map(this::toDatasetDto)
            .collect(Collectors.toList());
    }

    public SyncStatusDto syncStatus() {
        return syncLogRepository
            .findTop1ByOrderByFinishedAtDesc()
            .map(this::toStatusDto)
            .orElseGet(() -> new SyncStatusDto(null, 0, List.of()));
    }

    @Transactional
    public SyncStatusDto executeSync(String username) {
        IamClassificationSyncLog log = new IamClassificationSyncLog();
        log.setStatus("RUNNING");
        log.setStartedAt(Instant.now());
        log.setCreatedBy(username);
        log.setLastModifiedBy(username);
        log = syncLogRepository.save(log);

        log.setDeltaCount((int) userClassificationRepository.count());
        log.setFailureJson("[]");
        log.setStatus("SUCCESS");
        log.setFinishedAt(Instant.now());
        syncLogRepository.save(log);
        return toStatusDto(log);
    }

    @Transactional
    public SyncStatusDto retryFailure(String failureId, String username) {
        // For now simply trigger another sync run to refresh failure entries.
        LOG.info("Retrying IAM classification failure {} by {}", failureId, username);
        return executeSync(username);
    }

    private UserClassificationDto toDto(IamUserClassification entity) {
        return new UserClassificationDto(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            parseStringList(entity.getOrgPath()),
            parseStringList(entity.getRoles()),
            parseStringList(entity.getProjects()),
            entity.getSecurityLevel(),
            entity.getSyncedAt()
        );
    }

    private DatasetClassificationDto toDatasetDto(CatalogDataset dataset) {
        String domainName = dataset.getDomain() != null ? dataset.getDomain().getName() : null;
        return new DatasetClassificationDto(dataset.getId(), dataset.getName(), domainName, dataset.getOwner(), dataset.getClassification());
    }

    private SyncStatusDto toStatusDto(IamClassificationSyncLog log) {
        List<SyncFailureDto> failures = parseFailures(log.getFailureJson());
        return new SyncStatusDto(log.getFinishedAt(), log.getDeltaCount() != null ? log.getDeltaCount() : 0, failures);
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                node.forEach(item -> list.add(item.asText()));
                return list;
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse list json: {}", e.getMessage());
        }
        // fallback: split by comma
        String[] parts = json.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private List<SyncFailureDto> parseFailures(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<SyncFailureDto> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(
                    new SyncFailureDto(
                        item.path("id").asText(null),
                        item.path("type").asText(null),
                        item.path("target").asText(null),
                        item.path("reason").asText(null)
                    )
                );
            }
            return list;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse failure json: {}", e.getMessage());
            return List.of();
        }
    }
}
