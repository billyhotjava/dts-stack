package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetSecurityMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import com.yuzhi.dts.platform.service.security.DatasetSqlBuilder;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/security")
@Transactional(readOnly = true)
public class SecurityPolicyResource {

    private static final String SECURITY_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepository;
    private final CatalogDatasetGrantRepository datasetGrantRepository;
    private final CatalogRowFilterRuleRepository rowFilterRuleRepository;
    private final CatalogMaskingRuleRepository maskingRuleRepository;
    private final AccessChecker accessChecker;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final DatasetSqlBuilder datasetSqlBuilder;
    private final AuditService auditService;

    public SecurityPolicyResource(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepository,
        CatalogDatasetGrantRepository datasetGrantRepository,
        CatalogRowFilterRuleRepository rowFilterRuleRepository,
        CatalogMaskingRuleRepository maskingRuleRepository,
        AccessChecker accessChecker,
        DatasetSecurityMetadataResolver metadataResolver,
        DatasetSqlBuilder datasetSqlBuilder,
        AuditService auditService
    ) {
        this.datasetRepository = datasetRepository;
        this.datasetSecurityMappingRepository = datasetSecurityMappingRepository;
        this.datasetGrantRepository = datasetGrantRepository;
        this.rowFilterRuleRepository = rowFilterRuleRepository;
        this.maskingRuleRepository = maskingRuleRepository;
        this.accessChecker = accessChecker;
        this.metadataResolver = metadataResolver;
        this.datasetSqlBuilder = datasetSqlBuilder;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestHeader(value = "X-Active-Dept", required = false) String activeDept) {
        String effDept = resolveActiveDept(activeDept);
        boolean enforceDeptFilter = !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);

        List<CatalogDataset> visible = datasetRepository
            .findAll()
            .stream()
            .filter(accessChecker::canRead)
            .filter(ds -> !enforceDeptFilter || accessChecker.departmentAllowed(ds, effDept))
            .toList();

