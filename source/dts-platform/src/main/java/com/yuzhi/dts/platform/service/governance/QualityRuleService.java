package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleBindingRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QualityRuleService {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleService.class);
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final QualityEffectiveDepartmentResolver departmentResolver;
    private final QualityDatasetReadGuard datasetReadGuard;

    public QualityRuleService(
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        GovRuleBindingRepository bindingRepository,
        CatalogDatasetRepository datasetRepository,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        QualityAuditRecorder qualityAuditRecorder,
        ObjectMapper objectMapper,
        GovernanceProperties properties,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        QualityEffectiveDepartmentResolver departmentResolver,
        QualityDatasetReadGuard datasetReadGuard
    ) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.bindingRepository = bindingRepository;
        this.datasetRepository = datasetRepository;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.departmentResolver = departmentResolver;
        this.datasetReadGuard = datasetReadGuard;
    }

    @Transactional(readOnly = true)
    public List<QualityRuleDto> listAll(String activeDeptHeader) {
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        boolean instituteScope = hasInstituteScope();
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.currentDefaultLakeSourceId().orElse(null);
        return ruleRepository
            .findAll()
            .stream()
            .filter(rule -> isRuleVisible(rule, activeDept, instituteScope, defaultLakeSourceId))
            .map(GovernanceMapper::toDto)
            .collect(Collectors.toList());
    }

    private boolean isRuleVisible(GovRule rule, String activeDept, boolean instituteScope, UUID defaultLakeSourceId) {
        if (rule == null) {
            return false;
        }
        if (!isOwnerDeptVisible(rule.getOwnerDept(), activeDept, instituteScope)) {
            return false;
        }
        if (rule.getDatasetId() == null) {
            return true;
        }
        return datasetRepository
            .findById(rule.getDatasetId())
            .filter(accessChecker::canRead)
            .filter(dataset -> defaultLakeDatasetGuard.isDefaultLakeDataset(dataset, defaultLakeSourceId))
            .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
            .isPresent();
    }

    private boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = StringUtils.trimToNull(ownerDept);
        if (trimmedOwner == null) {
            return true;
        }
        if (instituteScope) {
            return true;
        }
        if (organizationVisibilityService.isRoot(trimmedOwner)) {
            return true;
        }
        if (StringUtils.isBlank(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    @Transactional(readOnly = true)
    public QualityRuleDto getRule(UUID id) {
        return getRule(id, null);
    }

    public QualityRuleDto getRule(UUID id, String activeDeptHeader) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleReadable(rule, activeDeptHeader);
        return GovernanceMapper.toDto(rule);
    }

    @Transactional(readOnly = true)
    public List<com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto> listRuleVersions(UUID id, String activeDeptHeader) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleReadable(rule, activeDeptHeader);
        return versionRepository.findByRuleIdOrderByVersionDesc(id).stream().map(GovernanceMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto getRuleVersion(
        UUID id,
        Integer version,
        String activeDeptHeader
    ) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleReadable(rule, activeDeptHeader);
        GovRuleVersion target = versionRepository
            .findByRuleIdOrderByVersionDesc(id)
            .stream()
            .filter(item -> Objects.equals(item.getVersion(), version))
            .findFirst()
            .orElseThrow(EntityNotFoundException::new);
        return GovernanceMapper.toDto(target);
    }

    private void ensureRuleReadable(GovRule rule, String activeDeptHeader) {
        if (rule == null) {
            throw new EntityNotFoundException("质量规则不存在");
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        boolean instituteScope = hasInstituteScope();
        if (!isOwnerDeptVisible(rule.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该质量规则");
        }
        if (rule.getDatasetId() == null) {
            return;
        }
        boolean datasetAllowed = datasetRepository
            .findById(rule.getDatasetId())
            .filter(accessChecker::canRead)
            .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
            .isPresent();
        if (!datasetAllowed) {
            throw new AccessDeniedException("当前账号无权访问该质量规则");
        }
    }

    private void ensureRuleWritable(GovRule rule, String activeDeptHeader) {
        ensureRuleReadable(rule, activeDeptHeader);
        if (hasInstituteScope()) {
            return;
        }
        if (!hasDepartmentScope()) {
            throw new AccessDeniedException("当前账号无权维护质量规则");
        }
        String ownerDept = StringUtils.trimToNull(rule.getOwnerDept());
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        if (StringUtils.isBlank(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (ownerDept != null && !DepartmentUtils.matches(ownerDept, activeDept)) {
            throw new AccessDeniedException("当前账号无权维护该质量规则");
        }
    }

    private String enforceOwnerDept(String requestedOwnerDept, String activeDeptHeader, String currentOwnerDept) {
        String requested = trimToNull(requestedOwnerDept);
        String current = trimToNull(currentOwnerDept);
        if (hasInstituteScope()) {
            if (requested != null) {
                return requested;
            }
            if (current != null) {
                return current;
            }
            return trimToNull(departmentResolver.resolve(activeDeptHeader));
        }
        if (!hasDepartmentScope()) {
            throw new AccessDeniedException("当前账号无权维护质量规则");
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        if (StringUtils.isBlank(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (current != null && !DepartmentUtils.matches(current, activeDept)) {
            throw new AccessDeniedException("当前账号无权维护该质量规则");
        }
        if (requested != null && !DepartmentUtils.matches(requested, activeDept)) {
            throw new AccessDeniedException("质量规则仅可归属当前登录部门");
        }
        return requested != null ? requested : (current != null ? current : activeDept.trim());
    }

    @Transactional(readOnly = true)
    public List<QualityRuleDto> findByDataset(UUID datasetId) {
        return findByDataset(datasetId, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRuleDto> findByDataset(UUID datasetId, String activeDeptHeader) {
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        boolean instituteScope = hasInstituteScope();
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.currentDefaultLakeSourceId().orElse(null);
        return ruleRepository
            .findAll()
            .stream()
            .filter(rule -> datasetId == null || datasetId.equals(rule.getDatasetId()))
            .filter(rule -> isRuleVisible(rule, activeDept, instituteScope, defaultLakeSourceId))
            .map(GovernanceMapper::toDto)
            .collect(Collectors.toList());
    }

    public QualityRuleDto createRule(QualityRuleUpsertRequest request, String actor) {
        return createRule(request, actor, null);
    }

    public QualityRuleDto createRule(QualityRuleUpsertRequest request, String actor, String activeDeptHeader) {
        try {
            return createRuleInternal(request, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordRuleFailure("GOV_RULE_MANAGE", "UNASSIGNED", "新增质量规则失败", ex);
            throw ex;
        }
    }

    private QualityRuleDto createRuleInternal(QualityRuleUpsertRequest request, String actor, String activeDeptHeader) {
        validateDatasetBindingContract(request, activeDeptHeader);
        String code = resolveRuleCode(request.getCode());
        if (ruleRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("编码重复: " + code);
        }

        String ownerDept = enforceOwnerDept(request.getOwnerDept(), activeDeptHeader, null);
        GovRule rule = new GovRule();
        rule.setCode(code);
        applyRuleMetadata(rule, request, ownerDept);
        ruleRepository.save(rule);

        GovRuleVersion version = persistVersion(rule, request, 1, actor);
        rule.setLatestVersion(version);
        ruleRepository.save(rule);

        GovRule persisted = ruleRepository.findById(rule.getId()).orElseThrow();
        Map<String, Object> after = toRuleAuditView(persisted);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", Map.of());
        auditPayload.put("after", after);
        auditPayload.put("targetId", persisted.getId().toString());
        auditPayload.put("targetName", persisted.getName());
        auditPayload.put("summary", "新增质量规则：" + persisted.getName());
        qualityAuditRecorder.recordAction("GOV_RULE_MANAGE", AuditStage.SUCCESS, persisted.getId().toString(), auditPayload);
        return GovernanceMapper.toDto(persisted);
    }

    public QualityRuleDto updateRule(UUID id, QualityRuleUpsertRequest request, String actor) {
        return updateRule(id, request, actor, null);
    }

    public QualityRuleDto updateRule(UUID id, QualityRuleUpsertRequest request, String actor, String activeDeptHeader) {
        try {
            return updateRuleInternal(id, request, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordRuleFailure("GOV_RULE_MANAGE", ruleResourceId(id), "修改质量规则失败", ex);
            throw ex;
        }
    }

    private QualityRuleDto updateRuleInternal(UUID id, QualityRuleUpsertRequest request, String actor, String activeDeptHeader) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleWritable(rule, activeDeptHeader);
        Map<String, Object> before = toRuleAuditView(rule);
        validateDatasetBindingContract(request, activeDeptHeader);
        if (StringUtils.isNotBlank(request.getCode())) {
            ruleRepository
                .findByCode(request.getCode().trim())
                .filter(other -> !Objects.equals(other.getId(), id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("编码已被占用");
                });
            rule.setCode(request.getCode().trim());
        }
        String ownerDept = enforceOwnerDept(
            request.getOwnerDept() != null ? request.getOwnerDept() : rule.getOwnerDept(),
            activeDeptHeader,
            rule.getOwnerDept()
        );
        applyRuleMetadata(rule, request, ownerDept);
        ruleRepository.save(rule);

        int nextVersion = versionRepository.findFirstByRuleIdOrderByVersionDesc(id).map(v -> v.getVersion() + 1).orElse(1);
        GovRuleVersion version = persistVersion(rule, request, nextVersion, actor);
        rule.setLatestVersion(version);
        ruleRepository.save(rule);

        GovRule persisted = ruleRepository.findById(id).orElseThrow();
        Map<String, Object> after = toRuleAuditView(persisted);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", after);
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", persisted.getName());
        auditPayload.put("summary", "修改质量规则：" + persisted.getName());
        qualityAuditRecorder.recordAction("GOV_RULE_MANAGE", AuditStage.SUCCESS, id.toString(), auditPayload);
        return GovernanceMapper.toDto(persisted);
    }

    public void deleteRule(UUID id) {
        deleteRule(id, null);
    }

    public void deleteRule(UUID id, String activeDeptHeader) {
        try {
            deleteRuleInternal(id, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordRuleFailure("GOV_RULE_MANAGE", ruleResourceId(id), "删除质量规则失败", ex);
            throw ex;
        }
    }

    private void deleteRuleInternal(UUID id, String activeDeptHeader) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleWritable(rule, activeDeptHeader);
        Map<String, Object> before = toRuleAuditView(rule);
        ruleRepository.delete(rule);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", Map.of("deleted", true));
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", rule.getName());
        auditPayload.put("summary", "删除质量规则：" + rule.getName());
        qualityAuditRecorder.recordAction("GOV_RULE_MANAGE", AuditStage.SUCCESS, id.toString(), auditPayload);
    }

    public QualityRuleDto toggleRule(UUID id, boolean enabled) {
        return toggleRule(id, enabled, null);
    }

    public QualityRuleDto toggleRule(UUID id, boolean enabled, String activeDeptHeader) {
        try {
            return toggleRuleInternal(id, enabled, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordRuleFailure("GOV_RULE_MANAGE", ruleResourceId(id), "切换质量规则状态失败", ex);
            throw ex;
        }
    }

    private QualityRuleDto toggleRuleInternal(UUID id, boolean enabled, String activeDeptHeader) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleWritable(rule, activeDeptHeader);
        Map<String, Object> before = toRuleAuditView(rule);
        rule.setEnabled(enabled);
        ruleRepository.save(rule);
        Map<String, Object> after = toRuleAuditView(rule);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("before", before);
        auditPayload.put("after", after);
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", rule.getName());
        auditPayload.put("summary", (enabled ? "启用" : "禁用") + "质量规则：" + rule.getName());
        qualityAuditRecorder.recordAction("GOV_RULE_MANAGE", AuditStage.SUCCESS, id.toString(), auditPayload);
        return GovernanceMapper.toDto(rule);
    }

    public com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto changeRuleVersionStatus(
        UUID id,
        Integer version,
        String targetStatus,
        String notes,
        String actor,
        String activeDeptHeader
    ) {
        try {
            return changeRuleVersionStatusInternal(id, version, targetStatus, notes, actor, activeDeptHeader);
        } catch (RuntimeException ex) {
            recordRuleFailure("GOV_RULE_VERSION_STATUS", ruleResourceId(id), "切换质量规则版本状态失败", ex);
            throw ex;
        }
    }

    private com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto changeRuleVersionStatusInternal(
        UUID id,
        Integer version,
        String targetStatus,
        String notes,
        String actor,
        String activeDeptHeader
    ) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ensureRuleWritable(rule, activeDeptHeader);
        GovRuleVersion target = versionRepository
            .findByRuleIdOrderByVersionDesc(id)
            .stream()
            .filter(item -> Objects.equals(item.getVersion(), version))
            .findFirst()
            .orElseThrow(EntityNotFoundException::new);
        String nextStatus = normalizeVersionStatus(targetStatus, target.getStatus());
        String currentStatus = normalizeVersionStatus(target.getStatus(), STATUS_DRAFT);
        ensureStatusTransitionAllowed(currentStatus, nextStatus);
        if (STATUS_PUBLISHED.equals(nextStatus)) {
            requireExecutableDefinition(target.getDefinition());
            requireExecutableBinding(rule, target, activeDeptHeader);
        }
        target.setStatus(nextStatus);
        if (StringUtils.isNotBlank(notes)) {
            target.setNotes(notes.trim());
        }
        target.setApprovedBy(StringUtils.isNotBlank(actor) ? actor : target.getApprovedBy());
        target.setApprovedAt(java.time.Instant.now());
        versionRepository.save(target);

        if (STATUS_PUBLISHED.equals(nextStatus)) {
            archiveOtherPublishedVersions(rule.getId(), target.getId(), actor);
            rule.setLatestVersion(target);
            ruleRepository.save(rule);
        } else if (Objects.equals(rule.getLatestVersion() != null ? rule.getLatestVersion().getId() : null, target.getId())) {
            GovRuleVersion fallback = versionRepository
                .findFirstByRuleIdAndStatusOrderByVersionDesc(rule.getId(), STATUS_PUBLISHED)
                .orElse(target);
            rule.setLatestVersion(fallback);
            ruleRepository.save(rule);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "切换质量规则版本状态：" + rule.getName());
        payload.put("targetId", id.toString());
        payload.put("version", version);
        payload.put("fromStatus", currentStatus);
        payload.put("toStatus", nextStatus);
        qualityAuditRecorder.recordAction("GOV_RULE_VERSION_STATUS", AuditStage.SUCCESS, id.toString(), payload);
        return GovernanceMapper.toDto(target);
    }

    private void recordRuleFailure(String actionCode, String resourceId, String summary, RuntimeException original) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("errorType", original.getClass().getSimpleName());
        try {
            qualityAuditRecorder.recordFailureAction(actionCode, resourceId, payload);
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_rule_failure_audit_write_failed actionCode={} resourceId={} errorType={}",
                actionCode,
                resourceId,
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    private String ruleResourceId(UUID id) {
        return id != null ? id.toString() : "UNASSIGNED";
    }

    private boolean hasInstituteScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
    }

    private boolean hasDepartmentScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPARTMENT_PRIVILEGED_ROLES);
    }

    private void applyRuleMetadata(GovRule rule, QualityRuleUpsertRequest request, String ownerDept) {
        rule.setName(trimToNull(request.getName()));
        rule.setType(trimToNull(request.getType()));
        rule.setDatasetId(request.getDatasetId());
        rule.setCategory(trimToNull(request.getCategory()));
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setOwner(trimToNull(request.getOwner()));
        rule.setOwnerDept(trimToNull(ownerDept));
        rule.setSeverity(trimToNull(request.getSeverity()));
        rule.setDataLevel(trimToNull(request.getDataLevel()));
        rule.setFrequencyCron(trimToNull(request.getFrequencyCron()));
        rule.setTemplate(Boolean.TRUE.equals(request.getTemplate()));
        rule.setExecutor(resolveExecutor(request));
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
    }

    private String resolveExecutor(QualityRuleUpsertRequest request) {
        if (StringUtils.isNotBlank(request.getExecutor())) {
            return request.getExecutor().trim();
        }
        return properties.getQuality().getDefaultExecutor();
    }

    private GovRuleVersion persistVersion(GovRule rule, QualityRuleUpsertRequest request, int versionNumber, String actor) {
        GovRuleVersion version = new GovRuleVersion();
        version.setRule(rule);
        version.setVersion(versionNumber);
        String targetStatus = resolveInitialVersionStatus(request);
        if (STATUS_PUBLISHED.equals(targetStatus)) {
            requireExecutableDefinition(request.getDefinition());
        }
        version.setStatus(targetStatus);
        version.setDefinition(writeDefinition(request.getDefinition()));
        if (STATUS_PUBLISHED.equals(targetStatus)) {
            version.setApprovedBy(actor);
            version.setApprovedAt(java.time.Instant.now());
        }
        versionRepository.save(version);

        if (STATUS_PUBLISHED.equals(targetStatus)) {
            archiveOtherPublishedVersions(rule.getId(), version.getId(), actor);
        }

        List<QualityRuleBindingRequest> bindings = request.getBindings() != null ? request.getBindings() : Collections.emptyList();
        if (bindings.isEmpty() && request.getDatasetId() != null) {
            QualityRuleBindingRequest datasetBinding = new QualityRuleBindingRequest();
            datasetBinding.setDatasetId(request.getDatasetId());
            datasetBinding.setScopeType("DATASET");
            bindings = List.of(datasetBinding);
        }
        bindings.stream().filter(binding -> binding.getDatasetId() != null).forEach(binding -> {
            defaultLakeDatasetGuard.requireDefaultLakeDataset(binding.getDatasetId());
            GovRuleBinding entity = new GovRuleBinding();
            entity.setRuleVersion(version);
            entity.setDatasetId(binding.getDatasetId());
            entity.setDatasetAlias(trimToNull(binding.getDatasetAlias()));
            entity.setScopeType(trimToNull(binding.getScopeType()));
            entity.setFieldRefs(GovernanceMapper.joinCsv(binding.getFieldRefs()));
            entity.setFilterExpression(trimToNull(binding.getFilterExpression()));
            entity.setScheduleOverride(trimToNull(binding.getScheduleOverride()));
            bindingRepository.save(entity);
        });
        return version;
    }

    private void archiveOtherPublishedVersions(UUID ruleId, UUID keepVersionId, String actor) {
        List<GovRuleVersion> versions = versionRepository.findByRuleIdOrderByVersionDesc(ruleId);
        versions
            .stream()
            .filter(item -> !Objects.equals(item.getId(), keepVersionId))
            .filter(item -> STATUS_PUBLISHED.equalsIgnoreCase(StringUtils.trimToEmpty(item.getStatus())))
            .forEach(item -> {
                item.setStatus(STATUS_ARCHIVED);
                item.setApprovedBy(StringUtils.isNotBlank(actor) ? actor : item.getApprovedBy());
                item.setApprovedAt(java.time.Instant.now());
                versionRepository.save(item);
            });
    }

    private String resolveInitialVersionStatus(QualityRuleUpsertRequest request) {
        if (request == null) {
            return STATUS_PUBLISHED;
        }
        if (request.getPublishNow() == null) {
            return STATUS_PUBLISHED;
        }
        return Boolean.TRUE.equals(request.getPublishNow()) ? STATUS_PUBLISHED : STATUS_DRAFT;
    }

    private String normalizeVersionStatus(String candidate, String fallback) {
        String source = StringUtils.trimToNull(candidate);
        if (source == null) {
            return StringUtils.defaultIfBlank(fallback, STATUS_DRAFT);
        }
        String normalized = source.toUpperCase(Locale.ROOT);
        if (STATUS_DRAFT.equals(normalized) || STATUS_PUBLISHED.equals(normalized) || STATUS_ARCHIVED.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的版本状态: " + source);
    }

    private void ensureStatusTransitionAllowed(String fromStatus, String toStatus) {
        if (Objects.equals(fromStatus, toStatus)) {
            return;
        }
        Set<String> allowedTargets = switch (StringUtils.defaultIfBlank(fromStatus, STATUS_DRAFT)) {
            case STATUS_DRAFT -> Set.of(STATUS_PUBLISHED, STATUS_ARCHIVED);
            case STATUS_PUBLISHED -> Set.of(STATUS_ARCHIVED);
            case STATUS_ARCHIVED -> Set.of();
            default -> Set.of();
        };
        if (!allowedTargets.contains(toStatus)) {
            throw new IllegalStateException("版本状态不允许从 " + fromStatus + " 变更为 " + toStatus);
        }
    }

    private String writeDefinition(Map<String, Object> definition) {
        Map<String, Object> payload = definition != null ? definition : Map.of();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("event=quality_rule_definition_serialize_failed errorType={}", e.getClass().getSimpleName());
            return "{}";
        }
    }

    private void requireExecutableDefinition(String definition) {
        if (StringUtils.isBlank(definition)) {
            throw new IllegalArgumentException("质量规则未配置可执行检测语句");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(definition, Map.class);
            requireExecutableDefinition(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("质量规则定义不是有效的 JSON", e);
        }
    }

    private void requireExecutableDefinition(Map<String, Object> definition) {
        if (definition == null) {
            throw new IllegalArgumentException("质量规则未配置可执行检测语句");
        }
        Object sql = definition.get("sql");
        if (sql != null && StringUtils.isNotBlank(String.valueOf(sql))) {
            return;
        }
        Object statements = definition.get("statements");
        if (
            statements instanceof Map<?, ?> statementMap &&
            statementMap.values().stream().anyMatch(value -> value != null && StringUtils.isNotBlank(String.valueOf(value)))
        ) {
            return;
        }
        throw new IllegalArgumentException("质量规则未配置可执行检测语句");
    }

    private Map<String, Object> toRuleAuditView(GovRule rule) {
        if (rule == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rule.getId());
        view.put("code", rule.getCode());
        view.put("name", rule.getName());
        view.put("datasetId", rule.getDatasetId());
        view.put("category", rule.getCategory());
        view.put("severity", rule.getSeverity());
        view.put("dataLevel", rule.getDataLevel());
        view.put("owner", rule.getOwner());
        view.put("ownerDept", rule.getOwnerDept());
        view.put("enabled", rule.getEnabled());
        view.put("template", rule.getTemplate());
        if (rule.getLatestVersion() != null) {
            view.put("latestVersion", rule.getLatestVersion().getVersion());
        }
        return view;
    }

    private void validateDatasetBindingContract(QualityRuleUpsertRequest request, String activeDeptHeader) {
        if (request == null) {
            throw new IllegalArgumentException("质量规则请求不能为空");
        }
        UUID datasetId = request.getDatasetId();
        List<QualityRuleBindingRequest> bindings = request.getBindings();
        if (bindings != null && !bindings.isEmpty()) {
            if (datasetId == null) {
                throw new IllegalArgumentException("规则数据资产不能为空");
            }
            if (bindings.size() != 1 || bindings.get(0) == null || bindings.get(0).getDatasetId() == null) {
                throw new IllegalArgumentException("一个质量规则只能绑定一个数据资产");
            }
            QualityRuleBindingRequest binding = bindings.get(0);
            if (!datasetId.equals(binding.getDatasetId())) {
                throw new IllegalArgumentException("绑定数据资产必须与规则数据资产一致");
            }
            if (StringUtils.isNotBlank(binding.getScopeType()) && !"DATASET".equalsIgnoreCase(binding.getScopeType().trim())) {
                throw new IllegalArgumentException("质量规则仅支持数据资产级绑定");
            }
        }
        if (datasetId == null) {
            if (STATUS_PUBLISHED.equals(resolveInitialVersionStatus(request))) {
                throw new IllegalArgumentException("已发布质量规则必须绑定数据资产");
            }
            return;
        }
        datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
    }

    private void requireExecutableBinding(GovRule rule, GovRuleVersion version, String activeDeptHeader) {
        UUID datasetId = rule != null ? rule.getDatasetId() : null;
        List<GovRuleBinding> bindings = version != null && version.getId() != null
            ? bindingRepository.findByRuleVersionId(version.getId())
            : List.of();
        if (
            datasetId == null ||
            bindings.size() != 1 ||
            bindings.get(0) == null ||
            !datasetId.equals(bindings.get(0).getDatasetId())
        ) {
            throw new IllegalArgumentException("发布质量规则前必须绑定数据资产");
        }
        datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
    }

    private String resolveRuleCode(String code) {
        if (StringUtils.isNotBlank(code)) {
            return code.trim();
        }
        return "QRULE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.isNotBlank(value) ? value.trim() : null;
    }
}
