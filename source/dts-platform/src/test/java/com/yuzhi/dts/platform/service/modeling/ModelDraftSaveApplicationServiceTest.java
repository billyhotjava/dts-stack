package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ModelDraftSaveApplicationServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private ModelSpecApplicationService modelSpecs;
    private ModelLifecycleService lifecycle;
    private ModelSpecSnapshotCodec modelSpecCodec;
    private ModelDraftSaveApplicationService service;

    @BeforeEach
    void setUp() {
        modelSpecs = mock(ModelSpecApplicationService.class);
        lifecycle = mock(ModelLifecycleService.class);
        modelSpecCodec = mock(ModelSpecSnapshotCodec.class);
        service = new ModelDraftSaveApplicationService(modelSpecs, lifecycle, modelSpecCodec);
    }

    @Test
    void commitsCreateFullDefinitionAndFirstImplementationThroughOneTransactionalBoundary() throws Exception {
        CreateModelSpecCommand create = createCommand();
        UpdateModelSpecCommand update = updateCommand();
        SaveImplementationCommand implementation = mock(SaveImplementationCommand.class);
        ModelSpecView seed = model(1, "a".repeat(64));
        ModelSpecView savedModel = model(2, "b".repeat(64));
        ImplementationView savedImplementation = mock(ImplementationView.class);
        when(modelSpecs.create("tenant", "alice", create)).thenReturn(new CreateResult(seed, false));
        when(modelSpecs.update(eq("tenant"), eq("alice"), eq(MODEL_ID), any(), eq(update))).thenReturn(savedModel);
        when(lifecycle.saveImplementation(
            eq("tenant"), eq("alice"), eq(MODEL_ID), any(), any(), eq(null), eq(null), eq(implementation)
        )).thenReturn(savedImplementation);

        var result = service.save("tenant", "alice", create, update, implementation);

        assertThat(result.model()).isSameAs(savedModel);
        assertThat(result.implementation()).isSameAs(savedImplementation);
        assertThat(result.replayed()).isFalse();
        assertThat(ModelDraftSaveApplicationService.class.getMethod(
            "save", String.class, String.class, CreateModelSpecCommand.class, UpdateModelSpecCommand.class,
            SaveImplementationCommand.class
        ).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void commitsTheCompleteDefinitionWithoutAVisualImplementationForDbtManagedModels() {
        CreateModelSpecCommand create = createCommand();
        UpdateModelSpecCommand update = updateCommand();
        ModelSpecView seed = model(1, "a".repeat(64));
        ModelSpecView savedModel = model(2, "b".repeat(64));
        when(update.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(modelSpecs.create("tenant", "alice", create)).thenReturn(new CreateResult(seed, false));
        when(modelSpecs.update(eq("tenant"), eq("alice"), eq(MODEL_ID), any(), eq(update))).thenReturn(savedModel);

        var result = service.save("tenant", "alice", create, update, null);

        assertThat(result.model()).isSameAs(savedModel);
        assertThat(result.implementation()).isNull();
        verify(lifecycle, never()).saveImplementation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resumesAnExactCreateReplayFromTheCurrentModelRevision() {
        CreateModelSpecCommand create = createCommand();
        UpdateModelSpecCommand update = updateCommand();
        SaveImplementationCommand implementation = mock(SaveImplementationCommand.class);
        ModelSpecView originalSeed = model(1, "a".repeat(64));
        ModelSpecView current = model(2, "b".repeat(64));
        ImplementationView savedImplementation = mock(ImplementationView.class);
        when(modelSpecs.create("tenant", "alice", create)).thenReturn(new CreateResult(originalSeed, true));
        when(modelSpecs.get("tenant", MODEL_ID)).thenReturn(current);
        when(modelSpecs.revision(eq("tenant"), any())).thenReturn(current);
        when(modelSpecCodec.toUpdatedView(eq(originalSeed), eq(update), eq(2), any())).thenReturn(current);
        when(lifecycle.saveImplementation(
            eq("tenant"), eq("alice"), eq(MODEL_ID), any(), any(), eq(null), eq(null), eq(implementation)
        )).thenReturn(savedImplementation);

        var result = service.save("tenant", "alice", create, update, implementation);

        assertThat(result.replayed()).isTrue();
        verify(modelSpecs).get("tenant", MODEL_ID);
        verify(modelSpecs, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAReplayWhoseFullModelDefinitionDiffersFromTheRecordedOperation() {
        CreateModelSpecCommand create = createCommand();
        UpdateModelSpecCommand update = updateCommand();
        ModelSpecView originalSeed = model(1, "a".repeat(64));
        ModelSpecView completed = model(2, "b".repeat(64));
        when(modelSpecs.create("tenant", "alice", create)).thenReturn(new CreateResult(originalSeed, true));
        when(modelSpecs.get("tenant", MODEL_ID)).thenReturn(completed);
        when(modelSpecs.revision(eq("tenant"), any())).thenReturn(completed);
        when(modelSpecCodec.toUpdatedView(eq(originalSeed), eq(update), eq(2), any()))
            .thenReturn(model(2, "c".repeat(64)));

        assertThatThrownBy(() -> service.save("tenant", "alice", create, update, mock(SaveImplementationCommand.class)))
            .isInstanceOfSatisfying(ModelSpecException.class, failure ->
                assertThat(failure.code()).isEqualTo("MODEL_DRAFT_OPERATION_IDEMPOTENCY_CONFLICT")
            );
        verify(lifecycle, never()).saveImplementation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMixedModelContextsBeforeCreatingAnything() {
        CreateModelSpecCommand create = createCommand();
        UpdateModelSpecCommand update = updateCommand();
        when(update.domainId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.save("tenant", "alice", create, update, mock(SaveImplementationCommand.class)))
            .isInstanceOfSatisfying(ModelSpecException.class, failure ->
                assertThat(failure.code()).isEqualTo("MODEL_DRAFT_OPERATION_INCONSISTENT")
            );
        verify(modelSpecs, never()).create(any(), any(), any());
    }

    private static CreateModelSpecCommand createCommand() {
        CreateModelSpecCommand command = mock(CreateModelSpecCommand.class);
        when(command.planId()).thenReturn(PLAN_ID);
        when(command.domainId()).thenReturn(DOMAIN_ID);
        when(command.modelType()).thenReturn(ModelType.FACT);
        when(command.layer()).thenReturn(Layer.DWD);
        when(command.name()).thenReturn("项目任务快照明细");
        when(command.warehouseLayerCode()).thenReturn("DWD");
        return command;
    }

    private static UpdateModelSpecCommand updateCommand() {
        UpdateModelSpecCommand command = mock(UpdateModelSpecCommand.class);
        when(command.planId()).thenReturn(PLAN_ID);
        when(command.domainId()).thenReturn(DOMAIN_ID);
        when(command.modelType()).thenReturn(ModelType.FACT);
        when(command.layer()).thenReturn(Layer.DWD);
        when(command.name()).thenReturn("项目任务快照明细");
        when(command.warehouseLayerCode()).thenReturn("DWD");
        return command;
    }

    private static ModelSpecView model(int revision, String checksum) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(checksum);
        when(model.updatedAt()).thenReturn(Instant.parse("2026-09-03T00:00:00Z"));
        return model;
    }
}
