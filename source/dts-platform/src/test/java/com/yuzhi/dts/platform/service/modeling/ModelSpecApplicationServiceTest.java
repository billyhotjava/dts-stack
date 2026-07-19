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
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
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
            compatibilityReader,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        lenient().when(repository.lockPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        lenient().when(repository.lockDomainBinding(TENANT, PLAN_ID, DOMAIN_ID))
            .thenReturn(Optional.of(new DomainBindingState(DOMAIN_ID, "CONFIRMED")));
        lenient().when(repository.findSourceBinding(TENANT, PLAN_ID, SOURCE_BINDING_ID))
            .thenReturn(
                Optional.of(new SourceBindingState(SOURCE_BINDING_ID, "CATALOG_TABLE", "ods.customer", "v1", "CONFIRMED"))
            );
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
    void rejectsSourcesThatAreNotConfirmedExactBindingsOfTheCurrentPlan() {
        CreateModelSpecCommand command = command("source-invalid", "customer_detail");
        when(repository.findByIdempotencyKey(TENANT, "source-invalid")).thenReturn(Optional.empty());
        when(repository.findSourceBinding(TENANT, PLAN_ID, SOURCE_BINDING_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException invalid = (ModelSpecException) error;
                assertThat(invalid.code()).isEqualTo("MODEL_SPEC_SOURCE_BINDING_INVALID");
                assertThat(invalid.details()).isInstanceOf(List.class);
            });

        when(repository.findSourceBinding(TENANT, PLAN_ID, SOURCE_BINDING_ID))
            .thenReturn(
                Optional.of(new SourceBindingState(SOURCE_BINDING_ID, "CATALOG_TABLE", "ods.customer", "v2", "CONFIRMED"))
            );

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SOURCE_BINDING_INVALID");

        when(repository.findSourceBinding(TENANT, PLAN_ID, SOURCE_BINDING_ID))
            .thenReturn(
                Optional.of(new SourceBindingState(SOURCE_BINDING_ID, "EXCEL_FILE", "ods.customer", "v1", "CONFIRMED"))
            );
        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SOURCE_BINDING_INVALID");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
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
