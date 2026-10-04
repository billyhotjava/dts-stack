package com.yuzhi.dts.platform.service.modeling.representation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewEvidenceState;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogModelPhysicalPreviewReferenceAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final UUID MODEL = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PIPELINE = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID EVIDENCE = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final String MODEL_SUM = "a".repeat(64);
    private static final String IMPLEMENTATION_SUM = "b".repeat(64);
    private static final String EVIDENCE_SUM = "c".repeat(64);

    @Test
    void emitsACompleteServingReferenceOnlyWhenTheCatalogPointerAndObservationMatch() {
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        RelationEvidence evidence = evidence(EVIDENCE_SUM);
        ServingRef serving = new ServingRef(
            MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM, CANDIDATE, 11, 2, PIPELINE, 1,
            EVIDENCE, EVIDENCE_SUM, UUID.randomUUID(), UUID.randomUUID(), "postgres", "warehouse",
            "finance", "dwd_budget", evidence.observedAt()
        );
        when(repository.findProjection(TENANT, MODEL)).thenReturn(
            Optional.of(
                new ModelServingProjection(
                    TENANT, MODEL, CatalogAssetType.SEMANTIC_MODEL, "semantic-model:" + MODEL,
                    null, serving, 2, "SYNCED", evidence.observedAt()
                )
            )
        );
        when(repository.findRelationEvidence(TENANT, EVIDENCE)).thenReturn(Optional.of(evidence));
        when(repository.findLatestSuccessfulCandidateEvidence(TENANT, MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM))
            .thenReturn(Optional.of(evidence));

        var projection = new CatalogModelPhysicalPreviewReferenceAdapter(repository)
            .resolve(TENANT, MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM);

        assertThat(projection.servingStatus()).isEqualTo(PhysicalPreviewEvidenceState.READY);
        assertThat(projection.serving()).satisfies(ref -> {
            assertThat(ref.modelChecksum()).isEqualTo(MODEL_SUM);
            assertThat(ref.implementationChecksum()).isEqualTo(IMPLEMENTATION_SUM);
            assertThat(ref.relationEvidenceId()).isEqualTo(EVIDENCE);
            assertThat(ref.evidenceChecksum()).isEqualTo(EVIDENCE_SUM);
        });
        assertThat(projection.candidate()).isNull();
    }

    @Test
    void staleServingEvidenceProducesAStableStateAndNeverAForgedReference() {
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        RelationEvidence evidence = evidence("d".repeat(64));
        ServingRef serving = new ServingRef(
            MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM, CANDIDATE, 11, 2, PIPELINE, 1,
            EVIDENCE, EVIDENCE_SUM, UUID.randomUUID(), UUID.randomUUID(), "postgres", "warehouse",
            "finance", "dwd_budget", evidence.observedAt()
        );
        when(repository.findProjection(TENANT, MODEL)).thenReturn(
            Optional.of(new ModelServingProjection(TENANT, MODEL, CatalogAssetType.SEMANTIC_MODEL,
                "semantic-model:" + MODEL, null, serving, 2, "SYNCED", evidence.observedAt()))
        );
        when(repository.findRelationEvidence(TENANT, EVIDENCE)).thenReturn(Optional.of(evidence));

        var projection = new CatalogModelPhysicalPreviewReferenceAdapter(repository)
            .resolve(TENANT, MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM);

        assertThat(projection.serving()).isNull();
        assertThat(projection.servingStatus()).isEqualTo(PhysicalPreviewEvidenceState.FAILED_STALE);
    }

    private static RelationEvidence evidence(String checksum) {
        return new RelationEvidence(
            TENANT, EVIDENCE, MODEL, 4, MODEL_SUM, 7, IMPLEMENTATION_SUM, CANDIDATE, 11,
            "PUBLISHED", 2, "COMPLETED", PIPELINE, "BUILT", 1, "postgres",
            "sha256:" + "9".repeat(64), "warehouse", "finance", "dwd_budget",
            ExpectedRelationType.TABLE, true, true,
            List.of(new PhysicalColumn(1, "account_code", "text", false)), checksum,
            Instant.parse("2026-08-02T10:00:00Z")
        );
    }
}
