package com.yuzhi.dts.platform.service.modeling;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetSemanticsView;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.RegistrationReceipt;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPublicationAssetObservationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-16T08:00:00Z");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PHYSICAL_ASSET_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID PIPELINE_RUN_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final String ASSET_KEY =
        "source:" + SOURCE_ID + "/schema:finance/table:dim_calendar";

    @Mock
    private CatalogAssetRegistrationService assets;

    @Mock
    private CandidateView candidate;

    @Mock
    private ResolvedCatalogTarget target;

    @Mock
    private PublicationEntryEvidence observation;

    @Mock
    private ModelSpecView model;

    private ModelPublicationAssetObservationAdapter adapter;
    private PublishedModelBinding binding;

    @BeforeEach
    void setUp() {
        adapter = new ModelPublicationAssetObservationAdapter(assets);
        binding = new PublishedModelBinding(
            MODEL_ID,
            UUID.randomUUID(),
            2,
            "model.finance.dim_calendar",
            "dim_calendar",
            "a".repeat(64),
            "b".repeat(64),
            PHYSICAL_ASSET_ID
        );
        lenient().when(candidate.id()).thenReturn(CANDIDATE_ID);
        lenient().when(candidate.version()).thenReturn(7);
        lenient().when(target.sourceId()).thenReturn(SOURCE_ID);
        lenient().when(observation.databaseName()).thenReturn("warehouse");
        lenient().when(observation.schemaName()).thenReturn("finance");
        lenient().when(observation.identifier()).thenReturn("dim_calendar");
        lenient().when(observation.relationType()).thenReturn(ExpectedRelationType.TABLE);
        lenient().when(observation.modelRevision()).thenReturn(2);
        lenient().when(observation.implementationRevision()).thenReturn(3);
        lenient().when(observation.pipelineRunId()).thenReturn(PIPELINE_RUN_ID);
        lenient().when(observation.metadataChecksum()).thenReturn("c".repeat(64));
        lenient().when(observation.observedAt()).thenReturn(NOW.minusSeconds(30));
        lenient().when(model.id()).thenReturn(MODEL_ID);
        lenient().when(model.name()).thenReturn("日期维度");
        lenient().when(model.domainId()).thenReturn(DOMAIN_ID);
        lenient().when(model.modelType()).thenReturn(ModelType.DIMENSION);
        lenient().when(model.warehouseLayerCode()).thenReturn("DIM");
    }

    @Test
    void registersPublishedPhysicalIdentityThroughTheSingleObservationBoundary() {
        when(assets.find(CatalogAssetType.DATASET, ASSET_KEY)).thenReturn(Optional.empty());
        when(assets.observe(any()))
            .thenReturn(
                new CatalogAssetRegistrationService.ObservationResult(
                    true,
                    false,
                    null,
                    new RegistrationReceipt(
                        true,
                        true,
                        1,
                        NOW,
                        CatalogAssetType.DATASET,
                        ASSET_KEY,
                        PHYSICAL_ASSET_ID
                    )
                )
            );

        CatalogAssetRegistrationService.ObservationResult result = adapter.observePublishedAsset(
            candidate,
            target,
            observation,
            model,
            binding,
            NOW
        );

        assertThat(result.receipt().resourceId()).isEqualTo(PHYSICAL_ASSET_ID);
        ArgumentCaptor<ObservationCommand> command = ArgumentCaptor.forClass(ObservationCommand.class);
        verify(assets).observe(command.capture());
        assertThat(command.getValue().assetKey()).isEqualTo(ASSET_KEY);
        assertThat(command.getValue().resourceId()).isEqualTo(PHYSICAL_ASSET_ID);
        assertThat(command.getValue().relationType()).isEqualTo(RelationType.TABLE);
        assertThat(command.getValue().warehouseLayer()).isEqualTo("DIM");
        assertThat(command.getValue().assetRole()).isEqualTo(AssetRole.DIMENSION_TABLE);
        assertThat(command.getValue().producerKind()).isEqualTo(ProducerKind.MODELING);
        assertThat(command.getValue().evidenceChannel()).isEqualTo(EvidenceChannel.MATERIALIZATION_OBSERVATION);
        assertThat(command.getValue().statusAxes().publication()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(command.getValue().statusAxes().serving()).isEqualTo(ServingHealth.HEALTHY);
    }

    @Test
    void failsClosedWhenTheSameAssetKeyPointsAtAnotherDurableResource() {
        UUID conflictingResourceId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        when(assets.find(CatalogAssetType.DATASET, ASSET_KEY))
            .thenReturn(Optional.of(existingSemantics(conflictingResourceId)));

        assertThatThrownBy(() ->
            adapter.observePublishedAsset(candidate, target, observation, model, binding, NOW)
        )
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, failure ->
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_CATALOG_ASSET_IDENTITY_CONFLICT")
            );
        verify(assets, never()).observe(any());
    }

    @Test
    void convertsStructuredObservationRejectionIntoPublicationFailure() {
        when(assets.find(CatalogAssetType.DATASET, ASSET_KEY)).thenReturn(Optional.empty());
        when(assets.observe(any()))
            .thenReturn(
                new CatalogAssetRegistrationService.ObservationResult(
                    false,
                    false,
                    "WAREHOUSE_LAYER_UNRECOGNIZED",
                    null
                )
            );

        assertThatThrownBy(() ->
            adapter.observePublishedAsset(candidate, target, observation, model, binding, NOW)
        )
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, failure -> {
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_CATALOG_ASSET_OBSERVATION_REJECTED");
                assertThat(failure.details().toString()).contains("WAREHOUSE_LAYER_UNRECOGNIZED");
            });
    }

    private AssetSemanticsView existingSemantics(UUID resourceId) {
        return new AssetSemanticsView(
            CatalogAssetType.DATASET,
            CatalogAssetKey.dataset(SOURCE_ID, "warehouse", "finance", "dim_calendar", "日期维度"),
            resourceId,
            RelationType.TABLE,
            DOMAIN_ID,
            "DWD",
            "DIM",
            AssetRole.DIMENSION_TABLE,
            null,
            List.of(),
            new StatusAxes(
                DiscoveryState.VERIFIED,
                GovernanceReadiness.INCOMPLETE,
                PublicationState.PUBLISHED,
                ServingHealth.HEALTHY,
                LifecycleState.ACTIVE
            ),
            null,
            null,
            new ConsumptionEligibility(EligibilityDecision.CONDITIONAL, List.of(), NOW),
            1,
            NOW
        );
    }
}
