package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersion;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersionStatus;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardVersionRepository;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardVersionDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class DataStandardService {

    private final DataStandardRepository repository;
    private final DataStandardVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public DataStandardService(
        DataStandardRepository repository,
        DataStandardVersionRepository versionRepository,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public Page<DataStandardDto> list(DataStandardFilter filter, Pageable pageable) {
        Specification<DataStandard> spec = buildSpecification(filter);
        return repository.findAll(spec, pageable).map(DataStandardMapper::toDto);
    }

    public DataStandardDto get(UUID id) {
        return DataStandardMapper.toDto(load(id));
    }

    public DataStandardDto create(DataStandardUpsertRequest request) {
        DataStandard entity = new DataStandard();
        applyUpsert(entity, request);
        entity.setStatus(Objects.requireNonNullElse(request.getStatus(), DataStandardStatus.DRAFT));
        entity.setSecurityLevel(Objects.requireNonNullElse(request.getSecurityLevel(), DataSecurityLevel.INTERNAL));
        String version = StringUtils.hasText(request.getVersion()) ? request.getVersion() : "v1";
        entity.setCurrentVersion(version);
        entity.setVersionNotes(request.getVersionNotes());
        entity.setLastReviewAt(request.getLastReviewAt());
        repository.save(entity);
        DataStandardVersionStatus versionStatus = request.getVersionStatus() != null
            ? request.getVersionStatus()
            : DataStandardVersionStatus.DRAFT;
        createVersionSnapshot(entity, version, request.getChangeSummary(), versionStatus);
        return DataStandardMapper.toDto(entity);
    }

    public DataStandardDto update(UUID id, DataStandardUpsertRequest request) {
        DataStandard entity = load(id);
        applyUpsert(entity, request);
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getSecurityLevel() != null) {
            entity.setSecurityLevel(request.getSecurityLevel());
        }
        String version = request.getVersion();
        if (!StringUtils.hasText(version)) {
            version = entity.getCurrentVersion();
        } else {
            entity.setCurrentVersion(version);
        }
        entity.setVersionNotes(request.getVersionNotes());
        entity.setLastReviewAt(request.getLastReviewAt());
        repository.save(entity);
        createVersionSnapshot(entity, version, request.getChangeSummary(), request.getVersionStatus());
        return DataStandardMapper.toDto(entity);
    }

    public void delete(UUID id) {
        DataStandard entity = load(id);
        repository.delete(entity);
    }

    public List<DataStandardVersionDto> listVersions(UUID id) {
        DataStandard entity = load(id);
        return versionRepository
            .findByStandardOrderByCreatedDateDesc(entity)
            .stream()
            .map(DataStandardMapper::toDto)
            .toList();
    }

    private DataStandard load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Data standard not found"));
    }

    private void applyUpsert(DataStandard entity, DataStandardUpsertRequest request) {
        if (StringUtils.hasText(request.getCode())) {
            repository
                .findByCodeIgnoreCase(request.getCode())
                .filter(found -> !found.getId().equals(entity.getId()))
                .ifPresent(found -> {
                    throw new IllegalArgumentException("标准编码已存在");
                });
            entity.setCode(request.getCode().trim());
        }
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        entity.setDomain(request.getDomain());
        entity.setScope(request.getScope());
        entity.setOwner(request.getOwner());
        entity.setTags(DataStandardMapper.joinTags(request.getTags()));
        entity.setDescription(request.getDescription());
        entity.setReviewCycle(request.getReviewCycle());
    }

    private void createVersionSnapshot(
        DataStandard entity,
        String version,
        String changeSummary,
        DataStandardVersionStatus status
    ) {
        if (!StringUtils.hasText(version)) {
            return;
        }
        DataStandardVersion snapshot = versionRepository
            .findByStandardAndVersion(entity, version)
            .orElseGet(DataStandardVersion::new);
        snapshot.setStandard(entity);
        snapshot.setVersion(version);
        boolean isNewSnapshot = snapshot.getId() == null;
        DataStandardVersionStatus effectiveStatus = status;
        if (effectiveStatus == null) {
            effectiveStatus = snapshot.getStatus();
            if (effectiveStatus == null) {
                effectiveStatus = DataStandardVersionStatus.DRAFT;
            }
        }
        snapshot.setStatus(effectiveStatus);
        snapshot.setChangeSummary(changeSummary);
        if (status != null) {
            snapshot.setReleasedAt(effectiveStatus == DataStandardVersionStatus.PUBLISHED ? Instant.now() : null);
        } else if (isNewSnapshot && effectiveStatus != DataStandardVersionStatus.PUBLISHED) {
            snapshot.setReleasedAt(null);
        }
        snapshot.setSnapshotJson(serializeSnapshot(entity));
        versionRepository.save(snapshot);
    }

    private String serializeSnapshot(DataStandard entity) {
        try {
            DataStandardDto dto = DataStandardMapper.toDto(entity);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize standard snapshot", e);
        }
    }

    private Specification<DataStandard> buildSpecification(DataStandardFilter filter) {
        Specification<DataStandard> spec = Specification.where(null);

        if (filter == null) {
            return spec;
        }

        if (StringUtils.hasText(filter.getKeyword())) {
            String like = "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) ->
                cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("owner")), like)
                )
            );
        }
        if (StringUtils.hasText(filter.getDomain())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("domain"), filter.getDomain()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getSecurityLevel() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("securityLevel"), filter.getSecurityLevel()));
        }

        return spec;
    }
}
