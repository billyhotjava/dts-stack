package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.DomainBindingState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.ResolvedWarehouseLayer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ReclassificationPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ModelSpecApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_BINDING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID BUSINESS_PROCESS_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID DATA_MART_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_DOMAIN_ID = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Mock
    private ModelSpecRepository repository;

    @Mock
    private DimensionDefinitionRepository dimensionDefinitions;

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
    private ModelSpecReader compatibilityReader;

    @Mock
    private AuditService auditService;

    private ModelSpecSnapshotCodec codec;
    private ModelSpecApplicationService service;

    @BeforeEach
    void setUp() {
        codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        service = new ModelSpecApplicationService(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            auditService,
            warehouseLayers(),
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
        StoredDimensionDefinition defaultDefinition = definition(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            DOMAIN_ID,
            1,
            DimensionDefinitionContract.Status.CURRENT
        );
        lenient().when(dimensionDefinitions.findRevision(eq(TENANT), any(), anyInt())).thenReturn(Optional.of(defaultDefinition));
        lenient().when(dimensionDefinitions.findCurrent(eq(TENANT), any())).thenReturn(Optional.of(defaultDefinition));
        lenient().when(dimensionDefinitions.findCurrentForShare(eq(TENANT), any())).thenReturn(Optional.of(defaultDefinition));
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
        InOrder order = inOrder(repository, auditService);
        order.verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result.modelSpec()), anyString());
        order.verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
    }

    @Test
    void createsAgainstAnAvailableGlobalDomainWithoutALegacyPlanDomainBinding() {
        CreateModelSpecCommand command = command("global-domain-create", "customer_global_domain");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.modelSpec().domainId()).isEqualTo(DOMAIN_ID);
        verify(domainResolution).resolve(DOMAIN_ID);
        verify(domainWriteAccess).canMaintain(DOMAIN_ID);
    }

    @Test
    void createsAnImportedModelWithTheIdCommittedByPreview() {
        UUID previewedId = UUID.fromString("30000000-0000-0000-0000-000000000070");
        CreateModelSpecCommand command = withBusinessContext(
            command("s70-import-create", "customer_imported"),
            BUSINESS_PROCESS_ID,
            null,
            null
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.hasConfirmedBusinessProcess(BUSINESS_PROCESS_ID, DOMAIN_ID)).thenReturn(true);
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.createImported(TENANT, ACTOR, previewedId, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.modelSpec().id()).isEqualTo(previewedId);
        InOrder order = inOrder(repository, auditService);
        order.verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result.modelSpec()), anyString());
        order.verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_IMPORT_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(previewedId.toString()),
            any()
        );
    }

    @Test
    void rejectsAnImportedFactWhoseStableProcessDoesNotBelongToTheModelDomain() {
        CreateModelSpecCommand command = withBusinessContext(
            command("fact-process-mismatch", "customer_process_mismatch"),
            BUSINESS_PROCESS_ID,
            null,
            null
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.hasConfirmedBusinessProcess(BUSINESS_PROCESS_ID, DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createImported(TENANT, ACTOR, MODEL_ID, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_BUSINESS_PROCESS_CONTEXT_INVALID");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnImportedApplicationWhoseSubjectDoesNotBelongToTheSelectedMart() {
        UUID upstreamId = UUID.fromString("73000000-0000-0000-0000-000000000001");
        CreateModelSpecCommand command = withBusinessContext(
            derivedCommand(
                "application-subject-mismatch",
                "customer_application_mismatch",
                ModelType.APPLICATION,
                List.of(new ModelRevisionRef(upstreamId, 1))
            ),
            null,
            DATA_MART_ID,
            SUBJECT_DOMAIN_ID
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.hasCurrentDataMartForDomain(TENANT, DATA_MART_ID, DOMAIN_ID)).thenReturn(true);
        when(repository.hasCurrentSubjectDomain(TENANT, SUBJECT_DOMAIN_ID, DATA_MART_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createImported(TENANT, ACTOR, MODEL_ID, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SUBJECT_DOMAIN_CONTEXT_INVALID");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnImportedApplicationWhoseDataMartDoesNotOwnTheModelDomain() {
        UUID upstreamId = UUID.fromString("73000000-0000-0000-0000-000000000002");
        CreateModelSpecCommand command = withBusinessContext(
            derivedCommand(
                "application-mart-mismatch",
                "customer_application_wrong_mart",
                ModelType.APPLICATION,
                List.of(new ModelRevisionRef(upstreamId, 1))
            ),
            null,
            DATA_MART_ID,
            SUBJECT_DOMAIN_ID
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.hasCurrentDataMartForDomain(TENANT, DATA_MART_ID, DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createImported(TENANT, ACTOR, MODEL_ID, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_DATA_MART_CONTEXT_INVALID");

        verify(repository, never()).hasCurrentSubjectDomain(anyString(), any(), any());
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
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
    void createsAnInteractiveFactDraftBeforeLogicalDesignIsFilled() {
        CreateModelSpecCommand command = withoutLogicalDesign(command("fact-lightweight", "customer_detail_draft"));
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.modelSpec().grain()).isNull();
        assertThat(result.modelSpec().fields()).isEmpty();
        assertThat(result.modelSpec().sourceRefs()).isEmpty();
        verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result.modelSpec()), anyString());
    }

    @Test
    void importedModelsStillRequireCompleteLogicalDesign() {
        CreateModelSpecCommand command = withoutLogicalDesign(command("fact-import-incomplete", "customer_import_incomplete"));

        assertThatThrownBy(() ->
                service.createImported(
                    TENANT,
                    ACTOR,
                    UUID.fromString("30000000-0000-0000-0000-000000000071"),
                    command
                )
            )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_VALIDATION_FAILED");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createsADimensionOnlyWhenItsPinnedDefinitionIsTheVisibleCurrentHead() {
        CreateModelSpecCommand command = pinnedDimensionCommand("dimension-current", null);
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        StoredDimensionDefinition pinned = definition(ref.dimensionDefinitionId(), DOMAIN_ID, ref.revision(), DimensionDefinitionContract.Status.CURRENT);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(dimensionDefinitions.findCurrentForShare(TENANT, ref.dimensionDefinitionId())).thenReturn(Optional.of(pinned));
        when(repository.insertV2(eq(TENANT), eq(ACTOR), eq(command), any(), anyString(), anyString())).thenReturn(1);

        ModelSpecApplicationService.CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.modelSpec().dimensionDefinitionRef()).isEqualTo(ref);
        verify(dimensionDefinitions).findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision());
        verify(dimensionDefinitions).findCurrentForShare(TENANT, ref.dimensionDefinitionId());
        verify(dimensionDefinitions, never()).findCurrent(TENANT, ref.dimensionDefinitionId());
        verify(domainReadAccess).canRead(DOMAIN_ID);
    }

    @Test
    void returnsTheRequiredDimensionDefinitionCodeWhenADimensionCreateOmitsItsPin() {
        CreateModelSpecCommand command = withoutDimensionDefinitionRef(pinnedDimensionCommand("dimension-required", null));

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_REQUIRED");

        verify(repository, never()).findByIdempotencyKey(any(), any());
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsADimensionCreationWhenItsPinnedDefinitionRevisionDoesNotExist() {
        CreateModelSpecCommand command = pinnedDimensionCommand("dimension-missing-revision", null);
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_NOT_CURRENT");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsADimensionCreationWhenThePinnedDefinitionDomainIsNotVisible() {
        CreateModelSpecCommand command = pinnedDimensionCommand("dimension-hidden", null);
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        StoredDimensionDefinition pinned = definition(ref.dimensionDefinitionId(), DOMAIN_ID, ref.revision(), DimensionDefinitionContract.Status.CURRENT);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_NOT_CURRENT");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsADimensionCreationWhenThePinnedDefinitionRevisionIsNoLongerTheCurrentHead() {
        CreateModelSpecCommand command = pinnedDimensionCommand("dimension-stale", null);
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        StoredDimensionDefinition pinned = definition(ref.dimensionDefinitionId(), DOMAIN_ID, 1, DimensionDefinitionContract.Status.CURRENT);
        StoredDimensionDefinition current = definition(ref.dimensionDefinitionId(), DOMAIN_ID, 2, DimensionDefinitionContract.Status.CURRENT);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(dimensionDefinitions.findCurrentForShare(TENANT, ref.dimensionDefinitionId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_NOT_CURRENT");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsADimensionCreationWhenTheDefinitionIsRetired() {
        CreateModelSpecCommand command = pinnedDimensionCommand("dimension-retired", null);
        DimensionDefinitionRef ref = command.dimensionDefinitionRef();
        StoredDimensionDefinition pinned = definition(ref.dimensionDefinitionId(), DOMAIN_ID, ref.revision(), DimensionDefinitionContract.Status.CURRENT);
        StoredDimensionDefinition retired = definition(ref.dimensionDefinitionId(), DOMAIN_ID, ref.revision(), DimensionDefinitionContract.Status.RETIRED);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(dimensionDefinitions.findCurrentForShare(TENANT, ref.dimensionDefinitionId())).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_NOT_CURRENT");

        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void keepsTheOriginalPinnedRevisionWhenTheDefinitionAdvancesBeforeADimensionUpdate() {
        CreateModelSpecCommand create = pinnedDimensionCommand("dimension-update", null);
        ModelSpecView current = codec.toCreatedView(MODEL_ID, create, NOW);
        StoredModelSpec stored = stored(current, null, null);
        DimensionDefinitionRef ref = current.dimensionDefinitionRef();
        StoredDimensionDefinition pinned = definition(ref.dimensionDefinitionId(), DOMAIN_ID, 1, DimensionDefinitionContract.Status.CURRENT);
        StoredDimensionDefinition advancedHead = definition(ref.dimensionDefinitionId(), DOMAIN_ID, 2, DimensionDefinitionContract.Status.CURRENT);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(current);
        when(dimensionDefinitions.findRevision(TENANT, ref.dimensionDefinitionId(), ref.revision())).thenReturn(Optional.of(pinned));
        when(dimensionDefinitions.findCurrent(TENANT, ref.dimensionDefinitionId())).thenReturn(Optional.of(advancedHead));
        when(repository.compareAndSetV2(eq(TENANT), eq(ACTOR), eq(1), eq(current.checksum()), any(), anyString())).thenReturn(1);

        ModelSpecView replacement = service.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 1, current.checksum()),
            canonicalDimensionUpdate(current, "customer_dimension_v2")
        );

        assertThat(replacement.dimensionDefinitionRef()).isEqualTo(ref);
        assertThat(replacement.revision()).isEqualTo(2);
    }

    @Test
    void transitionsOnlyDesignerOwnershipAndForwardsTheExactModelPins() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("ownership-transition", "customer_detail"), NOW);
        StoredModelSpec stored = stored(current, null, null);
        ExpectedVersion expected = new ExpectedVersion(MODEL_ID, current.revision(), current.checksum());
        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(current);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(compatibilityReader.read(stored)).thenReturn(current);
        when(repository.compareAndSetV2(
            eq(TENANT), eq(ACTOR), eq(current.revision()), eq(current.checksum()),
            argThat(view -> view.implementationMode() == ImplementationMode.DBT_MANAGED), anyString()
        )).thenReturn(1);

        ModelSpecView transitioned = service.transitionToDbtManaged(TENANT, ACTOR, MODEL_ID, expected);

        assertThat(transitioned.implementationMode()).isEqualTo(ImplementationMode.DBT_MANAGED);
        assertThat(transitioned.revision()).isEqualTo(current.revision() + 1);
        verify(repository).compareAndSetV2(
            eq(TENANT), eq(ACTOR), eq(expected.revision()), eq(expected.checksum()),
            argThat(view -> view.id().equals(MODEL_ID) && view.implementationMode() == ImplementationMode.DBT_MANAGED),
            anyString()
        );

        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(transitioned);
        assertThatThrownBy(() -> service.transitionToDbtManaged(
            TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, transitioned.revision(), transitioned.checksum())
        )).isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_DESIGNER_REQUIRED");
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
        verify(auditService, never()).auditAction(anyString(), any(), anyString(), any());

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
        verify(auditService, never()).auditAction(anyString(), any(), anyString(), any());
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
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_TYPE_IMMUTABLE");
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
        InOrder order = inOrder(repository, auditService);
        order.verify(repository).insertV2Revision(TENANT, ACTOR, result, codec.write(result));
        order.verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_UPDATE"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
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
        verify(auditService, never()).auditAction(anyString(), any(), anyString(), any());
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
        verify(auditService, never()).auditAction(anyString(), any(), anyString(), any());
    }

    @Test
    void previewsDraftReclassificationWithoutWritingAndExplainsTheRequiredCleanup() {
        ModelSpecView current = withFactOnlyFields(
            codec.toCreatedView(MODEL_ID, command("finance-r4", "finance_project"), NOW),
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("event_time")),
            List.of()
        );
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);

        ModelSpecApplicationService.ReclassificationPreview preview = service.previewReclassification(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ModelSpecApplicationService.ReclassificationPreviewRequest(
                ModelType.DIMENSION,
                new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1)
            )
        );

        assertThat(preview.eligible()).isTrue();
        assertThat(preview.fromType()).isEqualTo(ModelType.FACT);
        assertThat(preview.toType()).isEqualTo(ModelType.DIMENSION);
        assertThat(preview.targetLayer()).isEqualTo(Layer.DWD);
        assertThat(preview.clearFields()).contains("factShape", "timeSemantics");
        assertThat(preview.requiredFields()).contains("dimensionDefinitionRef", "grain", "fields.KEY");
        assertThat(preview.currentRevision()).isEqualTo(1);
        assertThat(preview.checksum()).isEqualTo(current.checksum());
        verify(dimensionDefinitions).findCurrent(
            TENANT,
            UUID.fromString("60000000-0000-0000-0000-000000000001")
        );
        verify(dimensionDefinitions, never()).findCurrentForShare(
            TENANT,
            UUID.fromString("60000000-0000-0000-0000-000000000001")
        );
        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
        verify(repository, never()).insertV2Revision(any(), any(), any(), anyString());
    }

    @Test
    void reclassificationPreviewTransactionPermitsTheExistingSharedAccessChecks() throws NoSuchMethodException {
        Transactional transaction = ModelSpecApplicationService.class
            .getMethod(
                "previewReclassification",
                String.class,
                String.class,
                UUID.class,
                ModelSpecApplicationService.ReclassificationPreviewRequest.class
            )
            .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }

    @Test
    void reclassifiesByAppendingARevisionOnlyAfterEveryClearFieldIsAccepted() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("finance-r4-apply", "finance_project"), NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.findReclassificationReplay(TENANT, MODEL_ID, "finance-r4-to-dimension")).thenReturn(Optional.empty());
        when(repository.compareAndSetV2(eq(TENANT), eq(ACTOR), eq(1), eq(current.checksum()), any(), anyString()))
            .thenReturn(1);

        ModelSpecApplicationService.ReclassificationPreview preview = service.previewReclassification(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ModelSpecApplicationService.ReclassificationPreviewRequest(
                ModelType.DIMENSION,
                new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1)
            )
        );
        ModelSpecView result = service.reclassify(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, current.revision(), current.checksum()),
            new ModelSpecApplicationService.ReclassificationCommand(
                ModelType.DIMENSION,
                new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1),
                preview.clearFields(),
                "finance-r4-to-dimension"
            )
        );

        assertThat(result.id()).isEqualTo(current.id());
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.modelType()).isEqualTo(ModelType.DIMENSION);
        assertThat(result.layer()).isEqualTo(Layer.DWD);
        assertThat(result.factShape()).isNull();
        assertThat(result.timeSemantics()).isNull();
        assertThat(result.dimensionDefinitionRef()).isNotNull();
        InOrder order = inOrder(repository, auditService);
        order.verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(result), anyString());
        order.verify(repository).insertReclassificationCommand(
            TENANT,
            ACTOR,
            MODEL_ID,
            "finance-r4-to-dimension",
            ModelType.FACT,
            ModelType.DIMENSION,
            result.revision(),
            result.checksum(),
            NOW
        );
        order.verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_RECLASSIFY"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
    }

    @Test
    void reclassificationFailsClosedWhenAnyRuntimeOrReleaseEvidenceExists() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("finance-r4-evidence", "finance_project"), NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.hasReclassificationEvidence(TENANT, MODEL_ID)).thenReturn(true);

        ModelSpecApplicationService.ReclassificationPreview preview = service.previewReclassification(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ModelSpecApplicationService.ReclassificationPreviewRequest(
                ModelType.DIMENSION,
                new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 1)
            )
        );

        assertThat(preview.eligible()).isFalse();
        assertThat(preview.reasonCodes()).containsExactly("MODEL_RECLASSIFY_RUNTIME_EVIDENCE_EXISTS");
    }

    @Test
    void ordinaryDraftUpdateCannotBypassTheExplicitReclassificationWorkflow() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("finance-r4-update", "finance_project"), NOW);
        UpdateModelSpecCommand requested = update(pinnedDimensionCommand("ignored", null));
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);

        assertThatThrownBy(
            () -> service.update(TENANT, ACTOR, MODEL_ID, new ExpectedVersion(MODEL_ID, 1, current.checksum()), requested)
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_TYPE_IMMUTABLE");

        verify(repository, never()).compareAndSetV2(any(), any(), anyInt(), anyString(), any(), anyString());
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
    void canonicalWriteFlagDisablesNewWritesWithoutHidingCanonicalReads() {
        ModelSpecApplicationService disabled = new ModelSpecApplicationService(
            repository,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            new ModelSpecFeatureFlags(false),
            auditService,
            warehouseLayers(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        ModelSpecView canonical = codec.toCreatedView(MODEL_ID, command("flag-v2", "customer_detail"), NOW);
        when(compatibilityReader.get(TENANT, MODEL_ID)).thenReturn(canonical);
        when(compatibilityReader.list(TENANT, null, null, null, null)).thenReturn(List.of(canonical));
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(true);

        assertThatThrownBy(() -> disabled.create(TENANT, ACTOR, command("write-off", "customer_detail")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_CANONICAL_WRITE_DISABLED");
        CreateModelSpecCommand replayCommand = command("write-off-replay", "customer_detail");
        ModelSpecView original = codec.toCreatedView(MODEL_ID, replayCommand, NOW);
        when(repository.findByIdempotencyKey(TENANT, "write-off-replay"))
            .thenReturn(Optional.of(stored(original, codec.requestHash(replayCommand), codec.write(original))));
        assertThat(disabled.create(TENANT, ACTOR, replayCommand).replayed()).isTrue();
        assertThat(disabled.get(TENANT, MODEL_ID)).isEqualTo(canonical);
        assertThat(disabled.list(TENANT, null, null, null, null)).containsExactly(canonical);
    }

    @Test
    void loadsABoundedRelationshipGraphSliceWithOneBatchDomainVisibilityDecision() {
        ModelSpecView canonical = codec.toCreatedView(MODEL_ID, command("graph-v2", "customer_detail"), NOW);
        StoredModelSpec stored = stored(canonical, "request-hash", codec.write(canonical));
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(DOMAIN_ID));
        when(repository.listCurrentForRelationshipGraph(TENANT, PLAN_ID, Set.of(DOMAIN_ID), 501))
            .thenReturn(List.of(stored));
        when(compatibilityReader.readForRelationshipGraph(List.of(stored))).thenReturn(List.of(canonical));

        assertThat(service.listForRelationshipGraph(TENANT, PLAN_ID, 501)).containsExactly(canonical);

        verify(domainReadAccess).visibleDomainIds();
        verify(repository).listCurrentForRelationshipGraph(TENANT, PLAN_ID, Set.of(DOMAIN_ID), 501);
        verify(domainReadAccess, never()).canRead(any());
    }

    @Test
    void forwardsTheRelationshipGraphUuidKeysetCursorIntoTheAuthorizedBatchQuery() {
        UUID cursor = UUID.fromString("30000000-0000-0000-0000-000000000000");
        ModelSpecView canonical = codec.toCreatedView(MODEL_ID, command("graph-cursor", "customer_detail"), NOW);
        StoredModelSpec stored = stored(canonical, "request-hash", codec.write(canonical));
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(DOMAIN_ID));
        when(repository.listCurrentForRelationshipGraph(TENANT, PLAN_ID, Set.of(DOMAIN_ID), cursor, 501))
            .thenReturn(List.of(stored));
        when(compatibilityReader.readForRelationshipGraph(List.of(stored))).thenReturn(List.of(canonical));

        assertThat(service.listForRelationshipGraph(TENANT, PLAN_ID, cursor, 501)).containsExactly(canonical);

        verify(repository).listCurrentForRelationshipGraph(TENANT, PLAN_ID, Set.of(DOMAIN_ID), cursor, 501);
        verify(domainReadAccess).visibleDomainIds();
        verify(domainReadAccess, never()).canRead(any());
    }

    @Test
    void loadsPinnedRelationshipGraphRevisionsInOneAuthorizedBatch() {
        ModelRevisionRef reference = new ModelRevisionRef(MODEL_ID, 2);
        ModelSpecView pinned = codec.toCreatedView(MODEL_ID, command("graph-revision", "customer_detail"), NOW);
        StoredModelSpec stored = stored(pinned, "request-hash", codec.write(pinned));
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(DOMAIN_ID));
        when(
            repository.listRevisionsForRelationshipGraph(
                TENANT,
                List.of(reference),
                Set.of(DOMAIN_ID),
                500
            )
        )
            .thenReturn(List.of(stored));
        when(compatibilityReader.readForRelationshipGraph(List.of(stored))).thenReturn(List.of(pinned));

        assertThat(service.revisionsForRelationshipGraph(TENANT, List.of(reference), 500))
            .containsExactly(pinned);

        verify(domainReadAccess).visibleDomainIds();
        verify(repository)
            .listRevisionsForRelationshipGraph(TENANT, List.of(reference), Set.of(DOMAIN_ID), 500);
        verify(domainReadAccess, never()).canRead(any());
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

    @Test
    void deletesAnUnreferencedDraftByArchivingItsCanonicalLedgerHead() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("delete-draft", "customer_detail"), NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.compareAndSetLifecycle(
                eq(TENANT),
                eq(ACTOR),
                eq(current.revision()),
                eq(current.checksum()),
                eq(ModelStatus.DRAFT),
                any()
            ))
            .thenReturn(1);
        when(repository.updateV2RevisionLifecycle(eq(TENANT), eq(ACTOR), eq(ModelStatus.DRAFT), any(), anyString()))
            .thenReturn(1);

        service.deleteDraft(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, current.revision(), current.checksum())
        );

        verify(repository).hasActiveModelReferences(TENANT, MODEL_ID);
        verify(repository).compareAndSetLifecycle(
            eq(TENANT),
            eq(ACTOR),
            eq(current.revision()),
            eq(current.checksum()),
            eq(ModelStatus.DRAFT),
            argThat(archived -> archived.status() == ModelStatus.ARCHIVED && archived.id().equals(MODEL_ID))
        );
        InOrder order = inOrder(repository, auditService);
        order.verify(repository).updateV2RevisionLifecycle(
            eq(TENANT),
            eq(ACTOR),
            eq(ModelStatus.DRAFT),
            argThat(archived -> archived.status() == ModelStatus.ARCHIVED),
            anyString()
        );
        order.verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_DELETE_DRAFT"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
    }

    @Test
    void refusesToDeleteDraftsThatAreReferencedByAnotherActiveModel() {
        ModelSpecView current = codec.toCreatedView(MODEL_ID, command("delete-referenced", "customer_detail"), NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.hasActiveModelReferences(TENANT, MODEL_ID)).thenReturn(true);

        assertThatThrownBy(
            () ->
                service.deleteDraft(
                    TENANT,
                    ACTOR,
                    MODEL_ID,
                    new ExpectedVersion(MODEL_ID, current.revision(), current.checksum())
                )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_DELETE_REFERENCED");

        verify(repository, never()).compareAndSetLifecycle(any(), any(), anyInt(), anyString(), any(), any());
        verify(repository, never()).updateV2RevisionLifecycle(any(), any(), any(), any(), anyString());
        verify(auditService, never()).auditAction(anyString(), any(), anyString(), any());
    }

    @Test
    void startsANewDraftRevisionWhenPublishedDbtSchemaAddsFields() {
        CreateModelSpecCommand base = command("dbt-schema-revision", "progress_kpi");
        CreateModelSpecCommand dbtManaged = new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            ImplementationMode.DBT_MANAGED,
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            base.idempotencyKey()
        );
        ModelSpecView created = codec.toCreatedView(MODEL_ID, dbtManaged, NOW);
        ModelSpecView published = codec.toLifecycleView(created, ModelStatus.PUBLISHED, created.revision(), NOW);
        when(repository.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored(published, null, null)));
        when(compatibilityReader.read(any())).thenReturn(published);
        when(
            repository.compareAndSetPublishedToDraftV2(
                eq(TENANT),
                eq(ACTOR),
                eq(published.revision()),
                eq(published.checksum()),
                any(),
                anyString()
            )
        ).thenReturn(1);
        List<ModelField> projected = List.of(
            published.fields().get(0),
            new ModelField(
                "project_total_cnt",
                "项目总数",
                "bigint",
                true,
                null,
                FieldRole.MEASURE,
                null,
                null,
                false,
                null
            )
        );

        ModelSpecView draft = service.synchronizeDbtManagedFields(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, published.revision(), published.checksum()),
            projected
        );

        assertThat(draft.status()).isEqualTo(ModelStatus.DRAFT);
        assertThat(draft.revision()).isEqualTo(2);
        assertThat(draft.fields()).containsExactlyElementsOf(projected);
        verify(repository).insertV2Revision(eq(TENANT), eq(ACTOR), eq(draft), anyString());
        verify(repository, never()).updateV2RevisionLifecycle(any(), any(), any(), any(), anyString());
        verify(auditService).auditAction(
            eq("MODELING_MODEL_SPEC_DBT_SCHEMA_REVISION_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
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

    private static CreateModelSpecCommand withBusinessContext(
        CreateModelSpecCommand command,
        UUID businessProcessId,
        UUID dataMartId,
        UUID subjectDomainId
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
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            command.dimensionDefinitionRef(),
            command.idempotencyKey(),
            dataMartId,
            command.variantCode(),
            command.implementationPolicy(),
            command.warehouseLayerCode(),
            businessProcessId,
            subjectDomainId
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

    private static CreateModelSpecCommand withoutLogicalDesign(CreateModelSpecCommand command) {
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
            null,
            command.factShape(),
            command.timeSemantics(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            command.generationStrategy(),
            command.dimensionProfile(),
            command.dimensionDefinitionRef(),
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

    private static CreateModelSpecCommand withoutDimensionDefinitionRef(CreateModelSpecCommand command) {
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
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            null,
            command.idempotencyKey()
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

    private static StoredDimensionDefinition definition(
        UUID id,
        UUID domainId,
        int revision,
        DimensionDefinitionContract.Status status
    ) {
        StoredDimensionDefinition definition = mock(StoredDimensionDefinition.class);
        lenient().when(definition.id()).thenReturn(id);
        lenient().when(definition.domainId()).thenReturn(domainId);
        lenient().when(definition.revision()).thenReturn(revision);
        lenient().when(definition.status()).thenReturn(status);
        return definition;
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



    @Test
    void resolvesWarehouseLayerSelectionBeforeHashingAndPersistsIt() {
        WarehouseLayerApplicationService layers = org.mockito.Mockito.mock(WarehouseLayerApplicationService.class);
        org.mockito.Mockito.when(layers.resolveSelection(eq("FIN_DETAIL"), eq(Layer.DWD)))
            .thenReturn(new ResolvedWarehouseLayer("FIN_DETAIL", Layer.DWD, false));
        service = new ModelSpecApplicationService(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            auditService,
            layers,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        CreateModelSpecCommand command = command("warehouse-layer-create-1", "budget_execution_detail");
        command = command.withLayerSelection(Layer.DWD, "FIN_DETAIL");
        org.mockito.Mockito.when(repository.insertV2(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        CreateResult created = service.create(TENANT, ACTOR, command);

        assertThat(created.modelSpec().warehouseLayerCode()).isEqualTo("FIN_DETAIL");
        assertThat(created.modelSpec().layer()).isEqualTo(Layer.DWD);
        org.mockito.Mockito.verify(layers).resolveSelection("FIN_DETAIL", Layer.DWD);
    }

    @Test
    void rejectsWarehouseLayerSelectionTypeMismatchDuringCreate() {
        WarehouseLayerApplicationService layers = org.mockito.Mockito.mock(WarehouseLayerApplicationService.class);
        org.mockito.Mockito.when(layers.resolveSelection(eq("FIN_SUMMARY"), eq(Layer.DWD)))
            .thenThrow(new WarehouseLayerException(
                "MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH",
                "分层 FIN_SUMMARY 的系统类型与模型目标层不匹配",
                WarehouseLayerException.Kind.UNPROCESSABLE,
                Map.of("warehouseLayerCode", "FIN_SUMMARY", "expectedSystemLayerCode", "DWD")
            ));
        service = new ModelSpecApplicationService(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            auditService,
            layers,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        CreateModelSpecCommand command = command("warehouse-layer-create-2", "budget_execution_detail")
            .withLayerSelection(Layer.DWD, "FIN_SUMMARY");

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(WarehouseLayerException.class)
            .extracting(error -> ((WarehouseLayerException) error).code())
            .isEqualTo("MODEL_SPEC_WAREHOUSE_LAYER_TYPE_MISMATCH");
        verify(repository, never()).insertV2(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resetsWarehouseLayerSelectionToCanonicalCodeOnReclassification() {
        WarehouseLayerApplicationService layers = org.mockito.Mockito.mock(WarehouseLayerApplicationService.class);
        org.mockito.Mockito.when(layers.resolveSelection(eq("FIN_DETAIL"), eq(Layer.DWD)))
            .thenReturn(new ResolvedWarehouseLayer("FIN_DETAIL", Layer.DWD, false));
        service = new ModelSpecApplicationService(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            auditService,
            layers,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> MODEL_ID
        );
        CreateModelSpecCommand create = command("warehouse-layer-create-3", "budget_execution_detail")
            .withLayerSelection(Layer.DWD, "FIN_DETAIL");
        org.mockito.Mockito.when(repository.insertV2(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        CreateResult created = service.create(TENANT, ACTOR, create);
        ModelSpecView current = created.modelSpec();
        assertThat(current.warehouseLayerCode()).isEqualTo("FIN_DETAIL");
        when(repository.findCurrent(TENANT, current.id())).thenReturn(Optional.of(stored(current, null, null)));
        when(compatibilityReader.read(any())).thenReturn(current);
        when(repository.compareAndSetV2(eq(TENANT), eq(ACTOR), anyInt(), anyString(), any(), anyString())).thenReturn(1);

        ModelSpecApplicationService.ReclassificationPreview preview = service.previewReclassification(
            TENANT, ACTOR, current.id(), new ReclassificationPreviewRequest(ModelType.SUMMARY, null)
        );
        ModelSpecApplicationService.ReclassificationCommand reclassify =
            new ModelSpecApplicationService.ReclassificationCommand(
                ModelType.SUMMARY, null, preview.clearFields(), "reclassify-layer-reset"
            );
        ModelSpecView replacement = service.reclassify(
            TENANT, ACTOR, current.id(),
            new ExpectedVersion(current.id(), current.revision(), current.checksum()),
            reclassify
        );

        assertThat(replacement.layer()).isEqualTo(Layer.DWS);
        assertThat(replacement.warehouseLayerCode()).isEqualTo("DWS");
    }
    private static WarehouseLayerApplicationService warehouseLayers() {
        WarehouseLayerApplicationService layers = org.mockito.Mockito.mock(WarehouseLayerApplicationService.class);
        org.mockito.Mockito.lenient().when(layers.resolveSelection(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer expected =
                    invocation.getArgument(1);
                return new ResolvedWarehouseLayer(expected.name(), expected, true);
            });
        return layers;
    }
}
