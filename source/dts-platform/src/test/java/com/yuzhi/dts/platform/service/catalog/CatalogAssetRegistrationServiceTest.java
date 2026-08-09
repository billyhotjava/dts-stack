package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private final CatalogAssetSemanticStore store = mock(CatalogAssetSemanticStore.class);
    private final CatalogAssetRegistrationService service = new CatalogAssetRegistrationService(
        store,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void routesAnAdmittedObservationThroughTheSingleStoreBoundary() {
        when(store.register(any(), eq(NOW)))
            .thenReturn(new CatalogAssetSemanticStore.RegistrationReceipt(true, true, 1, NOW));

        CatalogAssetRegistrationService.ObservationResult result = service.observe(validObservation());

        assertThat(result.admitted()).isTrue();
        assertThat(result.receipt().projectionVersion()).isEqualTo(1);
        verify(store).register(argThat(plan -> plan.assetKey().equals(validObservation().assetKey())), eq(NOW));
    }

    @Test
    void doesNotWriteExcludedEphemeralRelations() {
        ObservationCommand original = validObservation();
        ObservationCommand ephemeral = new ObservationCommand(
            original.assetType(),
            original.assetKey(),
            original.resourceId(),
            RelationType.EPHEMERAL,
            false,
            true,
            original.domainId(),
            original.warehouseLayer(),
            original.assetRole(),
            original.producerKind(),
            original.producerId(),
            original.producerVersion(),
            original.evidenceChannel(),
            original.evidenceRef(),
            original.observedAt(),
            original.evidenceStatus(),
            original.statusAxes(),
            original.qualityGatePassed(),
            original.permissionGatePassed()
        );

        CatalogAssetRegistrationService.ObservationResult result = service.observe(ephemeral);

        assertThat(result.excluded()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("NON_STABLE_RELATION_EXCLUDED");
        verifyNoInteractions(store);
    }

    @Test
    void failsClosedWithoutWritingWhenLayerNormalizationIsAmbiguous() {
        ObservationCommand original = validObservation();
        ObservationCommand ambiguousDim = new ObservationCommand(
            original.assetType(),
            original.assetKey(),
            original.resourceId(),
            original.relationType(),
            false,
            false,
            original.domainId(),
            "DIM",
            AssetRole.RELATION,
            original.producerKind(),
            original.producerId(),
            original.producerVersion(),
            original.evidenceChannel(),
            original.evidenceRef(),
            original.observedAt(),
            original.evidenceStatus(),
            original.statusAxes(),
            original.qualityGatePassed(),
            original.permissionGatePassed()
        );

        CatalogAssetRegistrationService.ObservationResult result = service.observe(ambiguousDim);

        assertThat(result.admitted()).isFalse();
        assertThat(result.excluded()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("LAYER_NORMALIZATION_REQUIRED");
        verifyNoInteractions(store);
    }

    @Test
    void synchronizesGovernanceForAnAlreadyObservedIdentityWithoutMintingAnotherAsset() {
        UUID domainId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(store.updateGovernance(CatalogAssetType.DATASET, validObservation().assetKey(), domainId, GovernanceReadiness.GOVERNED, NOW))
            .thenReturn(Optional.of(new CatalogAssetSemanticStore.ProjectionMutationReceipt(2, NOW)));

        Optional<CatalogAssetSemanticStore.ProjectionMutationReceipt> receipt = service.updateGovernance(
            CatalogAssetType.DATASET,
            validObservation().assetKey(),
            domainId,
            GovernanceReadiness.GOVERNED
        );

        assertThat(receipt).hasValueSatisfying(value -> assertThat(value.projectionVersion()).isEqualTo(2));
        verify(store).updateGovernance(
            CatalogAssetType.DATASET,
            validObservation().assetKey(),
            domainId,
            GovernanceReadiness.GOVERNED,
            NOW
        );
        verify(store, never()).register(any(), any());
    }

    private ObservationCommand validObservation() {
        return new ObservationCommand(
            CatalogAssetType.DATASET,
            "source:11111111-1111-1111-1111-111111111111/schema:public/table:orders",
            null,
            RelationType.TABLE,
            false,
            false,
            null,
            "DWD",
            AssetRole.RELATION,
            ProducerKind.DBT_MODEL,
            "model.orders",
            "v1",
            EvidenceChannel.DBT_SYNC,
            "run-1",
            NOW,
            EvidenceStatus.ACTIVE,
            new StatusAxes(
                DiscoveryState.VERIFIED,
                GovernanceReadiness.UNASSIGNED,
                PublicationState.UNPUBLISHED,
                ServingHealth.HEALTHY,
                LifecycleState.ACTIVE
            ),
            true,
            true
        );
    }
}
