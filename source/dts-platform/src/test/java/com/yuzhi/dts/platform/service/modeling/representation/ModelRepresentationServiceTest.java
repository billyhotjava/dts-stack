package com.yuzhi.dts.platform.service.modeling.representation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.BusinessModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DriftStatus;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewEvidenceState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewReferenceProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.Provenance;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.TechnicalModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.PublicationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RuntimeEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelRepresentationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "user-83";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000083");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");
    private static final UUID EVIDENCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000083");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Mock
    private ModelSpecApplicationService modelSpecs;

    @Mock
    private ModelRepresentationEvidencePort evidencePort;

    private ObjectMapper objectMapper;
    private ModelRepresentationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ModelRepresentationService(
            modelSpecs,
            evidencePort,
            new ModelRepresentationProvenanceProjector(objectMapper),
            new ModelVisualizationCapabilityEvaluator(),
            objectMapper
        );
    }

    @Test
    void businessRepresentationUsesOwnerReadGateAndNeverSerializesTechnicalText() throws Exception {
        ModelSpecView model = model(ImplementationMode.DBT_MANAGED);
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model);
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(
            Optional.of(exactEvidence(false))
        );

        BusinessModelRepresentationView view = business(exactEvidence(false));
        String json = objectMapper.writeValueAsString(view);

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.BUSINESS_VISUAL_READ);
        assertThat(view.capabilityReasons()).allSatisfy(reason -> assertThat(reason).isInstanceOf(CapabilityReason.class));
        assertThat(view.logicalModel().fields()).singleElement().satisfies(field -> {
            assertThat(field.name()).isEqualTo("budget_id");
            assertThat(field.provenance().source()).isEqualTo(Provenance.OBSERVED);
        });
        assertThat(json)
            .doesNotContain("technicalImplementation")
            .doesNotContain("artifactContent")
            .doesNotContain("select secret_value")
            .doesNotContain("models/dwd/budget.sql")
            .doesNotContain("malicious-secret")
            .doesNotContain("\"sql");
        verify(modelSpecs).revision(TENANT, new ModelRevisionRef(MODEL_ID, 2));
        verify(evidencePort).findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false);
    }

    @Test
    void businessRepresentationCarriesCompleteServerIssuedPreviewPins() throws Exception {
        ModelPhysicalPreviewReferencePort previewReferences = org.mockito.Mockito.mock(ModelPhysicalPreviewReferencePort.class);
        ModelRepresentationService previewService = new ModelRepresentationService(
            modelSpecs,
            evidencePort,
            previewReferences,
            new ModelRepresentationProvenanceProjector(objectMapper),
            new ModelVisualizationCapabilityEvaluator(),
            objectMapper
        );
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(Optional.of(exactEvidence(false)));
        PhysicalPreviewReferenceProjection serving = new PhysicalPreviewReferenceProjection(
            MODEL_ID,
            1,
            "1".repeat(64),
            2,
            "2".repeat(64),
            PhysicalPreviewScope.SERVING,
            UUID.fromString("60000000-0000-0000-0000-000000000083"),
            7,
            2,
            UUID.fromString("70000000-0000-0000-0000-000000000083"),
            1,
            EVIDENCE_ID,
            "d".repeat(64)
        );
        UUID candidateEvidenceId = UUID.fromString("80000000-0000-0000-0000-000000000083");
        PhysicalPreviewReferenceProjection candidate = new PhysicalPreviewReferenceProjection(
            MODEL_ID,
            2,
            MODEL_CHECKSUM,
            3,
            IMPLEMENTATION_CHECKSUM,
            PhysicalPreviewScope.CANDIDATE,
            UUID.fromString("90000000-0000-0000-0000-000000000083"),
            8,
            3,
            UUID.fromString("a0000000-0000-0000-0000-000000000083"),
            2,
            candidateEvidenceId,
            "e".repeat(64)
        );
        when(previewReferences.resolve(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, IMPLEMENTATION_CHECKSUM))
            .thenReturn(
                new PhysicalPreviewProjection(
                    serving,
                    candidate,
                    PhysicalPreviewEvidenceState.READY,
                    PhysicalPreviewEvidenceState.READY
                )
            );

        BusinessModelRepresentationView view = (BusinessModelRepresentationView) previewService.get(
            TENANT, ACTOR, MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false
        );
        String json = objectMapper.writeValueAsString(view);

        assertThat(view.physicalPreview().serving()).isEqualTo(serving);
        assertThat(view.physicalPreview().candidate()).isNull();
        assertThat(view.physicalPreview().candidateStatus()).isEqualTo(PhysicalPreviewEvidenceState.UNAVAILABLE);
        assertThat(json)
            .contains("\"relationEvidenceId\":\"" + EVIDENCE_ID + "\"")
            .contains("\"modelChecksum\":\"" + "1".repeat(64) + "\"")
            .doesNotContain(candidateEvidenceId.toString())
            .doesNotContain("schemaName")
            .doesNotContain("identifier");
    }

    @Test
    void invisibleModelsRemainOwnerControlledNotFoundAndDoNotReadEvidence() {
        ModelSpecException invisible = new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec was not found",
            ModelSpecException.Kind.NOT_FOUND
        );
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenThrow(invisible);

        assertThatThrownBy(() -> service.get(TENANT, ACTOR, MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false))
            .isSameAs(invisible);
        verifyNoInteractions(evidencePort);
    }

    @Test
    void technicalRepresentationAppliesOwnerReadGateBeforeTheAdditionalTechnicalAuthority() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));

        assertThatThrownBy(() -> service.get(TENANT, ACTOR, MODEL_ID, 2, 3, RepresentationScope.TECHNICAL, false))
            .isInstanceOf(ModelRepresentationException.class)
            .extracting("code")
            .isEqualTo("MODEL_REPRESENTATION_TECHNICAL_ACCESS_DENIED");

        verify(modelSpecs).revision(TENANT, new ModelRevisionRef(MODEL_ID, 2));
        verifyNoInteractions(evidencePort);
    }

    @Test
    void missingCanonicalActorIsRejectedBeforeOwnerDataIsRead() {
        assertThatThrownBy(() -> service.get(TENANT, null, MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false))
            .isInstanceOf(ModelRepresentationException.class)
            .extracting("code")
            .isEqualTo("MODEL_REPRESENTATION_ACTOR_REQUIRED");
        verifyNoInteractions(modelSpecs, evidencePort);
    }

    @Test
    void returnsBlockedLogicalRepresentationWhenTheModelHasNoImplementation() {
        ModelSpecView model = model(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model);
        when(evidencePort.findCurrentPin(TENANT, MODEL_ID)).thenReturn(Optional.empty());

        BusinessModelRepresentationView view = (BusinessModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            null,
            RepresentationScope.BUSINESS,
            false
        );

        assertThat(view.implementationRevision()).isNull();
        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.BLOCKED);
        assertThat(view.capabilityReasons()).containsExactly(CapabilityReason.MODEL_REPRESENTATION_NO_IMPLEMENTATION);
        assertThat(view.logicalModel().provenance().source()).isEqualTo(Provenance.DECLARED);
        verify(evidencePort, never()).findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 1, false);
    }

    @Test
    void technicalRepresentationAllowsAdvancedDbtDraftWhenManagedModelHasNoImplementation() {
        ModelSpecView model = model(ImplementationMode.DBT_MANAGED);
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model);
        when(evidencePort.findCurrentPin(TENANT, MODEL_ID)).thenReturn(Optional.empty());

        TechnicalModelRepresentationView view = (TechnicalModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            null,
            RepresentationScope.TECHNICAL,
            true
        );

        assertThat(view.implementationRevision()).isNull();
        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION);
        assertThat(view.allowedActions()).containsExactly("OPEN_ADVANCED_DBT");
    }

    @Test
    void requiresAnExplicitImplementationPinWhenAnImplementationAlreadyExists() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        when(evidencePort.findCurrentPin(TENANT, MODEL_ID)).thenReturn(
            Optional.of(new ImplementationPin(IMPLEMENTATION_ID, 3, IMPLEMENTATION_CHECKSUM))
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(Optional.of(exactEvidence(false)));

        assertThatThrownBy(() -> service.get(TENANT, ACTOR, MODEL_ID, 2, null, RepresentationScope.BUSINESS, false))
            .isInstanceOf(ModelRepresentationException.class)
            .extracting("code")
            .isEqualTo("MODEL_REPRESENTATION_IMPLEMENTATION_PIN_REQUIRED");
    }

    @Test
    void allowsAdvancedDraftWhenOnlyAPriorLogicalRevisionHasAnImplementation() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        when(evidencePort.findCurrentPin(TENANT, MODEL_ID)).thenReturn(
            Optional.of(new ImplementationPin(IMPLEMENTATION_ID, 3, IMPLEMENTATION_CHECKSUM))
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, true)).thenReturn(Optional.empty());

        TechnicalModelRepresentationView view = (TechnicalModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            null,
            RepresentationScope.TECHNICAL,
            true
        );

        assertThat(view.implementationRevision()).isNull();
        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION);
        assertThat(view.allowedActions()).containsExactly("OPEN_ADVANCED_DBT");
    }

    @Test
    void rejectsImplementationAndArtifactPinMismatchesStructurally() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence evidence = exactEvidence(false);
        ImplementationSnapshot wrongModelPin = new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            1,
            "0".repeat(64),
            3,
            IMPLEMENTATION_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            "finance",
            "model.finance.budget_fact",
            "GENERATED",
            evidence.implementation().inputs(),
            evidence.implementation().fieldMappings(),
            evidence.implementation().settings(),
            "table"
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(
            Optional.of(new RepresentationEvidence(wrongModelPin, evidence.artifacts(), evidence.latestPublished(), evidence.serving(), evidence.runtime(), List.of()))
        );

        assertThatThrownBy(() -> service.get(TENANT, ACTOR, MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false))
            .isInstanceOf(ModelRepresentationException.class)
            .extracting("code")
            .isEqualTo("MODEL_REPRESENTATION_PIN_MISMATCH");
    }

    @Test
    void rejectsAnArtifactWhoseImplementationPinDoesNotMatch() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence evidence = exactEvidence(false);
        ArtifactEvidence wrongArtifact = new ArtifactEvidence(
            "SCHEMA",
            "malicious-secret/path.yml",
            "f".repeat(64),
            "COMPILED",
            "{\"columns\":[]}",
            MODEL_ID,
            2,
            MODEL_CHECKSUM,
            99,
            "9".repeat(64)
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(
            Optional.of(new RepresentationEvidence(evidence.implementation(), List.of(wrongArtifact), evidence.latestPublished(), evidence.serving(), null, List.of()))
        );

        assertThatThrownBy(() -> service.get(TENANT, ACTOR, MODEL_ID, 2, 3, RepresentationScope.BUSINESS, false))
            .isInstanceOf(ModelRepresentationException.class)
            .extracting("code")
            .isEqualTo("MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH");
    }

    @Test
    void staleRuntimeEvidenceNeverOverridesCompiledProvenance() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence evidence = exactEvidence(false);
        RuntimeEvidence stale = runtime(1, "c".repeat(64), OBSERVED_AT);
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(
            Optional.of(new RepresentationEvidence(evidence.implementation(), evidence.artifacts(), evidence.latestPublished(), evidence.serving(), stale, List.of()))
        );

        BusinessModelRepresentationView view = (BusinessModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.BUSINESS,
            false
        );

        assertThat(view.runtimeObservation().driftStatus()).isEqualTo(DriftStatus.STALE);
        assertThat(view.logicalModel().fields()).singleElement().satisfies(field ->
            assertThat(field.provenance().source()).isEqualTo(Provenance.COMPILED)
        );
        assertThat(view.capabilityReasons()).contains(CapabilityReason.MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE);
    }

    @Test
    void technicalRepresentationIncludesControlledArtifactsOnlyAfterAuthorization() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, true)).thenReturn(
            Optional.of(exactEvidence(true))
        );

        TechnicalModelRepresentationView view = (TechnicalModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.TECHNICAL,
            true
        );

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION);
        assertThat(view.technicalImplementation().artifacts())
            .extracting("artifactContent")
            .contains("select secret_value from raw_budget");
    }

    @Test
    void designerOwnershipRemainsVisuallyEditableWhenStrictSchemaIsTrusted() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DESIGNER_GENERATED));
        RepresentationEvidence evidence = withOwnership(exactEvidence(false), ImplementationMode.DESIGNER_GENERATED);
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(Optional.of(evidence));

        BusinessModelRepresentationView view = (BusinessModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.BUSINESS,
            false
        );

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.BUSINESS_VISUAL_EDIT);
    }

    @Test
    void designerOwnershipExposesPinnedReadOnlyDbtPreviewWithoutAdvancedWriteAction() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DESIGNER_GENERATED));
        RepresentationEvidence evidence = withOwnership(exactEvidence(true), ImplementationMode.DESIGNER_GENERATED);
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, true)).thenReturn(Optional.of(evidence));

        TechnicalModelRepresentationView view = (TechnicalModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.TECHNICAL,
            true
        );

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.DESIGNER_DBT_PREVIEW);
        assertThat(view.allowedActions()).containsExactly("OPEN_DBT_PREVIEW");
        assertThat(view.allowedActions()).doesNotContain("OPEN_ADVANCED_DBT");
    }

    @Test
    void managedTechnicalRepresentationRemainsEditableWhileBusinessProjectionEvidenceIsIncomplete() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence evidence = exactEvidence(true);
        ArtifactEvidence emptySchema = artifact("SCHEMA", "models/dwd/budget.sql#schema", "{\"columns\":[],\"tests\":[]}");
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, true)).thenReturn(
            Optional.of(
                new RepresentationEvidence(
                    evidence.implementation(),
                    List.of(evidence.artifacts().get(0), emptySchema),
                    evidence.latestPublished(),
                    evidence.serving(),
                    null,
                    evidence.projectionIssues()
                )
            )
        );

        TechnicalModelRepresentationView view = (TechnicalModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.TECHNICAL,
            true
        );

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION);
        assertThat(view.allowedActions()).containsExactly("OPEN_ADVANCED_DBT");
        assertThat(view.capabilityReasons()).contains(CapabilityReason.MODEL_REPRESENTATION_FIELDS_UNTRUSTED);
    }

    @Test
    void nestedArbitraryNameNodesAreNotTrustedAsSchemaAndNeverLeakThroughReasons() throws Exception {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence evidence = exactEvidence(false);
        ArtifactEvidence malicious = artifact(
            "SCHEMA",
            "malicious-secret/schema.json",
            "{\"payload\":{\"name\":\"budget_id\",\"dataType\":\"varchar\",\"text\":\"select malicious-secret\"}}"
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false)).thenReturn(
            Optional.of(new RepresentationEvidence(evidence.implementation(), List.of(malicious), evidence.latestPublished(), evidence.serving(), null, List.of()))
        );

        BusinessModelRepresentationView view = (BusinessModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.BUSINESS,
            false
        );
        String json = objectMapper.writeValueAsString(view);

        assertThat(view.visualizationCapability()).isEqualTo(VisualizationCapability.BLOCKED);
        assertThat(view.capabilityReasons()).contains(
            CapabilityReason.MODEL_REPRESENTATION_FIELDS_UNTRUSTED,
            CapabilityReason.MODEL_REPRESENTATION_SCHEMA_INVALID
        );
        assertThat(json).doesNotContain("malicious-secret").doesNotContain("select");
    }

    @Test
    void etagChangesForEveryPublishedServingAndRuntimePointerChange() {
        when(modelSpecs.revision(TENANT, new ModelRevisionRef(MODEL_ID, 2))).thenReturn(model(ImplementationMode.DBT_MANAGED));
        RepresentationEvidence base = exactEvidence(false);
        RepresentationEvidence publishedChanged = new RepresentationEvidence(
            base.implementation(),
            base.artifacts(),
            new PublicationEvidence(2, MODEL_CHECKSUM, 3, IMPLEMENTATION_CHECKSUM, "catalog:budget:r2-other"),
            base.serving(),
            base.runtime(),
            List.of()
        );
        RepresentationEvidence servingChanged = new RepresentationEvidence(
            base.implementation(),
            base.artifacts(),
            base.latestPublished(),
            new PublicationEvidence(1, "1".repeat(64), 2, "2".repeat(64), "catalog:budget:r1-other"),
            base.runtime(),
            List.of()
        );
        RepresentationEvidence runtimeChanged = new RepresentationEvidence(
            base.implementation(),
            base.artifacts(),
            base.latestPublished(),
            base.serving(),
            runtime(2, MODEL_CHECKSUM, OBSERVED_AT.plusSeconds(1)),
            List.of()
        );
        when(evidencePort.findExact(TENANT, MODEL_ID, 2, MODEL_CHECKSUM, 3, false))
            .thenReturn(Optional.of(base), Optional.of(publishedChanged), Optional.of(servingChanged), Optional.of(runtimeChanged));

        String baseEtag = getBusiness().etag();
        assertThat(getBusiness().etag()).isNotEqualTo(baseEtag);
        assertThat(getBusiness().etag()).isNotEqualTo(baseEtag);
        assertThat(getBusiness().etag()).isNotEqualTo(baseEtag);
    }

    private BusinessModelRepresentationView business(RepresentationEvidence ignored) {
        return getBusiness();
    }

    private BusinessModelRepresentationView getBusiness() {
        return (BusinessModelRepresentationView) service.get(
            TENANT,
            ACTOR,
            MODEL_ID,
            2,
            3,
            RepresentationScope.BUSINESS,
            false
        );
    }

    private ModelSpecView model(ImplementationMode implementationMode) {
        return new ModelSpecView(
            2,
            MODEL_ID,
            PLAN_ID,
            UUID.fromString("50000000-0000-0000-0000-000000000083"),
            ModelType.FACT,
            Layer.DWD,
            "budget_fact",
            "预算事实",
            implementationMode,
            "table",
            null,
            null,
            null,
            null,
            null,
            List.of(new ModelField("budget_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            2,
            MODEL_CHECKSUM,
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private RepresentationEvidence exactEvidence(boolean includeTechnicalContent) {
        ImplementationSnapshot implementation = implementation(ImplementationMode.DBT_MANAGED);
        List<ArtifactEvidence> artifacts = List.of(
            artifact(
                "SQL",
                "models/dwd/budget.sql",
                includeTechnicalContent ? "select secret_value from raw_budget" : null
            ),
            artifact(
                "SCHEMA",
                "models/dwd/schema.json",
                "{\"columns\":[{\"name\":\"budget_id\",\"dataType\":\"varchar\"}]}"
            )
        );
        PublicationEvidence published = new PublicationEvidence(2, MODEL_CHECKSUM, 3, IMPLEMENTATION_CHECKSUM, "catalog:budget:r2");
        PublicationEvidence serving = new PublicationEvidence(1, "1".repeat(64), 2, "2".repeat(64), "catalog:budget:r1");
        return new RepresentationEvidence(implementation, artifacts, published, serving, runtime(2, MODEL_CHECKSUM, OBSERVED_AT), List.of());
    }

    private ImplementationSnapshot implementation(ImplementationMode ownership) {
        return new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            2,
            MODEL_CHECKSUM,
            3,
            IMPLEMENTATION_CHECKSUM,
            ownership,
            "finance",
            "model.finance.budget_fact",
            "GENERATED",
            objectMapper.valueToTree(List.of(Map.of("generatorType", "DBT"))),
            objectMapper.createArrayNode(),
            objectMapper.createObjectNode(),
            "table"
        );
    }

    private ArtifactEvidence artifact(String type, String path, String content) {
        return new ArtifactEvidence(
            type,
            path,
            "f".repeat(64),
            "COMPILED",
            content,
            MODEL_ID,
            2,
            MODEL_CHECKSUM,
            3,
            IMPLEMENTATION_CHECKSUM
        );
    }

    private RuntimeEvidence runtime(int modelRevision, String modelChecksum, Instant observedAt) {
        return new RuntimeEvidence(
            EVIDENCE_ID,
            MODEL_ID,
            modelRevision,
            modelChecksum,
            3,
            IMPLEMENTATION_CHECKSUM,
            true,
            true,
            "d".repeat(64),
            observedAt,
            List.of(new ModelRepresentationEvidencePort.ObservedField("budget_id", "varchar"))
        );
    }

    private RepresentationEvidence withOwnership(RepresentationEvidence evidence, ImplementationMode ownership) {
        return new RepresentationEvidence(
            implementation(ownership),
            evidence.artifacts(),
            evidence.latestPublished(),
            evidence.serving(),
            evidence.runtime(),
            evidence.projectionIssues()
        );
    }
}
