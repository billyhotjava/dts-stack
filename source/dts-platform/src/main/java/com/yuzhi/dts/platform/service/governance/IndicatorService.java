package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    private final GovIndicatorDefinitionRepository repository;
    private final CatalogDatasetRepository datasetRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final QueryGateway queryGateway;
    private final SecuritySqlRewriter securitySqlRewriter;

    public IndicatorService(
        GovIndicatorDefinitionRepository repository,
        CatalogDatasetRepository datasetRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        QueryGateway queryGateway,
        SecuritySqlRewriter securitySqlRewriter
    ) {
        this.repository = repository;
        this.datasetRepository = datasetRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.queryGateway = queryGateway;
        this.securitySqlRewriter = securitySqlRewriter;
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
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto update(UUID id, IndicatorUpsertRequest request, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, activeDept);
        validateUpsert(entity, id, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto publish(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        ensurePublishReady(entity, activeDept);
        entity.setStatus("PUBLISHED");
        applyDefaults(entity, activeDept);
        return IndicatorMapper.toDto(repository.save(entity));
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
            effectiveSql = securitySqlRewriter.guard(limitedSql, dataset);
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

    public IndicatorDto archive(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        entity.setStatus("DEPRECATED");
        applyDefaults(entity, activeDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public void delete(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        repository.delete(entity);
    }

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
        if (!StringUtils.hasText(entity.getOwner()) && SecurityUtils.getCurrentUserDisplayName().isPresent()) {
            entity.setOwner(SecurityUtils.getCurrentUserDisplayName().orElse(null));
        }
        if (!StringUtils.hasText(entity.getOwnerDept()) && StringUtils.hasText(activeDept)) {
            entity.setOwnerDept(activeDept.trim());
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("DRAFT");
        }
        if (!StringUtils.hasText(entity.getVersion())) {
            entity.setVersion("v1");
        }
        entity.setDataLevel(normalizeDataLevel(entity.getDataLevel()));
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
        return contains(entity.getName(), kw) || contains(entity.getCode(), kw) || contains(entity.getCategory(), kw);
    }

    private boolean statusMatches(GovIndicatorDefinition entity, String status) {
        if (!StringUtils.hasText(status)) return true;
        String expected = status.trim().toUpperCase(Locale.ROOT);
        String actual = entity.getStatus() == null ? "" : entity.getStatus().trim().toUpperCase(Locale.ROOT);
        return expected.equals(actual);
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
