package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.util.StringUtils;

public final class CatalogAssetContractMapper {

    private CatalogAssetContractMapper() {}

    public static CatalogAssetContract fromOpenMetadata(
        OpenMetadataAssetCache asset,
        CatalogAssetExtension extension,
        CatalogAssetMapping mapping,
        CatalogDataset legacy
    ) {
        if (asset == null) {
            throw new IllegalArgumentException("openmetadata asset is required");
        }
        CatalogAssetIdentity identity = legacy != null ? legacyIdentity(legacy) : openMetadataIdentity(asset);
        CatalogAssetGovernanceProfile profile = inspect(asset, extension, legacy);
        UUID legacyDatasetId = firstUuid(
            extension != null ? extension.getLegacyDatasetId() : null,
            mapping != null ? mapping.getLegacyDatasetId() : null,
            legacy != null ? legacy.getId() : null
        );
        return new CatalogAssetContract(
            asset.getId(),
            identity.grantAssetType(),
            identity.assetKey(),
            identity.grantAssetType(),
            identity.grantAssetId(),
            identity.sourceRef(),
            asset.getOmEntityId(),
            asset.getFqn(),
            firstNonBlank(asset.getDisplayName(), asset.getTableName(), asset.getFqn()),
            asset.getServiceName(),
            asset.getDatabaseName(),
            asset.getSchemaName(),
            asset.getTableName(),
            firstNonBlank(extension != null ? extension.getClassification() : null, legacy != null ? legacy.getClassification() : null),
            firstNonBlank(extension != null ? extension.getWarehouseLayer() : null, legacy != null ? legacy.getWarehouseLayer() : null),
            firstNonBlank(extension != null ? extension.getOwnerDept() : null, legacy != null ? legacy.getOwnerDept() : null),
            firstNonBlank(extension != null ? extension.getBusinessOwner() : null, legacy != null ? legacy.getOwner() : null, asset.getOwnerName()),
            firstUuid(extension != null ? extension.getDomainId() : null, legacyDomainId(legacy)),
            profile.lifecycleStatus(),
            profile.governanceStatus(),
            profile.missingFields(),
            profile.consumable(),
            resolveMatchStatus(mapping, legacy),
            mapping != null ? mapping.getMatchReason() : null,
            legacyDatasetId,
            asset.getSyncStatus(),
            "openmetadata-cache"
        );
    }

