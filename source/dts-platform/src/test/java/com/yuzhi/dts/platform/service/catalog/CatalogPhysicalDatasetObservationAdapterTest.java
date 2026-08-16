package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService.ObservationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetSemanticsView;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.RegistrationReceipt;
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
class CatalogPhysicalDatasetObservationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-17T03:00:00Z");
    private static final UUID DATASET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Mock
    private CatalogAssetRegistrationService assets;

    private CatalogPhysicalDatasetObservationAdapter adapter;
    private CatalogDataset dataset;
    private String assetKey;

    @BeforeEach
    void setUp() {
        adapter = new CatalogPhysicalDatasetObservationAdapter(assets);
        dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        dataset.setSourceId(SOURCE_ID);
        dataset.setName("日期维度");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("dim_date");
        dataset.setWarehouseLayer("DIM");
        assetKey = CatalogAssetKey.dataset(dataset);
    }

    @Test
    void observesTheDurableDatasetAndPreservesGovernanceAndLifecycleAxes() {
        when(assets.find(CatalogAssetType.DATASET, assetKey)).thenReturn(Optional.of(existingSemantics(DATASET_ID)));
        when(assets.observe(any()))
            .thenReturn(
                new ObservationResult(
                    true,
                    false,
                    null,
                    new RegistrationReceipt(true, true, 8, NOW, CatalogAssetType.DATASET, assetKey, DATASET_ID)
                )
            );

        ObservationResult result = adapter.observe(dataset, observation());

        assertThat(result.receipt().resourceId()).isEqualTo(DATASET_ID);
        ArgumentCaptor<ObservationCommand> command = ArgumentCaptor.forClass(ObservationCommand.class);
        verify(assets).observe(command.capture());
        assertThat(command.getValue().assetKey()).isEqualTo(assetKey);
        assertThat(command.getValue().resourceId()).isEqualTo(DATASET_ID);
        assertThat(command.getValue().domainId()).isEqualTo(DOMAIN_ID);
        assertThat(command.getValue().warehouseLayer()).isEqualTo("DIM");
        assertThat(command.getValue().statusAxes().governance()).isEqualTo(GovernanceReadiness.GOVERNED);
        assertThat(command.getValue().statusAxes().publication()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(command.getValue().statusAxes().lifecycle()).isEqualTo(LifecycleState.DEPRECATED);
        assertThat(command.getValue().qualityGatePassed()).isTrue();
        assertThat(command.getValue().permissionGatePassed()).isFalse();
    }

    @Test
    void rejectsAConflictingDurableResourceBeforeWritingObservation() {
        when(assets.find(CatalogAssetType.DATASET, assetKey)).thenReturn(Optional.of(existingSemantics(UUID.randomUUID())));

        assertThatThrownBy(() -> adapter.observe(dataset, observation()))
            .isInstanceOfSatisfying(CatalogAssetObservationException.class, failure ->
                assertThat(failure.code()).isEqualTo("CATALOG_ASSET_RESOURCE_ID_CONFLICT")
            );
        verify(assets, never()).observe(any());
    }

    @Test
    void propagatesStructuredAdmissionFailure() {
        when(assets.find(CatalogAssetType.DATASET, assetKey)).thenReturn(Optional.empty());
        when(assets.observe(any())).thenReturn(new ObservationResult(false, false, "WAREHOUSE_LAYER_UNRECOGNIZED", null));

        assertThatThrownBy(() -> adapter.observe(dataset, observation()))
            .isInstanceOfSatisfying(CatalogAssetObservationException.class, failure ->
                assertThat(failure.code()).isEqualTo("WAREHOUSE_LAYER_UNRECOGNIZED")
            );
    }

    @Test
    void rejectsAReceiptForAnotherAssetIdentity() {
        when(assets.find(CatalogAssetType.DATASET, assetKey)).thenReturn(Optional.empty());
        when(assets.observe(any()))
            .thenReturn(
                new ObservationResult(
                    true,
                    false,
                    null,
                    new RegistrationReceipt(true, true, 1, NOW, CatalogAssetType.DATASET, assetKey, UUID.randomUUID())
                )
            );

        assertThatThrownBy(() -> adapter.observe(dataset, observation()))
            .isInstanceOfSatisfying(CatalogAssetObservationException.class, failure ->
                assertThat(failure.code()).isEqualTo("CATALOG_ASSET_OBSERVATION_RECEIPT_MISMATCH")
            );
    }

    private CatalogPhysicalDatasetObservationAdapter.DatasetObservation observation() {
        return new CatalogPhysicalDatasetObservationAdapter.DatasetObservation(
            RelationType.TABLE,
            null,
            null,
            AssetRole.DIMENSION_TABLE,
            ProducerKind.DBT_MODEL,
            "model.pjm.dim_date",
            "run:100",
            EvidenceChannel.DBT_SYNC,
            "dbt:model.pjm.dim_date:run:100",
            NOW,
            EvidenceStatus.ACTIVE,
            DiscoveryState.VERIFIED,
            null,
            ServingHealth.HEALTHY
        );
    }

    private AssetSemanticsView existingSemantics(UUID resourceId) {
        return new AssetSemanticsView(
            CatalogAssetType.DATASET,
            assetKey,
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
                GovernanceReadiness.GOVERNED,
                PublicationState.PUBLISHED,
                ServingHealth.HEALTHY,
                LifecycleState.DEPRECATED
            ),
            true,
            false,
            new ConsumptionEligibility(EligibilityDecision.BLOCKED, List.of("PERMISSION_GATE_PENDING"), NOW),
            7,
            NOW
        );
    }
}
