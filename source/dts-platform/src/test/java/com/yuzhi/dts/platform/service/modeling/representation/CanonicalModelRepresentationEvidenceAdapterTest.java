package com.yuzhi.dts.platform.service.modeling.representation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelRepresentationEvidenceRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanonicalModelRepresentationEvidenceAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID IMPLEMENTATION_ID = UUID.randomUUID();
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);

    @Mock
    private ModelRepresentationEvidenceRepository evidence;

    @Mock
    private CatalogModelServingProjectionRepository catalog;

    @Test
    void returnsFrozenImplementationArtifactPublicationAndRuntimeEvidence() {
        ObjectMapper mapper = new ObjectMapper();
        ImplementationSnapshot implementation = new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            4,
            MODEL_CHECKSUM,
            7,
            IMPLEMENTATION_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            "finance",
            "model.finance.budget",
            "GENERATED",
            mapper.createArrayNode(),
            mapper.createArrayNode(),
            mapper.createObjectNode(),
            "table"
        );
        ArtifactEvidence artifact = new ArtifactEvidence(
            "SQL",
            "models/budget.sql",
            "c".repeat(64),
            "IMPORTED",
            "select 1",
            MODEL_ID,
            4,
            MODEL_CHECKSUM,
            7,
            IMPLEMENTATION_CHECKSUM
        );
        UUID candidateId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID pipelineRunId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-02T02:00:00Z");
        PublishedRef published = new PublishedRef(
            MODEL_ID,
            4,
            MODEL_CHECKSUM,
            7,
            IMPLEMENTATION_CHECKSUM,
            candidateId,
            2,
            2,
            null,
            observedAt
        );
        ServingRef serving = new ServingRef(
            MODEL_ID,
            4,
            MODEL_CHECKSUM,
            7,
            IMPLEMENTATION_CHECKSUM,
            candidateId,
            2,
            1,
            pipelineRunId,
            1,
            relationId,
            "d".repeat(64),
            null,
            null,
            "postgres",
            "warehouse",
            "dwd",
            "budget",
            observedAt
        );
        ModelServingProjection projection = new ModelServingProjection(
            TENANT,
            MODEL_ID,
            CatalogAssetType.DBT_MODEL,
            "catalog:model:" + MODEL_ID,
            published,
            serving,
            3,
            "SYNCED",
            observedAt
        );
        RelationEvidence relation = new RelationEvidence(
            TENANT,
            relationId,
            MODEL_ID,
            4,
            MODEL_CHECKSUM,
            7,
            IMPLEMENTATION_CHECKSUM,
            candidateId,
            2,
            "PUBLISHED",
            1,
            "COMPLETED",
            pipelineRunId,
            "BUILT",
            1,
            "postgres",
            "credential-v1",
            "warehouse",
            "dwd",
            "budget",
            ExpectedRelationType.TABLE,
            true,
            true,
            List.of(new PhysicalColumn(1, "budget_id", "bigint", false)),
            "d".repeat(64),
            observedAt
        );
        when(evidence.findExactImplementation(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 7)).thenReturn(Optional.of(implementation));
        when(evidence.listExactArtifacts(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 7, IMPLEMENTATION_CHECKSUM, true)).thenReturn(
            List.of(artifact)
        );
        when(catalog.findProjection(TENANT, MODEL_ID)).thenReturn(Optional.of(projection));
        when(catalog.findLatestSuccessfulCandidateEvidence(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 7, IMPLEMENTATION_CHECKSUM))
            .thenReturn(Optional.of(relation));

        var result = new CanonicalModelRepresentationEvidenceAdapter(evidence, catalog)
            .findExact(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 7, true)
            .orElseThrow();

        assertThat(result.implementation()).isEqualTo(implementation);
        assertThat(result.artifacts()).containsExactly(artifact);
        assertThat(result.latestPublished().assetRef()).isEqualTo("catalog:model:" + MODEL_ID);
        assertThat(result.serving().modelRevision()).isEqualTo(4);
        assertThat(result.runtime().evidenceId()).isEqualTo(relationId);
        assertThat(result.runtime().fields()).containsExactly(new ModelRepresentationEvidencePort.ObservedField("budget_id", "bigint"));
    }

    @Test
    void delegatesCurrentPinAndReturnsEmptyWhenTheExactImplementationDoesNotExist() {
        ImplementationPin pin = new ImplementationPin(IMPLEMENTATION_ID, 7, IMPLEMENTATION_CHECKSUM);
        when(evidence.findCurrentPin(TENANT, MODEL_ID)).thenReturn(Optional.of(pin));
        when(evidence.findExactImplementation(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 6)).thenReturn(Optional.empty());
        CanonicalModelRepresentationEvidenceAdapter adapter = new CanonicalModelRepresentationEvidenceAdapter(evidence, catalog);

        assertThat(adapter.findCurrentPin(TENANT, MODEL_ID)).contains(pin);
        assertThat(adapter.findExact(TENANT, MODEL_ID, 4, MODEL_CHECKSUM, 6, false)).isEmpty();

        verify(evidence).findCurrentPin(TENANT, MODEL_ID);
    }
}
