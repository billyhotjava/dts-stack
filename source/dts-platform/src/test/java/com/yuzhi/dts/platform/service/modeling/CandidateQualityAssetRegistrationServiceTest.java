package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService.ObservationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.RegistrationReceipt;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.PublicationState;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter.DatasetObservation;
import com.yuzhi.dts.platform.service.modeling.ModelClassificationPublishGate.Decision;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateQualityAssetRegistrationServiceTest {

    private static final String TENANT = "tenant-a";
    private static final UUID MODEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID DOMAIN_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-09-04T01:00:00Z");

    @Mock
    private CandidatePublicationEvidenceRepository evidence;

    @Mock
    private ModelExecutionTargetCatalogResolver targets;

    @Mock
    private ModelSpecReader models;

    @Mock
    private ModelClassificationPublishGate classifications;

    @Mock
    private CandidatePublicationRepository publications;

    @Mock
    private CatalogDatasetRepository datasets;

    @Mock
    private CatalogPhysicalDatasetObservationAdapter observations;

    private CandidateQualityAssetRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CandidateQualityAssetRegistrationService(
            evidence,
            targets,
            models,
            classifications,
            publications,
            datasets,
            observations,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void preparesVerifiedUnpublishedPhysicalAssetForGovernanceQuality() {
        CandidateView candidate = mock(CandidateView.class);
        PublicationEntryEvidence physical = physicalEvidence();
        ModelSpecView model = mock(ModelSpecView.class);
        ResolvedCatalogTarget target = new ResolvedCatalogTarget("postgres:warehouse/prod", SOURCE_ID, "postgres");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);

        when(candidate.status()).thenReturn(DeliveryStatus.QUALITY_RUNNING);
        when(candidate.tenantId()).thenReturn(TENANT);
        when(candidate.version()).thenReturn(7);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(physical));
        when(targets.resolve(candidate)).thenReturn(target);
        when(models.revision(TENANT, new ModelRevisionRef(MODEL_ID, 3))).thenReturn(model);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(model.domainId()).thenReturn(DOMAIN_ID);
        when(model.warehouseLayerCode()).thenReturn("DWD");
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(classifications.evaluate(TENANT, MODEL_ID, 3, "a".repeat(64)))
            .thenReturn(
                new Decision(
                    true,
                    MODEL_ID,
                    3,
                    "dbt:model.dts.prjdemo_dwd_project_task_snapshot",
                    "CONFIDENTIAL",
                    Map.of("source", "CONFIDENTIAL"),
                    Map.of(),
                    List.of(),
                    null
                )
            );
        when(
            publications.prepareQualityDataset(
                eq(candidate),
                eq(target),
                eq(physical),
                eq(model),
                eq("CONFIDENTIAL"),
                eq("service:dts-platform-quality"),
                eq(NOW)
            )
        ).thenReturn(DATASET_ID);
        when(datasets.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(observations.observe(eq(dataset), org.mockito.ArgumentMatchers.any()))
            .thenReturn(
                new ObservationResult(
                    true,
                    false,
                    null,
                    new RegistrationReceipt(true, true, 1, NOW, null, null, null)
                )
            );

        List<UUID> registered = service.ensureRegistered(candidate);

        assertThat(registered).containsExactly(DATASET_ID);
        ArgumentCaptor<DatasetObservation> observation = ArgumentCaptor.forClass(DatasetObservation.class);
        verify(observations).observe(eq(dataset), observation.capture());
        assertThat(observation.getValue().publication()).isEqualTo(PublicationState.UNPUBLISHED);
        assertThat(observation.getValue().warehouseLayer()).isEqualTo("DWD");
        assertThat(observation.getValue().domainId()).isEqualTo(DOMAIN_ID);
    }

    @Test
    void acceptsBuildVerifiedCandidatesBeforeQualityStarts() {
        CandidateView candidate = mock(CandidateView.class);
        when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
        when(candidate.tenantId()).thenReturn(TENANT);
        when(candidate.version()).thenReturn(7);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ensureRegistered(candidate))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .hasMessageContaining("Candidate has no verified physical outputs");

        verify(evidence).requireCurrent(candidate, false);
    }

    private static PublicationEntryEvidence physicalEvidence() {
        return new PublicationEntryEvidence(
            UUID.randomUUID(),
            MODEL_ID,
            3,
            "a".repeat(64),
            2,
            "b".repeat(64),
            "model.dts.prjdemo_dwd_project_task_snapshot",
            "prjdemo_dwd_project_task_snapshot",
            "c".repeat(64),
            "d".repeat(64),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "postgres",
            "biadmin",
            "public",
            "prjdemo_dwd_project_task_snapshot",
            ExpectedRelationType.TABLE,
            List.of(new PhysicalColumn(1, "task_snapshot_id", "text", false)),
            "e".repeat(64),
            NOW.minusSeconds(10)
        );
    }
}