    public static CatalogAssetContract fromLegacy(CatalogDataset dataset) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        CatalogAssetIdentity identity = legacyIdentity(dataset);
        CatalogAssetGovernanceProfile profile = CatalogAssetGovernanceInspector.inspect(dataset);
        return new CatalogAssetContract(
            dataset.getId(),
            identity.grantAssetType(),
            identity.assetKey(),
            identity.grantAssetType(),
            identity.grantAssetId(),
            identity.sourceRef(),
            null,
            legacyFqn(dataset),
            firstNonBlank(dataset.getName(), dataset.getHiveTable(), dataset.getId() == null ? null : dataset.getId().toString()),
            null,
            dataset.getHiveDatabase(),
            dataset.getHiveDatabase(),
            dataset.getHiveTable(),
            dataset.getClassification(),
            dataset.getWarehouseLayer(),
            dataset.getOwnerDept(),
            dataset.getOwner(),
            legacyDomainId(dataset),
            profile.lifecycleStatus(),
            profile.governanceStatus(),
            profile.missingFields(),
            profile.consumable(),
            "DTS_NATIVE",
            "DTS原生资产",
            dataset.getId(),
            Boolean.FALSE.equals(dataset.getEnabled()) ? "DISABLED" : "SYNCED",
            "dts-catalog"
        );
    }

    private static CatalogAssetIdentity openMetadataIdentity(OpenMetadataAssetCache asset) {
        CatalogAssetSourceReference sourceRef = CatalogAssetSourceReference.openMetadata(asset, "asset-contract");
        return new CatalogAssetIdentity(
            CatalogAssetType.DATASET,
            safeOpenMetadataKey(asset),
            asset.getId() == null ? null : asset.getId().toString(),
            sourceRef.stableRef()
        );
    }

    private static CatalogAssetIdentity legacyIdentity(CatalogDataset dataset) {
        String assetKey = safeLegacyKey(dataset);
        return new CatalogAssetIdentity(
            CatalogAssetType.DATASET,
            assetKey,
            dataset.getId() == null ? null : dataset.getId().toString(),
            "dts-catalog:" + firstNonBlank(dataset.getId() == null ? null : dataset.getId().toString(), assetKey)
        );
    }

    private static CatalogAssetGovernanceProfile inspect(
        OpenMetadataAssetCache asset,
        CatalogAssetExtension extension,
        CatalogDataset legacy
    ) {
        List<String> missing = new ArrayList<>();
        boolean enabled = extension == null || !Boolean.FALSE.equals(extension.getEnabled());
        if (legacy != null && Boolean.FALSE.equals(legacy.getEnabled())) {
            enabled = false;
        }
        if (
            !StringUtils.hasText(firstNonBlank(
                    extension != null ? extension.getBusinessOwner() : null,
                    extension != null ? extension.getOwnerDept() : null,
                    legacy != null ? legacy.getOwner() : null,
                    legacy != null ? legacy.getOwnerDept() : null,
                    asset.getOwnerName()
                ))
        ) {
            missing.add("owner");
        }
        if (!StringUtils.hasText(firstNonBlank(extension != null ? extension.getClassification() : null, legacy != null ? legacy.getClassification() : null))) {
            missing.add("classification");
        }
        if (!StringUtils.hasText(firstNonBlank(extension != null ? extension.getWarehouseLayer() : null, legacy != null ? legacy.getWarehouseLayer() : null))) {
            missing.add("warehouseLayer");
        }
        if (
            !StringUtils.hasText(firstNonBlank(
                    asset.getServiceName(),
                    asset.getDatabaseName(),
                    asset.getFqn(),
                    legacy != null && legacy.getSourceId() != null ? legacy.getSourceId().toString() : null,
                    legacy != null ? legacy.getHiveDatabase() : null
                ))
        ) {
            missing.add("sourceSystem");
        }
        if (firstUuid(extension != null ? extension.getDomainId() : null, legacyDomainId(legacy)) == null) {
            missing.add("domain");
        }

        String lifecycle = CatalogAssetGovernancePolicy.normalizeLifecycle(
            firstNonBlank(extension != null ? extension.getLifecycleStatus() : null, legacy != null ? legacy.getLifecycleStatus() : null)
        );
        String governance = resolveGovernanceStatus(enabled, missing);
        boolean consumable = enabled && CatalogAssetLifecycleStatus.ACTIVE.name().equals(lifecycle) && missing.isEmpty();
        return new CatalogAssetGovernanceProfile(lifecycle, governance, List.copyOf(missing), consumable);
    }

    private static String resolveGovernanceStatus(boolean enabled, List<String> missing) {
        if (!enabled) {
            return CatalogAssetGovernanceStatus.DISABLED.name();
        }
        boolean missingOwner = missing.contains("owner");
        boolean missingClassification = missing.contains("classification");
        boolean missingDomain = missing.contains("domain");
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

    private static String resolveMatchStatus(CatalogAssetMapping mapping, CatalogDataset legacy) {
        if (mapping != null && StringUtils.hasText(mapping.getMatchStatus())) {
            return mapping.getMatchStatus().trim().toUpperCase();
        }
        return legacy != null ? "MATCHED" : "UNMATCHED";
    }

    private static String safeOpenMetadataKey(OpenMetadataAssetCache asset) {
        if (StringUtils.hasText(asset.getFqn())) {
            return CatalogAssetKey.openMetadataDataset(asset);
        }
        return "om:" + firstNonBlank(asset.getId() == null ? null : asset.getId().toString(), "unknown");
    }

    private static String safeLegacyKey(CatalogDataset dataset) {
        try {
            return CatalogAssetKey.dataset(dataset);
        } catch (IllegalArgumentException ignored) {
            return CatalogAssetKey.dataset(
                dataset.getSourceId(),
                firstNonBlank(dataset.getHiveDatabase(), "default"),
                firstNonBlank(dataset.getHiveDatabase(), "default"),
                dataset.getHiveTable(),
                firstNonBlank(dataset.getName(), dataset.getId() == null ? null : dataset.getId().toString(), "unknown")
            );
        }
    }

    private static String legacyFqn(CatalogDataset dataset) {
        String database = firstNonBlank(dataset.getHiveDatabase());
        String table = firstNonBlank(dataset.getHiveTable());
        if (database != null && table != null) {
            return database + "." + table;
        }
        return firstNonBlank(table, dataset.getName(), dataset.getId() == null ? null : dataset.getId().toString());
    }

    private static UUID legacyDomainId(CatalogDataset dataset) {
        return dataset != null && dataset.getDomain() != null ? dataset.getDomain().getId() : null;
    }

    private static UUID firstUuid(UUID... values) {
        if (values == null) {
            return null;
        }
        for (UUID value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
