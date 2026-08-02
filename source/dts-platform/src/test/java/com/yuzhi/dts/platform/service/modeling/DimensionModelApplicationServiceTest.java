package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.AttributeSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ScopeType;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.BindingMode;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.PreparedCreate;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class DimensionModelApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Mock
    private DimensionDefinitionApplicationService dimensionDefinitions;

    @Mock
    private ModelSpecApplicationService modelSpecs;

    @Mock
    private DimensionDefinitionRepository dimensionDefinitionRepository;

    @Mock
    private ModelSpecRepository modelSpecRepository;

    @Mock
    private ModelSpecSnapshotCodec modelSpecCodec;

    @Mock
    private AuditService auditService;

    private DimensionModelCreateRequestDecoder decoder;
    private DimensionModelApplicationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        decoder = new DimensionModelCreateRequestDecoder(
            objectMapper,
            new ModelSpecCreateRequestDecoder(objectMapper),
            new ModelSpecUpdateRequestDecoder(objectMapper)
        );
        service = new DimensionModelApplicationService(
            dimensionDefinitions,
            modelSpecs,
            dimensionDefinitionRepository,
            modelSpecRepository,
            decoder,
            modelSpecCodec,
            auditService
        );
    }

    @Test
    void createsConfirmedDefinitionSeedAndCompleteRevisionTwoInOneTransactionalBoundary() throws Exception {
        PreparedCreate prepared = prepared();
        View draft = definition(Status.DRAFT, 1, "a".repeat(64));
        View currentDefinition = definition(Status.CURRENT, 2, "b".repeat(64));
        ModelSpecView seed = model(1, "c".repeat(64));
        ModelSpecView completed = model(2, "d".repeat(64));
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.empty());
        when(dimensionDefinitions.create(TENANT, ACTOR, prepared.definitionBinding().definition()))
            .thenReturn(new CreateResult(draft, false));
        when(dimensionDefinitions.confirm(
            TENANT,
            ACTOR,
            DEFINITION_ID,
            new DimensionDefinitionApplicationService.ExpectedVersion(DEFINITION_ID, 1, "a".repeat(64))
        )).thenReturn(currentDefinition);
        when(modelSpecs.create(eq(TENANT), eq(ACTOR), any(CreateModelSpecCommand.class)))
            .thenReturn(new ModelSpecApplicationService.CreateResult(seed, false));
        when(modelSpecs.update(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ModelSpecApplicationService.ExpectedVersion(MODEL_ID, 1, "c".repeat(64)),
            prepared.modelSpec()
        )).thenReturn(completed);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(completed);

        DimensionModelApplicationService.OperationResult result = service.create(TENANT, ACTOR, prepared);

        assertThat(result.dimensionDefinitionRevision()).isEqualTo(currentDefinition);
        assertThat(result.modelSpecRevision()).isEqualTo(completed);
        assertThat(result.currentModelSpec()).isEqualTo(completed);
        assertThat(result.replayed()).isFalse();
        InOrder order = inOrder(dimensionDefinitions, modelSpecs);
        order.verify(dimensionDefinitions).create(TENANT, ACTOR, prepared.definitionBinding().definition());
        order.verify(dimensionDefinitions).confirm(eq(TENANT), eq(ACTOR), eq(DEFINITION_ID), any());
        order.verify(modelSpecs).create(eq(TENANT), eq(ACTOR), any(CreateModelSpecCommand.class));
        order.verify(modelSpecs).update(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), eq(prepared.modelSpec()));
        verify(auditService).auditActionStrict(
            eq("MODELING_DIMENSION_MODEL_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(MODEL_ID.toString()),
            any()
        );
        assertThat(
            DimensionModelApplicationService.class
                .getMethod("create", String.class, String.class, PreparedCreate.class)
                .isAnnotationPresent(Transactional.class)
        ).isTrue();
    }

    @Test
    void replaysTheFixedCompletionRevisionWithoutUpdatingOrDuplicatingStrictAudit() throws Exception {
        PreparedCreate prepared = prepared();
        View replaySeed = definition(Status.DRAFT, 1, "a".repeat(64));
        View fixedDefinition = definition(Status.CURRENT, 2, "b".repeat(64));
        ModelSpecView seed = model(1, "c".repeat(64));
        ModelSpecView fixedModel = model(2, "d".repeat(64));
        ModelSpecView currentModel = model(3, "e".repeat(64));
        ModelSpecView expected = model(2, "d".repeat(64));
        StoredModelSpec stored = mock(StoredModelSpec.class);
        StoredDimensionDefinition storedDefinition = mock(StoredDimensionDefinition.class);
        when(stored.id()).thenReturn(MODEL_ID);
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.of(stored));
        when(dimensionDefinitionRepository.findByIdempotencyKey(TENANT, "dm:v2:dimension:" + OPERATION_ID))
            .thenReturn(Optional.of(storedDefinition));
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(currentModel);
        when(dimensionDefinitions.create(TENANT, ACTOR, prepared.definitionBinding().definition()))
            .thenReturn(new CreateResult(replaySeed, true));
        when(dimensionDefinitions.revision(TENANT, DEFINITION_ID, 2)).thenReturn(fixedDefinition);
        when(modelSpecs.create(eq(TENANT), eq(ACTOR), any(CreateModelSpecCommand.class)))
            .thenReturn(new ModelSpecApplicationService.CreateResult(seed, true));
        when(modelSpecs.revision(TENANT, new ModelSpecContract.ModelRevisionRef(MODEL_ID, 2)))
            .thenReturn(fixedModel);
        when(modelSpecCodec.toUpdatedView(seed, prepared.modelSpec(), 2, fixedModel.updatedAt()))
            .thenReturn(expected);
        DimensionModelApplicationService.OperationResult result = service.create(TENANT, ACTOR, prepared);

        assertThat(result.modelSpecRevision()).isEqualTo(fixedModel);
        assertThat(result.currentModelSpec()).isEqualTo(currentModel);
        assertThat(result.replayed()).isTrue();
        verify(modelSpecs, never()).update(any(), any(), any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void bindsAnExistingCurrentDefinitionWithoutCreatingAnotherDimension() throws Exception {
        PreparedCreate prepared = existingPrepared();
        View fixedDefinition = definition(Status.CURRENT, 2, "b".repeat(64));
        ModelSpecView seed = model(1, "c".repeat(64));
        ModelSpecView completed = model(2, "d".repeat(64));
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.empty());
        when(dimensionDefinitions.revision(TENANT, DEFINITION_ID, 2)).thenReturn(fixedDefinition);
        when(dimensionDefinitions.get(TENANT, DEFINITION_ID)).thenReturn(fixedDefinition);
        when(modelSpecs.create(eq(TENANT), eq(ACTOR), any(CreateModelSpecCommand.class)))
            .thenReturn(new ModelSpecApplicationService.CreateResult(seed, false));
        when(modelSpecs.update(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), eq(prepared.modelSpec())))
            .thenReturn(completed);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(completed);

        DimensionModelApplicationService.OperationResult result = service.create(TENANT, ACTOR, prepared);

        assertThat(result.bindingMode()).isEqualTo(BindingMode.EXISTING);
        verify(dimensionDefinitions, never()).create(any(), any(), any());
        verify(dimensionDefinitions, never()).confirm(any(), any(), any(), any());
    }

    @Test
    void recoversFixedOperationRevisionAndReturnsTheNewerCurrentModel() {
        StoredModelSpec storedModel = mock(StoredModelSpec.class);
        StoredDimensionDefinition storedDefinition = mock(StoredDimensionDefinition.class);
        when(storedModel.id()).thenReturn(MODEL_ID);
        when(storedDefinition.id()).thenReturn(DEFINITION_ID);
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.of(storedModel));
        when(dimensionDefinitionRepository.findByIdempotencyKey(TENANT, "dm:v2:dimension:" + OPERATION_ID))
            .thenReturn(Optional.of(storedDefinition));
        ModelSpecView fixedModel = model(2, "d".repeat(64));
        ModelSpecView currentModel = model(4, "f".repeat(64));
        View fixedDefinition = definition(Status.CURRENT, 2, "b".repeat(64));
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(currentModel);
        when(modelSpecs.revision(TENANT, new ModelSpecContract.ModelRevisionRef(MODEL_ID, 2)))
            .thenReturn(fixedModel);
        when(dimensionDefinitions.get(TENANT, DEFINITION_ID)).thenReturn(fixedDefinition);
        when(dimensionDefinitions.revision(TENANT, DEFINITION_ID, 2)).thenReturn(fixedDefinition);

        DimensionModelApplicationService.OperationResult result = service.recover(TENANT, ACTOR, OPERATION_ID);

        assertThat(result.bindingMode()).isEqualTo(BindingMode.CREATE);
        assertThat(result.modelSpecRevision()).isEqualTo(fixedModel);
        assertThat(result.currentModelSpec()).isEqualTo(currentModel);
        assertThat(result.replayed()).isTrue();
    }

    @Test
    void hidesUnauthorizedOperationLookupBeforeInspectingFixedRevisions() {
        StoredModelSpec storedModel = mock(StoredModelSpec.class);
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.of(storedModel));
        doThrow(
            new ModelSpecException(
                "MODEL_SPEC_NOT_FOUND",
                "ModelSpec does not exist",
                ModelSpecException.Kind.NOT_FOUND
            )
        ).when(modelSpecs).requireReplayAccess(TENANT, ACTOR, storedModel);

        assertThatThrownBy(() -> service.recover(TENANT, ACTOR, OPERATION_ID))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_MODEL_OPERATION_NOT_FOUND");
        verify(modelSpecs, never()).get(any(), any());
        verify(modelSpecs, never()).revision(any(), any());
        verifyNoInteractions(dimensionDefinitions);
    }

    @Test
    void rejectsBindingModeDriftForAnExistingOperationIdBeforeAnyReplayMutation() throws Exception {
        PreparedCreate prepared = existingPrepared();
        StoredModelSpec storedModel = mock(StoredModelSpec.class);
        StoredDimensionDefinition storedDefinition = mock(StoredDimensionDefinition.class);
        when(storedModel.id()).thenReturn(MODEL_ID);
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.of(storedModel));
        when(dimensionDefinitionRepository.findByIdempotencyKey(TENANT, "dm:v2:dimension:" + OPERATION_ID))
            .thenReturn(Optional.of(storedDefinition));
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model(2, "d".repeat(64)));

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, prepared))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_MODEL_IDEMPOTENCY_CONFLICT");
        verifyNoInteractions(dimensionDefinitions);
        verify(modelSpecs, never()).create(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void hidesBindingModeBeforeOwnerScopedReplayAuthorization() throws Exception {
        PreparedCreate prepared = existingPrepared();
        StoredModelSpec storedModel = mock(StoredModelSpec.class);
        StoredDimensionDefinition storedDefinition = mock(StoredDimensionDefinition.class);
        when(modelSpecRepository.findByIdempotencyKey(TENANT, "dm:v2:model:" + OPERATION_ID))
            .thenReturn(Optional.of(storedModel));
        when(dimensionDefinitionRepository.findByIdempotencyKey(TENANT, "dm:v2:dimension:" + OPERATION_ID))
            .thenReturn(Optional.of(storedDefinition));
        doThrow(
            new ModelSpecException(
                "MODEL_SPEC_NOT_FOUND",
                "ModelSpec does not exist",
                ModelSpecException.Kind.NOT_FOUND
            )
        )
            .when(modelSpecs)
            .requireReplayAccess(TENANT, ACTOR, storedModel);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, prepared))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_MODEL_OPERATION_NOT_FOUND");
        verify(modelSpecs, never()).get(any(), any());
        verifyNoInteractions(dimensionDefinitions);
        verifyNoInteractions(auditService);
    }

    @Test
    void propagatesStrictAuditFailureForTheOuterTransactionToRollBackEveryWrite() throws Exception {
        PreparedCreate prepared = prepared();
        View draft = definition(Status.DRAFT, 1, "a".repeat(64));
        View currentDefinition = definition(Status.CURRENT, 2, "b".repeat(64));
        ModelSpecView seed = model(1, "c".repeat(64));
        ModelSpecView completed = model(2, "d".repeat(64));
        when(dimensionDefinitions.create(TENANT, ACTOR, prepared.definitionBinding().definition()))
            .thenReturn(new CreateResult(draft, false));
        when(dimensionDefinitions.confirm(eq(TENANT), eq(ACTOR), eq(DEFINITION_ID), any()))
            .thenReturn(currentDefinition);
        when(modelSpecs.create(eq(TENANT), eq(ACTOR), any()))
            .thenReturn(new ModelSpecApplicationService.CreateResult(seed, false));
        when(modelSpecs.update(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), eq(prepared.modelSpec())))
            .thenReturn(completed);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(auditService)
            .auditActionStrict(
                eq("MODELING_DIMENSION_MODEL_CREATE"),
                eq(AuditStage.SUCCESS),
                eq(MODEL_ID.toString()),
                any()
            );

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, prepared))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");
        verify(modelSpecs, never()).get(TENANT, MODEL_ID);
    }

    private PreparedCreate prepared() throws Exception {
        return decoder.decode(new ObjectMapper().readTree(DimensionModelCreateRequestDecoderTest.validCreateRequest()));
    }

    private PreparedCreate existingPrepared() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            DimensionModelCreateRequestDecoderTest.validCreateRequest()
        );
        request.set(
            "definitionBinding",
            mapper
                .createObjectNode()
                .put("mode", "EXISTING")
                .set(
                    "dimensionDefinitionRef",
                    mapper
                        .createObjectNode()
                        .put("dimensionDefinitionId", DEFINITION_ID.toString())
                        .put("revision", 2)
                )
        );
        return decoder.decode(request);
    }

    private static View definition(Status status, int revision, String checksum) {
        return new View(
            DEFINITION_ID,
            "dim_30000000000000000000000000000001",
            DOMAIN_ID,
            "Customer",
            "Customer dimension",
            ACTOR,
            ReuseScope.DOMAIN,
            List.of(),
            status,
            revision,
            checksum,
            0,
            Instant.EPOCH,
            Instant.EPOCH,
            ScopeType.DOMAIN,
            null,
            List.of(
                new AttributeSemantic(
                    "CUSTOMER_CODE",
                    "Customer code",
                    "Customer code",
                    true,
                    null,
                    null,
                    1
                )
            )
        );
    }

    private static ModelSpecView model(int revision, String checksum) {
        return new ModelSpecView(
            2,
            MODEL_ID,
            PLAN_ID,
            DOMAIN_ID,
            ModelType.DIMENSION,
            Layer.DWD,
            "dim_customer",
            "Customer dimension",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            new DimensionDefinitionRef(DEFINITION_ID, 2),
            ModelStatus.DRAFT,
            revision,
            checksum,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(revision),
            CompatibilityMode.CANONICAL,
            null,
            null,
            "DEFAULT",
            null
        );
    }
}
