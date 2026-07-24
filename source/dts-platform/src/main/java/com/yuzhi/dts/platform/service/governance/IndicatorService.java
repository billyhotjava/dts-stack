package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CodeAssetLifecycleMapper;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class IndicatorService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_DEPRECATED = "DEPRECATED";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)^v(\\d+)$");
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_GENERATION_GRAPH_NODES = 64;
    private static final int MAX_GENERATION_GRAPH_DEPTH = 64;
    private static final int MAX_DIRECT_GENERATION_DEPENDENCIES = 32;

    private final GovIndicatorDefinitionRepository repository;
    private final GovIndicatorVersionRepository versionRepository;
    private final GovIndicatorReferenceRepository referenceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final QueryGateway queryGateway;
    private final SecuritySqlRewriter securitySqlRewriter;
    private final ObjectMapper objectMapper;
    private final CatalogDomainRepository catalogDomainRepository;
    private final CodeAssetGrantWriter codeAssetGrantWriter;
    private final IndicatorDerivationValidationService derivationValidationService;

    public IndicatorService(
        GovIndicatorDefinitionRepository repository,
        GovIndicatorVersionRepository versionRepository,
        GovIndicatorReferenceRepository referenceRepository,
        CatalogDatasetRepository datasetRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        QueryGateway queryGateway,
        SecuritySqlRewriter securitySqlRewriter,
        ObjectMapper objectMapper,
        CatalogDomainRepository catalogDomainRepository,
        CodeAssetGrantWriter codeAssetGrantWriter,
        IndicatorDerivationValidationService derivationValidationService
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.referenceRepository = referenceRepository;
        this.datasetRepository = datasetRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.queryGateway = queryGateway;
        this.securitySqlRewriter = securitySqlRewriter;
        this.objectMapper = objectMapper;
        this.catalogDomainRepository = catalogDomainRepository;
        this.codeAssetGrantWriter = codeAssetGrantWriter;
        this.derivationValidationService = derivationValidationService;
    }

    @Transactional(readOnly = true)
    public Page<IndicatorDto> list(String keyword, String status, Pageable pageable, String activeDept) {
        return list(keyword, status, null, null, null, pageable, activeDept);
    }

    @Transactional(readOnly = true)
    public Page<IndicatorDto> list(String keyword, String status, String domain, String category, Boolean derived, Pageable pageable, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        // Push domain/status/category/derived filtering to the database via Specification
        Specification<GovIndicatorDefinition> spec = buildSpec(status, domain, category, derived);
        List<GovIndicatorDefinition> dbFiltered = repository.findAll(spec);

        // Keyword and security filters remain in-memory (cross-field search + runtime context)
        List<GovIndicatorDefinition> filtered = new ArrayList<>();
        for (GovIndicatorDefinition indicator : dbFiltered) {
            if (indicator == null) continue;
            if (!keywordMatches(indicator, keyword)) continue;
            if (!deptAllowed(indicator, trustedActiveDept)) continue;
            if (!levelAllowed(indicator)) continue;
            filtered.add(indicator);
        }
        filtered.sort(
            Comparator.comparing(GovIndicatorDefinition::getLastModifiedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
        );

        int total = filtered.size();
        if (pageable == null) {
            List<IndicatorDto> content = filtered.stream().map(IndicatorMapper::toDto).toList();
            return new PageImpl<>(content, Pageable.unpaged(), total);
        }
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<IndicatorDto> content = filtered.subList(start, end).stream().map(IndicatorMapper::toDto).toList();
        return new PageImpl<>(content, pageable, total);
    }

    private Specification<GovIndicatorDefinition> buildSpec(String status, String domain, String category, Boolean derived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status)) {
                String normalizedStatus = normalizeStatus(status, "");
                if (StringUtils.hasText(normalizedStatus)) {
                    predicates.add(cb.equal(cb.upper(root.get("status")), normalizedStatus));
                }
            }
            if (StringUtils.hasText(domain)) {
                predicates.add(cb.equal(cb.lower(root.get("domain")), domain.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }
            if (derived != null) {
                if (derived) {
                    predicates.add(cb.equal(root.get("isDerived"), true));
                } else {
                    predicates.add(cb.or(
                        cb.equal(root.get("isDerived"), false),
                        cb.isNull(root.get("isDerived"))
                    ));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public IndicatorDto get(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findById(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        return IndicatorMapper.toDto(entity);
    }

    public void lockLifecycleGraphForUpdate() {
        repository.findAllForLifecycleUpdate();
    }

    public IndicatorDto create(IndicatorUpsertRequest request, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = new GovIndicatorDefinition();
        IndicatorMapper.apply(entity, request);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersion("v1");
        clearValidation(entity);
        validateDomainCode(entity.getDomain());
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, null, trustedActiveDept);
        GovIndicatorDefinition saved = repository.save(entity);
        syncCodeAssetGrant(saved);
        syncIndicatorDependencyReferences(saved);
        snapshot(saved, saved.getVersion(), "DRAFT", saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto update(UUID id, IndicatorUpsertRequest request, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        requireMutationAccess(entity, trustedActiveDept);
        requireMatchingLastModifiedDate(entity, request);
        String currentStatus = normalizeStatus(entity.getStatus(), STATUS_DRAFT);
        if (STATUS_PUBLISHED.equals(currentStatus)) {
            throw new IndicatorConflictException("已发布指标不可直接修改，请使用发布新版本端点");
        }
        requireStableCode(entity, request);
        String currentCode = entity.getCode();
        String targetVersion = STATUS_ARCHIVED.equals(currentStatus) ? nextVersion(entity) : entity.getVersion();
        IndicatorMapper.apply(entity, request);
        entity.setCode(currentCode);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersion(StringUtils.hasText(targetVersion) ? targetVersion : "v1");
        clearValidation(entity);
        validateDomainCode(entity.getDomain());
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, id, trustedActiveDept);
        GovIndicatorDefinition saved = repository.save(entity);
        syncCodeAssetGrant(saved);
        syncIndicatorDependencyReferences(saved);
        snapshot(saved, saved.getVersion(), StringUtils.hasText(saved.getStatus()) ? saved.getStatus() : "DRAFT", saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto stageRevision(UUID id, IndicatorUpsertRequest request, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        lockLifecycleGraphForUpdate();
        GovIndicatorDefinition entity = repository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        requireMutationAccess(entity, trustedActiveDept);
        requireMatchingLastModifiedDate(entity, request);
        if (!STATUS_PUBLISHED.equals(normalizeStatus(entity.getStatus(), STATUS_DRAFT))) {
            throw new IndicatorConflictException("只有已发布指标可以创建发布新版本");
        }
        requireStableCode(entity, request);
        String currentCode = entity.getCode();
        String revisionVersion = nextVersion(entity);
        IndicatorMapper.apply(entity, request);
        entity.setCode(currentCode);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersion(revisionVersion);
        clearValidation(entity);
        validateDomainCode(entity.getDomain());
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, id, trustedActiveDept);
        GovIndicatorDefinition saved = repository.save(entity);
        syncCodeAssetGrant(saved);
        syncIndicatorDependencyReferences(saved);
        snapshot(saved, revisionVersion, STATUS_DRAFT, saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto publish(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        lockLifecycleGraphForUpdate();
        GovIndicatorDefinition entity = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        requireMutationAccess(entity, trustedActiveDept);
        if (!STATUS_DRAFT.equals(normalizeStatus(entity.getStatus(), STATUS_DRAFT))) {
            throw new IndicatorConflictException("只有当前草稿指标可以发布");
        }
        ensurePublishReady(entity, trustedActiveDept);
        entity.setStatus(STATUS_PUBLISHED);
        applyDefaults(entity, trustedActiveDept);
        GovIndicatorDefinition saved = repository.save(entity);
        syncCodeAssetGrant(saved);
        snapshot(saved, saved.getVersion(), STATUS_PUBLISHED, saved.getVersionNotes(), Instant.now());
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorValidationResultDto validateComputeRule(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        IndicatorValidationResultDto result = new IndicatorValidationResultDto();
        result.setIndicatorId(id);
        Instant now = Instant.now();
        result.setValidatedAt(now);

        String signature = computeSignature(entity);
        result.setSignature(signature);

        if (!StringUtils.hasText(entity.getDatasetId())) {
            return persistValidation(entity, now, signature, result, "FAILED", "请先绑定数据集", null);
        }
        UUID datasetId;
        try {
            datasetId = UUID.fromString(entity.getDatasetId().trim());
        } catch (IllegalArgumentException ex) {
            return persistValidation(entity, now, signature, result, "FAILED", "数据集ID格式错误", null);
        }
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            return persistValidation(entity, now, signature, result, "FAILED", "绑定的数据集不存在", null);
        }
        boolean read = accessChecker.canRead(dataset);
        boolean deptOk = accessChecker.departmentAllowed(dataset, trustedActiveDept);
        if (!read || !deptOk) {
            String msg = !deptOk ? "当前部门上下文不可访问该数据集" : "无权限访问该数据集";
            return persistValidation(entity, now, signature, result, "FAILED", msg, null);
        }
        if (!StringUtils.hasText(entity.getExpressionSql())) {
            return persistValidation(entity, now, signature, result, "FAILED", "请先配置计算SQL", null);
        }
        String rawSql = entity.getExpressionSql().trim();
        String limitedSql = wrapWithLimit(rawSql, 1);
        String effectiveSql;
        try {
            effectiveSql = securitySqlRewriter.guard(limitedSql, dataset, trustedActiveDept);
        } catch (SecurityGuardException ex) {
            return persistValidation(entity, now, signature, result, "FAILED", ex.getMessage(), null);
        } catch (IllegalStateException ex) {
            return persistValidation(entity, now, signature, result, "FAILED", ex.getMessage(), null);
        } catch (Exception ex) {
            return persistValidation(entity, now, signature, result, "FAILED", "SQL安全校验失败：" + ex.getMessage(), null);
        }
        result.setEffectiveSql(effectiveSql);

        try {
            Map<String, Object> payload = queryGateway.execute(effectiveSql);
            result.setHeaders(extractHeaders(payload));
            result.setRowCount(numberOrNull(payload.get("rowCount")));
            result.setDurationMs(numberOrNull(payload.get("durationMs")));
            return persistValidation(entity, now, signature, result, "SUCCESS", "OK", payload);
        } catch (Exception ex) {
            return persistValidation(entity, now, signature, result, "FAILED", "执行失败：" + safeMessage(ex.getMessage()), null);
        }
    }

    public IndicatorDerivationValidationResult validateDerivation(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        IndicatorDerivationValidationResult result = enforceDependencyAccess(
            derivationValidationService.validate(id),
            trustedActiveDept
        );
        entity.setLastValidationStatus(result.valid() ? "SUCCESS" : "FAILED");
        entity.setLastValidationMessage(
            result.valid()
                ? "OK"
                : result.issues().stream().map(IndicatorDerivationValidationResult.Issue::message).collect(java.util.stream.Collectors.joining("；"))
        );
        entity.setLastValidatedAt(Instant.now());
        entity.setLastValidationSignature(computeSignature(entity));
        repository.save(entity);
        return result;
    }

    /**
     * 预览指标计算结果（不会写入 last_validation_* 字段）。
     * - 仅用于配置人员调试 SQL；真正发布仍需走 validate + publish。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previewComputeRule(UUID id, int limit, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findById(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("indicatorId", id.toString());
        result.put("code", entity.getCode());
        result.put("name", entity.getName());

        if (!StringUtils.hasText(entity.getDatasetId())) {
            result.put("status", "FAILED");
            result.put("message", "请先配置数据集");
            return result;
        }
        UUID datasetId;
        try {
            datasetId = UUID.fromString(entity.getDatasetId().trim());
        } catch (IllegalArgumentException ex) {
            result.put("status", "FAILED");
            result.put("message", "数据集ID格式错误");
            return result;
        }
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            result.put("status", "FAILED");
            result.put("message", "绑定的数据集不存在");
            return result;
        }
        boolean read = accessChecker.canRead(dataset);
        boolean deptOk = accessChecker.departmentAllowed(dataset, trustedActiveDept);
        if (!read || !deptOk) {
            result.put("status", "FAILED");
            result.put("message", !deptOk ? "当前部门上下文不可访问该数据集" : "无权限访问该数据集");
            return result;
        }
        if (!StringUtils.hasText(entity.getExpressionSql())) {
            result.put("status", "FAILED");
            result.put("message", "请先配置计算SQL");
            return result;
        }

        String rawSql = entity.getExpressionSql().trim();
        String limitedSql = wrapWithLimit(rawSql, Math.max(1, Math.min(limit, 200)));
        String effectiveSql;
        try {
            effectiveSql = securitySqlRewriter.guard(limitedSql, dataset, trustedActiveDept);
        } catch (SecurityGuardException ex) {
            result.put("status", "FAILED");
            result.put("message", ex.getMessage());
            return result;
        } catch (Exception ex) {
            result.put("status", "FAILED");
            result.put("message", "SQL安全校验失败：" + ex.getMessage());
            return result;
        }
        result.put("effectiveSql", effectiveSql);

        try {
            Map<String, Object> payload = queryGateway.execute(effectiveSql);
            result.put("status", "SUCCESS");
            result.put("message", "OK");
            result.put("headers", extractHeaders(payload));
            result.put("rows", payload.get("rows"));
            result.put("rowCount", payload.get("rowCount"));
            result.put("durationMs", payload.get("durationMs"));
            return result;
        } catch (Exception ex) {
            result.put("status", "FAILED");
            result.put("message", "执行失败：" + safeMessage(ex.getMessage()));
            return result;
        }
    }

    public IndicatorDto archive(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        lockLifecycleGraphForUpdate();
        GovIndicatorDefinition entity = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        requireMutationAccess(entity, trustedActiveDept);
        ensureNoPublishedDerivedDependents(entity);
        if (STATUS_ARCHIVED.equals(normalizeStatus(entity.getStatus(), STATUS_DRAFT))) {
            return IndicatorMapper.toDto(entity);
        }
        entity.setVersion(nextVersion(entity));
        entity.setStatus(STATUS_ARCHIVED);
        clearValidation(entity);
        applyDefaults(entity, trustedActiveDept);
        GovIndicatorDefinition saved = repository.save(entity);
        syncCodeAssetGrant(saved);
        snapshot(saved, saved.getVersion(), STATUS_ARCHIVED, saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public void delete(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        lockLifecycleGraphForUpdate();
        GovIndicatorDefinition entity = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + id));
        requireMutationAccess(entity, trustedActiveDept);
        ensureNoPublishedDerivedDependents(entity);
        if (STATUS_PUBLISHED.equals(normalizeStatus(entity.getStatus(), STATUS_DRAFT))) {
            throw new IndicatorConflictException("已发布指标不可直接删除，请先归档");
        }
        referenceRepository.deleteByIndicator(entity);
        versionRepository.deleteByIndicator(entity);
        repository.flush();
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<IndicatorDto> listByDomain(String domain, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        List<GovIndicatorDefinition> all = repository.findByDomainAndStatusNot(domain, STATUS_ARCHIVED);
        List<IndicatorDto> result = new ArrayList<>();
        for (GovIndicatorDefinition indicator : all) {
            if (indicator == null) continue;
            if (!deptAllowed(indicator, trustedActiveDept)) continue;
            if (!levelAllowed(indicator)) continue;
            result.add(IndicatorMapper.toDto(indicator));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IndicatorDto> listByTemplateId(UUID templateId, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        List<GovIndicatorDefinition> all = repository.findByTemplateId(templateId);
        List<IndicatorDto> result = new ArrayList<>();
        for (GovIndicatorDefinition indicator : all) {
            if (indicator == null) continue;
            if (!deptAllowed(indicator, trustedActiveDept)) continue;
            if (!levelAllowed(indicator)) continue;
            result.add(IndicatorMapper.toDto(indicator));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IndicatorVersionDto> listVersions(UUID indicatorId, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        return versionRepository
            .findByIndicatorOrderByCreatedDateDesc(entity)
            .stream()
            .map(IndicatorMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public IndicatorVersionDto getVersion(UUID indicatorId, String version, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        GovIndicatorVersion snap = versionRepository
            .findByIndicatorAndVersion(entity, version)
            .orElseThrow(() -> new IndicatorNotFoundException("指标版本不存在"));
        return IndicatorMapper.toDto(snap);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(UUID indicatorId, String leftVersion, String rightVersion, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovIndicatorDefinition entity = repository.findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }

        IndicatorDto current = IndicatorMapper.toDto(entity);
        SnapshotRef left = resolveSnapshot(entity, current, leftVersion, true);
        SnapshotRef right = resolveSnapshot(entity, current, rightVersion, false);
        List<Map<String, Object>> diffs = diffSnapshots(left.payload(), right.payload());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicatorId", indicatorId.toString());
        result.put("leftVersion", left.version());
        result.put("rightVersion", right.version());
        result.put("leftSnapshot", left.payload());
        result.put("rightSnapshot", right.payload());
        result.put("diffCount", diffs.size());
        result.put("diffs", diffs);
        return result;
    }

    public Map<String, Object> rollbackToVersion(
        UUID indicatorId,
        String sourceVersion,
        String activeDept,
        String reason,
        boolean publishAfterRollback
    ) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        lockLifecycleGraphForUpdate();
        GovIndicatorDefinition entity = repository.findByIdForUpdate(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        requireMutationAccess(entity, trustedActiveDept);
        if (!StringUtils.hasText(sourceVersion)) {
            throw new IndicatorRequestException("回滚版本不能为空");
        }
        GovIndicatorVersion source = versionRepository
            .findByIndicatorAndVersion(entity, sourceVersion.trim())
            .orElseThrow(() -> new IndicatorNotFoundException("目标回滚版本不存在"));

        IndicatorDto snapshotDto = parseSnapshot(source.getSnapshotJson());
        if (snapshotDto == null) {
            throw new IndicatorConflictException("目标版本快照不可读，无法回滚");
        }

        String stableCode = entity.getCode();
        applySnapshotToEntity(entity, snapshotDto);
        entity.setCode(stableCode);
        String rollbackVersion = nextVersion(entity);
        entity.setVersion(rollbackVersion);
        String reasonText = StringUtils.hasText(reason) ? reason.trim() : "版本回滚";
        entity.setVersionNotes(reasonText + "（来源版本: " + sourceVersion.trim() + "）");
        entity.setStatus(STATUS_DRAFT);
        clearValidation(entity);
        validateDomainCode(entity.getDomain());
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, indicatorId, trustedActiveDept);
        repository.save(entity);
        syncIndicatorDependencyReferences(entity);
        syncCodeAssetGrant(entity);

        Instant releasedAt = null;
        if (publishAfterRollback) {
            ensurePublishReady(entity, trustedActiveDept);
            entity.setStatus(STATUS_PUBLISHED);
            releasedAt = Instant.now();
            repository.save(entity);
            syncCodeAssetGrant(entity);
        }
        snapshot(entity, rollbackVersion, entity.getStatus(), entity.getVersionNotes(), releasedAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", IndicatorMapper.toDto(entity));
        result.put("rollbackFromVersion", sourceVersion.trim());
        result.put("rollbackToVersion", rollbackVersion);
        result.put("published", publishAfterRollback);
        return result;
    }

    public void validateGenerationAccess(Collection<UUID> indicatorIds, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            throw new IndicatorRequestException("生成指标不能为空");
        }
        LinkedHashSet<UUID> targets = new LinkedHashSet<>();
        for (UUID indicatorId : indicatorIds) {
            if (indicatorId == null) {
                throw new IndicatorRequestException("生成指标标识不能为空");
            }
            targets.add(indicatorId);
        }
        if (targets.size() > MAX_GENERATION_GRAPH_NODES) {
            throw new IndicatorConflictException("单次生成目标及传递依赖总节点数不能超过 " + MAX_GENERATION_GRAPH_NODES);
        }

        lockLifecycleGraphForUpdate();
        Set<UUID> validated = new LinkedHashSet<>();
        Set<UUID> visiting = new LinkedHashSet<>();
        targets
            .stream()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(indicatorId ->
                validateGenerationNode(indicatorId, null, trustedActiveDept, 0, validated, visiting)
            );
    }

    private void validateGenerationNode(
        UUID indicatorId,
        String expectedCode,
        String trustedActiveDept,
        int depth,
        Set<UUID> validated,
        Set<UUID> visiting
    ) {
        if (depth > MAX_GENERATION_GRAPH_DEPTH) {
            throw new IndicatorConflictException("指标依赖深度不能超过 " + MAX_GENERATION_GRAPH_DEPTH);
        }
        if (validated.contains(indicatorId)) {
            return;
        }
        if (!visiting.add(indicatorId)) {
            throw new IndicatorConflictException("指标依赖存在循环");
        }
        if (validated.size() + visiting.size() > MAX_GENERATION_GRAPH_NODES) {
            throw new IndicatorConflictException("指标及传递依赖总节点数不能超过 " + MAX_GENERATION_GRAPH_NODES);
        }

        try {
            boolean dependency = StringUtils.hasText(expectedCode);
            GovIndicatorDefinition entity = repository
                .findByIdForUpdate(indicatorId)
                .orElseThrow(() -> {
                    if (dependency) {
                        return new IndicatorConflictException("依赖指标不存在: " + expectedCode);
                    }
                    return new IndicatorNotFoundException("指标不存在: " + indicatorId);
                });
            String label = dependency ? "依赖指标" : "指标";
            if (
                dependency &&
                (!StringUtils.hasText(entity.getCode()) || !entity.getCode().trim().equalsIgnoreCase(expectedCode.trim()))
            ) {
                throw new IndicatorConflictException("依赖指标解析结果已变化，请刷新后重试");
            }
            if (!STATUS_PUBLISHED.equals(normalizeStatus(entity.getStatus(), STATUS_DRAFT))) {
                throw new IndicatorConflictException(label + "必须处于已发布状态: " + safeIndicatorCode(entity));
            }
            if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
                throw new org.springframework.security.access.AccessDeniedException(
                    label + "不可访问: " + safeIndicatorCode(entity)
                );
            }

            List<String> dependencies = Boolean.TRUE.equals(entity.getIsDerived())
                ? parseDependencyCodes(entity.getDependencyIndicators())
                : List.of();
            if (dependencies.size() > MAX_DIRECT_GENERATION_DEPENDENCIES) {
                throw new IndicatorConflictException(
                    "指标直接依赖数不能超过 " + MAX_DIRECT_GENERATION_DEPENDENCIES + ": " + safeIndicatorCode(entity)
                );
            }
            dependencies
                .stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(code -> {
                    Optional<GovIndicatorDefinition> resolved = repository.findFirstByCodeIgnoreCase(code);
                    GovIndicatorDefinition dependencyEntity = resolved != null ? resolved.orElse(null) : null;
                    if (dependencyEntity == null || dependencyEntity.getId() == null) {
                        throw new IndicatorConflictException("依赖指标不存在: " + code);
                    }
                    validateGenerationNode(
                        dependencyEntity.getId(),
                        code,
                        trustedActiveDept,
                        depth + 1,
                        validated,
                        visiting
                    );
                });

            String currentSignature = computeSignature(entity);
            if (
                !"SUCCESS".equalsIgnoreCase(String.valueOf(entity.getLastValidationStatus())) ||
                !StringUtils.hasText(entity.getLastValidationSignature()) ||
                !Objects.equals(entity.getLastValidationSignature(), currentSignature)
            ) {
                throw new IndicatorConflictException(label + "当前配置尚未通过有效校验: " + safeIndicatorCode(entity));
            }
            validated.add(indicatorId);
        } finally {
            visiting.remove(indicatorId);
        }
    }

    private String safeIndicatorCode(GovIndicatorDefinition entity) {
        return entity != null && StringUtils.hasText(entity.getCode()) ? entity.getCode().trim() : "UNKNOWN";
    }

    private void snapshot(GovIndicatorDefinition indicator, String version, String status, String changeSummary, Instant releasedAt) {
        if (indicator == null || indicator.getId() == null || !StringUtils.hasText(version)) {
            return;
        }
        String normalizedVersion = version.trim();
        GovIndicatorVersion snapshot = versionRepository
            .findByIndicatorAndVersion(indicator, normalizedVersion)
            .orElseGet(GovIndicatorVersion::new);
        snapshot.setIndicator(indicator);
        snapshot.setVersion(normalizedVersion);
        snapshot.setStatus(normalizeStatus(status, STATUS_DRAFT));
        snapshot.setChangeSummary(StringUtils.hasText(changeSummary) ? changeSummary.trim() : null);
        snapshot.setReleasedAt(releasedAt);
        snapshot.setSnapshotJson(serializeSnapshot(indicator));
        try {
            versionRepository.save(snapshot);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to persist indicator snapshot", ex);
        }
    }

    private String serializeSnapshot(GovIndicatorDefinition indicator) {
        try {
            IndicatorDto dto = IndicatorMapper.toDto(indicator);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize indicator snapshot", e);
        }
    }

    private SnapshotRef resolveSnapshot(
        GovIndicatorDefinition entity,
        IndicatorDto current,
        String version,
        boolean defaultLeft
    ) {
        String normalized = StringUtils.hasText(version) ? version.trim() : null;
        if (!StringUtils.hasText(normalized)) {
            normalized = defaultLeft ? "CURRENT" : String.valueOf(entity.getVersion());
        }
        if ("CURRENT".equalsIgnoreCase(normalized)) {
            return new SnapshotRef("CURRENT", toMap(current));
        }
        final String lookupVersion = normalized;
        GovIndicatorVersion snap = versionRepository
            .findByIndicatorAndVersion(entity, lookupVersion)
            .orElseThrow(() -> new IndicatorNotFoundException("版本不存在: " + lookupVersion));
        IndicatorDto snapshotDto = parseSnapshot(snap.getSnapshotJson());
        if (snapshotDto == null) {
            throw new IndicatorConflictException("版本快照不可读: " + lookupVersion);
        }
        return new SnapshotRef(lookupVersion, toMap(snapshotDto));
    }

    private List<Map<String, Object>> diffSnapshots(Map<String, Object> left, Map<String, Object> right) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        diffField(diffs, "name", "指标名称", left, right, false);
        diffField(diffs, "code", "指标编码", left, right, false);
        diffField(diffs, "category", "分类", left, right, false);
        diffField(diffs, "definition", "定义", left, right, false);
        diffField(diffs, "datasetId", "数据集", left, right, true);
        diffField(diffs, "expressionSql", "计算SQL", left, right, true);
        diffField(diffs, "dataLevel", "数据密级", left, right, true);
        diffField(diffs, "tags", "标签", left, right, false);
        diffField(diffs, "owner", "负责人", left, right, false);
        diffField(diffs, "ownerDept", "所属部门", left, right, false);
        diffField(diffs, "status", "状态", left, right, false);
        return diffs;
    }

    private void diffField(
        List<Map<String, Object>> diffs,
        String field,
        String label,
        Map<String, Object> left,
        Map<String, Object> right,
        boolean blocker
    ) {
        String before = normalizeDiffValue(left != null ? left.get(field) : null);
        String after = normalizeDiffValue(right != null ? right.get(field) : null);
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("label", label);
        row.put("before", before);
        row.put("after", after);
        row.put("severity", blocker ? "BLOCKER" : "INFO");
        diffs.add(row);
    }

    private String normalizeDiffValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private IndicatorDto parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, IndicatorDto.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> toMap(IndicatorDto dto) {
        if (dto == null) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(dto, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void applySnapshotToEntity(GovIndicatorDefinition entity, IndicatorDto snapshot) {
        if (entity == null || snapshot == null) {
            return;
        }
        entity.setCode(snapshot.getCode());
        entity.setName(snapshot.getName());
        entity.setCategory(snapshot.getCategory());
        entity.setDefinition(snapshot.getDefinition());
        entity.setExpressionSql(snapshot.getExpressionSql());
        entity.setDatasetId(snapshot.getDatasetId());
        entity.setOwner(snapshot.getOwner());
        entity.setOwnerDept(snapshot.getOwnerDept());
        entity.setDataLevel(snapshot.getDataLevel());
        entity.setTags(snapshot.getTags());
        // 计算定义
        entity.setAggregationType(snapshot.getAggregationType());
        entity.setMeasureField(snapshot.getMeasureField());
        entity.setNumeratorExpression(snapshot.getNumeratorExpression());
        entity.setDenominatorExpression(snapshot.getDenominatorExpression());
        entity.setStaticFilter(snapshot.getStaticFilter());
        entity.setDynamicFilterConfig(snapshot.getDynamicFilterConfig());
        entity.setIsDerived(snapshot.getIsDerived());
        entity.setDependencyIndicators(snapshot.getDependencyIndicators());
        entity.setWindowFunction(snapshot.getWindowFunction());
        // 维度与粒度
        entity.setDimensionFields(snapshot.getDimensionFields());
        entity.setDateColumn(snapshot.getDateColumn());
        entity.setTimeGrain(snapshot.getTimeGrain());
        entity.setGranularity(snapshot.getGranularity());
        // 数据绑定
        entity.setSourceTable(snapshot.getSourceTable());
        entity.setJoinConfig(snapshot.getJoinConfig());
        entity.setSourceLayer(snapshot.getSourceLayer());
        entity.setTargetLayer(snapshot.getTargetLayer());
        entity.setTargetModelName(snapshot.getTargetModelName());
        // 业务属性
        entity.setUnit(snapshot.getUnit());
        entity.setPrecisionScale(snapshot.getPrecisionScale());
        entity.setThresholdMin(snapshot.getThresholdMin());
        entity.setThresholdMax(snapshot.getThresholdMax());
        entity.setDirection(snapshot.getDirection());
        entity.setBusinessOwner(snapshot.getBusinessOwner());
        entity.setDataPrivacy(snapshot.getDataPrivacy());
        // LLM 预留
        entity.setLlmGenerated(snapshot.getLlmGenerated());
        entity.setLlmConfidence(snapshot.getLlmConfidence());
        entity.setLlmSourceRef(snapshot.getLlmSourceRef());
        entity.setHumanVerified(snapshot.getHumanVerified());
        // 管理
        entity.setDomain(snapshot.getDomain());
        entity.setIcon(snapshot.getIcon());
        entity.setDisplayOrder(snapshot.getDisplayOrder());
        entity.setTemplateId(snapshot.getTemplateId());
    }

    private void clearValidation(GovIndicatorDefinition entity) {
        if (entity == null) {
            return;
        }
        entity.setLastValidationStatus(null);
        entity.setLastValidationMessage(null);
        entity.setLastValidatedAt(null);
        entity.setLastValidationSignature(null);
    }

    private void requireStableCode(GovIndicatorDefinition entity, IndicatorUpsertRequest request) {
        if (entity == null || request == null) {
            throw new IndicatorRequestException("Invalid indicator payload");
        }
        String current = StringUtils.hasText(entity.getCode()) ? entity.getCode().trim() : "";
        String requested = StringUtils.hasText(request.getCode()) ? request.getCode().trim() : "";
        if (!current.equals(requested)) {
            throw new IndicatorRequestException("指标编码创建后不可修改");
        }
    }

    private void requireMatchingLastModifiedDate(
        GovIndicatorDefinition entity,
        IndicatorUpsertRequest request
    ) {
        Instant expected = request != null ? request.getExpectedLastModifiedDate() : null;
        Instant actual = entity != null ? entity.getLastModifiedDate() : null;
        if (expected == null || actual == null || !actual.equals(expected)) {
            throw new OptimisticLockingFailureException("指标已被其他用户修改，请刷新后重试");
        }
    }

    private void requireMutationAccess(GovIndicatorDefinition entity, String trustedActiveDept) {
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Access denied for indicator mutation"
            );
        }
    }

    private void syncIndicatorDependencyReferences(GovIndicatorDefinition indicator) {
        if (indicator == null || indicator.getId() == null) {
            return;
        }
        Map<UUID, GovIndicatorDefinition> desired = new LinkedHashMap<>();
        for (String code : parseDependencyCodes(indicator.getDependencyIndicators())) {
            GovIndicatorDefinition dependency = repository
                .findFirstByCodeIgnoreCase(code)
                .orElseThrow(() -> new IndicatorRequestException("依赖指标不存在: " + code));
            if (dependency.getId() == null) {
                throw new IndicatorRequestException("依赖指标缺少稳定标识: " + code);
            }
            if (indicator.getId().equals(dependency.getId())) {
                throw new IndicatorRequestException("指标不能依赖自身: " + code);
            }
            desired.putIfAbsent(dependency.getId(), dependency);
        }

        List<GovIndicatorReference> existing = referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator);
        if (existing == null) {
            existing = List.of();
        }
        Set<GovIndicatorReference> retained = new LinkedHashSet<>();
        for (GovIndicatorDefinition dependency : desired.values()) {
            String targetId = dependency.getId().toString();
            GovIndicatorReference matching = existing
                .stream()
                .filter(ref -> "INDICATOR".equalsIgnoreCase(ref.getRefType()))
                .filter(ref -> targetId.equalsIgnoreCase(String.valueOf(ref.getRefTarget())))
                .filter(ref -> !retained.contains(ref))
                .findFirst()
                .orElse(null);
            if (matching != null) {
                retained.add(matching);
                continue;
            }
            GovIndicatorReference created = new GovIndicatorReference();
            created.setIndicator(indicator);
            created.setRefType("INDICATOR");
            created.setRefTarget(targetId);
            created.setRefName(dependency.getName());
            created.setNotes("由 dependencyIndicators 自动同步");
            referenceRepository.save(created);
        }

        for (GovIndicatorReference reference : existing) {
            if ("INDICATOR".equalsIgnoreCase(reference.getRefType()) && !retained.contains(reference)) {
                referenceRepository.delete(reference);
            }
        }
    }

    private List<String> parseDependencyCodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            LinkedHashMap<String, String> unique = new LinkedHashMap<>();
            for (Object item : raw) {
                if (!(item instanceof String code) || !StringUtils.hasText(code)) {
                    throw new IndicatorRequestException("依赖指标编码必须是非空字符串");
                }
                String value = code.trim();
                if (!CODE_PATTERN.matcher(value).matches()) {
                    throw new IndicatorRequestException("依赖指标编码不合法: " + value);
                }
                unique.putIfAbsent(value.toUpperCase(Locale.ROOT), value);
            }
            return List.copyOf(unique.values());
        } catch (IndicatorRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IndicatorRequestException("dependencyIndicators 不是合法的指标编码数组", ex);
        }
    }

    private IndicatorDerivationValidationResult enforceDependencyAccess(
        IndicatorDerivationValidationResult validation,
        String activeDept
    ) {
        if (validation == null) {
            return new IndicatorDerivationValidationResult(
                false,
                null,
                List.of(new IndicatorDerivationValidationResult.Issue("DERIVATION_VALIDATION_MISSING", "派生校验结果缺失")),
                List.of()
            );
        }
        List<IndicatorDerivationValidationResult.Issue> issues = new ArrayList<>(validation.issues());
        for (String code : validation.dependencyCodes()) {
            GovIndicatorDefinition dependency = repository.findFirstByCodeIgnoreCase(code).orElse(null);
            if (dependency != null && (!deptAllowed(dependency, activeDept) || !levelAllowed(dependency))) {
                issues.add(
                    new IndicatorDerivationValidationResult.Issue(
                        "DERIVATION_DEPENDENCY_ACCESS_DENIED",
                        "当前组织或密级上下文无权使用依赖指标: " + code
                    )
                );
            }
        }
        boolean valid = validation.valid() && issues.size() == validation.issues().size();
        return new IndicatorDerivationValidationResult(
            valid,
            valid ? validation.compiledExpression() : null,
            issues,
            validation.dependencyCodes()
        );
    }

    private void ensureNoPublishedDerivedDependents(GovIndicatorDefinition target) {
        if (target == null || !StringUtils.hasText(target.getCode())) {
            return;
        }
        String targetCode = target.getCode().trim();
        List<GovIndicatorDefinition> derivedIndicators = repository.findByIsDerivedTrue();
        if (derivedIndicators == null) {
            return;
        }
        for (GovIndicatorDefinition candidate : derivedIndicators) {
            if (
                candidate == null ||
                candidate.getId() == null ||
                candidate.getId().equals(target.getId()) ||
                !STATUS_PUBLISHED.equals(normalizeStatus(candidate.getStatus(), STATUS_DRAFT))
            ) {
                continue;
            }
            boolean dependsOnTarget = parseDependencyCodes(candidate.getDependencyIndicators())
                .stream()
                .anyMatch(code -> targetCode.equalsIgnoreCase(code));
            if (dependsOnTarget) {
                throw new IndicatorConflictException(
                    "指标被已发布派生指标 " + candidate.getCode() + " 引用，不能归档或删除"
                );
            }
        }
    }

    private String nextVersion(GovIndicatorDefinition entity) {
        int max = 0;
        List<GovIndicatorVersion> all = versionRepository.findByIndicatorOrderByCreatedDateDesc(entity);
        for (GovIndicatorVersion item : all) {
            if (item == null || !StringUtils.hasText(item.getVersion())) {
                continue;
            }
            Matcher matcher = VERSION_PATTERN.matcher(item.getVersion().trim());
            if (matcher.matches()) {
                try {
                    int n = Integer.parseInt(matcher.group(1));
                    if (n > max) {
                        max = n;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (max == 0 && StringUtils.hasText(entity.getVersion())) {
            Matcher matcher = VERSION_PATTERN.matcher(entity.getVersion().trim());
            if (matcher.matches()) {
                try {
                    max = Integer.parseInt(matcher.group(1));
                } catch (Exception ignored) {}
            }
        }
        return "v" + (max + 1);
    }

    private record SnapshotRef(String version, Map<String, Object> payload) {}

    private void ensurePublishReady(GovIndicatorDefinition entity, String activeDept) {
        if (entity == null) {
            throw new IndicatorRequestException("Invalid indicator");
        }
        if (Boolean.TRUE.equals(entity.getIsDerived())) {
            IndicatorDerivationValidationResult result = validateDerivation(entity.getId(), activeDept);
            if (!result.valid()) {
                String message = result
                    .issues()
                    .stream()
                    .map(IndicatorDerivationValidationResult.Issue::message)
                    .collect(java.util.stream.Collectors.joining("；"));
                throw new IndicatorConflictException("发布校验未通过：" + safeMessage(message));
            }
            return;
        }
        if (!StringUtils.hasText(entity.getDatasetId()) || !StringUtils.hasText(entity.getExpressionSql())) {
            throw new IndicatorConflictException("发布前需配置数据集与计算SQL");
        }
        String signature = computeSignature(entity);
        if (!"SUCCESS".equalsIgnoreCase(String.valueOf(entity.getLastValidationStatus()))
            || !StringUtils.hasText(entity.getLastValidationSignature())
            || !entity.getLastValidationSignature().equalsIgnoreCase(signature)) {
            IndicatorValidationResultDto result = validateComputeRule(entity.getId(), activeDept);
            if (!"SUCCESS".equalsIgnoreCase(result.getStatus())) {
                throw new IndicatorConflictException("发布校验未通过：" + safeMessage(result.getMessage()));
            }
        }
    }

    private String wrapWithLimit(String sql, int limit) {
        String candidate = sql == null ? "" : sql.trim();
        while (candidate.endsWith(";")) {
            candidate = candidate.substring(0, candidate.length() - 1).trim();
        }
        if (candidate.isEmpty()) {
            return candidate;
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return "SELECT * FROM (" + candidate + ") indicator_sql LIMIT " + safeLimit;
    }

    private String computeSignature(GovIndicatorDefinition entity) {
        try {
            return IndicatorValidationSignature.compute(entity, objectMapper, repository);
        } catch (IllegalArgumentException ex) {
            throw new IndicatorConflictException("指标当前配置不合法：" + safeMessage(ex.getMessage()));
        }
    }

    private IndicatorValidationResultDto persistValidation(
        GovIndicatorDefinition entity,
        Instant now,
        String signature,
        IndicatorValidationResultDto result,
        String status,
        String message,
        Map<String, Object> payload
    ) {
        entity.setLastValidationStatus(status);
        entity.setLastValidationMessage(message);
        entity.setLastValidatedAt(now);
        entity.setLastValidationSignature(signature);
        repository.save(entity);

        result.setStatus(status);
        result.setMessage(message);
        result.setSignature(signature);
        if (payload != null && result.getHeaders() == null) {
            result.setHeaders(extractHeaders(payload));
        }
        if (payload != null && result.getRowCount() == null) {
            result.setRowCount(numberOrNull(payload.get("rowCount")));
        }
        if (payload != null && result.getDurationMs() == null) {
            result.setDurationMs(numberOrNull(payload.get("durationMs")));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractHeaders(Map<String, Object> queryResult) {
        if (queryResult == null) return List.of();
        Object headers = queryResult.get("headers");
        if (headers instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) out.add(String.valueOf(item));
            }
            out.removeIf(String::isBlank);
            if (!out.isEmpty()) return out;
        }
        Object rows = queryResult.get("rows");
        if (rows instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            for (Object k : map.keySet()) {
                if (k != null) keys.add(String.valueOf(k));
            }
            keys.removeIf(String::isBlank);
            return keys;
        }
        return List.of();
    }

    private Long numberOrNull(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number n) return n.longValue();
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return null;
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) return "";
        String trimmed = message.trim();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) : trimmed;
    }

    String resolveTrustedActiveDept(String requestedActiveDept) {
        String requested = StringUtils.hasText(requestedActiveDept) ? requestedActiveDept.trim() : null;
        String claimed = SecurityUtils.getCurrentUserDept().filter(StringUtils::hasText).map(String::trim).orElse(null);
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return requested != null ? requested : claimed;
        }
        if (requested != null && !sameDepartment(requested, claimed)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        return claimed;
    }

    private boolean sameDepartment(String left, String right) {
        String normalizedLeft = DepartmentUtils.normalize(left);
        String normalizedRight = DepartmentUtils.normalize(right);
        return (
            StringUtils.hasText(normalizedLeft) &&
            StringUtils.hasText(normalizedRight) &&
            normalizedLeft.equalsIgnoreCase(normalizedRight)
        );
    }

    private void applyDefaults(GovIndicatorDefinition entity, String activeDept) {
        if (entity == null) return;
        if (!StringUtils.hasText(entity.getOwner())) {
            String login = SecurityUtils.getCurrentUserLogin().orElse(null);
            String display = SecurityUtils.getCurrentUserDisplayName().orElse(null);
            String formatted = formatOwner(display, login);
            if (StringUtils.hasText(formatted)) {
                entity.setOwner(formatted);
            }
        }
        if (!StringUtils.hasText(entity.getOwnerDept()) && StringUtils.hasText(activeDept)) {
            entity.setOwnerDept(activeDept.trim());
        }
        entity.setStatus(normalizeStatus(entity.getStatus(), STATUS_DRAFT));
        if (!StringUtils.hasText(entity.getVersion())) {
            entity.setVersion("v1");
        }
        entity.setDataLevel(normalizeDataLevel(entity.getDataLevel()));
    }

    private void syncCodeAssetGrant(GovIndicatorDefinition entity) {
        if (codeAssetGrantWriter == null || entity == null || entity.getId() == null) {
            return;
        }
        String naturalKey = StringUtils.hasText(entity.getCode()) ? entity.getCode().trim() : entity.getId().toString();
        CatalogAssetIdentity identity = new CatalogAssetIdentity(
            CatalogAssetType.GOV_INDICATOR,
            CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "default", naturalKey),
            entity.getId().toString(),
            "gov-indicator:" + naturalKey
        );
        codeAssetGrantWriter.upsertCodeAsset(
            identity,
            entity.getOwnerDept(),
            SecurityUtils.getCurrentUserLogin().orElse("dts-platform"),
            entity.getDataLevel(),
            CodeAssetLifecycleMapper.fromIndicatorStatus(entity.getStatus())
        );
    }

    private String formatOwner(String displayName, String username) {
        String display = displayName == null ? "" : displayName.trim();
        String user = username == null ? "" : username.trim();
        if (display.isEmpty() && user.isEmpty()) return null;
        if (display.isEmpty()) return user;
        if (user.isEmpty()) return display;
        if (display.equalsIgnoreCase(user)) return display;
        return display + " (" + user + ")";
    }

    private String normalizeDataLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DataLevel.DATA_INTERNAL.name();
        }
        DataLevel normalized = DataLevel.normalize(raw);
        if (normalized != null) return normalized.name();
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("DATA_")) return upper;
        return DataLevel.DATA_INTERNAL.name();
    }

    private boolean keywordMatches(GovIndicatorDefinition entity, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), kw) || contains(entity.getCode(), kw) || contains(entity.getCategory(), kw) || contains(entity.getOwner(), kw);
    }

    private boolean domainMatches(GovIndicatorDefinition entity, String domain) {
        if (!StringUtils.hasText(domain)) return true;
        return domain.trim().equalsIgnoreCase(entity.getDomain() != null ? entity.getDomain().trim() : "");
    }

    private boolean categoryMatches(GovIndicatorDefinition entity, String category) {
        if (!StringUtils.hasText(category)) return true;
        return category.trim().equalsIgnoreCase(entity.getCategory() != null ? entity.getCategory().trim() : "");
    }

    private boolean derivedMatches(GovIndicatorDefinition indicator, Boolean derived) {
        if (derived == null) return true;
        if (derived) {
            return Boolean.TRUE.equals(indicator.getIsDerived());
        }
        return !Boolean.TRUE.equals(indicator.getIsDerived());
    }

    private void validateDomainCode(String domain) {
        if (StringUtils.hasText(domain) && !catalogDomainRepository.existsByCodeIgnoreCase(domain)) {
            throw new IndicatorRequestException("域编码不存在: " + domain);
        }
    }

    private boolean statusMatches(GovIndicatorDefinition entity, String status) {
        if (!StringUtils.hasText(status)) return true;
        String expected = normalizeStatus(status, "");
        String actual = normalizeStatus(entity.getStatus(), "");
        return expected.equals(actual);
    }

    private String normalizeStatus(String status, String defaultValue) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!StringUtils.hasText(normalized)) {
            return defaultValue;
        }
        if (STATUS_DEPRECATED.equals(normalized)) {
            return STATUS_ARCHIVED;
        }
        return normalized;
    }

    private boolean contains(String value, String keywordLower) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keywordLower)) return false;
        return value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private boolean deptAllowed(GovIndicatorDefinition entity, String activeDept) {
        if (entity == null) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        if (!StringUtils.hasText(activeDept)) {
            // No context selected: only expose global/root-owned indicators.
            return isGlobalOrRoot(entity.getOwnerDept());
        }
        if (isGlobalOrRoot(entity.getOwnerDept())) {
            return true;
        }
        return sameDepartment(entity.getOwnerDept(), activeDept);
    }

    private boolean isGlobalOrRoot(String ownerDept) {
        if (!StringUtils.hasText(ownerDept)) {
            return true;
        }
        try {
            return organizationVisibilityService.isRoot(ownerDept);
        } catch (Exception ignored) {
            return "ROOT".equalsIgnoreCase(ownerDept.trim());
        }
    }

    private boolean levelAllowed(GovIndicatorDefinition entity) {
        if (entity == null) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        DataLevel resource = DataLevel.normalize(entity.getDataLevel());
        if (resource == null) {
            resource = DataLevel.DATA_INTERNAL;
        }
        int maxRank = accessChecker.resolveHighestDataLevel().rank();
        return resource.rank() <= maxRank;
    }

    private void validateUpsert(GovIndicatorDefinition entity, UUID existingId, String activeDept) {
        if (entity == null) throw new IndicatorRequestException("Invalid indicator payload");
        if (!StringUtils.hasText(entity.getCode())) {
            throw new IndicatorRequestException("指标编码不能为空");
        }
        if (!CODE_PATTERN.matcher(entity.getCode().trim()).matches()) {
            throw new IndicatorRequestException("指标编码仅允许字母、数字与下划线，且不能以数字开头");
        }
        if (!StringUtils.hasText(entity.getName())) {
            throw new IndicatorRequestException("指标名称不能为空");
        }
        if (StringUtils.hasText(entity.getDatasetId())) {
            try {
                UUID.fromString(entity.getDatasetId().trim());
            } catch (IllegalArgumentException ex) {
                throw new IndicatorRequestException("数据集ID格式错误", ex);
            }
        }
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            if (StringUtils.hasText(activeDept) && StringUtils.hasText(entity.getOwnerDept())) {
                if (!isGlobalOrRoot(entity.getOwnerDept()) && !sameDepartment(entity.getOwnerDept(), activeDept)) {
                    throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
                }
            }
        }
        repository
            .findFirstByCodeIgnoreCase(entity.getCode().trim())
            .ifPresent(existing -> {
                if (existingId == null || existing.getId() == null || !existing.getId().equals(existingId)) {
                    throw new IndicatorRequestException("指标编码已存在");
                }
            });
        try {
            IndicatorValidationSignature.compute(entity, objectMapper, repository);
        } catch (IllegalArgumentException ex) {
            throw new IndicatorRequestException("指标配置不合法：" + safeMessage(ex.getMessage()), ex);
        }
    }
}
