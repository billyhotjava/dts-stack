package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.*;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single command boundary for stable physical-asset observation and projection. */
@Service
public class CatalogAssetRegistrationService {

    private final CatalogAssetSemanticStore store;
    private final Clock clock;

    @Autowired
    public CatalogAssetRegistrationService(CatalogAssetSemanticStore store) {
        this(store, Clock.systemUTC());
    }

    CatalogAssetRegistrationService(CatalogAssetSemanticStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public ObservationResult observe(ObservationCommand command) {
        Instant now = clock.instant();
        AdmissionDecision decision = CatalogAssetSemanticsContract.admit(command, now);
        if (!decision.admitted()) {
            return new ObservationResult(false, decision.excluded(), decision.reasonCode(), null);
        }
        try {
            RegistrationReceipt receipt = store.register(decision.plan(), now);
            return new ObservationResult(true, false, null, receipt);
        } catch (CatalogAssetSemanticConflictException conflict) {
            return new ObservationResult(false, false, conflict.code(), null);
        }
    }

    @Transactional
    public Optional<ProjectionMutationReceipt> updateGovernance(
        CatalogAssetType assetType,
        String assetKey,
        UUID domainId,
        GovernanceReadiness governance
    ) {
        if (assetType == null || assetKey == null || assetKey.isBlank() || governance == null) {
            throw new IllegalArgumentException("ASSET_GOVERNANCE_PROJECTION_INPUT_INVALID");
        }
        return store.updateGovernance(assetType, assetKey.trim(), domainId, governance, clock.instant());
    }

    @Transactional(readOnly = true)
    public Optional<AssetSemanticsView> find(CatalogAssetType assetType, String assetKey) {
        return store.find(assetType, assetKey, clock.instant());
    }

    @Transactional(readOnly = true)
    public StatsSnapshot stats(UUID domainId) {
        return store.stats(domainId, clock.instant());
    }

    @Transactional
    public ReconciliationReceipt reconcile() {
        return store.reconcile(clock.instant());
    }

    public record ObservationResult(boolean admitted, boolean excluded, String reasonCode, RegistrationReceipt receipt) {}
}
