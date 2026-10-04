package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the asset-semantics projection. */
public interface CatalogAssetSemanticStore {

    RegistrationReceipt register(RegistrationPlan plan, Instant now);

    Optional<ProjectionMutationReceipt> updateGovernance(
        CatalogAssetType assetType,
        String assetKey,
        UUID domainId,
        GovernanceReadiness governance,
        Instant now
    );

    Optional<AssetSemanticsView> find(CatalogAssetType assetType, String assetKey, Instant now);

    /** Bounded read projection used by asset-list pages; implementations must fetch the supplied keys in one query. */
    Map<String, AssetStatusSnapshot> findStatusSnapshots(
        CatalogAssetType assetType,
        Collection<String> assetKeys,
        Instant now
    );

    StatsSnapshot stats(UUID domainId, Instant now);

    ReconciliationReceipt reconcile(Instant now);

    record RegistrationReceipt(
        boolean created,
        boolean evidenceCreated,
        long projectionVersion,
        Instant asOf,
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId
    ) {
        /** Compatibility constructor for callers created before identity was exposed in the receipt. */
        public RegistrationReceipt(
            boolean created,
            boolean evidenceCreated,
            long projectionVersion,
            Instant asOf
        ) {
            this(created, evidenceCreated, projectionVersion, asOf, null, null, null);
        }
    }

    record ProjectionMutationReceipt(long projectionVersion, Instant asOf) {}

    record AssetSemanticsView(
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId,
        RelationType relationType,
        UUID domainId,
        String canonicalLayer,
        String legacyLayerCode,
        AssetRole assetRole,
        ProducerRef currentProducer,
        List<RegistrationEvidence> evidence,
        StatusAxes statusAxes,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed,
        ConsumptionEligibility eligibility,
        long projectionVersion,
        Instant asOf
    ) {
        public AssetSemanticsView {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    record AssetStatusSnapshot(
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId,
        StatusAxes statusAxes,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed,
        ConsumptionEligibility eligibility,
        long projectionVersion,
        Instant asOf
    ) {}

    record StatsBucket(
        UUID domainId,
        String warehouseLayer,
        CatalogAssetType assetType,
        GovernanceReadiness governance,
        long count
    ) {}

    record StatsSnapshot(
        List<StatsBucket> buckets,
        long total,
        Instant asOf,
        Freshness freshness,
        boolean approximate,
        String projectionState
    ) {
        public StatsSnapshot {
            buckets = buckets == null ? List.of() : List.copyOf(buckets);
        }
    }

    record ReconciliationReceipt(long assetCount, int bucketCount, Instant asOf) {}
}
