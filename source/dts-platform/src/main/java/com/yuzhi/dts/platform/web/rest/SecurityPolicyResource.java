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
