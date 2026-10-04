package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ServingSyncState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetQualityStatusReader.QualityStatusSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetStatusSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService.AssetDeliveryStatus;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetStatusViewServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T08:00:00Z");
    private static final String REGISTERED_KEY = "source:one/schema:public/table:orders";
    private static final String MISSING_KEY = "source:one/schema:public/table:missing";
    private static final UUID DATASET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final CatalogAssetSemanticStore semanticStore = mock(CatalogAssetSemanticStore.class);
    private final CatalogModelServingProjectionRepository servingRepository = mock(CatalogModelServingProjectionRepository.class);
    private final CatalogAssetQualityStatusReader qualityStatusReader = mock(CatalogAssetQualityStatusReader.class);
    private final CatalogAssetStatusViewService service = new CatalogAssetStatusViewService(
        semanticStore,
        servingRepository,
        qualityStatusReader,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void joinsSemanticAndServingFactsInTwoBatchReadsAndKeepsMissingExplicit() {
        StatusAxes axes = new StatusAxes(
            DiscoveryState.VERIFIED,
            GovernanceReadiness.GOVERNED,
            PublicationState.PUBLISHED,
            ServingHealth.HEALTHY,
            LifecycleState.ACTIVE
        );
        when(semanticStore.findStatusSnapshots(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY, MISSING_KEY), NOW))
            .thenReturn(
                Map.of(
                    REGISTERED_KEY,
                    new AssetStatusSnapshot(
                        CatalogAssetType.DATASET,
                        REGISTERED_KEY,
                        DATASET_ID,
                        axes,
                        true,
                        true,
                        new ConsumptionEligibility(EligibilityDecision.ELIGIBLE, List.of(), NOW),
                        4,
                        NOW.minusSeconds(5)
                    )
                )
            );
        UUID modelId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID candidateId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PublishedRef published = new PublishedRef(
            modelId,
            3,
            "model-checksum",
            2,
            "implementation-checksum",
            candidateId,
            7,
            6,
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            NOW.minusSeconds(60)
        );
        ModelServingProjection projection = new ModelServingProjection(
            "default",
            modelId,
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + modelId,
            published,
            null,
            9,
            "SYNC_FAILED",
            NOW.minusSeconds(2)
        );
        when(servingRepository.findSyncStatesByAssetKeys(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY, MISSING_KEY)))
            .thenReturn(
                Map.of(
                    REGISTERED_KEY,
                    List.of(new ServingSyncState(projection, 6, "ANALYTICS_TIMEOUT", NOW.plusSeconds(60)))
                )
            );
        when(qualityStatusReader.readLatest(Set.of(DATASET_ID))).thenReturn(Map.of());

        Map<AssetRef, CatalogAssetStatusViewService.AssetDeliveryStatus> result = service.read(
            List.of(
                new AssetRef("DATASET", REGISTERED_KEY),
                new AssetRef("DATASET", MISSING_KEY)
            )
        );

        assertThat(result.get(new AssetRef("DATASET", REGISTERED_KEY))).satisfies(status -> {
            assertThat(status.statusAxes()).isEqualTo(axes);
            assertThat(status.consumptionEligibility()).isEqualTo("ELIGIBLE");
            assertThat(status.qualityStatus()).isEqualTo("PASSED");
            assertThat(status.modelRefs()).singleElement().satisfies(ref -> {
                assertThat(ref.modelSpecId()).isEqualTo(modelId);
                assertThat(ref.modelRevision()).isEqualTo(3);
                assertThat(ref.candidateId()).isEqualTo(candidateId);
            });
            assertThat(status.servingSync().status()).isEqualTo("SYNC_FAILED");
            assertThat(status.servingSync().attempts()).isEqualTo(6);
            assertThat(status.servingSync().lastError()).isEqualTo("ANALYTICS_TIMEOUT");
        });
        assertThat(result.get(new AssetRef("DATASET", MISSING_KEY))).satisfies(status -> {
            assertThat(status.statusAxes()).isNull();
            assertThat(status.consumptionEligibility()).isEqualTo("CONDITIONAL");
            assertThat(status.eligibilityReasons()).containsExactly("ASSET_SEMANTICS_MISSING");
            assertThat(status.qualityStatus()).isEqualTo("UNKNOWN");
            assertThat(status.servingSync().status()).isEqualTo("NOT_APPLICABLE");
        });
        verify(semanticStore).findStatusSnapshots(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY, MISSING_KEY), NOW);
        verify(servingRepository).findSyncStatesByAssetKeys(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY, MISSING_KEY));
        verify(qualityStatusReader).readLatest(Set.of(DATASET_ID));
    }

    @Test
    void overlaysLatestGovernanceQualityWithoutCopyingTheRunIntoSemanticProjection() {
        StatusAxes axes = new StatusAxes(
            DiscoveryState.VERIFIED,
            GovernanceReadiness.GOVERNED,
            PublicationState.PUBLISHED,
            ServingHealth.HEALTHY,
            LifecycleState.ACTIVE
        );
        when(semanticStore.findStatusSnapshots(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY), NOW))
            .thenReturn(
                Map.of(
                    REGISTERED_KEY,
                    new AssetStatusSnapshot(
                        CatalogAssetType.DATASET,
                        REGISTERED_KEY,
                        DATASET_ID,
                        axes,
                        null,
                        null,
                        new ConsumptionEligibility(
                            EligibilityDecision.CONDITIONAL,
                            List.of("QUALITY_EVIDENCE_MISSING", "ACCESS_EVIDENCE_MISSING"),
                            NOW.minusSeconds(60)
                        ),
                        4,
                        NOW.minusSeconds(60)
                    )
                )
            );
        when(servingRepository.findSyncStatesByAssetKeys(CatalogAssetType.DATASET, Set.of(REGISTERED_KEY)))
            .thenReturn(Map.of());
        UUID runId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(qualityStatusReader.readLatest(Set.of(DATASET_ID)))
            .thenReturn(Map.of(DATASET_ID, new QualityStatusSnapshot(DATASET_ID, runId, "SUCCEEDED", NOW.minusSeconds(5))));

        AssetDeliveryStatus status = service.read(List.of(new AssetRef("DATASET", REGISTERED_KEY)))
            .get(new AssetRef("DATASET", REGISTERED_KEY));

        assertThat(status.qualityStatus()).isEqualTo("PASSED");
        assertThat(status.consumptionEligibility()).isEqualTo("CONDITIONAL");
        assertThat(status.eligibilityReasons()).containsExactly("ACCESS_EVIDENCE_MISSING");
        verify(qualityStatusReader).readLatest(Set.of(DATASET_ID));
    }
}
