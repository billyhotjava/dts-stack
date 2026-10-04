package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyRepository;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Executes one forward-undo item in an isolated transaction using public append-only writers. */
@Service
public class ModelSpecImportForwardUndoWorker {

    private final ModelSpecImportForwardRestoreService restoreService;
    private final ModelSpecImportApplyRepository applyRepository;

    public ModelSpecImportForwardUndoWorker(
        ModelSpecImportForwardRestoreService restoreService,
        ModelSpecImportApplyRepository applyRepository
    ) {
        this.restoreService = restoreService;
        this.applyRepository = applyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public CandidateResult execute(Execution execution) {
        UndoTargetItem target = execution.target();
        if ("CREATE".equals(target.appliedAction())) {
            return recordBlocked(execution, "CREATED_MODEL_NO_BASE_REVISION", "OPEN_MODEL");
        }
        if ("SKIP".equals(target.appliedAction())) {
            return recordSkipped(execution);
        }

        ModelSpecImportForwardRestoreService.ForwardRestoreResult restored = restoreService.restoreImportedDbtRevision(
            execution.tenantId(),
            execution.actorId(),
            new ModelSpecImportForwardRestoreService.ForwardRestoreCommand(
                target.modelSpecId(),
                new ExpectedVersion(
                    target.modelSpecId(),
                    target.postPins().modelRevision(),
                    target.postPins().modelChecksum()
                ),
                new ExpectedImplementationVersion(
                    target.modelSpecId(),
                    target.postPins().implementationRevision(),
                    target.postPins().implementationChecksum()
                ),
                target.prePins(),
                execution.projectKey(),
                target.dbtUniqueId(),
                execution.candidateIdempotencyKey() + ":restore"
            )
        );
        if (restored.blocked()) {
            return recordBlocked(execution, restored.blockedReasonCode(), "OPEN_MODEL");
        }
        ModelSpecView model = restored.modelSpec();
        ImplementationView implementation = restored.implementation();
        if (!Objects.equals(implementation.implementationChecksum(), target.prePins().implementationChecksum())) {
            throw new IllegalStateException("Restored implementation does not match the pre-attempt checkpoint");
        }

        CandidateResult result = result(
            execution,
            ResultStatus.UPDATED,
            model.revision(),
            model.checksum(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            List.of(),
            "UPDATE",
            target.postPins()
        );
        return applyRepository.recordSuccess(
            execution.tenantId(), execution.runId(), execution.attemptId(), execution.ownerToken(), result
        );
    }

    private CandidateResult recordSkipped(Execution execution) {
        RevisionPins pins = execution.target().postPins();
        CandidateResult result = result(
            execution,
            ResultStatus.SKIPPED,
            pins.modelRevision(),
            pins.modelChecksum(),
            pins.implementationRevision(),
            pins.implementationChecksum(),
            List.of(),
            "SKIP",
            pins
        );
        return applyRepository.recordSuccess(
            execution.tenantId(), execution.runId(), execution.attemptId(), execution.ownerToken(), result
        );
    }

    private CandidateResult recordBlocked(Execution execution, String code, String recoveryAction) {
        RevisionPins pins = execution.target().postPins();
        CandidateResult result = result(
            execution,
            ResultStatus.BLOCKED,
            pins.modelRevision(),
            pins.modelChecksum(),
            pins.implementationRevision(),
            pins.implementationChecksum(),
            List.of(
                new ApplyIssue(
                    code,
                    Severity.ERROR,
                    "UNDO",
                    "CONFLICT",
                    false,
                    "$.selectedItemIds",
                    execution.target().dbtUniqueId(),
                    null,
                    "The selected import item is not eligible for forward undo",
                    recoveryAction,
                    UUID.randomUUID().toString()
                )
            ),
            execution.target().appliedAction(),
            null
        );
        return applyRepository.recordBlocked(
            execution.tenantId(), execution.runId(), execution.attemptId(), execution.ownerToken(), result
        );
    }

    private CandidateResult result(
        Execution execution,
        ResultStatus status,
        Integer modelRevision,
        String modelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        List<ApplyIssue> issues,
        String action,
        RevisionPins preAttemptPins
    ) {
        return new CandidateResult(
            UUID.randomUUID(),
            execution.sequence(),
            execution.target().dbtUniqueId(),
            execution.candidateIdempotencyKey(),
            execution.candidateRequestHash(),
            status,
            execution.target().modelSpecId(),
            modelRevision,
            modelChecksum,
            implementationRevision,
            implementationChecksum,
            0,
            issues,
            Instant.now(),
            action,
            execution.projectKey(),
            null,
            preAttemptPins,
            execution.target().dependencies()
        );
    }

    public record Execution(
        String tenantId,
        String actorId,
        UUID runId,
        UUID attemptId,
        String ownerToken,
        int sequence,
        String projectKey,
        UndoTargetItem target,
        String candidateIdempotencyKey,
        String candidateRequestHash
    ) {}
}
