package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class CatalogAssetGovernanceInspector {

    private CatalogAssetGovernanceInspector() {}

    public static CatalogAssetGovernanceProfile inspect(CatalogDataset dataset) {
        List<String> missing = new ArrayList<>();
        if (dataset == null) {
            return new CatalogAssetGovernanceProfile(
                CatalogAssetLifecycleStatus.BLOCKED.name(),
                CatalogAssetGovernanceStatus.DISABLED.name(),
                List.of("asset"),
                false
            );
        }
        if (!StringUtils.hasText(dataset.getOwner()) && !StringUtils.hasText(dataset.getOwnerDept())) {
            missing.add("owner");
        }
        if (!StringUtils.hasText(dataset.getClassification())) {
            missing.add("classification");
        }
        if (!StringUtils.hasText(dataset.getWarehouseLayer())) {
            missing.add("warehouseLayer");
        }
        if (dataset.getSourceId() == null && !StringUtils.hasText(dataset.getHiveDatabase())) {
            missing.add("sourceSystem");
        }
        if (dataset.getDomain() == null || dataset.getDomain().getId() == null) {
            missing.add("domain");
        }

        String lifecycle = CatalogAssetGovernancePolicy.normalizeLifecycle(dataset.getLifecycleStatus());
        String governance = CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset);
        boolean consumable = Boolean.TRUE.equals(dataset.getEnabled())
            && CatalogAssetLifecycleStatus.ACTIVE.name().equals(lifecycle)
            && missing.isEmpty();
        return new CatalogAssetGovernanceProfile(lifecycle, governance, List.copyOf(missing), consumable);
    }
}
