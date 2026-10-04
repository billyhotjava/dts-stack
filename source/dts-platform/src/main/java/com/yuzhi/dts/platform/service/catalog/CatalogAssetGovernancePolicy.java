package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import org.springframework.util.StringUtils;

public final class CatalogAssetGovernancePolicy {

    private CatalogAssetGovernancePolicy() {}

    public static String lifecycleForDiscoveredAsset() {
        return CatalogAssetLifecycleStatus.PENDING_GOVERNANCE.name();
    }

    public static String normalizeLifecycle(String value) {
        return CatalogAssetLifecycleStatus.normalizeOrDefault(value, CatalogAssetLifecycleStatus.PENDING_GOVERNANCE);
    }

    public static String resolveGovernanceStatus(CatalogDataset dataset) {
        if (dataset == null || Boolean.FALSE.equals(dataset.getEnabled())) {
            return CatalogAssetGovernanceStatus.DISABLED.name();
        }
        boolean missingOwner = !StringUtils.hasText(dataset.getOwner()) && !StringUtils.hasText(dataset.getOwnerDept());
        boolean missingClassification = !StringUtils.hasText(dataset.getClassification());
        boolean missingDomain = dataset.getDomain() == null || dataset.getDomain().getId() == null;
        if (missingOwner && missingClassification && missingDomain) {
            return CatalogAssetGovernanceStatus.PENDING_GOVERNANCE.name();
        }
        if (missingOwner) {
            return CatalogAssetGovernanceStatus.PENDING_CLAIM.name();
        }
        if (missingClassification) {
            return CatalogAssetGovernanceStatus.PENDING_CLASSIFICATION.name();
        }
        if (missingDomain) {
            return CatalogAssetGovernanceStatus.PENDING_DOMAIN.name();
        }
        return CatalogAssetGovernanceStatus.GOVERNED.name();
    }
}
