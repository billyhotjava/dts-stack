package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AccessChecker {

    private static final Logger log = LoggerFactory.getLogger(AccessChecker.class);

    private final ClassificationUtils classificationUtils;
    private final CatalogDatasetGrantRepository grantRepository;

    public AccessChecker(ClassificationUtils classificationUtils, CatalogDatasetGrantRepository grantRepository) {
        this.classificationUtils = classificationUtils;
        this.grantRepository = grantRepository;
    }

    public boolean canRead(CatalogDataset dataset) {
        if (dataset == null) return false;
        // Special handling: OP_ADMIN (and ADMIN) can access all datasets without restriction
        if (isSuperAdmin()) return true;
        // Level check (ABAC: personnel_level vs dataset classification)，缺失时回退旧密级逻辑
        if (!levelAllowed(dataset)) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Dataset {}({}) rejected by level gate: classification={}, userPersonnel={}, userMaxLevel={}",
                    dataset.getName(),
                    dataset.getId(),
                    dataset.getClassification(),
                    extractPersonnelLevelFromJwt(),
                    classificationUtils.getCurrentUserMaxLevel()
                );
            }
            return false;
        }
        return true;
    }

    private boolean levelAllowed(CatalogDataset dataset) {
        // Normalize dataset level to new DATA_*; accept legacy values
        String levelStr = dataset.getClassification();
        DataLevel resourceLevel = DataLevel.normalize(levelStr);
        // No level info on resource → fall back to legacy behavior
        if (resourceLevel == null) {
            boolean allowed = classificationUtils.canAccess(levelStr);
            if (log.isDebugEnabled()) {
                log.debug(
                    "Dataset {}({}) evaluated via classification fallback: classification={}, userMaxLevel={}, allowed={}",
                    dataset.getName(),
                    dataset.getId(),
                    levelStr,
                    classificationUtils.getCurrentUserMaxLevel(),
                    allowed
                );
            }
            return allowed;
        }
        // Extract personnel_level from JWT claims if present
        PersonnelLevel personnel = extractPersonnelLevelFromJwt();
        if (personnel == null) {
            // Fallback to legacy role-based classification gates when claim missing
            boolean allowed = classificationUtils.canAccess(levelStr);
            if (log.isDebugEnabled()) {
                log.debug(
                    "Dataset {}({}) fell back to classification gate due to missing personnel claim: classification={}, userMaxLevel={}, allowed={}",
                    dataset.getName(),
                    dataset.getId(),
                    dataset.getClassification(),
                    classificationUtils.getCurrentUserMaxLevel(),
                    allowed
                );
            }
            return allowed;
        }
        // Compare ranks: personnel_level_rank >= data_level_rank
        boolean allowed = personnel.rank() >= resourceLevel.rank();
        if (log.isDebugEnabled()) {
            log.debug(
                "Dataset {}({}) ABAC check: classification={}, resourceRank={}, personnelLevel={}, personnelRank={}, allowed={}",
                dataset.getName(),
                dataset.getId(),
                dataset.getClassification(),
                resourceLevel.rank(),
                personnel,
                personnel.rank(),
                allowed
            );
        }
        return allowed;
    }

    /** Department gate: ensure dataset falls within the active department context when provided. */
    public boolean departmentAllowed(CatalogDataset dataset, String activeDept) {
        if (dataset == null) return false;
        if (dataset.getId() != null && isExplicitlyGranted(dataset)) {
            return true;
        }
        if (isSuperAdmin() || hasAuthority(AuthoritiesConstants.INST_DATA_OWNER)) {
            return true;
        }
        String normalizedOwner = DepartmentUtils.normalize(dataset.getOwnerDept());
        // Without explicit owner department, keep legacy permissive behaviour
        if (normalizedOwner.isEmpty()) {
            return false;
        }
        String normalizedContext = DepartmentUtils.normalize(activeDept);
        if (normalizedContext.isEmpty()) {
            // Missing context cannot satisfy department restriction
            if (log.isDebugEnabled()) {
                log.debug(
                    "Dataset {}({}) blocked by department gate: ownerDept={}, normalizedOwner={}, activeDept={}, normalizedActive=<empty>",
                    dataset.getName(),
                    dataset.getId(),
                    dataset.getOwnerDept(),
                    normalizedOwner,
                    activeDept
                );
            }
            return false;
        }
        if (DepartmentUtils.matches(dataset.getOwnerDept(), activeDept)) {
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug(
                "Dataset {}({}) blocked by department gate: ownerDept={}, normalizedOwner={}, activeDept={}, normalizedActive={}",
                dataset.getName(),
                dataset.getId(),
                dataset.getOwnerDept(),
                normalizedOwner,
                activeDept,
                normalizedContext
            );
        }
        return false;
    }

    public DataLevel resolveHighestDataLevel() {
        PersonnelLevel personnel = extractPersonnelLevelFromJwt();
        if (personnel != null) {
            List<DataLevel> allowed = personnel.allowedDataLevels();
            return allowed.get(allowed.size() - 1);
        }
        DataLevel fromClassification = DataLevel.normalize(classificationUtils.getCurrentUserMaxLevel());
        if (fromClassification != null) {
            return fromClassification;
        }
        return DataLevel.DATA_INTERNAL;
    }

    public List<DataLevel> resolveAllowedDataLevels() {
        PersonnelLevel personnel = extractPersonnelLevelFromJwt();
        if (personnel != null) {
            return personnel.allowedDataLevels();
        }
        int maxRank = resolveHighestDataLevel().rank();
        return Arrays
            .stream(DataLevel.values())
            .filter(level -> level.rank() <= maxRank)
            .sorted(Comparator.comparingInt(DataLevel::rank))
            .collect(Collectors.toList());
    }

    private boolean isSuperAdmin() {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.ADMIN
        )) {
            return true;
        }
        return SecurityUtils.isOpAdminAccount();
    }

    private boolean hasAuthority(String authority) {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(authority);
    }

    private boolean isExplicitlyGranted(CatalogDataset dataset) {
        if (dataset.getId() == null) {
            return false;
        }
        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if ((userId == null || userId.isBlank()) && (username == null || username.isBlank())) {
            return false;
        }
        boolean granted = grantRepository.existsForDatasetAndUser(dataset.getId(), userId, username);
        if (granted && log.isDebugEnabled()) {
            log.debug(
                "Dataset {}({}) allowed via explicit grant for userId={}, username={}",
                dataset.getName(),
                dataset.getId(),
                userId,
                username
            );
        }
        return granted;
    }

    private PersonnelLevel extractPersonnelLevelFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (auth instanceof JwtAuthenticationToken token) {
                Map<String, Object> claims = token.getToken().getClaims();
                String value = extractStringClaim(claims.get("personnel_level"));
                if (value == null) {
                    value = extractStringClaim(claims.get("person_security_level"));
                }
                if (value != null) {
                    return PersonnelLevel.normalize(value);
                }
            } else if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                String value = extractStringClaim(principal.getAttribute("personnel_level"));
                if (value == null) {
                    value = extractStringClaim(principal.getAttribute("person_security_level"));
                }
                if (value != null) {
                    return PersonnelLevel.normalize(value);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractStringClaim(Object raw) {
        Object flattened = flattenValue(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return (text == null || text.isBlank()) ? null : text;
    }

    private Object flattenValue(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }
}
