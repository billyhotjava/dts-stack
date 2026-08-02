package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.OperationType;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardUndoWorker.Execution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoRequest;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MergeCheckpoint;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecImportForwardUndoServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void ignoresCancelledNoWriteDecisionAndExecutesOnlyThePinnedServerClosure() {
        ModelSpecImportApplyRepository applyRepository = mock(ModelSpecImportApplyRepository.class);
        ModelSpecImportPreviewRepository previewRepository = mock(ModelSpecImportPreviewRepository.class);
        ModelSpecImportForwardUndoWorker worker = mock(ModelSpecImportForwardUndoWorker.class);
        WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
        WarehousePlanAuthorizationGuard authorization = mock(WarehousePlanAuthorizationGuard.class);
        ModelSpecImportApplyPayloadCodec payloadCodec = mock(ModelSpecImportApplyPayloadCodec.class);
        ModelSpecImportReconciliationAudit audit = mock(ModelSpecImportReconciliationAudit.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ModelSpecImportForwardUndoService service = new ModelSpecImportForwardUndoService(
            applyRepository,
            previewRepository,
            new ModelSpecImportForwardUndoPlanner(),
            worker,
            actorProvider,
            authorization,
            payloadCodec,
            objectMapper,
            audit,
            "default"
        );

        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID targetAttemptId = UUID.randomUUID();
        RevisionPins post = pins(10, 'a', 20, 'c');
        RevisionPins pre = pins(9, 'a', 19, 'd');
        CandidateResult updated = targetResult(
            "model.finance.fact_budget",
            ResultStatus.UPDATED,
            "UPDATE",
            post,
            pre,
            true
        );
        CandidateResult cancelled = targetResult(
            "model.finance.cancelled",
            ResultStatus.SKIPPED,
            "CANCEL",
            post,
            null,
            false
        );
        Attempt target = attempt(
            targetAttemptId,
            runId,
            planId,
            AttemptStatus.SUCCESS,
            List.of(updated, cancelled),
            OperationType.APPLY,
            null,
            new ApplySummary(2, 0, 1, 0, 1, 1, 0, 0)
        );
        UUID undoAttemptId = UUID.randomUUID();
        Attempt runningUndo = attempt(
            undoAttemptId,
            runId,
            planId,
            AttemptStatus.RUNNING,
            List.of(),
            OperationType.FORWARD_UNDO,
            targetAttemptId,
            ApplySummary.running(1)
        );
        CandidateResult restored = targetResult(
            updated.dbtUniqueId(),
            ResultStatus.UPDATED,
            "UPDATE",
            pins(10, 'a', 21, 'd'),
            post,
            false
        );
        Attempt completedUndo = attempt(
            undoAttemptId,
            runId,
            planId,
            AttemptStatus.SUCCESS,
            List.of(restored),
            OperationType.FORWARD_UNDO,
            targetAttemptId,
            new ApplySummary(1, 0, 1, 0, 1, 0, 0, 0)
        );

        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("actor", "finance"));
        when(applyRepository.find("default", targetAttemptId)).thenReturn(Optional.of(target));
        when(previewRepository.findPlan("default", planId)).thenReturn(Optional.of(plan(planId)));
        when(payloadCodec.checksum(any())).thenReturn("f".repeat(64));
        when(applyRepository.begin(any())).thenReturn(
            new BeginResult(BeginDisposition.STARTED, runningUndo, "owner-token")
        );
        when(worker.execute(any())).thenReturn(restored);
        when(applyRepository.finalizeAttempt(eq("default"), eq(runId), eq(undoAttemptId), any(), eq("actor"), any()))
            .thenReturn(completedUndo);

        var response = service.forwardUndo(
            new ForwardUndoRequest(
                targetAttemptId,
                List.of(updated.dbtUniqueId()),
                Map.of(updated.dbtUniqueId(), post),
                "undo-key"
            )
        );

        assertThat(response.status()).isEqualTo(AttemptStatus.SUCCESS);
        assertThat(response.items()).extracting(CandidateResult::dbtUniqueId).containsExactly(updated.dbtUniqueId());
        ArgumentCaptor<Execution> execution = ArgumentCaptor.forClass(Execution.class);
        verify(worker).execute(execution.capture());
        assertThat(execution.getValue().target().dbtUniqueId()).isEqualTo(updated.dbtUniqueId());
        verify(audit).beginForwardUndo(undoAttemptId, targetAttemptId, runId, 1);
        verify(authorization).requirePlanMaintenance(any(), eq(new WarehousePlanActor("actor", "finance")));
    }

    @Test
    void blocksAnUpstreamRestoreAfterItsDownstreamDependentFails() {
        RevisionPins post = pins(10, 'a', 20, 'c');
        RevisionPins pre = pins(9, 'a', 19, 'd');
        UndoTargetItem staging = new UndoTargetItem(
            UUID.randomUUID(),
            "model.finance.stg_budget",
            "UPDATE",
            List.of(),
            post,
            pre
        );
        UndoTargetItem fact = new UndoTargetItem(
            UUID.randomUUID(),
            "model.finance.fact_budget",
            "UPDATE",
            List.of(staging.dbtUniqueId()),
            post,
            pre
        );

        assertThat(
            ModelSpecImportForwardUndoService.failedDependent(
                staging,
                List.of(fact, staging),
                Map.of(fact.dbtUniqueId(), ResultStatus.FAILED)
            )
        ).isEqualTo(fact.dbtUniqueId());
    }

    private static CandidateResult targetResult(
        String uniqueId,
        ResultStatus status,
        String action,
        RevisionPins post,
        RevisionPins pre,
        boolean checkpoint
    ) {
        return new CandidateResult(
            UUID.randomUUID(),
            0,
            uniqueId,
            "key:" + uniqueId,
            "hash:" + uniqueId,
            status,
            UUID.nameUUIDFromBytes(uniqueId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            post.modelRevision(),
            post.modelChecksum(),
            post.implementationRevision(),
            post.implementationChecksum(),
            0,
            List.of(),
            NOW,
            action,
            "finance",
            checkpoint
                ? new MergeCheckpoint(
                    "default",
                    "finance",
                    uniqueId,
                    "e".repeat(64),
                    post.implementationRevision(),
                    post.implementationChecksum(),
                    post.modelRevision(),
                    post.modelChecksum()
                )
                : null,
            pre,
            List.of()
        );
    }

    private static Attempt attempt(
        UUID id,
        UUID runId,
        UUID planId,
        AttemptStatus status,
        List<CandidateResult> results,
        OperationType operation,
        UUID targetAttemptId,
        ApplySummary summary
    ) {
        List<String> ids = results.isEmpty()
            ? List.of("model.finance.fact_budget")
            : results.stream().map(CandidateResult::dbtUniqueId).toList();
        return new Attempt(
            id,
            runId,
            planId,
            null,
            "default",
            operation == OperationType.APPLY ? 1 : 2,
            "preview-hash",
            ids,
            ids,
            operation == OperationType.APPLY ? "apply-key" : "undo-key",
            "request-hash",
            status,
            summary,
            "actor",
            NOW,
            status == AttemptStatus.RUNNING ? null : NOW,
            results,
            operation,
            targetAttemptId
        );
    }

    private static PlanSnapshot plan(UUID planId) {
        return new PlanSnapshot(
            planId,
            "default",
            "FIN",
            "Finance",
            "Finance modeling",
            "finance",
            "actor",
            "finance",
            OnboardingMode.BUSINESS_FIRST,
            LifecycleStatus.DRAFT,
            1,
            1,
            1
        );
    }

    private static RevisionPins pins(
        int modelRevision,
        char modelChecksum,
        int implementationRevision,
        char implementationChecksum
    ) {
        return new RevisionPins(
            modelRevision,
            String.valueOf(modelChecksum).repeat(64),
            implementationRevision,
            String.valueOf(implementationChecksum).repeat(64)
        );
    }
}
