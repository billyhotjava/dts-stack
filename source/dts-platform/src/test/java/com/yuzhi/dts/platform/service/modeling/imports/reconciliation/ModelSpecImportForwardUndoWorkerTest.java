package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyRepository;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardUndoWorker.Execution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ModelSpecImportForwardUndoWorkerTest {

    private final ModelSpecImportForwardRestoreService restoreService = mock(ModelSpecImportForwardRestoreService.class);
    private final ModelSpecImportApplyRepository applyRepository = mock(ModelSpecImportApplyRepository.class);
    private final ModelSpecImportForwardUndoWorker worker = new ModelSpecImportForwardUndoWorker(
        restoreService, applyRepository
    );

    @BeforeEach
    void returnRecordedResults() {
        when(applyRepository.recordSuccess(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(4));
        when(applyRepository.recordBlocked(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(4));
    }

    @Test
    void ownsTheRequiresNewSerializableProductionTransactionBoundary() throws Exception {
        Transactional boundary = AnnotationUtils.findAnnotation(
            ModelSpecImportForwardUndoWorker.class.getMethod("execute", Execution.class),
            Transactional.class
        );

        assertThat(boundary).isNotNull();
        assertThat(boundary.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(boundary.isolation()).isEqualTo(Isolation.SERIALIZABLE);
    }

    @Test
    void blocksCreatedModelsAndSkipsOriginallySkippedItemsWithoutMutation() {
        CandidateResult created = worker.execute(execution(item("CREATE", null)));
        CandidateResult skipped = worker.execute(execution(item("SKIP", pins(9, 'b', 19, 'd'))));

        assertThat(created.status()).isEqualTo(ResultStatus.BLOCKED);
        assertThat(created.issues()).extracting(issue -> issue.code()).containsExactly("CREATED_MODEL_NO_BASE_REVISION");
        assertThat(skipped.status()).isEqualTo(ResultStatus.SKIPPED);
        verify(restoreService, never()).restoreImportedDbtRevision(any(), any(), any());
    }

    @Test
    void restoresModelAndImplementationThroughTheCanonicalAtomicBoundary() {
        UndoTargetItem target = item("UPDATE", pins(9, 'b', 19, 'd'));
        ModelSpecView restored = model(11, 'b');
        ImplementationView implementation = new ImplementationView(
            UUID.randomUUID(), target.modelSpecId(), restored.planId(), restored.revision(), restored.checksum(),
            ImplementationMode.DBT_MANAGED, "finance", target.dbtUniqueId(), "ACTIVE", 21, "d".repeat(64),
            InputMode.GENERATED, List.of(), List.of(), Map.of(), "table"
        );
        when(restoreService.restoreImportedDbtRevision(any(), any(), any()))
            .thenReturn(ModelSpecImportForwardRestoreService.ForwardRestoreResult.restored(restored, implementation));

        CandidateResult result = worker.execute(execution(target));

        assertThat(result.status()).isEqualTo(ResultStatus.UPDATED);
        assertThat(result.revision()).isEqualTo(11);
        assertThat(result.implementationRevision()).isEqualTo(21);
        assertThat(result.preAttemptPins()).isEqualTo(target.postPins());
        ArgumentCaptor<ModelSpecImportForwardRestoreService.ForwardRestoreCommand> command = ArgumentCaptor.forClass(
            ModelSpecImportForwardRestoreService.ForwardRestoreCommand.class
        );
        verify(restoreService).restoreImportedDbtRevision(any(), any(), command.capture());
        assertThat(command.getValue().restorePins()).isEqualTo(target.prePins());
        assertThat(command.getValue().expectedCurrentModel().revision()).isEqualTo(target.postPins().modelRevision());
        assertThat(command.getValue().expectedCurrentImplementation().revision())
            .isEqualTo(target.postPins().implementationRevision());
    }

    @Test
    void blocksUpdateWhenReleaseOrDownstreamEligibilityFactsFail() {
        UndoTargetItem target = item("UPDATE", pins(9, 'b', 19, 'd'));
        when(restoreService.restoreImportedDbtRevision(any(), any(), any()))
            .thenReturn(ModelSpecImportForwardRestoreService.ForwardRestoreResult.blocked("DOWNSTREAM_MODEL_PIN"));

        CandidateResult result = worker.execute(execution(target));

        assertThat(result.status()).isEqualTo(ResultStatus.BLOCKED);
        assertThat(result.issues()).extracting(issue -> issue.code()).containsExactly("DOWNSTREAM_MODEL_PIN");
        verify(restoreService).restoreImportedDbtRevision(any(), any(), any());
    }

    private static Execution execution(UndoTargetItem target) {
        return new Execution(
            "default", "actor", UUID.randomUUID(), UUID.randomUUID(), "owner", 0, "finance", target,
            "candidate-key", "candidate-hash"
        );
    }

    private static UndoTargetItem item(String action, RevisionPins pre) {
        String uniqueId = "model.finance.fact_budget";
        return new UndoTargetItem(
            UUID.nameUUIDFromBytes(uniqueId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            uniqueId,
            action,
            List.of(),
            pins(10, 'a', 20, 'c'),
            pre
        );
    }

    private static RevisionPins pins(int modelRevision, char modelChecksum, int implementationRevision, char implementationChecksum) {
        return new RevisionPins(
            modelRevision,
            String.valueOf(modelChecksum).repeat(64),
            implementationRevision,
            String.valueOf(implementationChecksum).repeat(64)
        );
    }

    private static ModelSpecView model(int revision, char checksum) {
        return new ModelSpecView(
            2,
            UUID.nameUUIDFromBytes("model.finance.fact_budget".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            UUID.randomUUID(),
            UUID.randomUUID(),
            ModelType.FACT,
            Layer.DWD,
            "fact_budget",
            "budget fact",
            ImplementationMode.DBT_MANAGED,
            "table",
            null,
            null,
            new Grain("one row per budget item", List.of("budget_id")),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            revision,
            String.valueOf(checksum).repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }
}
