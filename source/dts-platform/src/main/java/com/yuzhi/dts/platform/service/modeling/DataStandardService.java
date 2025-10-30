package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersion;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersionStatus;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardVersionDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final AuditService auditService;
    private final DataStandardSecurity security;

    public DataStandardService(
        DataStandardRepository repository,
        DataStandardVersionRepository versionRepository,
        ObjectMapper objectMapper,
        AuditService auditService,
        DataStandardSecurity security
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.security = security;
    }

    public Page<DataStandardDto> list(DataStandardFilter filter, Pageable pageable, String activeDeptHeader) {
        DataStandardFilter effectiveFilter = filter != null ? filter : new DataStandardFilter();
        if (!security.hasInstituteScope()) {
            String activeDept = security.resolveActiveDept(activeDeptHeader);
            if (!StringUtils.hasText(activeDept)) {
                return Page.empty(pageable);
            }
            effectiveFilter.setDomain(activeDept);
        }
        Specification<DataStandard> spec = buildSpecification(effectiveFilter);
        return repository.findAll(spec, pageable).map(DataStandardMapper::toDto);
    }

    public DataStandardDto get(UUID id, String activeDeptHeader) {
        DataStandard entity = load(id);
        security.ensureReadable(entity, activeDeptHeader);
        return DataStandardMapper.toDto(entity);
    }

    public DataStandardDto create(DataStandardUpsertRequest request, String activeDeptHeader) {
        DataStandard entity = new DataStandard();
        String enforcedDomain = security.enforceUpsertDomain(request.getDomain(), activeDeptHeader);
        request.setDomain(enforcedDomain);
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
        java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", java.util.Map.of());
        auditPayload.put("after", toStandardAuditView(entity));
        auditPayload.put("targetId", entity.getId().toString());
        auditPayload.put("targetName", entity.getName());
        auditPayload.put("operationType", "CREATE");
        auditPayload.put("summary", "新增数据标准：" + entity.getName());
        auditService.auditAction("MODELING_STANDARD_EDIT", AuditStage.SUCCESS, entity.getId().toString(), auditPayload);
        return DataStandardMapper.toDto(entity);
    }

    public DataStandardDto update(UUID id, DataStandardUpsertRequest request, String activeDeptHeader) {
        DataStandard entity = load(id);
        security.ensureWritable(entity, activeDeptHeader);
        String domainCandidate = StringUtils.hasText(request.getDomain()) ? request.getDomain() : entity.getDomain();
        String enforcedDomain = security.enforceUpsertDomain(domainCandidate, activeDeptHeader);
        request.setDomain(enforcedDomain);
        java.util.Map<String, Object> before = toStandardAuditView(entity);
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
        java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toStandardAuditView(entity));
        auditPayload.put("targetId", entity.getId().toString());
        auditPayload.put("targetName", entity.getName());
        Object previousStatusValue = before.get("status");
        DataStandardStatus previousStatus = previousStatusValue instanceof DataStandardStatus ? (DataStandardStatus) previousStatusValue : null;
        DataStandardStatus currentStatus = entity.getStatus();
        boolean archiveOperation = currentStatus == DataStandardStatus.ARCHIVED && previousStatus != DataStandardStatus.ARCHIVED;
        boolean publishOperation = false;
        if (!archiveOperation) {
            publishOperation = request.getVersionStatus() == DataStandardVersionStatus.PUBLISHED;
            if (!publishOperation && request.getStatus() == DataStandardStatus.ACTIVE && previousStatus != DataStandardStatus.ACTIVE) {
                publishOperation = true;
            }
            if (!publishOperation && previousStatus != DataStandardStatus.ACTIVE && currentStatus == DataStandardStatus.ACTIVE) {
                publishOperation = true;
            }
        }
        String operationType;
        String summary;
        String actionCode;
        if (archiveOperation) {
            operationType = "ARCHIVE";
            summary = "归档数据标准：" + entity.getName();
            actionCode = "MODELING_STANDARD_EDIT";
        } else if (publishOperation) {
            operationType = "PUBLISH";
            summary = "发布数据标准：" + entity.getName();
            actionCode = "MODELING_STANDARD_VERSION_PUBLISH";
        } else {
            operationType = "UPDATE";
            summary = "修改数据标准：" + entity.getName();
            actionCode = "MODELING_STANDARD_EDIT";
        }
        auditPayload.put("operationType", operationType);
        auditPayload.put("summary", summary);
        auditService.auditAction(actionCode, AuditStage.SUCCESS, entity.getId().toString(), auditPayload);
        return DataStandardMapper.toDto(entity);
    }

    public DataStandardDto archive(UUID id, String activeDeptHeader) {
        DataStandard entity = load(id);
        security.ensureWritable(entity, activeDeptHeader);
        if (entity.getStatus() == DataStandardStatus.ARCHIVED) {
            return DataStandardMapper.toDto(entity);
        }
        java.util.Map<String, Object> before = toStandardAuditView(entity);
        entity.setStatus(DataStandardStatus.ARCHIVED);
        repository.save(entity);
        java.util.Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", toStandardAuditView(entity));
        auditPayload.put("targetId", entity.getId().toString());
        auditPayload.put("targetName", entity.getName());
        auditPayload.put("operationType", "ARCHIVE");
        auditPayload.put("summary", "归档数据标准：" + entity.getName());
        auditService.auditAction("MODELING_STANDARD_EDIT", AuditStage.SUCCESS, entity.getId().toString(), auditPayload);
        return DataStandardMapper.toDto(entity);
    }

    public void delete(UUID id, String activeDeptHeader) {
        DataStandard entity = load(id);
        security.ensureWritable(entity, activeDeptHeader);
        java.util.Map<String, Object> before = toStandardAuditView(entity);
        repository.delete(entity);
        java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", java.util.Map.of("deleted", true));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", entity.getName());
        auditPayload.put("operationType", "DELETE");
        auditPayload.put("summary", "删除数据标准：" + entity.getName());
        auditService.auditAction("MODELING_STANDARD_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);
    }

    public List<DataStandardVersionDto> listVersions(UUID id, String activeDeptHeader) {
        DataStandard entity = load(id);
        security.ensureReadable(entity, activeDeptHeader);
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

    private java.util.Map<String, Object> toStandardAuditView(DataStandard entity) {
        if (entity == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", entity.getId());
        view.put("code", entity.getCode());
        view.put("name", entity.getName());
        view.put("domain", entity.getDomain());
        view.put("scope", entity.getScope());
        view.put("owner", entity.getOwner());
        view.put("status", entity.getStatus());
        view.put("securityLevel", entity.getSecurityLevel());
        view.put("currentVersion", entity.getCurrentVersion());
        view.put("tags", entity.getTags());
        view.put("reviewCycle", entity.getReviewCycle());
        return view;
    }
}