        Map<String, Long> byClassification = new LinkedHashMap<>();
        int missingDataLevel = 0;
        int missingDept = 0;
        for (CatalogDataset ds : visible) {
            String classification = StringUtils.hasText(ds.getClassification()) ? ds.getClassification().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
            byClassification.put(classification, byClassification.getOrDefault(classification, 0L) + 1L);
            if (metadataResolver.findDataLevelColumn(ds).isEmpty()) {
                missingDataLevel++;
            }
            if (enforceDeptFilter && metadataResolver.findDeptColumn(ds).isEmpty()) {
                missingDept++;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activeDept", effDept);
        payload.put("deptFilterEnforced", enforceDeptFilter);
        payload.put("personnelMaxDataLevel", accessChecker.resolveHighestDataLevel().name());
        payload.put("visibleDatasetsTotal", visible.size());
        payload.put("visibleDatasetsByClassification", byClassification);
        payload.put("missingDataLevelColumnCount", missingDataLevel);
        payload.put("missingDeptColumnCount", missingDept);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看安全策略概览");
        auditPayload.put("activeDept", effDept);
        auditPayload.put("deptFilterEnforced", enforceDeptFilter);
        auditPayload.put("visibleDatasetsTotal", visible.size());
        auditPayload.put("missingDataLevelColumnCount", missingDataLevel);
        auditPayload.put("missingDeptColumnCount", missingDept);
        auditService.auditAction("SECURITY_POLICY_OVERVIEW_VIEW", AuditStage.SUCCESS, "overview", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/datasets/{id}/policy-explain")
    public ApiResponse<Map<String, Object>> explainDatasetPolicy(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        String effDept = resolveActiveDept(activeDept);
        boolean datasetVisible = accessChecker.canRead(dataset) && accessChecker.departmentAllowed(dataset, effDept);
        boolean enforceDeptFilter = !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);

        DatasetSecurityMetadataResolver.ResolvedColumn dataLevelCol = metadataResolver.findDataLevelColumnInfo(dataset).orElse(null);
        String deptCol = metadataResolver.findDeptColumn(dataset).orElse(null);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("deptFilterEnforced", enforceDeptFilter);
        policy.put("activeDept", effDept);
        policy.put("personnelMaxDataLevel", accessChecker.resolveHighestDataLevel().name());
        policy.put("allowedDataLevels", accessChecker.resolveAllowedDataLevels().stream().map(DataLevel::name).toList());
        policy.put("dataLevelColumn", dataLevelCol != null ? dataLevelCol.name() : null);
        policy.put("dataLevelColumnDataType", dataLevelCol != null ? dataLevelCol.dataType() : null);
        policy.put("dataLevelColumnNumeric", dataLevelCol != null && dataLevelCol.numeric());
        policy.put("deptColumn", deptCol);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", id.toString());
        result.put("datasetName", dataset.getName());
        result.put("datasetOwnerDept", dataset.getOwnerDept());
        result.put("datasetType", dataset.getType());
        result.put("datasetClassification", dataset.getClassification());
        result.put("datasetVisible", datasetVisible);
        result.put("policy", policy);
        result.put("securityMapping", toSecurityMappingDto(datasetSecurityMappingRepository.findById(id).orElse(null)));

        // Grants / row-filter / masking: used for permission-matrix explanation.
        boolean canViewFullPolicy = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        boolean explicitlyGranted = datasetGrantRepository.existsForDatasetAndUser(id, userId, username);
        result.put("explicitlyGrantedToCurrentUser", explicitlyGranted);
        if (canViewFullPolicy) {
            List<Map<String, Object>> grants = datasetGrantRepository
                .findByDatasetIdOrderByCreatedDateAsc(id)
                .stream()
                .map(this::toGrantDto)
                .toList();
            result.put("grants", grants);
        }

        Set<String> currentAuthorities = currentAuthorities();
        List<Map<String, Object>> rowFilters = rowFilterRuleRepository
            .findByDataset(dataset)
            .stream()
            .map(rule -> toRowFilterDto(rule, currentAuthorities))
            .toList();
        result.put("rowFilterRules", rowFilters);

        List<Map<String, Object>> maskingRules = maskingRuleRepository
            .findByDataset(dataset)
            .stream()
            .map(this::toMaskingDto)
            .toList();
        result.put("maskingRules", maskingRules);

        boolean allowed = datasetVisible;
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (!datasetVisible) {
            allowed = false;
            reasons.add("数据集在当前上下文不可见（无权限或部门不匹配）");
        }
        if (metadataResolver.findDataLevelColumn(dataset).isEmpty()) {
            allowed = false;
            reasons.add("数据集缺少数据密级字段，无法应用行级安全");
        }
        if (enforceDeptFilter && !StringUtils.hasText(deptCol)) {
            allowed = false;
            reasons.add("数据集缺少部门字段，无法应用部门行过滤");
        }

        try {
            String alias = "ds";
            String dataLevelPredicate = datasetSqlBuilder.resolveDataLevelPredicate(dataset, alias).orElse(null);
            String deptPredicate = enforceDeptFilter
                ? datasetSqlBuilder.resolveDeptPredicate(dataset, alias, effDept).orElse(null)
                : null;
            result.put("dataLevelPredicate", dataLevelPredicate);
            result.put("deptPredicate", deptPredicate);
            if (StringUtils.hasText(dataLevelPredicate) && StringUtils.hasText(deptPredicate)) {
                result.put("whereClause", "(" + dataLevelPredicate + ") AND (" + deptPredicate + ")");
            } else if (StringUtils.hasText(dataLevelPredicate)) {
                result.put("whereClause", "(" + dataLevelPredicate + ")");
            } else if (StringUtils.hasText(deptPredicate)) {
                result.put("whereClause", "(" + deptPredicate + ")");
            } else {
                result.put("whereClause", null);
            }
        } catch (SecurityGuardException ex) {
            allowed = false;
            reasons.add(ex.getMessage());
        } catch (Exception ex) {
            allowed = false;
            reasons.add("生成策略失败：" + ex.getMessage());
        }

        result.put("allowed", allowed);
        if (!reasons.isEmpty()) {
            result.put("reasons", reasons);
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集安全策略解释");
        auditPayload.put("datasetVisible", datasetVisible);
        auditPayload.put("allowed", allowed);
        auditPayload.put("activeDept", effDept);
        auditPayload.put("explicitlyGrantedToCurrentUser", explicitlyGranted);
        auditPayload.put("rowFilterRuleCount", rowFilters.size());
        auditPayload.put("maskingRuleCount", maskingRules.size());
        auditService.auditAction("SECURITY_POLICY_EXPLAIN_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(result);
    }

    /**
     * 访问权限矩阵（行/列级权限汇总视图）：
     * - 行级：数据密级 + 部门门禁 + 行过滤规则（可选）
     * - 列级：脱敏规则
     * - 显式授权：dataset grants
     */
    @GetMapping("/matrix")
    @PreAuthorize(SECURITY_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> matrix(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = resolveActiveDept(activeDept);
        boolean enforceDeptFilter = !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<CatalogDataset> candidates = datasetRepository
            .findAll()
            .stream()
            .filter(ds -> normalizedKeyword == null || matchesDatasetKeyword(ds, normalizedKeyword))
            .filter(accessChecker::canRead)
            .filter(ds -> !enforceDeptFilter || accessChecker.departmentAllowed(ds, effDept))
            .limit(safeLimit)
            .toList();

        List<Map<String, Object>> items = candidates.stream().map(ds -> toMatrixRow(ds)).toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看访问权限矩阵");
        auditPayload.put("count", items.size());
        auditPayload.put("limit", safeLimit);
        if (normalizedKeyword != null) {
            auditPayload.put("keyword", keyword.trim());
        }
        auditPayload.put("activeDept", effDept);
        auditService.auditAction("SECURITY_PERMISSION_MATRIX_VIEW", AuditStage.SUCCESS, "matrix", auditPayload);
        return ApiResponses.ok(items);
    }

    @GetMapping("/datasets/{id}/permission-impact")
    @PreAuthorize(SECURITY_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> permissionImpact(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        String effDept = resolveActiveDept(activeDept);
        boolean allowed = accessChecker.canRead(dataset) && accessChecker.departmentAllowed(dataset, effDept);
        if (!allowed && !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该数据集");
        }

        CatalogDatasetSecurityMapping mapping = datasetSecurityMappingRepository.findById(id).orElse(null);
        List<CatalogDatasetGrant> grants = datasetGrantRepository.findByDatasetIdOrderByCreatedDateAsc(id);
        List<CatalogRowFilterRule> rowFilters = rowFilterRuleRepository.findByDataset(dataset);
        List<CatalogMaskingRule> maskingRules = maskingRuleRepository.findByDataset(dataset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", id.toString());
        result.put("datasetName", dataset.getName());
        result.put("ownerDept", dataset.getOwnerDept());
        result.put("classification", dataset.getClassification());
        result.put("enabled", dataset.getEnabled());
        result.put("securityMapping", toSecurityMappingDto(mapping));
        result.put("explicitGrants", grants.stream().map(this::toGrantDto).toList());
        result.put("rowFilters", rowFilters.stream().map(rule -> toRowFilterDto(rule, currentAuthorities())).toList());
        result.put("maskingRules", maskingRules.stream().map(this::toMaskingDto).toList());

        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("summary", "权限变更影响提示（轻量）");
        hint.put("grantCount", grants.size());
        hint.put("rowFilterCount", rowFilters.size());
        hint.put("maskingRuleCount", maskingRules.size());
        hint.put("departmentGate", dataset.getOwnerDept());
        hint.put("dataLevel", DataLevel.normalize(dataset.getClassification()).classification());
        result.put("impact", hint);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看权限变更影响");
        auditPayload.put("datasetId", id.toString());
        auditPayload.put("datasetName", dataset.getName());
        auditPayload.put("grantCount", grants.size());
        auditPayload.put("rowFilterCount", rowFilters.size());
        auditPayload.put("maskingRuleCount", maskingRules.size());
        auditPayload.put("activeDept", effDept);
        auditService.auditAction("SECURITY_PERMISSION_IMPACT_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(result);
    }

    private Map<String, Object> toSecurityMappingDto(CatalogDatasetSecurityMapping mapping) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (mapping == null) {
            return dto;
        }
        if (mapping.getDatasetId() != null) {
            dto.put("datasetId", mapping.getDatasetId().toString());
        }
        dto.put("dataLevelField", mapping.getDataLevelField());
        dto.put("deptField", mapping.getDeptField());
        return dto;
    }

    private Map<String, Object> toGrantDto(CatalogDatasetGrant grant) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (grant == null) {
            return dto;
        }
        if (grant.getId() != null) {
            dto.put("id", grant.getId().toString());
        }
        dto.put("granteeId", grant.getGranteeId());
        dto.put("granteeUsername", grant.getGranteeUsername());
        dto.put("granteeName", grant.getGranteeName());
        dto.put("granteeDept", grant.getGranteeDept());
        dto.put("createdBy", grant.getCreatedBy());
        dto.put("createdDate", grant.getCreatedDate());
        return dto;
    }

    private Map<String, Object> toRowFilterDto(CatalogRowFilterRule rule, Set<String> currentAuthorities) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (rule == null) {
            return dto;
        }
        if (rule.getId() != null) {
            dto.put("id", rule.getId().toString());
        }
        dto.put("roles", rule.getRoles());
        dto.put("expression", rule.getExpression());
        dto.put("appliesToCurrentUser", rolesMatch(rule.getRoles(), currentAuthorities));
        return dto;
    }

    private boolean rolesMatch(String rolesCsv, Set<String> currentAuthorities) {
        if (!StringUtils.hasText(rolesCsv)) {
            return true;
        }
        if (currentAuthorities == null || currentAuthorities.isEmpty()) {
            return false;
        }
        String[] parts = rolesCsv.split(",");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String normalized = part.trim().toUpperCase(Locale.ROOT);
            if (!normalized.startsWith("ROLE_")) {
                normalized = "ROLE_" + normalized;
            }
            if (currentAuthorities.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toMaskingDto(CatalogMaskingRule rule) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (rule == null) {
            return dto;
        }
        if (rule.getId() != null) {
            dto.put("id", rule.getId().toString());
        }
        dto.put("column", rule.getColumn());
        dto.put("function", rule.getFunction());
        dto.put("args", rule.getArgs());
        dto.put("createdBy", rule.getCreatedBy());
        dto.put("createdDate", rule.getCreatedDate());
        return dto;
    }

    private boolean matchesDatasetKeyword(CatalogDataset dataset, String keyword) {
        if (dataset == null || !StringUtils.hasText(keyword)) {
            return true;
        }
        String key = keyword.trim().toLowerCase(Locale.ROOT);
        if (dataset.getId() != null && dataset.getId().toString().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        if (StringUtils.hasText(dataset.getName()) && dataset.getName().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        if (StringUtils.hasText(dataset.getHiveDatabase()) && dataset.getHiveDatabase().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        if (StringUtils.hasText(dataset.getHiveTable()) && dataset.getHiveTable().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        return false;
    }

    private Map<String, Object> toMatrixRow(CatalogDataset dataset) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (dataset == null) {
            return dto;
        }
        dto.put("datasetId", dataset.getId() != null ? dataset.getId().toString() : null);
        dto.put("datasetName", dataset.getName());
        dto.put("ownerDept", dataset.getOwnerDept());
        dto.put("classification", dataset.getClassification());
        dto.put("enabled", dataset.getEnabled());
        dto.put("hiveDatabase", dataset.getHiveDatabase());
        dto.put("hiveTable", dataset.getHiveTable());
        CatalogDatasetSecurityMapping mapping = dataset.getId() != null ? datasetSecurityMappingRepository.findById(dataset.getId()).orElse(null) : null;
        dto.put("securityMapping", toSecurityMappingDto(mapping));
        int grants = dataset.getId() != null ? datasetGrantRepository.findByDatasetIdOrderByCreatedDateAsc(dataset.getId()).size() : 0;
        int rowFilters = rowFilterRuleRepository.findByDataset(dataset).size();
        int maskingRules = maskingRuleRepository.findByDataset(dataset).size();
        dto.put("grantCount", grants);
        dto.put("rowFilterCount", rowFilters);
        dto.put("maskingRuleCount", maskingRules);
        return dto;
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String resolveActiveDept(String activeDeptHeader) {
        if (StringUtils.hasText(activeDeptHeader)) {
            String trimmed = activeDeptHeader.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return claim("dept_code");
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
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            if (length > 0) {
                Object first = Array.get(raw, 0);
                if (first != null) return first;
            }
        }
        return raw;
    }
}
