package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AccessChecker {

    private final CatalogAccessPolicyRepository policyRepo;
    private final ClassificationUtils classificationUtils;

    public AccessChecker(CatalogAccessPolicyRepository policyRepo, ClassificationUtils classificationUtils) {
        this.policyRepo = policyRepo;
        this.classificationUtils = classificationUtils;
    }

    public boolean canRead(CatalogDataset dataset) {
        if (dataset == null) return false;
        // Special handling: OP_ADMIN (and ADMIN) can access all datasets without restriction
        if (isSuperAdmin()) return true;
        // Level check (new ABAC: personnel_level vs data_level), fallback to legacy when claims absent
        if (!levelAllowed(dataset)) return false;
        // AccessPolicy check (if exists)
        CatalogAccessPolicy p = policyRepo.findByDataset(dataset).orElse(null);
        if (p == null) return true;
        String rolesCsv = p.getAllowRoles();
        if (rolesCsv == null || rolesCsv.isBlank()) return true;
        Set<String> allowed = new HashSet<>(Arrays.asList(rolesCsv.split("\\s*,\\s*")));
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(allowed.toArray(new String[0]));
    }

    private boolean levelAllowed(CatalogDataset dataset) {
        // Normalize dataset level to new DATA_*; accept legacy values
        String levelStr = dataset.getDataLevel() != null ? dataset.getDataLevel() : dataset.getClassification();
        DataLevel resourceLevel = DataLevel.normalize(levelStr);
        // No level info on resource → fall back to legacy behavior
        if (resourceLevel == null) {
            return classificationUtils.canAccess(levelStr);
        }
        // Extract personnel_level from JWT claims if present
        PersonnelLevel personnel = extractPersonnelLevelFromJwt();
        if (personnel == null) {
            // Fallback to legacy role-based classification gates when claim missing
            return classificationUtils.canAccess(levelStr);
        }
        // Compare ranks: personnel_level_rank >= data_level_rank
        return personnel.rank() >= resourceLevel.rank();
    }

    /**
     * Scope gate: ensure dataset matches the current active scope/dept and share policy.
     * activeScope: "DEPT" or "INST" (case-insensitive). activeDept: department code when scope=DEPT.
     */
    public boolean scopeAllowed(CatalogDataset dataset, String activeScope, String activeDept) {
        if (dataset == null) return false;
        // Special handling for super admins
        if (isSuperAdmin()) return true;
        String dsScope = Optional.ofNullable(dataset.getScope()).orElse("").trim().toUpperCase();
        String dsOwnerDept = Optional.ofNullable(dataset.getOwnerDept()).orElse("").trim();
        String dsShare = Optional.ofNullable(dataset.getShareScope()).orElse("").trim().toUpperCase();
        String as = Optional.ofNullable(activeScope).orElse("").trim().toUpperCase();
        // If dataset not annotated with scope, do not enforce scope gate (backward compatible)
        if (dsScope.isEmpty()) return true;
        if ("DEPT".equals(as)) {
            if (!"DEPT".equals(dsScope)) return false;
            // If owner_dept missing on dataset, do not block (assume legacy)
            if (dsOwnerDept.isEmpty()) return true;
            return dsOwnerDept.equalsIgnoreCase(Optional.ofNullable(activeDept).orElse(""));
        } else if ("INST".equals(as)) {
            if (!"INST".equals(dsScope)) return false;
            // If share_scope missing, allow by default (legacy)
            return dsShare.isEmpty() || "SHARE_INST".equals(dsShare) || "PUBLIC_INST".equals(dsShare);
        }
        // Unknown scope defaults to deny
        return false;
    }

    private boolean isSuperAdmin() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.ADMIN
        );
    }

    private PersonnelLevel extractPersonnelLevelFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (auth instanceof JwtAuthenticationToken token) {
                Map<String, Object> claims = token.getToken().getClaims();
                Object v = claims.get("person_security_level");
                if (v == null) v = claims.get("personnel_level");
                if (v instanceof String s) {
                    return PersonnelLevel.normalize(s);
                }
            } else if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                String v = principal.getAttribute("personnel_level");
                if (v == null) v = principal.getAttribute("person_security_level");
                if (v != null) {
                    return PersonnelLevel.normalize(v);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
