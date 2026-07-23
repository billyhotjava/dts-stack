package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStandardEvidencePort.StandardEvidence;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateEvidence;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelSpecStageGateServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void separatesLegacyCompatibleDraftSaveFromDimensionImplementationRequirements() {
        ModelSpecView legacyCompatible = dimension(null, List.of(), null);

        assertThat(ModelSpecStageGateService.evaluate(legacyCompatible, Stage.DRAFT_SAVE, GateEvidence.currentFor(legacyCompatible)).status())
            .isEqualTo(GateStatus.READY);
        assertThat(
            ModelSpecStageGateService
                .evaluate(legacyCompatible, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(legacyCompatible))
                .blockers()
        )
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_DIMENSION_PROFILE_REQUIRED", "MODEL_SPEC_DIMENSION_INPUT_REQUIRED");
    }

    @Test
    void acceptsARevisionPinnedDimensionWithClosedKeysHierarchyScdAndGeneratedInput() {
        DimensionProfile profile = new DimensionProfile(
            "DIM_ORGANIZATION",
            List.of(
                new DimensionHierarchy(
                    "ORG_TREE",
                    "Organization hierarchy",
                    List.of(new DimensionLevel("organization_id", 1), new DimensionLevel("organization_name", 2))
                )
            ),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master"));

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void factImplementationExplainsShapeTimeAndFieldRepairsWithoutRequiringActivity() {
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.PERIODIC_SNAPSHOT,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            sources(),
            List.of(),
            List.of(),
            null
        );

        assertThat(model.businessActivityRef()).isNull();
        assertThat(
            ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).blockers()
        )
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_FACT_TIME_SHAPE_MISMATCH");
    }

    @Test
    void factDraftWithoutInputIsSavableButImplementationExplainsTheMissingInput() {
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of(),
            List.of(),
            List.of(),
            null
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.DRAFT_SAVE, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
        assertThat(
            ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).blockers()
        )
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_FACT_INPUT_REQUIRED");
    }

    @Test
    void legacyOdsFactRemainsReadableButEveryNewGateExplainsTheLayerMismatch() {
        ModelSpecView odsFact = withLayer(
            view(
                ModelType.FACT,
                null,
                FactShape.TRANSACTION,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
                sources(),
                List.of(),
                List.of(),
                null
            ),
            Layer.ODS
        );

        assertThat(ModelSpecStageGateService.evaluate(odsFact, Stage.DRAFT_SAVE, GateEvidence.currentFor(odsFact)).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_TYPE_LAYER_MISMATCH");
        assertThat(
            ModelSpecStageGateService.evaluate(odsFact, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(odsFact)).blockers()
        )
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_TYPE_LAYER_MISMATCH");
    }

    @Test
    void factImplementationAcceptsARevisionPinnedUpstreamInsteadOfADirectPhysicalSource() {
        ModelRevisionRef upstream = new ModelRevisionRef(UUID.fromString("40000000-0000-0000-0000-000000000001"), 3);
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of(),
            List.of(upstream),
            List.of(),
            null
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void factImplementationRejectsAStaleRevisionPinnedUpstream() {
        ModelRevisionRef upstream = new ModelRevisionRef(UUID.fromString("40000000-0000-0000-0000-000000000001"), 3);
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of(),
            List.of(upstream),
            List.of(),
            null
        );
        GateEvidence current = GateEvidence.currentFor(model);
        GateEvidence staleUpstream = new GateEvidence(
            current.revision(),
            current.checksum(),
            current.sources(),
            EvidenceState.STALE,
            current.dimensions(),
            current.standards(),
            current.quality(),
            current.permissions(),
            current.build(),
            current.tests()
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, staleUpstream).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_UPSTREAM_EVIDENCE_STALE");
    }

    @Test
    void factImplementationValidatesEveryProvidedInputInsteadOfMaskingAStaleSource() {
        ModelRevisionRef upstream = new ModelRevisionRef(UUID.fromString("40000000-0000-0000-0000-000000000001"), 3);
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            sources(),
            List.of(upstream),
            List.of(),
            null
        );
        GateEvidence current = GateEvidence.currentFor(model);
        GateEvidence staleSource = new GateEvidence(
            current.revision(),
            current.checksum(),
            EvidenceState.STALE,
            current.dependencies(),
            current.dimensions(),
            current.standards(),
            current.quality(),
            current.permissions(),
            current.build(),
            current.tests()
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, staleSource).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_SOURCE_EVIDENCE_STALE");
    }

    @ParameterizedTest
    @MethodSource("supportedFactTimePairs")
    void acceptsEverySupportedFactShapeAndTimePair(FactShape factShape, TimeSemanticsType timeType) {
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            factShape,
            new TimeSemantics(timeType, List.of("event_time")),
            sources(),
            List.of(),
            List.of(),
            null
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void factTimeFieldFailurePointsToFieldsRepairRoute() {
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("organization_name")),
            sources(),
            List.of(),
            List.of(),
            null
        );

        ModelSpecStageGateService.GateBlocker blocker = ModelSpecStageGateService
            .evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model))
            .blockers()
            .get(0);
        assertThat(blocker.code()).isEqualTo("MODEL_SPEC_TIME_FIELD_INVALID");
        assertThat(blocker.repairRoute()).isEqualTo("/modeling/models/" + MODEL_ID + "?tab=fields");
    }

    @Test
    void summaryAndApplicationUseDifferentImplementationRules() {
        ModelRevisionRef upstream = new ModelRevisionRef(UUID.fromString("40000000-0000-0000-0000-000000000001"), 3);
        ModelSpecView summary = view(ModelType.SUMMARY, null, null, null, List.of(), List.of(upstream), List.of(), null);
        ModelSpecView application = view(ModelType.APPLICATION, null, null, null, List.of(), List.of(upstream), List.of(), "dashboard");

        assertThat(ModelSpecStageGateService.evaluate(summary, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(summary)).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_SUMMARY_MEASURE_REQUIRED");
        assertThat(ModelSpecStageGateService.evaluate(application, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(application)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void releaseGateRejectsEvidenceFromAnotherRevision() {
        ModelSpecView model = releaseReadyFact();
        GateEvidence stale = GateEvidence.currentFor(model).withRevision(model.revision() - 1, model.checksum());

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.RELEASE_READY, stale).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_GATE_EVIDENCE_STALE");
        assertThat(ModelSpecStageGateService.evaluate(model, Stage.RELEASE_READY, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void releaseGateDoesNotTreatSecurityOnlyBindingsAsCurrentStandards() {
        DimensionProfile profile = new DimensionProfile(
            "DIM_ORGANIZATION",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master"));
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(standards.evaluate("tenant-a", model)).thenReturn(StandardEvidence.CURRENT);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "SQL")).thenReturn(true);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "SCHEMA")).thenReturn(true);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "TEST")).thenReturn(true);

        GateView release = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();

        assertThat(release.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_STANDARD_EVIDENCE_STALE");
    }

    @Test
    void implementationGateUsesLiveSourceEvidenceAndFailsClosedWhenItIsUnavailable() {
        ModelSpecView model = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            sources(),
            List.of(),
            List.of(),
            null
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        ModelSpecSourceValidationPort sourceValidation = mock(ModelSpecSourceValidationPort.class);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(sourceValidation.isCurrentBindingForGate("tenant-a", model.planId(), model.sourceRefs().getFirst())).thenReturn(false);

        GateView implementation = new ModelSpecStageGateService(modelSpecs, repository, standards, null, sourceValidation)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_SOURCE_EVIDENCE_STALE");
    }

    @Test
    void releaseGateUsesProfessionalOwnerEvidenceForVersionedBindings() {
        DimensionProfile profile = new DimensionProfile(
            "DIM_ORGANIZATION",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = withMeasurementUnitBindings(
            dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master"))
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(standards.evaluate("tenant-a", model)).thenReturn(StandardEvidence.STALE);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "SQL")).thenReturn(true);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "SCHEMA")).thenReturn(true);
        when(repository.hasCompiledArtifact("tenant-a", MODEL_ID, model.revision(), model.checksum(), "TEST")).thenReturn(true);

        GateView release = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();

        assertThat(release.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_STANDARD_EVIDENCE_STALE");
    }

    private static ModelSpecView dimension(
        DimensionProfile profile,
        List<SourceRef> sources,
        GenerationStrategy generationStrategy
    ) {
        return view(ModelType.DIMENSION, profile, null, null, sources, List.of(), List.of(), null, generationStrategy);
    }

    private static ModelSpecView view(
        ModelType type,
        DimensionProfile profile,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<SourceRef> sources,
        List<ModelRevisionRef> dependsOn,
        List<MetricRef> metricRefs,
        String consumptionScenario
    ) {
        return view(type, profile, factShape, timeSemantics, sources, dependsOn, metricRefs, consumptionScenario, null);
    }

    private static ModelSpecView view(
        ModelType type,
        DimensionProfile profile,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<SourceRef> sources,
        List<ModelRevisionRef> dependsOn,
        List<MetricRef> metricRefs,
        String consumptionScenario,
        GenerationStrategy generationStrategy
    ) {
        List<ModelField> fields = List.of(
            new ModelField("organization_id", "varchar", false, "source.organization_id", FieldRole.KEY, "INTERNAL"),
            new ModelField("organization_name", "varchar", true, "source.organization_name", FieldRole.ATTRIBUTE, "INTERNAL"),
            new ModelField("event_time", "timestamp", false, "source.event_time", FieldRole.TIME, "INTERNAL")
        );
        return new ModelSpecView(
            2,
            MODEL_ID,
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            type,
            type == ModelType.SUMMARY ? Layer.DWS : type == ModelType.APPLICATION ? Layer.ADS : Layer.DWD,
            "generic_" + type.name().toLowerCase(),
            "Generic model definition",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            consumptionScenario,
            new Grain("one row per record", List.of("organization_id")),
            factShape,
            timeSemantics,
            fields,
            sources,
            dependsOn,
            List.of(),
            metricRefs,
            fields.stream().map(field -> new StandardBinding(field.name(), null, null, null, null, null, null, "INTERNAL")).toList(),
            generationStrategy,
            profile,
            ModelStatus.DRAFT,
            2,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static ModelSpecView releaseReadyFact() {
        return view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            sources(),
            List.of(),
            List.of(),
            null
        );
    }

    private static Stream<Arguments> supportedFactTimePairs() {
        return Stream.of(
            Arguments.of(FactShape.TRANSACTION, TimeSemanticsType.EVENT_TIME),
            Arguments.of(FactShape.PERIODIC_SNAPSHOT, TimeSemanticsType.SNAPSHOT_DATE),
            Arguments.of(FactShape.PERIODIC_SNAPSHOT, TimeSemanticsType.PERIOD),
            Arguments.of(FactShape.ACCUMULATING_SNAPSHOT, TimeSemanticsType.MILESTONE_DATES)
        );
    }

    private static ModelSpecView withMeasurementUnitBindings(ModelSpecView model) {
        UUID unitId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        List<StandardBinding> bindings = model.fields().stream()
            .map(field -> new StandardBinding(field.name(), null, null, null, null, unitId, 1, field.securityLevel()))
            .toList();
        return new ModelSpecView(
            model.contractVersion(),
            model.id(),
            model.planId(),
            model.domainId(),
            model.modelType(),
            model.layer(),
            model.name(),
            model.description(),
            model.implementationMode(),
            model.materialization(),
            model.businessActivityRef(),
            model.consumptionScenario(),
            model.grain(),
            model.factShape(),
            model.timeSemantics(),
            model.fields(),
            model.sourceRefs(),
            model.dependsOn(),
            model.dimensionRefs(),
            model.metricRefs(),
            bindings,
            model.generationStrategy(),
            model.dimensionProfile(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs()
        );
    }

    private static ModelSpecView withLayer(ModelSpecView model, Layer layer) {
        return new ModelSpecView(
            model.contractVersion(),
            model.id(),
            model.planId(),
            model.domainId(),
            model.modelType(),
            layer,
            model.name(),
            model.description(),
            model.implementationMode(),
            model.materialization(),
            model.businessActivityRef(),
            model.consumptionScenario(),
            model.grain(),
            model.factShape(),
            model.timeSemantics(),
            model.fields(),
            model.sourceRefs(),
            model.dependsOn(),
            model.dimensionRefs(),
            model.metricRefs(),
            model.standardBindings(),
            model.generationStrategy(),
            model.dimensionProfile(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs()
        );
    }

    private static List<SourceRef> sources() {
        return List.of(
            new SourceRef(
                SourceKind.TABLE,
                "catalog.dataset.source",
                Layer.ODS,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                0,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "v1"
            )
        );
    }
}
