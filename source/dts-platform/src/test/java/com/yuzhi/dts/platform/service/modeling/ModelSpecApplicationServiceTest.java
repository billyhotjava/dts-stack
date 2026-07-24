package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.DomainBindingState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_BINDING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Mock
    private ModelSpecRepository repository;

    @Mock
    private CatalogDomainResolutionPort domainResolution;

    @Mock
    private ModelSpecDomainWriteAccessPort domainWriteAccess;

    @Mock
    private ModelSpecDomainReadAccessPort domainReadAccess;

    @Mock
    private ModelSpecPlanWriteAccessPort planWriteAccess;

    @Mock
    private ModelSpecSourceValidationPort sourceValidation;

    @Mock
    private ModelSpecCompatibilityReader compatibilityReader;

    private ModelSpecSnapshotCodec codec;
    private ModelSpecApplicationService service;

    @BeforeEach
    void setUp() {
        codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        service = new ModelSpecApplicationService(
            repository,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        lenient().when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        lenient().when(repository.lockDomainBinding(TENANT, PLAN_ID, DOMAIN_ID))
            .thenReturn(Optional.of(new DomainBindingState(DOMAIN_ID, "CONFIRMED")));
        lenient().when(sourceValidation.isCurrentBinding(eq(TENANT), eq(PLAN_ID), eq(ACTOR), any())).thenReturn(true);
        lenient().when(domainResolution.resolve(DOMAIN_ID))
            .thenReturn(new DomainResolution(DOMAIN_ID, CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE, "Customers", "CUSTOMER", "owner", null));
        lenient().when(domainWriteAccess.canMaintain(DOMAIN_ID)).thenReturn(true);
        lenient().when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(true);
        lenient().when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
    }

    @Test
    void createsAnObjectlessV2SnapshotAndAppendsRevision() {
        CreateModelSpecCommand command = command("create-1", "customer_detail");
        when(repository.findByIdempotencyKey(TENANT, "create-1")).thenReturn(Optional.empty());
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.modelSpec().id()).isEqualTo(MODEL_ID);
        assertThat(result.modelSpec().contractVersion()).isEqualTo(2);
        assertThat(result.modelSpec().compatibilityMode()).isEqualTo(CompatibilityMode.CANONICAL);
        verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result.modelSpec()), anyString());
    }

    @Test
    void createsAFactDraftWithoutResolvingAnUnmappedPhysicalSource() {
        CreateModelSpecCommand command = withInputs(command("fact-source-pending", "customer_detail_pending"), List.of(), List.of());
        when(repository.findByIdempotencyKey(TENANT, "fact-source-pending")).thenReturn(Optional.empty());
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.modelSpec().sourceRefs()).isEmpty();
        verify(sourceValidation, never()).isCurrentBinding(any(), any(), any(), any());
        verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result.modelSpec()), anyString());
    }

    @Test
    void replaysTheImmutableCreateResponseAndRejectsKeyReuseWithDifferentContent() {
        CreateModelSpecCommand original = command("create-1", "customer_detail");
        ModelSpecView originalView = codec.toCreatedView(MODEL_ID, original, NOW);
        StoredModelSpec stored = stored(originalView, codec.requestHash(original), codec.write(originalView));
        when(repository.findByIdempotencyKey(TENANT, "create-1")).thenReturn(Optional.of(stored));

        ModelSpecApplicationService.CreateResult replay = service.create(TENANT, ACTOR, original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.modelSpec()).isEqualTo(originalView);
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command("create-1", "changed_name")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void replaysOriginalCreateAfterPlanLifecycleAndDomainBindingStateChange() {
        CreateModelSpecCommand command = command("replay-after-state-change", "customer_detail");
        ModelSpecView original = codec.toCreatedView(MODEL_ID, command, NOW);
        when(repository.findByIdempotencyKey(TENANT, "replay-after-state-change"))
            .thenReturn(Optional.of(stored(original, codec.requestHash(command), codec.write(original))));

        ModelSpecApplicationService.CreateResult replay = service.create(TENANT, ACTOR, command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.modelSpec()).isEqualTo(original);
        verify(repository, never()).lockPlan(TENANT, PLAN_ID);
        verify(repository, never()).lockDomainBinding(TENANT, PLAN_ID, DOMAIN_ID);
        verify(domainResolution, never()).resolve(DOMAIN_ID);
    }

    @Test
    void rejectsAnIdempotencySnapshotThatIsNotTheOriginalCanonicalRevision() {
        CreateModelSpecCommand command = command("create-corrupt", "customer_detail");
        ModelSpecView created = codec.toCreatedView(MODEL_ID, command, NOW);
        ModelSpecView corrupt = codec.toUpdatedView(created, update(command), 2, NOW.plusSeconds(1));
        StoredModelSpec stored = stored(created, codec.requestHash(command), codec.write(corrupt));
        when(repository.findByIdempotencyKey(TENANT, "create-corrupt")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_IDEMPOTENCY_SNAPSHOT_INVALID");
    }

    @Test
    void treatsEquivalentPutAsANoOpAndDoesNotAppendARevision() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);

        ModelSpecView result = service.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 1, current.checksum()),
            update(create)
        );

        assertThat(result).isEqualTo(current);
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void treatsEquivalentPinnedDimensionPutAsANoOpWithTheCurrentImmutableContent() {
        CreateModelSpecCommand create = pinnedDimensionCommand("dimension-noop", legacyDimensionProfile());
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);

        ModelSpecView result = service.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 1, current.checksum()),
            canonicalDimensionUpdate(current, current.name())
        );

        assertThat(result).isEqualTo(current);
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void rejectsChangingAPinnedDimensionToANonDimensionBeforePersistence() {
        CreateModelSpecCommand create = pinnedDimensionCommand("dimension-type-change", null);
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        UpdateModelSpecCommand factReplacement = new UpdateModelSpecCommand(
            current.planId(),
            current.domainId(),
            ModelType.FACT,
            Layer.DWD,
            current.name(),
            current.description(),
            current.implementationMode(),
            current.materialization(),
            null,
            null,
            current.grain(),
            null,
            null,
            current.fields(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null
        );

        assertThatThrownBy(
            () ->
                service.update(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new ExpectedVersion(MODEL_ID, 1, current.checksum()),
                    factReplacement
                )
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException validation = (ModelSpecException) error;
                assertThat(validation.code()).isEqualTo("MODEL_SPEC_VALIDATION_FAILED");
                assertThat((List<?>) validation.details())
                    .extracting(
                        issue -> ((FieldIssue) issue).code(),
                        issue -> ((FieldIssue) issue).field()
                    )
                    .contains(
                        org.assertj.core.groups.Tuple.tuple(
                            "MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED",
                            "dimensionDefinitionRef"
                        )
                    );
            });
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void preservesLegacyDimensionIdentityFieldsOnTheFirstCanonicalUpdate() {
        CreateModelSpecCommand create = pinnedDimensionCommand("dimension-legacy-update", legacyDimensionProfile());
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.compareAndSetV2(eq(TENANT), eq(ACTOR), eq(1), eq(current.checksum()), any(), anyString()))
            .thenReturn(1);

        ModelSpecView result = service.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 1, current.checksum()),
            canonicalDimensionUpdate(current, "customer_dimension_v2")
        );

        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.dimensionDefinitionRef()).isEqualTo(current.dimensionDefinitionRef());
        assertThat(result.dimensionProfile().dimensionCode()).isEqualTo("DIM_CUSTOMER");
        assertThat(result.dimensionProfile().reuseScope()).isEqualTo(ReuseScope.DOMAIN);
        verify(repository).insertV2Revision(TENANT, ACTOR, result, codec.write(result));
    }

    @Test
    void refusesUpdatesToLegacyRowsWithoutConsultingBusinessObjects() {
        StoredModelSpec legacy = new StoredModelSpec(
            1,
            TENANT,
            MODEL_ID,
            PLAN_ID,
            DOMAIN_ID,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            null,
            "{}",
            null,
            null,
            null,
            NOW,
            NOW
        );
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, "a".repeat(64)), update(command("x", "customer_detail")))
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_LEGACY_READONLY");
    }

    @Test
    void refusesUpdatesToHistoricalCanonicalRowsWithNonCanonicalTargetLayers() {
        CreateModelSpecCommand historicalCommand = withLayer(command("historical-ods", "customer_detail"), Layer.ODS);
        ModelSpecView historical = codec.toCreatedView(MODEL_ID, historicalCommand, NOW);
        StoredModelSpec stored = stored(historical, null, null);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(historical);

        assertThatThrownBy(
            () ->
                service.update(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new ExpectedVersion(MODEL_ID, 1, historical.checksum()),
                    update(historicalCommand)
                )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_LEGACY_READONLY");

        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void refusesUpdatesToCanonicalRowsWithHistoricalTypeBoundaryPollution() {
        ModelSpecView base = codec.toCreatedView(MODEL_ID, command("historical-boundary", "customer_detail"), NOW);
        DimensionProfile profile = new DimensionProfile(
            "DIM_LEGACY",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.PLAN
        );
        ModelRevisionRef dependency = new ModelRevisionRef(
            UUID.fromString("40000000-0000-0000-0000-000000000090"),
            1
        );
        SourceRef dwsSource = withLayer(base.sourceRefs().getFirst(), Layer.DWS);
        List<ModelSpecView> historicalRows = List.of(
            copyModel(
                base,
                2,
                ModelType.FACT,
                Layer.DWD,
                null,
                null,
                base.sourceRefs(),
                List.of(),
                null,
                profile,
                CompatibilityMode.CANONICAL
            ),
            copyModel(
                base,
                2,
                ModelType.DIMENSION,
                Layer.DWD,
                "legacy-activity",
                null,
                base.sourceRefs(),
                List.of(),
                null,
                null,
                CompatibilityMode.CANONICAL
            ),
            withFactOnlyFields(
                copyModel(
                    base,
                    2,
                    ModelType.DIMENSION,
                    Layer.DWD,
                    null,
                    null,
                    base.sourceRefs(),
                    List.of(),
                    null,
                    null,
                    CompatibilityMode.CANONICAL
                ),
                null,
                null,
                List.of(dependency)
            ),
            withFactOnlyFields(
                copyModel(
                    base,
                    2,
                    ModelType.SUMMARY,
                    Layer.DWS,
                    null,
                    null,
                    List.of(),
                    List.of(dependency),
                    null,
                    null,
                    CompatibilityMode.CANONICAL
                ),
                FactShape.TRANSACTION,
                null,
                List.of()
            ),
            withFactOnlyFields(
                copyModel(
                    base,
                    2,
                    ModelType.APPLICATION,
                    Layer.ADS,
                    null,
                    "legacy-consumer",
                    List.of(),
                    List.of(dependency),
                    null,
                    null,
                    CompatibilityMode.CANONICAL
                ),
                null,
                new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("customer_id")),
                List.of()
            ),
            copyModel(
                base,
                2,
                ModelType.FACT,
                Layer.DWD,
                null,
                "legacy-consumer",
                base.sourceRefs(),
                List.of(),
                null,
                null,
                CompatibilityMode.CANONICAL
            ),
            copyModel(
                base,
                2,
                ModelType.SUMMARY,
                Layer.DWS,
                null,
                null,
                base.sourceRefs(),
                List.of(dependency),
                new GenerationStrategy("REFERENCE", "legacy-summary"),
                null,
                CompatibilityMode.CANONICAL
            ),
            copyModel(
                base,
                2,
                ModelType.DIMENSION,
                Layer.DWD,
                null,
                null,
                base.sourceRefs(),
                List.of(dependency),
                null,
                null,
                CompatibilityMode.CANONICAL
            ),
            copyModel(
                base,
                2,
                ModelType.FACT,
                Layer.DWD,
                null,
                null,
                List.of(dwsSource),
                List.of(),
                null,
                null,
                CompatibilityMode.CANONICAL
            )
        );

        for (ModelSpecView historical : historicalRows) {
            StoredModelSpec stored = stored(historical, null, null);
            when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
            when(compatibilityReader.read(stored)).thenReturn(historical);

            assertThatThrownBy(
                () ->
                    service.update(
                        TENANT,
                        ACTOR,
                        MODEL_ID,
                        new ExpectedVersion(MODEL_ID, historical.revision(), historical.checksum()),
                        update(historical)
                    )
            )
                .as(historical.modelType() + " historical type-boundary pollution")
                .isInstanceOf(ModelSpecException.class)
                .extracting(error -> ((ModelSpecException) error).code())
                .isEqualTo("MODEL_SPEC_LEGACY_READONLY");
        }

        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void keepsCanonicalDraftsWithImplementationGapsEditable() {
        CreateModelSpecCommand incompleteDraft = withInputs(
            command("incomplete-draft", "customer_detail"),
            List.of(),
            List.of()
        );
        ModelSpecView current = codec.toCreatedView(MODEL_ID, incompleteDraft, NOW);
        StoredModelSpec stored = stored(current, null, null);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(current);

        ModelSpecView result = service.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, current.revision(), current.checksum()),
            update(current)
        );

        assertThat(result).isEqualTo(current);
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void checksAuthorizationBeforeReportingHistoricalWrongLayerRowsAsReadOnly() {
        CreateModelSpecCommand historicalCommand = withLayer(command("historical-ods-forbidden", "customer_detail"), Layer.ODS);
        ModelSpecView historical = codec.toCreatedView(MODEL_ID, historicalCommand, NOW);
        StoredModelSpec stored = stored(historical, null, null);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(historical);
        when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);

        assertThatThrownBy(
            () ->
                service.update(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new ExpectedVersion(MODEL_ID, 1, historical.checksum()),
                    update(historicalCommand)
                )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_PLAN_FORBIDDEN");
    }

    @Test
    void checksEtagBeforeReportingHistoricalWrongLayerRowsAsReadOnly() {
        CreateModelSpecCommand historicalCommand = withLayer(command("historical-ods-stale", "customer_detail"), Layer.ODS);
        ModelSpecView historical = codec.toCreatedView(MODEL_ID, historicalCommand, NOW);
        StoredModelSpec stored = stored(historical, null, null);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(historical);

        assertThatThrownBy(
            () ->
                service.update(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new ExpectedVersion(MODEL_ID, 1, "b".repeat(64)),
                    update(historicalCommand)
                )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_REVISION_CONFLICT");
    }

    @Test
    void hidesCanonicalReadsWhenTheCurrentActorCannotReadTheDomain() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("create-1", "customer_detail"), NOW);
        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(current);
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.get(TENANT, MODEL_ID))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_NOT_FOUND");
    }

    @Test
    void validatesPinnedDimensionRevisionsBeforeWriting() {
        UUID dimensionId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        CreateModelSpecCommand base = command("create-ref", "customer_detail");
        CreateModelSpecCommand withMissingDimension = new CreateModelSpecCommand(
            base.planId(), base.domainId(), base.modelType(), base.layer(), base.name(), base.description(),
            base.implementationMode(), base.materialization(), base.businessActivityRef(), base.consumptionScenario(),
            base.grain(), base.factShape(), base.timeSemantics(), base.fields(), base.sourceRefs(), base.dependsOn(),
            List.of(new ModelRevisionRef(dimensionId, 2)), base.metricRefs(), base.standardBindings(),
            base.generationStrategy(), base.idempotencyKey()
        );
        when(repository.findByIdempotencyKey(TENANT, "create-ref")).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, dimensionId, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, withMissingDimension))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_REFERENCE_NOT_FOUND");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDimensionReferencesUnlessTheyResolveToCanonicalDimensionsAtDwd() {
        UUID dimensionId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        ModelRevisionRef dimensionRef = new ModelRevisionRef(dimensionId, 1);
        CreateModelSpecCommand requested = withDimensionRefs(
            command("dimension-contract", "customer_detail"),
            List.of(dimensionRef)
        );
        ModelSpecView base = codec.toCreatedView(
            dimensionId,
            command("dimension-target", "dimension_target"),
            NOW
        );
        List<ModelSpecView> invalidDimensions = List.of(
            copyModel(
                base,
                2,
                ModelType.DIMENSION,
                Layer.ODS,
                null,
                null,
                base.sourceRefs(),
                List.of(),
                null,
                null,
                CompatibilityMode.CANONICAL
            ),
            copyModel(
                base,
                1,
                ModelType.DIMENSION,
                Layer.DWD,
                null,
                null,
                base.sourceRefs(),
                List.of(),
                null,
                null,
                CompatibilityMode.LEGACY_READONLY
            ),
            withFactOnlyFields(
                copyModel(
                    base,
                    2,
                    ModelType.DIMENSION,
                    Layer.DWD,
                    null,
                    null,
                    base.sourceRefs(),
                    List.of(),
                    null,
                    null,
                    CompatibilityMode.CANONICAL
                ),
                FactShape.TRANSACTION,
                null,
                List.of()
            )
        );
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());

        for (ModelSpecView invalidDimension : invalidDimensions) {
            StoredModelSpec stored = stored(invalidDimension, null, null);
            when(repository.findRevision(TENANT, dimensionId, 1)).thenReturn(Optional.of(stored));
            when(compatibilityReader.read(stored)).thenReturn(invalidDimension);

            assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
                .as(invalidDimension.contractVersion() + ":" + invalidDimension.modelType() + "@" + invalidDimension.layer())
                .isInstanceOf(ModelSpecException.class)
                .extracting(error -> ((ModelSpecException) error).code())
                .isEqualTo("MODEL_SPEC_DIMENSION_REF_TYPE_INVALID");
        }

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hidesUnreadableInvalidDimensionReferencesBeforeReportingTheirContractViolation() {
        UUID dimensionId = UUID.fromString("40000000-0000-0000-0000-000000000003");
        ModelRevisionRef dimensionRef = new ModelRevisionRef(dimensionId, 1);
        CreateModelSpecCommand requested = withDimensionRefs(
            command("unreadable-dimension", "customer_detail"),
            List.of(dimensionRef)
        );
        ModelSpecView base = codec.toCreatedView(
            dimensionId,
            command("unreadable-dimension-target", "dimension_target"),
            NOW
        );
        ModelSpecView invalidDimension = copyModel(
            base,
            1,
            ModelType.DIMENSION,
            Layer.DWD,
            null,
            null,
            base.sourceRefs(),
            List.of(),
            null,
            null,
            CompatibilityMode.LEGACY_READONLY
        );
        StoredModelSpec stored = stored(invalidDimension, null, null);
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, dimensionId, 1)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(invalidDimension);
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_REFERENCE_NOT_FOUND");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDirectSummaryDependencyCycle() {
        UUID originalUpstreamId = UUID.fromString("30000000-0000-0000-0000-000000000010");
        CreateModelSpecCommand create = derivedCommand(
            "summary-direct-cycle",
            "customer_summary",
            ModelType.SUMMARY,
            List.of(new ModelRevisionRef(originalUpstreamId, 1))
        );
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        StoredModelSpec currentStored = stored(current, null, null);
        UpdateModelSpecCommand requested = update(
            derivedCommand(
                "ignored",
                "customer_summary",
                ModelType.SUMMARY,
                List.of(new ModelRevisionRef(MODEL_ID, 1))
            )
        );
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(currentStored));
        when(repository.findRevision(TENANT, MODEL_ID, 1)).thenReturn(Optional.of(currentStored));
        when(compatibilityReader.read(currentStored)).thenReturn(current);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), requested)
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException cycle = (ModelSpecException) error;
                assertThat(cycle.code()).isEqualTo("MODEL_SPEC_DEPENDENCY_CYCLE");
                assertThat(cycle.details()).isEqualTo(
                    java.util.Map.of("dependencyPath", List.of(MODEL_ID.toString(), MODEL_ID.toString()))
                );
            });
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void rejectsDirectFactDependencyCycleWhenAnUpstreamModelSuppliesItsInput() {
        UUID originalUpstreamId = UUID.fromString("30000000-0000-0000-0000-000000000010");
        CreateModelSpecCommand create = withInputs(
            command("fact-direct-cycle", "customer_detail"),
            List.of(),
            List.of(new ModelRevisionRef(originalUpstreamId, 1))
        );
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        StoredModelSpec currentStored = stored(current, null, null);
        UpdateModelSpecCommand requested = update(
            withInputs(command("ignored", "customer_detail"), List.of(), List.of(new ModelRevisionRef(MODEL_ID, 1)))
        );
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(currentStored));
        when(repository.findRevision(TENANT, MODEL_ID, 1)).thenReturn(Optional.of(currentStored));
        when(compatibilityReader.read(currentStored)).thenReturn(current);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), requested)
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_DEPENDENCY_CYCLE");
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void rejectsFactDependencyOnAHigherLayerApplicationModelBeforeWrite() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000040");
        CreateModelSpecCommand requested = withInputs(
            command("fact-higher-layer-upstream", "customer_detail"),
            List.of(),
            List.of(new ModelRevisionRef(upstreamId, 1))
        );
        ModelSpecView application = codec.toCreatedView(
            upstreamId,
            derivedCommand("application-upstream", "customer_application", ModelType.APPLICATION, List.of()),
            NOW
        );
        StoredModelSpec applicationStored = stored(application, null, null);
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, upstreamId, 1)).thenReturn(Optional.of(applicationStored));
        when(compatibilityReader.read(applicationStored)).thenReturn(application);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsHistoricalWrongLayerUpstreamModelsBeforeWrite() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000041");
        CreateModelSpecCommand requested = derivedCommand(
            "summary-wrong-layer-upstream",
            "customer_summary",
            ModelType.SUMMARY,
            List.of(new ModelRevisionRef(upstreamId, 1))
        );
        ModelSpecView wrongLayerFact = codec.toCreatedView(
            upstreamId,
            withLayer(command("historical-fact-dws", "historical_fact"), Layer.DWS),
            NOW
        );
        StoredModelSpec upstreamStored = stored(wrongLayerFact, null, null);
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, upstreamId, 1)).thenReturn(Optional.of(upstreamStored));
        when(compatibilityReader.read(upstreamStored)).thenReturn(wrongLayerFact);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsCanonicalLayerUpstreamsThatStillCarryHistoricalTypePollution() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000043");
        CreateModelSpecCommand requested = derivedCommand(
            "summary-polluted-upstream",
            "customer_summary",
            ModelType.SUMMARY,
            List.of(new ModelRevisionRef(upstreamId, 1))
        );
        ModelSpecView base = codec.toCreatedView(
            upstreamId,
            command("polluted-fact", "polluted_fact"),
            NOW
        );
        ModelSpecView pollutedFact = copyModel(
            base,
            2,
            ModelType.FACT,
            Layer.DWD,
            null,
            null,
            base.sourceRefs(),
            List.of(),
            null,
            new DimensionProfile(
                "DIM_POLLUTION",
                List.of(),
                new ScdPolicy(ScdType.TYPE1, null, null, null),
                ReuseScope.PLAN
            ),
            CompatibilityMode.CANONICAL
        );
        StoredModelSpec upstreamStored = stored(pollutedFact, null, null);
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, upstreamId, 1)).thenReturn(Optional.of(upstreamStored));
        when(compatibilityReader.read(upstreamStored)).thenReturn(pollutedFact);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hidesUnreadableInvalidUpstreamModelsBeforeReportingTheirPolicyViolation() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000042");
        CreateModelSpecCommand requested = withInputs(
            command("fact-unreadable-upstream", "customer_detail"),
            List.of(),
            List.of(new ModelRevisionRef(upstreamId, 1))
        );
        ModelSpecView application = codec.toCreatedView(
            upstreamId,
            derivedCommand("unreadable-application", "customer_application", ModelType.APPLICATION, List.of()),
            NOW
        );
        StoredModelSpec upstreamStored = stored(application, null, null);
        when(repository.findByIdempotencyKey(TENANT, requested.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.findRevision(TENANT, upstreamId, 1)).thenReturn(Optional.of(upstreamStored));
        when(compatibilityReader.read(upstreamStored)).thenReturn(application);
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, requested))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_REFERENCE_NOT_FOUND");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsIndirectApplicationDependencyCycle() {
        UUID originalUpstreamId = UUID.fromString("30000000-0000-0000-0000-000000000010");
        UUID summaryId = UUID.fromString("30000000-0000-0000-0000-000000000020");
        CreateModelSpecCommand create = derivedCommand(
            "application-indirect-cycle",
            "customer_application",
            ModelType.APPLICATION,
            List.of(new ModelRevisionRef(originalUpstreamId, 1))
        );
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        StoredModelSpec currentStored = stored(current, null, null);
        ModelSpecView summary = codec.toCreatedView(
            summaryId,
            derivedCommand(
                "summary-upstream",
                "customer_summary",
                ModelType.SUMMARY,
                List.of(new ModelRevisionRef(MODEL_ID, 1))
            ),
            NOW
        );
        StoredModelSpec summaryStored = stored(summary, null, null);
        UpdateModelSpecCommand requested = update(
            derivedCommand(
                "ignored",
                "customer_application",
                ModelType.APPLICATION,
                List.of(new ModelRevisionRef(summaryId, 1))
            )
        );
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(currentStored));
        when(repository.findRevision(TENANT, summaryId, 1)).thenReturn(Optional.of(summaryStored));
        when(compatibilityReader.read(currentStored)).thenReturn(current);
        when(compatibilityReader.read(summaryStored)).thenReturn(summary);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), requested)
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException cycle = (ModelSpecException) error;
                assertThat(cycle.code()).isEqualTo("MODEL_SPEC_DEPENDENCY_CYCLE");
                assertThat(cycle.details()).isEqualTo(
                    java.util.Map.of(
                        "dependencyPath",
                        List.of(MODEL_ID.toString(), summaryId.toString(), MODEL_ID.toString())
                    )
                );
            });
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void dependencyGraphKeepsPinnedRevisionAndReportsCurrentRevisionDrift() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000030");
        ModelSpecView root = codec.toCreatedView(
            MODEL_ID,
            derivedCommand(
                "summary-graph",
                "customer_summary",
                ModelType.SUMMARY,
                List.of(new ModelRevisionRef(upstreamId, 3))
            ),
            NOW
        );
        ModelSpecView upstreamV1 = codec.toCreatedView(upstreamId, command("upstream-v1", "customer_detail"), NOW);
        ModelSpecView upstreamV2 = codec.toUpdatedView(upstreamV1, update(command("ignored", "customer_detail")), 2, NOW);
        ModelSpecView upstreamV3 = codec.toUpdatedView(upstreamV2, update(command("ignored", "customer_detail")), 3, NOW);
        ModelSpecView upstreamV4 = codec.toUpdatedView(upstreamV3, update(command("ignored", "customer_detail")), 4, NOW);
        StoredModelSpec pinned = stored(upstreamV3, null, null);
        StoredModelSpec current = stored(upstreamV4, null, null);
        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(root);
        when(repository.findRevision(TENANT, upstreamId, 3)).thenReturn(Optional.of(pinned));
        when(repository.findCurrent(TENANT, upstreamId)).thenReturn(Optional.of(current));
        when(compatibilityReader.read(pinned)).thenReturn(upstreamV3);

        ModelSpecApplicationService.DependencyGraph graph = service.dependencyGraph(TENANT, MODEL_ID);

        assertThat(graph.rootModelSpecId()).isEqualTo(MODEL_ID);
        assertThat(graph.nodes()).extracting(ModelSpecApplicationService.DependencyNode::modelSpecId)
            .containsExactlyInAnyOrder(MODEL_ID, upstreamId);
        assertThat(graph.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.fromModelSpecId()).isEqualTo(MODEL_ID);
            assertThat(edge.toModelSpecId()).isEqualTo(upstreamId);
            assertThat(edge.pinnedRevision()).isEqualTo(3);
            assertThat(edge.currentRevision()).isEqualTo(4);
            assertThat(edge.state()).isEqualTo(ModelSpecApplicationService.DependencyState.STALE);
        });
    }

    @Test
    void rejectsUnavailableSourceBindingsBeforeAnyModelWrite() {
        CreateModelSpecCommand command = command("source-invalid", "customer_detail");
        when(repository.findByIdempotencyKey(TENANT, "source-invalid")).thenReturn(Optional.empty());
        when(sourceValidation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, command.sourceRefs().getFirst())).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException invalid = (ModelSpecException) error;
                assertThat(invalid.code()).isEqualTo("MODEL_SPEC_SOURCE_BINDING_INVALID");
                assertThat(invalid.details()).isInstanceOf(List.class);
            });
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void rejectsUnavailableSourceBindingsBeforeAnyModelUpdate() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        UpdateModelSpecCommand update = update(create);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(sourceValidation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, update.sourceRefs().getFirst())).thenReturn(false);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), update)
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SOURCE_BINDING_INVALID");

        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void deniesWritesWhenTheActorCannotMaintainTheWarehousePlan() {
        CreateModelSpecCommand command = command("plan-denied", "customer_detail");
        when(repository.findByIdempotencyKey(TENANT, "plan-denied")).thenReturn(Optional.empty());
        when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_PLAN_FORBIDDEN");
    }

    @Test
    void rejectsMovingAModelToAnotherDomain() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        UUID anotherDomain = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UpdateModelSpecCommand base = update(create);
        UpdateModelSpecCommand moved = new UpdateModelSpecCommand(
            base.planId(), anotherDomain, base.modelType(), base.layer(), base.name(), base.description(),
            base.implementationMode(), base.materialization(), base.businessActivityRef(), base.consumptionScenario(),
            base.grain(), base.factShape(), base.timeSemantics(), base.fields(), base.sourceRefs(), base.dependsOn(),
            base.dimensionRefs(), base.metricRefs(), base.standardBindings(), base.generationStrategy()
        );

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), moved)
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_DOMAIN_IMMUTABLE");
    }

    @Test
    void authorizesBeforeReturningStaleEtagDetails() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 99, "f".repeat(64)), update(create))
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException denied = (ModelSpecException) error;
                assertThat(denied.code()).isEqualTo("MODEL_SPEC_PLAN_FORBIDDEN");
                assertThat(denied.details()).isNull();
            });
    }

    @Test
    void authorizesTheCurrentContextBeforeReportingAnImmutableTarget() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);
        UUID anotherDomain = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UpdateModelSpecCommand base = update(create);
        UpdateModelSpecCommand moved = new UpdateModelSpecCommand(
            base.planId(), anotherDomain, base.modelType(), base.layer(), base.name(), base.description(),
            base.implementationMode(), base.materialization(), base.businessActivityRef(), base.consumptionScenario(),
            base.grain(), base.factShape(), base.timeSemantics(), base.fields(), base.sourceRefs(), base.dependsOn(),
            base.dimensionRefs(), base.metricRefs(), base.standardBindings(), base.generationStrategy()
        );

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), moved)
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_PLAN_FORBIDDEN");
        verify(repository).lockPlan(TENANT, PLAN_ID);
        verify(repository, never()).lockDomainBinding(TENANT, PLAN_ID, anotherDomain);
    }

    @Test
    void convergesAConcurrentInsertThroughTheOriginalResponseSnapshot() {
        CreateModelSpecCommand command = command("concurrent-1", "customer_detail");
        ModelSpecView created = codec.toCreatedView(MODEL_ID, command, NOW);
        StoredModelSpec winner = stored(created, codec.requestHash(command), codec.write(created));
        when(repository.findByIdempotencyKey(TENANT, "concurrent-1"))
            .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(0);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.modelSpec()).isEqualTo(created);
        verify(repository, never()).insertV2Revision(any(), any(), any(), any());
    }

    @Test
    void returnsLatestVersionDetailsWhenRepositoryCasLosesTheRace() {
        CreateModelSpecCommand create = command("create-1", "customer_detail");
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        UpdateModelSpecCommand requested = update(command("ignored", "customer_detail_requested"));
        ModelSpecView latest = codec.toUpdatedView(current, update(command("ignored", "customer_detail_other")), 2, NOW.plusSeconds(30));
        when(repository.findCurrent(TENANT, MODEL_ID))
            .thenReturn(Optional.of(stored(current, null, null)), Optional.of(stored(latest, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current, latest);
        when(repository.compareAndSetV2(eq(TENANT), eq(ACTOR), eq(1), eq(current.checksum()), any(), anyString()))
            .thenReturn(0);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), requested)
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException conflict = (ModelSpecException) error;
                assertThat(conflict.code()).isEqualTo("MODEL_SPEC_REVISION_CONFLICT");
                assertThat(conflict.details()).isEqualTo(
                    java.util.Map.of(
                        "currentRevision", 2,
                        "currentChecksum", latest.checksum(),
                        "currentEtag", ModelSpecApplicationService.etag(latest)
                    )
                );
            });
        verify(repository, never()).insertV2Revision(eq(TENANT), eq(ACTOR), any(), anyString());
    }

    @Test
    void rejectsMissingServerTenantAndActorBeforePersistence() {
        CreateModelSpecCommand command = command("context-1", "customer_detail");

        assertThatThrownBy(() -> service.create(" ", ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SERVER_TENANT_REQUIRED");
        assertThatThrownBy(() -> service.create(TENANT, " ", command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_ACTOR_REQUIRED");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rollbackFlagsDisableCanonicalWritesAndHideV2WhileLegacyReadsRemainAvailable() {
        ModelSpecApplicationService disabled = new ModelSpecApplicationService(
            repository,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            new ModelSpecFeatureFlags(false, false),
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        ModelSpecView canonical = codec.toCreatedView(MODEL_ID, command("flag-v2", "customer_detail"), NOW);
        ModelSpecView legacy = legacyView();
        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(canonical);
        when(compatibilityReader.get(TENANT, legacy.id())).thenReturn(legacy);
        when(compatibilityReader.list(TENANT, null, null, null, null)).thenReturn(List.of(canonical, legacy));

        assertThatThrownBy(() -> disabled.create(TENANT, ACTOR, command("write-off", "customer_detail")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_CANONICAL_WRITE_DISABLED");
        CreateModelSpecCommand replayCommand = command("write-off-replay", "customer_detail");
        ModelSpecView original = codec.toCreatedView(MODEL_ID, replayCommand, NOW);
        when(repository.findByIdempotencyKey(TENANT, "write-off-replay"))
            .thenReturn(Optional.of(stored(original, codec.requestHash(replayCommand), codec.write(original))));
        assertThat(disabled.create(TENANT, ACTOR, replayCommand).replayed()).isTrue();
        assertThatThrownBy(() -> disabled.get(TENANT, MODEL_ID))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_NOT_FOUND");
        assertThat(disabled.get(TENANT, legacy.id())).isEqualTo(legacy);
        assertThat(disabled.list(TENANT, null, null, null, null)).containsExactly(legacy);
    }

    @Test
    void failsClosedForUnavailableOrNonMaintainableDomains() {
        when(domainResolution.resolve(DOMAIN_ID)).thenReturn(
            new DomainResolution(DOMAIN_ID, CatalogDomainResolutionPort.ResolutionStatus.MISSING, null, null, null, null),
            new DomainResolution(DOMAIN_ID, CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED, "Archived", "ARCHIVED", null, null),
            new DomainResolution(DOMAIN_ID, CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN, null, null, null, null),
            new DomainResolution(DOMAIN_ID, CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE, "Customers", "CUSTOMERS", null, null)
        );
        when(domainWriteAccess.canMaintain(DOMAIN_ID)).thenReturn(false);

        assertCreateCode("domain-missing", "MODEL_SPEC_DOMAIN_MISSING");
        assertCreateCode("domain-archived", "MODEL_SPEC_DOMAIN_ARCHIVED");
        assertCreateCode("domain-forbidden", "MODEL_SPEC_DOMAIN_FORBIDDEN");
        assertCreateCode("domain-no-edit", "MODEL_SPEC_DOMAIN_FORBIDDEN");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    private void assertCreateCode(String idempotencyKey, String code) {
        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command(idempotencyKey, "customer_detail_" + idempotencyKey)))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo(code);
    }

    private StoredModelSpec stored(ModelSpecView view, String requestHash, String responseSnapshot) {
        return new StoredModelSpec(
            2,
            TENANT,
            view.id(),
            view.planId(),
            view.domainId(),
            view.status(),
            view.revision(),
            view.checksum(),
            codec.write(view),
            null,
            "create-1",
            requestHash,
            responseSnapshot,
            view.createdAt(),
            view.updatedAt()
        );
    }

    private static CreateModelSpecCommand command(String idempotencyKey, String name) {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelType.FACT,
            Layer.DWD,
            name,
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer event", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    SOURCE_BINDING_ID,
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            idempotencyKey
        );
    }

    private static CreateModelSpecCommand derivedCommand(
        String idempotencyKey,
        String name,
        ModelType modelType,
        List<ModelRevisionRef> dependsOn
    ) {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            modelType,
            modelType == ModelType.SUMMARY ? Layer.DWS : Layer.ADS,
            name,
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            modelType == ModelType.APPLICATION ? "customer dashboard" : null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(),
            dependsOn,
            List.of(),
            List.of(),
            List.of(),
            null,
            idempotencyKey
        );
    }

    private static CreateModelSpecCommand withInputs(
        CreateModelSpecCommand command,
        List<SourceRef> sourceRefs,
        List<ModelRevisionRef> dependsOn
    ) {
        return new CreateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            sourceRefs,
            dependsOn,
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand withDimensionRefs(
        CreateModelSpecCommand command,
        List<ModelRevisionRef> dimensionRefs
    ) {
        return new CreateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            dimensionRefs,
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand withLayer(CreateModelSpecCommand command, Layer layer) {
        return new CreateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            layer,
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.idempotencyKey()
        );
    }

    private static SourceRef withLayer(SourceRef source, Layer layer) {
        return new SourceRef(
            source.kind(),
            source.ref(),
            layer,
            source.role(),
            source.alias(),
            source.joinType(),
            source.joinExpression(),
            source.sortOrder(),
            source.sourceBindingId(),
            source.resolvedVersion()
        );
    }

    private static UpdateModelSpecCommand update(CreateModelSpecCommand command) {
        return new UpdateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy()
        );
    }

    private static UpdateModelSpecCommand update(ModelSpecView view) {
        return new UpdateModelSpecCommand(
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile()
        );
    }

    private static CreateModelSpecCommand pinnedDimensionCommand(
        String idempotencyKey,
        DimensionProfile dimensionProfile
    ) {
        return new CreateModelSpecCommand(
            PLAN_ID,
            DOMAIN_ID,
            ModelType.DIMENSION,
            Layer.DWD,
            "customer_dimension",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            dimensionProfile,
            new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1),
            idempotencyKey
        );
    }

    private static DimensionProfile legacyDimensionProfile() {
        return new DimensionProfile(
            "DIM_CUSTOMER",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.DOMAIN
        );
    }

    private static UpdateModelSpecCommand canonicalDimensionUpdate(ModelSpecView current, String name) {
        DimensionProfile currentProfile = current.dimensionProfile();
        DimensionProfile canonicalProfile = currentProfile == null
            ? null
            : new DimensionProfile(null, currentProfile.hierarchies(), currentProfile.scdPolicy(), null);
        return new UpdateModelSpecCommand(
            current.planId(),
            current.domainId(),
            current.modelType(),
            current.layer(),
            name,
            current.description(),
            current.implementationMode(),
            current.materialization(),
            current.businessActivityRef(),
            current.consumptionScenario(),
            current.grain(),
            current.factShape(),
            current.timeSemantics(),
            current.fields(),
            current.sourceRefs(),
            current.dependsOn(),
            current.dimensionRefs(),
            current.metricRefs(),
            current.standardBindings(),
            current.generationStrategy(),
            canonicalProfile
        );
    }

    private static ModelSpecView copyModel(
        ModelSpecView base,
        int contractVersion,
        ModelType modelType,
        Layer layer,
        String businessActivityRef,
        String consumptionScenario,
        List<SourceRef> sourceRefs,
        List<ModelRevisionRef> dependsOn,
        GenerationStrategy generationStrategy,
        DimensionProfile dimensionProfile,
        CompatibilityMode compatibilityMode
    ) {
        return new ModelSpecView(
            contractVersion,
            base.id(),
            base.planId(),
            base.domainId(),
            modelType,
            layer,
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            businessActivityRef,
            consumptionScenario,
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            sourceRefs,
            dependsOn,
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            generationStrategy,
            dimensionProfile,
            base.status(),
            base.revision(),
            base.checksum(),
            base.createdAt(),
            base.updatedAt(),
            compatibilityMode,
            base.legacyRefs()
        );
    }

    private static ModelSpecView withFactOnlyFields(
        ModelSpecView base,
        FactShape factShape,
        TimeSemantics timeSemantics,
        List<ModelRevisionRef> dimensionRefs
    ) {
        return new ModelSpecView(
            base.contractVersion(),
            base.id(),
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            factShape,
            timeSemantics,
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            dimensionRefs,
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.dimensionProfile(),
            base.status(),
            base.revision(),
            base.checksum(),
            base.createdAt(),
            base.updatedAt(),
            base.compatibilityMode(),
            base.legacyRefs()
        );
    }

    private static ModelSpecView legacyView() {
        return new ModelSpecView(
            1,
            UUID.fromString("30000000-0000-0000-0000-000000000099"),
            null,
            null,
            ModelType.DIMENSION,
            Layer.DWD,
            "legacy_customer",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "legacy_unknown", true, null, FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            NOW,
            NOW,
            CompatibilityMode.LEGACY_READONLY,
            new LegacyRefs("legacy-model", List.of(), List.of(), List.of())
        );
    }
}
