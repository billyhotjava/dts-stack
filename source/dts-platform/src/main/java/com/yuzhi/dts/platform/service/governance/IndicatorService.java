package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    private final GovIndicatorDefinitionRepository repository;
    private final GovIndicatorVersionRepository versionRepository;
    private final GovIndicatorReferenceRepository referenceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final QueryGateway queryGateway;
    private final SecuritySqlRewriter securitySqlRewriter;
    private final ObjectMapper objectMapper;

    public IndicatorService(
        GovIndicatorDefinitionRepository repository,
        GovIndicatorVersionRepository versionRepository,
        GovIndicatorReferenceRepository referenceRepository,
        CatalogDatasetRepository datasetRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        QueryGateway queryGateway,
        SecuritySqlRewriter securitySqlRewriter,
        ObjectMapper objectMapper
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
    }

    @Transactional(readOnly = true)
    public Page<IndicatorDto> list(String keyword, String status, Pageable pageable, String activeDept) {
        List<GovIndicatorDefinition> all = repository.findAll();
        List<GovIndicatorDefinition> filtered = new ArrayList<>();
        for (GovIndicatorDefinition indicator : all) {
            if (indicator == null) continue;
            if (!keywordMatches(indicator, keyword)) continue;
            if (!statusMatches(indicator, status)) continue;
            if (!deptAllowed(indicator, activeDept)) continue;
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

    @Transactional(readOnly = true)
    public IndicatorDto get(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        return IndicatorMapper.toDto(entity);
    }

    public IndicatorDto create(IndicatorUpsertRequest request, String activeDept) {
        GovIndicatorDefinition entity = new GovIndicatorDefinition();
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, activeDept);
        validateUpsert(entity, null, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        snapshot(saved, saved.getVersion(), "DRAFT", saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto update(UUID id, IndicatorUpsertRequest request, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, activeDept);
        validateUpsert(entity, id, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        snapshot(saved, saved.getVersion(), StringUtils.hasText(saved.getStatus()) ? saved.getStatus() : "DRAFT", saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto publish(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        ensurePublishReady(entity, activeDept);
        entity.setStatus(STATUS_PUBLISHED);
        applyDefaults(entity, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        snapshot(saved, saved.getVersion(), STATUS_PUBLISHED, saved.getVersionNotes(), Instant.now());
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorValidationResultDto validateComputeRule(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
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
        String effDept = resolveActiveDeptContext(activeDept);
        boolean read = accessChecker.canRead(dataset);
        boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
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
            effectiveSql = securitySqlRewriter.guard(limitedSql, dataset, activeDept);
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

    /**
     * 预览指标计算结果（不会写入 last_validation_* 字段）。
     * - 仅用于配置人员调试 SQL；真正发布仍需走 validate + publish。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previewComputeRule(UUID id, int limit, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
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
        String effDept = resolveActiveDeptContext(activeDept);
        boolean read = accessChecker.canRead(dataset);
        boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
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
            effectiveSql = securitySqlRewriter.guard(limitedSql, dataset, activeDept);
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
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        entity.setStatus(STATUS_ARCHIVED);
        applyDefaults(entity, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        snapshot(saved, saved.getVersion(), STATUS_ARCHIVED, saved.getVersionNotes(), null);
        return IndicatorMapper.toDto(saved);
    }

    public void delete(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        referenceRepository.deleteByIndicator(entity);
        versionRepository.deleteByIndicator(entity);
        repository.flush();
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<IndicatorVersionDto> listVersions(UUID indicatorId, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(indicatorId).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
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
        GovIndicatorDefinition entity = repository.findById(indicatorId).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        GovIndicatorVersion snap = versionRepository
            .findByIndicatorAndVersion(entity, version)
            .orElseThrow(() -> new IllegalArgumentException("指标版本不存在"));
        return IndicatorMapper.toDto(snap);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(UUID indicatorId, String leftVersion, String rightVersion, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(indicatorId).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
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
        GovIndicatorDefinition entity = repository.findById(indicatorId).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        if (!StringUtils.hasText(sourceVersion)) {
            throw new IllegalArgumentException("回滚版本不能为空");
        }
        GovIndicatorVersion source = versionRepository
            .findByIndicatorAndVersion(entity, sourceVersion.trim())
            .orElseThrow(() -> new IllegalArgumentException("目标回滚版本不存在"));

        IndicatorDto snapshotDto = parseSnapshot(source.getSnapshotJson());
        if (snapshotDto == null) {
            throw new IllegalArgumentException("目标版本快照不可读，无法回滚");
        }

        applySnapshotToEntity(entity, snapshotDto);
        String rollbackVersion = nextVersion(entity);
        entity.setVersion(rollbackVersion);
        String reasonText = StringUtils.hasText(reason) ? reason.trim() : "版本回滚";
        entity.setVersionNotes(reasonText + "（来源版本: " + sourceVersion.trim() + "）");
        entity.setStatus(STATUS_DRAFT);
        clearValidation(entity);
        repository.save(entity);

        Instant releasedAt = null;
        if (publishAfterRollback) {
            ensurePublishReady(entity, activeDept);
            entity.setStatus(STATUS_PUBLISHED);
            releasedAt = Instant.now();
            repository.save(entity);
        }
        snapshot(entity, rollbackVersion, entity.getStatus(), entity.getVersionNotes(), releasedAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indicator", IndicatorMapper.toDto(entity));
        result.put("rollbackFromVersion", sourceVersion.trim());
        result.put("rollbackToVersion", rollbackVersion);
        result.put("published", publishAfterRollback);
        return result;
    }

    private void snapshot(GovIndicatorDefinition indicator, String version, String status, String changeSummary, Instant releasedAt) {
        if (indicator == null || indicator.getId() == null || !StringUtils.hasText(version)) {
            return;
        }
        try {
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
            versionRepository.save(snapshot);
        } catch (Exception ignored) {}
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
            .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + lookupVersion));
        IndicatorDto snapshotDto = parseSnapshot(snap.getSnapshotJson());
        if (snapshotDto == null) {
            throw new IllegalArgumentException("版本快照不可读: " + lookupVersion);
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
            throw new IllegalArgumentException("Invalid indicator");
        }
        if (!StringUtils.hasText(entity.getDatasetId()) || !StringUtils.hasText(entity.getExpressionSql())) {
            throw new IllegalArgumentException("发布前需配置数据集与计算SQL");
        }
        String signature = computeSignature(entity);
        if (!"SUCCESS".equalsIgnoreCase(String.valueOf(entity.getLastValidationStatus()))
            || !StringUtils.hasText(entity.getLastValidationSignature())
            || !entity.getLastValidationSignature().equalsIgnoreCase(signature)) {
            IndicatorValidationResultDto result = validateComputeRule(entity.getId(), activeDept);
            if (!"SUCCESS".equalsIgnoreCase(result.getStatus())) {
                throw new IllegalArgumentException("发布校验未通过：" + safeMessage(result.getMessage()));
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
        if (entity == null) return null;
        String datasetId = String.valueOf(entity.getDatasetId() == null ? "" : entity.getDatasetId()).trim();
        String sql = String.valueOf(entity.getExpressionSql() == null ? "" : entity.getExpressionSql()).trim();
        String input = datasetId + "\n" + sql;
        return DigestUtils.sha256Hex(input);
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

    private String resolveActiveDeptContext(String activeDept) {
        if (StringUtils.hasText(activeDept)) {
            return activeDept.trim();
        }
        String fromClaim = claim("dept_code");
        if (!StringUtils.hasText(fromClaim)) fromClaim = claim("deptCode");
        if (!StringUtils.hasText(fromClaim)) fromClaim = claim("department");
        if (!StringUtils.hasText(fromClaim)) fromClaim = claim("org_code");
        if (!StringUtils.hasText(fromClaim)) fromClaim = claim("orgCode");
        return StringUtils.hasText(fromClaim) ? fromClaim.trim() : "";
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            if (length > 0) {
                Object first = java.lang.reflect.Array.get(raw, 0);
                if (first != null) return first;
            }
        }
        return raw;
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
        return DepartmentUtils.matches(entity.getOwnerDept(), activeDept);
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
        if (entity == null) throw new IllegalArgumentException("Invalid indicator payload");
        if (!StringUtils.hasText(entity.getCode())) {
            throw new IllegalArgumentException("指标编码不能为空");
        }
        if (!StringUtils.hasText(entity.getName())) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        if (StringUtils.hasText(entity.getDatasetId())) {
            try {
                UUID.fromString(entity.getDatasetId().trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("数据集ID格式错误");
            }
        }
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            if (StringUtils.hasText(activeDept) && StringUtils.hasText(entity.getOwnerDept())) {
                if (!isGlobalOrRoot(entity.getOwnerDept()) && !DepartmentUtils.matches(entity.getOwnerDept(), activeDept)) {
                    throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
                }
            }
        }
        repository
            .findFirstByCodeIgnoreCase(entity.getCode().trim())
            .ifPresent(existing -> {
                if (existingId == null || existing.getId() == null || !existing.getId().equals(existingId)) {
                    throw new IllegalArgumentException("指标编码已存在");
                }
            });
    }
}
