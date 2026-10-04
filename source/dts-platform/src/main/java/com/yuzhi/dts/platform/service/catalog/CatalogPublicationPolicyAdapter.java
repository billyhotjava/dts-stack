package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned lookup and ABAC adapter for model publication admission. */
@Component
@Transactional(readOnly = true)
public class CatalogPublicationPolicyAdapter implements CatalogPublicationPolicyPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogPublicationPolicyAdapter.class);

    private final CatalogDatasetRepository catalogs;
    private final AccessChecker accessChecker;

    public CatalogPublicationPolicyAdapter(CatalogDatasetRepository catalogs, AccessChecker accessChecker) {
        this.catalogs = catalogs;
        this.accessChecker = accessChecker;
    }

    @Override
    public Decision authorizeUpsert(UUID sourceId, String schemaName, String tableName) {
        UUID proposedId = new CatalogPhysicalLocator(sourceId, schemaName, tableName).assetId();
        List<CatalogDataset> matches = catalogs
            .findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schemaName, tableName)
            .stream()
            .filter(dataset -> dataset.getSourceId() == null || sourceId.equals(dataset.getSourceId()))
            .toList();
        if (matches.size() > 1) {
            return new Decision(
                Status.AMBIGUOUS,
                Action.UPDATE,
                proposedId,
                matches.stream().map(CatalogDataset::getId).toList()
            );
        }
        CatalogDataset subject = matches.isEmpty()
            ? prospective(proposedId, sourceId, schemaName, tableName)
            : matches.getFirst();
        Action action = matches.isEmpty() ? Action.CREATE : Action.UPDATE;
        return authorize(subject, action, List.of(subject.getId()));
    }

    @Override
    public Decision authorizeArchive(UUID assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId is required");
        }
        CatalogDataset current = catalogs
            .findById(assetId)
            .filter(asset -> Boolean.TRUE.equals(asset.getEnabled()) && isPublishedCatalogAsset(asset))
            .orElse(null);
        if (current == null) {
            return new Decision(Status.PUBLISHED_ASSET_REQUIRED, Action.ARCHIVE, assetId, List.of());
        }
        return authorize(current, Action.ARCHIVE, List.of(assetId));
    }

    private Decision authorize(CatalogDataset subject, Action action, List<UUID> matches) {
        try {
            boolean allowed = accessChecker.canPerform(subject, toAssetAction(action));
            return new Decision(allowed ? Status.ALLOWED : Status.FORBIDDEN, action, subject.getId(), matches);
        } catch (RuntimeException failure) {
            log.warn(
                "Catalog publication policy evaluation failed: assetId={}, action={}",
                subject.getId(),
                action,
                failure
            );
            return new Decision(Status.POLICY_UNAVAILABLE, action, subject.getId(), matches);
        }
    }

    private static AssetAction toAssetAction(Action action) {
        return switch (action) {
            case CREATE -> AssetAction.CREATE;
            case UPDATE -> AssetAction.UPDATE;
            case ARCHIVE -> AssetAction.ARCHIVE;
        };
    }

    private static CatalogDataset prospective(UUID id, UUID sourceId, String schemaName, String tableName) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setName(tableName);
        dataset.setType("jdbc");
        dataset.setSourceId(sourceId);
        dataset.setClassification(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
        dataset.setHiveDatabase(schemaName);
        dataset.setHiveTable(tableName);
        dataset.setEnabled(Boolean.TRUE);
        dataset.setExposedBy("VIEW");
        dataset.setLifecycleStatus(CatalogAssetLifecycleStatus.ACTIVE.name());
        return dataset;
    }

    private static boolean isPublishedCatalogAsset(CatalogDataset asset) {
        String lifecycle = asset.getLifecycleStatus();
        return CatalogAssetLifecycleStatus.ACTIVE.name().equalsIgnoreCase(lifecycle) || "PUBLISHED".equalsIgnoreCase(lifecycle);
    }
}
