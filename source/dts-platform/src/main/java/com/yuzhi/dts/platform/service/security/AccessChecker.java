package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
        // Level check
        if (!classificationUtils.canAccess(dataset.getClassification())) return false;
        // AccessPolicy check (if exists)
        CatalogAccessPolicy p = policyRepo.findByDataset(dataset).orElse(null);
        if (p == null) return true;
        String rolesCsv = p.getAllowRoles();
        if (rolesCsv == null || rolesCsv.isBlank()) return true;
        Set<String> allowed = new HashSet<>(Arrays.asList(rolesCsv.split("\\s*,\\s*")));
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(allowed.toArray(new String[0]));
    }
}
