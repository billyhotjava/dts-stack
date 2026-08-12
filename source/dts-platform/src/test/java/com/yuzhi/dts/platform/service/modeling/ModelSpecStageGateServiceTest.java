package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStandardEvidencePort.StandardEvidence;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateEvidence;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        ModelSpecView legacyCompatible = withDimensionDefinitionRef(dimension(null, List.of(), null), 1);

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
    void implementationGateReportsOnlyTheCanonicalImplementationOwnerWhenNoImplementationExists() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationInputPolicy adapter = mock(ModelImplementationInputPolicy.class);
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
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.empty());

        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            mock(ModelSpecStandardEvidencePort.class),
            lifecycle,
            null,
            null,
            null,
            adapter
        );

        assertThat(implementationGate(gates).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_IMPLEMENTATION_REQUIRED");
    }

    @Test
    void addsStableImplementationInputBlockerWhenPersistedPhysicalSourceIsNoLongerConfirmed() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecSourceValidationPort sourceValidation = mock(ModelSpecSourceValidationPort.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecView model = dimension(
            new DimensionProfile("organization", List.of(), new ScdPolicy(ScdType.TYPE1, null, null, null), ReuseScope.PLAN),
            sources(),
            null
        );
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(sourceValidation.isCurrentBindingForGate("tenant-a", model.planId(), model.sourceRefs().get(0))).thenReturn(false);
        ImplementationView implementation = new ImplementationView(
            UUID.randomUUID(),
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DESIGNER_GENERATED,
            null,
            "model.test.dimension",
            "ACTIVE",
            1,
            "b".repeat(64),
            ModelLifecycleContract.InputMode.PHYSICAL_ASSET,
            List.of(
                new ModelLifecycleContract.PhysicalAssetInput(
                    model.sourceRefs().getFirst().sourceBindingId(),
                    model.sourceRefs().getFirst().resolvedVersion()
                )
            ),
            List.of(),
            java.util.Map.of(
                "targetPhysicalName", "dwd_dimension",
                "loadStrategy", "FULL",
                "partitionFields", List.of("event_time")
            ),
            "table"
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(implementation));
        ModelImplementationInputPolicy adapter = new ModelImplementationInputPolicy(
            modelSpecs,
            repository,
            lifecycle,
            sourceValidation
        );
        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            mock(ModelSpecStandardEvidencePort.class),
            lifecycle,
            sourceValidation,
            null,
            null,
            adapter
        );

        assertThat(implementationGate(gates).blockers()).extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("PHYSICAL_ASSET_NOT_CONFIRMED");
    }

    @Test
    void acceptsPersistedDbtManagedInputWhenItsExecutionSettingsPassTheUnifiedPlanner() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        DimensionProfile profile = new DimensionProfile(
            "DIM_COMPLETION_STATUS",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = withImplementationMode(
            withDimensionDefinitionRef(
                dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "manual-dbt")),
                1
            ),
            ImplementationMode.DBT_MANAGED
        );
        ImplementationView implementation = new ImplementationView(
            UUID.randomUUID(),
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DBT_MANAGED,
            "pjm",
            "model.pjm.dim_completion_status_v2",
            "ACTIVE",
            1,
            "b".repeat(64),
            ModelLifecycleContract.InputMode.GENERATED,
            List.of(new ModelLifecycleContract.GeneratedInput("DBT", java.util.Map.of())),
            List.of(),
            java.util.Map.of(
                "targetPhysicalName",
                "dim_completion_status_v2",
                "loadStrategy",
                "FULL",
                "partitionFields",
                List.of()
            ),
            "table"
        );
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(implementation));
        ModelImplementationInputPolicy inputPolicy = new ModelImplementationInputPolicy(
            modelSpecs,
            repository,
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            mock(ModelSpecStandardEvidencePort.class),
            lifecycle,
            null,
            null,
            null,
            inputPolicy
        );

        assertThat(implementationGate(gates).blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .doesNotContain("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED", "IMPLEMENTATION_TARGET_REQUIRED");
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
        ModelSpecView model = withDimensionDefinitionRef(
            dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master")),
            1
        );

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.IMPLEMENTATION_READY, GateEvidence.currentFor(model)).status())
            .isEqualTo(GateStatus.READY);
    }

    @Test
    void implementationGateRechecksPinnedDefinitionsWithoutChangingThePinnedRevision() {
        DimensionProfile profile = new DimensionProfile(
            "DIM_ORGANIZATION",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = withDimensionDefinitionRef(
            dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master")),
            1
        );
        DimensionDefinitionRef ref = model.dimensionDefinitionRef();
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        DimensionDefinitionRepository definitions = mock(DimensionDefinitionRepository.class);
        StoredDimensionDefinition pinned = dimensionDefinition(ref.dimensionDefinitionId(), 1, DimensionDefinitionContract.Status.CURRENT);
        StoredDimensionDefinition current = dimensionDefinition(ref.dimensionDefinitionId(), 2, DimensionDefinitionContract.Status.CURRENT);
        StoredDimensionDefinition retired = dimensionDefinition(ref.dimensionDefinitionId(), 2, DimensionDefinitionContract.Status.RETIRED);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(definitions.findRevision("tenant-a", ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(definitions.findCurrent("tenant-a", ref.dimensionDefinitionId())).thenReturn(Optional.of(current));
        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            standards,
            null,
            null,
            definitions
        );

        GateView ready = implementationGate(gates);
        when(definitions.findCurrent("tenant-a", ref.dimensionDefinitionId())).thenReturn(Optional.of(retired));
        GateView retiredGate = implementationGate(gates);
        when(definitions.findCurrent("tenant-a", ref.dimensionDefinitionId())).thenReturn(Optional.empty());
        GateView deletedGate = implementationGate(gates);

        assertThat(ready.status()).isEqualTo(GateStatus.READY);
        assertThat(retiredGate.blockers()).extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("DIMENSION_DEFINITION_NOT_CURRENT");
        assertThat(deletedGate.blockers()).extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("DIMENSION_DEFINITION_NOT_CURRENT");
        assertThat(model.dimensionDefinitionRef()).isEqualTo(ref);
    }

    @Test
    void implementationGateFailsClosedWhenThePinnedDefinitionDomainIsNoLongerVisible() {
        DimensionProfile profile = new DimensionProfile(
            "DIM_ORGANIZATION",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView model = withDimensionDefinitionRef(
            dimension(profile, List.of(), new GenerationStrategy("REFERENCE", "organization-master")),
            1
        );
        DimensionDefinitionRef ref = model.dimensionDefinitionRef();
        UUID definitionDomainId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        DimensionDefinitionRepository definitions = mock(DimensionDefinitionRepository.class);
        ModelSpecDomainReadAccessPort domainReadAccess = mock(ModelSpecDomainReadAccessPort.class);
        StoredDimensionDefinition pinned = dimensionDefinition(
            ref.dimensionDefinitionId(),
            1,
            DimensionDefinitionContract.Status.CURRENT
        );
        StoredDimensionDefinition current = dimensionDefinition(
            ref.dimensionDefinitionId(),
            2,
            DimensionDefinitionContract.Status.CURRENT
        );
        when(pinned.domainId()).thenReturn(definitionDomainId);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(definitions.findRevision("tenant-a", ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(definitions.findCurrent("tenant-a", ref.dimensionDefinitionId())).thenReturn(Optional.of(current));
        when(domainReadAccess.canRead(definitionDomainId)).thenReturn(false);
        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            standards,
            null,
            null,
            definitions,
            domainReadAccess,
            null
        );

        GateView implementation = implementationGate(gates);

        assertThat(implementation.status()).isEqualTo(GateStatus.BLOCKED);
        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("DIMENSION_DEFINITION_NOT_CURRENT");
        verify(domainReadAccess, times(3)).canRead(definitionDomainId);
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
    void factLogicalDesignCanCompleteWithoutImplementationInput() {
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

        assertThat(ModelSpecStageGateService.evaluate(model, Stage.DESIGNED, GateEvidence.currentFor(model)).status())
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
        assertThat(blocker.repairRoute()).isEqualTo("/modeling/models/" + MODEL_ID + "?activeStage=logical&tab=fields");
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
    void dbtReleaseGateRequiresExactlyTheSqlAndSchemaArtifactSlots() {
        ModelSpecView model = withImplementationMode(releaseReadyFact(), ImplementationMode.DBT_MANAGED);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecSourceValidationPort sourceValidation = mock(ModelSpecSourceValidationPort.class);
        ImplementationView implementation = new ImplementationView(
            UUID.randomUUID(),
            model.id(),
            model.planId(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DBT_MANAGED,
            "pjm",
            "model.pjm.fact",
            "ACTIVE",
            1,
            model.checksum(),
            ModelLifecycleContract.InputMode.GENERATED,
            List.of(new ModelLifecycleContract.GeneratedInput("DBT", java.util.Map.of())),
            List.of(),
            java.util.Map.of(),
            "table"
        );
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(sourceValidation.isCurrentBindingForGate("tenant-a", model.planId(), model.sourceRefs().getFirst())).thenReturn(true);
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(implementation));
        when(lifecycle.hasPassedEvidence(
            "tenant-a",
            MODEL_ID,
            model.revision(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            ModelLifecycleContract.EventType.COMPILE
        )).thenReturn(true);
        when(lifecycle.hasPassedEvidence(
            "tenant-a",
            MODEL_ID,
            model.revision(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            ModelLifecycleContract.EventType.TEST
        )).thenReturn(true);
        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            standards,
            lifecycle,
            sourceValidation
        );

        when(lifecycle.currentArtifactTypes("tenant-a", MODEL_ID, implementation)).thenReturn(Set.of("SQL", "SCHEMA", "DOC"));
        GateView polluted = gates.evaluateAll("tenant-a", MODEL_ID).stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();
        when(lifecycle.currentArtifactTypes("tenant-a", MODEL_ID, implementation)).thenReturn(Set.of("SQL", "SCHEMA"));
        GateView exact = gates.evaluateAll("tenant-a", MODEL_ID).stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();
        when(lifecycle.currentArtifactTypes("tenant-a", MODEL_ID, implementation)).thenReturn(Set.of("SQL", "SCHEMA", "CONFIG"));
        GateView uiDraft = gates.evaluateAll("tenant-a", MODEL_ID).stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();

        assertThat(polluted.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_BUILD_EVIDENCE_UNKNOWN");
        assertThat(exact.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .doesNotContain("MODEL_SPEC_BUILD_EVIDENCE_UNKNOWN");
        assertThat(uiDraft.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .doesNotContain("MODEL_SPEC_BUILD_EVIDENCE_UNKNOWN");
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
    void releaseGateAppliesKeyAndMeasureCoverageAndAdvisoryQualityOnlyAtRelease() {
        ModelSpecView model = withStandardsForRoles(releaseReadyFact(), Set.of(FieldRole.KEY, FieldRole.MEASURE));
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        ModelGovernancePolicyPort governancePolicy = mock(ModelGovernancePolicyPort.class);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(standards.evaluate("tenant-a", model)).thenReturn(StandardEvidence.CURRENT);
        when(governancePolicy.resolve()).thenReturn(
            ModelGovernancePolicyPort.Policy.available(
                ModelGovernancePolicyPort.StandardCoverage.KEY_AND_MEASURE,
                ModelGovernancePolicyPort.QualityGate.ADVISORY
            )
        );

        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            standards,
            null,
            null,
            null,
            null,
            null,
            null,
            governancePolicy
        );
        List<GateView> decisions = gates.evaluateAll("tenant-a", MODEL_ID);
        GateView designed = decisions.stream().filter(gate -> gate.stage() == Stage.DESIGNED).findFirst().orElseThrow();
        GateView release = decisions.stream().filter(gate -> gate.stage() == Stage.RELEASE_READY).findFirst().orElseThrow();

        assertThat(designed.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .doesNotContain(
                "MODEL_SPEC_STANDARD_EVIDENCE_STALE",
                "MODEL_SPEC_QUALITY_EVIDENCE_UNKNOWN",
                "MODEL_GOVERNANCE_POLICY_UNAVAILABLE"
            );
        assertThat(release.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .doesNotContain(
                "MODEL_SPEC_STANDARD_EVIDENCE_STALE",
                "MODEL_SPEC_QUALITY_EVIDENCE_UNKNOWN",
                "MODEL_GOVERNANCE_POLICY_UNAVAILABLE"
            );
    }

    @Test
    void releaseGateRequiresAllFieldsWhenConfiguredAndFailsClosedWhenPolicyIsUnreadable() {
        ModelSpecView model = withStandardsForRoles(releaseReadyFact(), Set.of(FieldRole.KEY));
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        ModelGovernancePolicyPort governancePolicy = mock(ModelGovernancePolicyPort.class);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(standards.evaluate("tenant-a", model)).thenReturn(StandardEvidence.CURRENT);
        when(governancePolicy.resolve())
            .thenReturn(
                ModelGovernancePolicyPort.Policy.available(
                    ModelGovernancePolicyPort.StandardCoverage.ALL_FIELDS,
                    ModelGovernancePolicyPort.QualityGate.BLOCKING
                )
            )
            .thenReturn(ModelGovernancePolicyPort.Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE"));

        ModelSpecStageGateService gates = new ModelSpecStageGateService(
            modelSpecs,
            repository,
            standards,
            null,
            null,
            null,
            null,
            null,
            null,
            governancePolicy
        );
        GateView strict = gates.evaluateAll("tenant-a", MODEL_ID).stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();
        GateView unavailable = gates.evaluateAll("tenant-a", MODEL_ID).stream()
            .filter(gate -> gate.stage() == Stage.RELEASE_READY)
            .findFirst()
            .orElseThrow();

        assertThat(strict.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_STANDARD_EVIDENCE_STALE", "MODEL_SPEC_QUALITY_EVIDENCE_UNKNOWN");
        assertThat(unavailable.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_GOVERNANCE_POLICY_UNAVAILABLE");
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
    void implementationGateLoadsPinnedDependenciesAndRejectsAnInvalidOwnerMatrix() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000010");
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 3);
        ModelSpecView owner = view(
            ModelType.FACT,
            null,
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of(),
            List.of(upstreamRef),
            List.of(),
            null
        );
        ModelSpecView application = withId(
            view(ModelType.APPLICATION, null, null, null, List.of(), List.of(upstreamRef), List.of(), "dashboard"),
            upstreamId
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        StoredModelSpec pinned = mock(StoredModelSpec.class);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(3);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(owner);
        when(modelSpecs.revision("tenant-a", upstreamRef)).thenReturn(application);
        when(repository.findRevision("tenant-a", upstreamId, 3)).thenReturn(Optional.of(pinned));
        when(repository.findCurrent("tenant-a", upstreamId)).thenReturn(Optional.of(current));

        GateView implementation = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .containsExactly("MODEL_SPEC_UPSTREAM_EVIDENCE_STALE");
        verify(modelSpecs).revision("tenant-a", upstreamRef);
    }

    @Test
    void implementationGateRejectsCanonicalLayerUpstreamsWithHistoricalTypePollution() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000014");
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 2);
        ModelSpecView owner = view(
            ModelType.SUMMARY,
            null,
            null,
            null,
            List.of(),
            List.of(upstreamRef),
            List.of(),
            null
        );
        DimensionProfile pollution = new DimensionProfile(
            "DIM_POLLUTION",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelSpecView pollutedFact = withId(
            view(
                ModelType.FACT,
                pollution,
                FactShape.TRANSACTION,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
                sources(),
                List.of(),
                List.of(),
                null
            ),
            upstreamId
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(2);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(owner);
        when(modelSpecs.revision("tenant-a", upstreamRef)).thenReturn(pollutedFact);
        when(repository.findCurrent("tenant-a", upstreamId)).thenReturn(Optional.of(current));

        GateView implementation = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_UPSTREAM_EVIDENCE_STALE");
    }

    @Test
    void implementationGateRejectsNonCanonicalDimensionTargetsAtThePinnedRevision() {
        UUID dimensionId = UUID.fromString("40000000-0000-0000-0000-000000000011");
        ModelRevisionRef dimensionRef = new ModelRevisionRef(dimensionId, 2);
        ModelSpecView owner = withReferences(
            view(
                ModelType.FACT,
                null,
                FactShape.TRANSACTION,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
                List.of(),
                List.of(),
                List.of(),
                null
            ),
            List.of(),
            List.of(dimensionRef)
        );
        ModelSpecView wrongLayerDimension = withId(
            withLayer(dimension(null, List.of(), new GenerationStrategy("REFERENCE", "organization-master")), Layer.ODS),
            dimensionId
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        StoredModelSpec pinned = mock(StoredModelSpec.class);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(2);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(owner);
        when(modelSpecs.revision("tenant-a", dimensionRef)).thenReturn(wrongLayerDimension);
        when(repository.findRevision("tenant-a", dimensionId, 2)).thenReturn(Optional.of(pinned));
        when(repository.findCurrent("tenant-a", dimensionId)).thenReturn(Optional.of(current));

        GateView implementation = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_DIMENSION_EVIDENCE_STALE");
    }

    @Test
    void referencedModelLookupFailuresBecomeStaleEvidenceInsteadOfEscapingTheGateApi() {
        UUID dimensionId = UUID.fromString("40000000-0000-0000-0000-000000000012");
        ModelRevisionRef dimensionRef = new ModelRevisionRef(dimensionId, 2);
        ModelSpecView owner = withReferences(
            view(
                ModelType.FACT,
                null,
                FactShape.TRANSACTION,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
                List.of(),
                List.of(),
                List.of(),
                null
            ),
            List.of(),
            List.of(dimensionRef)
        );
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        StoredModelSpec pinned = mock(StoredModelSpec.class);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(2);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(owner);
        when(modelSpecs.revision("tenant-a", dimensionRef)).thenThrow(new IllegalStateException("reference hidden"));
        when(repository.findRevision("tenant-a", dimensionId, 2)).thenReturn(Optional.of(pinned));
        when(repository.findCurrent("tenant-a", dimensionId)).thenReturn(Optional.of(current));

        GateView implementation = new ModelSpecStageGateService(modelSpecs, repository, standards)
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(implementation.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_DIMENSION_EVIDENCE_STALE");
    }

    @Test
    void crossPlanReferencesMustRemainPublishedToCountAsCurrentGateEvidence() {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000013");
        UUID upstreamPlanId = UUID.fromString("10000000-0000-0000-0000-000000000099");
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 2);
        ModelSpecView owner = view(
            ModelType.APPLICATION,
            null,
            null,
            null,
            List.of(),
            List.of(upstreamRef),
            List.of(),
            "dashboard"
        );
        ModelSpecView target = withId(
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
            upstreamId
        );
        ModelSpecView crossPlanDraft = withPlanAndStatus(target, upstreamPlanId, ModelStatus.DRAFT);
        ModelSpecView crossPlanPublished = withPlanAndStatus(target, upstreamPlanId, ModelStatus.PUBLISHED);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelSpecStandardEvidencePort standards = mock(ModelSpecStandardEvidencePort.class);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(2);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(owner);
        when(repository.findCurrent("tenant-a", upstreamId)).thenReturn(Optional.of(current));
        when(modelSpecs.revision("tenant-a", upstreamRef)).thenReturn(crossPlanDraft, crossPlanPublished);

        ModelSpecStageGateService gates = new ModelSpecStageGateService(modelSpecs, repository, standards);
        GateView draftGate = gates
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();
        GateView publishedGate = gates
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();

        assertThat(draftGate.blockers())
            .extracting(ModelSpecStageGateService.GateBlocker::code)
            .contains("MODEL_SPEC_UPSTREAM_EVIDENCE_STALE");
        assertThat(publishedGate.status()).isEqualTo(GateStatus.READY);
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

    private static GateView implementationGate(ModelSpecStageGateService gates) {
        return gates
            .evaluateAll("tenant-a", MODEL_ID)
            .stream()
            .filter(gate -> gate.stage() == Stage.IMPLEMENTATION_READY)
            .findFirst()
            .orElseThrow();
    }

    private static StoredDimensionDefinition dimensionDefinition(
        UUID id,
        int revision,
        DimensionDefinitionContract.Status status
    ) {
        StoredDimensionDefinition definition = mock(StoredDimensionDefinition.class);
        when(definition.id()).thenReturn(id);
        when(definition.revision()).thenReturn(revision);
        when(definition.status()).thenReturn(status);
        return definition;
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
            null,
            ModelStatus.DRAFT,
            2,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null,
            type == ModelType.APPLICATION ? UUID.fromString("70000000-0000-0000-0000-000000000001") : null,
            null,
            type == ModelType.DIMENSION
                ? new ImplementationPolicy("dwd_" + type.name().toLowerCase(), LoadStrategy.FULL, null, List.of())
                : null,
            null,
            type == ModelType.FACT ? UUID.fromString("80000000-0000-0000-0000-000000000001") : null,
            type == ModelType.APPLICATION ? UUID.fromString("90000000-0000-0000-0000-000000000001") : null
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
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withStandardsForRoles(ModelSpecView model, Set<FieldRole> roles) {
        UUID unitId = UUID.fromString("60000000-0000-0000-0000-000000000002");
        List<StandardBinding> bindings = model.fields().stream()
            .map(field ->
                roles.contains(field.role())
                    ? new StandardBinding(field.name(), null, null, null, null, unitId, 1, field.securityLevel())
                    : new StandardBinding(field.name(), null, null, null, null, null, null, field.securityLevel())
            )
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
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withImplementationMode(ModelSpecView model, ImplementationMode implementationMode) {
        return new ModelSpecView(
            model.contractVersion(),
            model.id(),
            model.planId(),
            model.domainId(),
            model.modelType(),
            model.layer(),
            model.name(),
            model.description(),
            implementationMode,
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
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withDimensionDefinitionRef(ModelSpecView model, int revision) {
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
            model.standardBindings(),
            model.generationStrategy(),
            model.dimensionProfile(),
            new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), revision),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
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
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withId(ModelSpecView model, UUID modelSpecId) {
        return new ModelSpecView(
            model.contractVersion(),
            modelSpecId,
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
            model.standardBindings(),
            model.generationStrategy(),
            model.dimensionProfile(),
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withReferences(
        ModelSpecView model,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs
    ) {
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
            dependsOn,
            dimensionRefs,
            model.metricRefs(),
            model.standardBindings(),
            model.generationStrategy(),
            model.dimensionProfile(),
            model.dimensionDefinitionRef(),
            model.status(),
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
        );
    }

    private static ModelSpecView withPlanAndStatus(
        ModelSpecView model,
        UUID planId,
        ModelStatus status
    ) {
        return new ModelSpecView(
            model.contractVersion(),
            model.id(),
            planId,
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
            model.standardBindings(),
            model.generationStrategy(),
            model.dimensionProfile(),
            model.dimensionDefinitionRef(),
            status,
            model.revision(),
            model.checksum(),
            model.createdAt(),
            model.updatedAt(),
            model.compatibilityMode(),
            model.legacyRefs(),
            model.dataMartId(),
            model.variantCode(),
            model.implementationPolicy(),
            model.warehouseLayerCode(),
            model.businessProcessId(),
            model.subjectDomainId()
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
