package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void sameKeyAndRequestReplaysTerminalAttemptButDoesNotJoinRunningAsOwner() {
        BeginCommand command = command("request-hash");

        assertThat(
            ModelSpecImportApplyRepository.replayDisposition(command, attempt(command, AttemptStatus.SUCCEEDED))
        )
            .isEqualTo(BeginDisposition.REPLAY);
        assertThat(
            ModelSpecImportApplyRepository.replayDisposition(command, attempt(command, AttemptStatus.RUNNING))
        )
            .isEqualTo(BeginDisposition.RUNNING);
    }

    @Test
    void sameKeyWithDifferentRequestHashFailsClosed() {
        BeginCommand command = command("request-hash");
        Attempt existing = attempt(command("different-hash"), AttemptStatus.SUCCEEDED);

        assertThatThrownBy(() -> ModelSpecImportApplyRepository.replayDisposition(command, existing))
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_IDEMPOTENCY_CONFLICT")
            );
    }

    @Test
    void differentKeyCannotStartWhileRunHasRunningAttempt() {
        Attempt running = attempt(command("first-request"), AttemptStatus.RUNNING);

        assertThatThrownBy(() -> ModelSpecImportApplyRepository.requireNoCompetingRunning(running))
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_ALREADY_RUNNING")
            );
    }

    @Test
    void derivesTerminalStatusOnlyFromRecordedCandidateFacts() {
        CandidateResult created = result("model.project.fact", ResultStatus.CREATED);
        CandidateResult failed = result("model.project.summary", ResultStatus.FAILED);
        CandidateResult blocked = result("model.project.application", ResultStatus.BLOCKED);

        ApplySummary success = ModelSpecImportApplyRepository.summarize(List.of(created));
        ApplySummary partial = ModelSpecImportApplyRepository.summarize(List.of(created, failed, blocked));
        ApplySummary failedOnly = ModelSpecImportApplyRepository.summarize(List.of(failed, blocked));

        assertThat(ModelSpecImportApplyRepository.terminalStatus(success)).isEqualTo(AttemptStatus.SUCCEEDED);
        assertThat(ModelSpecImportApplyRepository.terminalStatus(partial)).isEqualTo(AttemptStatus.PARTIAL);
        assertThat(ModelSpecImportApplyRepository.terminalStatus(failedOnly)).isEqualTo(AttemptStatus.FAILED);
        assertThat(partial).isEqualTo(new ApplySummary(3, 1, 0, 0, 0, 1, 1));
    }

    @Test
    void finalizationRequiresExactFrozenCandidateSet() {
        BeginCommand command = command("request-hash");
        Attempt running = attempt(command, AttemptStatus.RUNNING);

        assertThatThrownBy(() ->
            ModelSpecImportApplyRepository.requireCompleteResults(
                running,
                List.of(
                    result("model.project.fact", ResultStatus.CREATED),
                    result("model.project.unselected", ResultStatus.CREATED)
                )
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_RESULTS_INCOMPLETE")
            );
    }

    @Test
    void retrySourceMustBeLatestUnresolvedAttemptFromSameFrozenPreview() {
        BeginCommand retry = new BeginCommand(
            UUID.fromString("10000000-0000-0000-0000-000000000010"),
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            UUID.fromString("10000000-0000-0000-0000-000000000003"),
            UUID.fromString("10000000-0000-0000-0000-000000000009"),
            "default",
            "preview-hash",
            List.of("model.project.summary"),
            List.of("model.project.summary"),
            "retry-key",
            "retry-hash",
            "actor",
            NOW
        );
        Attempt wrongRun = new Attempt(
            retry.retrySourceAttemptId(),
            UUID.randomUUID(),
            retry.planId(),
            null,
            retry.tenantId(),
            1,
            retry.previewHash(),
            List.of("model.project.summary"),
            List.of("model.project.fact", "model.project.summary"),
            "source-key",
            "source-hash",
            AttemptStatus.PARTIAL,
            new ApplySummary(2, 1, 0, 0, 0, 1, 0),
            "actor",
            NOW,
            NOW,
            List.of(
                result("model.project.fact", ResultStatus.CREATED),
                result("model.project.summary", ResultStatus.FAILED)
            )
        );

        assertThatThrownBy(() -> ModelSpecImportApplyRepository.validateRetrySource(retry, wrongRun))
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_RETRY_SOURCE_STALE")
            );
    }

    private static BeginCommand command(String requestHash) {
        return new BeginCommand(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            UUID.fromString("10000000-0000-0000-0000-000000000003"),
            null,
            "default",
            "preview-hash",
            List.of("model.project.summary"),
            List.of("model.project.fact", "model.project.summary"),
            "apply-key",
            requestHash,
            "actor",
            NOW
        );
    }

    private static Attempt attempt(BeginCommand command, AttemptStatus status) {
        return new Attempt(
            command.attemptId(),
            command.runId(),
            command.planId(),
            null,
            command.tenantId(),
            1,
            command.previewHash(),
            command.selectedUniqueIds(),
            command.selectedClosure(),
            command.idempotencyKey(),
            command.requestHash(),
            status,
            ApplySummary.EMPTY,
            command.actorId(),
            NOW,
            status == AttemptStatus.RUNNING ? null : NOW,
            List.of()
        );
    }

    private static CandidateResult result(String uniqueId, ResultStatus status) {
        return new CandidateResult(
            UUID.randomUUID(),
            0,
            uniqueId,
            "candidate-key:" + uniqueId,
            "candidate-hash:" + uniqueId,
            status,
            status == ResultStatus.FAILED || status == ResultStatus.BLOCKED ? null : UUID.randomUUID(),
            status == ResultStatus.FAILED || status == ResultStatus.BLOCKED ? null : 1,
            null,
            null,
            null,
            0,
            List.of(),
            NOW
        );
    }
}
