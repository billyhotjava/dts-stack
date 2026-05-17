package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.UUID;

public record CatalogAssetContract(
    UUID id,
    String assetType,
    String assetKey,
    String grantAssetType,
    String grantAssetId,
    String sourceRef,
    String omEntityId,
    String fqn,
    String displayName,
    String service,
    String database,
    String schema,
    String table,
    String classification,
    String warehouseLayer,
    String ownerDept,
    String owner,
    UUID domainId,
    String lifecycleStatus,
    String governanceStatus,
    List<String> missingGovernanceFields,
    boolean consumable,
    String matchStatus,
    String matchReason,
    UUID legacyDatasetId,
    String syncStatus,
    String metadataSource
) {
    public CatalogAssetContract {
        missingGovernanceFields = missingGovernanceFields == null ? List.of() : List.copyOf(missingGovernanceFields);
    }
}
