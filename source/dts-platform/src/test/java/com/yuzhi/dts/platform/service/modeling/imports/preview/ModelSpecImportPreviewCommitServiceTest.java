package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditOutcome;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditFacts;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewFailureFact;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecImportPreviewCommitServiceTest {

    @Test
    void persistsBeforeStrictAuditAndEmitsOnlyAllowlistedFacts() {
        ModelSpecImportPreviewRepository repository = mock(ModelSpecImportPreviewRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecImportPreviewCommitService service = new ModelSpecImportPreviewCommitService(repository, audit);
        UUID runId = UUID.randomUUID();
        PersistedRun run = run(runId);
        PreviewSummary summary = new PreviewSummary(3, 2, 1, 1, 1, 0, 0);
        PreviewAuditFacts facts = new PreviewAuditFacts(
            runId,
            "tenant-a",
            run.planId(),
            "actor-a",
            "preview-hash",
            "a".repeat(64),
            summary,
            PreviewAuditOutcome.PARTIAL,
            List.of(
                new PreviewFailureFact(
                    "model.demo.orders",
                    "MODEL_IMPORT_DEPENDENCY_MISSING",
                    "PREVIEW",
                    "DEPENDENCY",
                    false,
                    "INCLUDE_DEPENDENCY",
                    "request-correlation"
                )
            ),
            "request-correlation"
        );
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        service.commit(run, List.of(), facts);

        var order = inOrder(repository, audit);
        order.verify(repository).save(run, List.of());
        order
            .verify(audit)
            .auditActionStrict(
                eq(ModelSpecImportPreviewCommitService.PREVIEW_ACTION),
                eq(AuditStage.FAIL),
                eq(runId.toString()),
                payload.capture()
            );
        @SuppressWarnings("unchecked")
        Map<String, Object> auditPayload = (Map<String, Object>) payload.getValue();
        assertThat(auditPayload)
            .containsEntry("runId", runId)
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("planId", run.planId())
            .containsEntry("actorId", "actor-a")
            .containsEntry("correlationId", "request-correlation")
            .containsEntry("outcome", "PARTIAL")
            .containsEntry("previewHash", "preview-hash")
            .containsEntry("packageChecksum", "a".repeat(64))
            .containsEntry("blocked", 1)
            .doesNotContainKeys("sql", "zip", "credentials", "packageJson", "requestJson");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) auditPayload.get("failures");
        assertThat(failures)
            .singleElement()
            .satisfies(failure ->
                assertThat(failure)
                    .containsEntry("itemIdentity", "model.demo.orders")
                    .containsEntry("code", "MODEL_IMPORT_DEPENDENCY_MISSING")
                    .containsEntry("stage", "PREVIEW")
                    .containsEntry("category", "DEPENDENCY")
                    .containsEntry("retryable", false)
                    .containsEntry("recoveryAction", "INCLUDE_DEPENDENCY")
                    .containsEntry("correlationId", "request-correlation")
                    .doesNotContainKeys("message", "sql", "zip", "credentials")
            );
    }

    @Test
    void conflictOnlyPreviewIsAuditedAsBlockedFailure() {
        assertFailureOutcome(new PreviewSummary(1, 0, 0, 0, 0, 0, 1), PreviewAuditOutcome.BLOCKED);
    }

    @Test
    void mixedConflictPreviewIsAuditedAsPartialFailure() {
        assertFailureOutcome(new PreviewSummary(2, 1, 0, 1, 0, 0, 1), PreviewAuditOutcome.PARTIAL);
    }

    @Test
    void propagatesStrictAuditFailureToTransactionalCaller() {
        ModelSpecImportPreviewRepository repository = mock(ModelSpecImportPreviewRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecImportPreviewCommitService service = new ModelSpecImportPreviewCommitService(repository, audit);
        UUID runId = UUID.randomUUID();
        PersistedRun run = run(runId);
        PreviewAuditFacts facts = new PreviewAuditFacts(
            runId,
            "tenant-a",
            run.planId(),
            "actor-a",
            "preview-hash",
            "a".repeat(64),
            new PreviewSummary(1, 1, 0, 1, 0, 0, 0),
            PreviewAuditOutcome.SUCCESS,
            List.of(),
            "request-correlation"
        );
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditActionStrict(
                eq(ModelSpecImportPreviewCommitService.PREVIEW_ACTION),
                eq(AuditStage.SUCCESS),
                eq(runId.toString()),
                org.mockito.ArgumentMatchers.any()
            );

        assertThatThrownBy(() -> service.commit(run, List.of(), facts))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");
        verify(repository).save(run, List.of());
    }

    private static void assertFailureOutcome(PreviewSummary summary, PreviewAuditOutcome expectedOutcome) {
        ModelSpecImportPreviewRepository repository = mock(ModelSpecImportPreviewRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecImportPreviewCommitService service = new ModelSpecImportPreviewCommitService(repository, audit);
        UUID runId = UUID.randomUUID();
        PersistedRun run = run(runId);
        PreviewAuditFacts facts = new PreviewAuditFacts(
            runId,
            "tenant-a",
            run.planId(),
            "actor-a",
            "preview-hash",
            "a".repeat(64),
            summary,
            expectedOutcome,
            List.of(),
            "request-correlation"
        );
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        service.commit(run, List.of(), facts);

        verify(audit)
            .auditActionStrict(
                eq(ModelSpecImportPreviewCommitService.PREVIEW_ACTION),
                eq(AuditStage.FAIL),
                eq(runId.toString()),
                payload.capture()
            );
        @SuppressWarnings("unchecked")
        Map<String, Object> auditPayload = (Map<String, Object>) payload.getValue();
        assertThat(auditPayload).containsEntry("outcome", expectedOutcome.name());
    }

    private static PersistedRun run(UUID runId) {
        return new PersistedRun(
            runId,
            "tenant-a",
            UUID.randomUUID(),
            "a".repeat(64),
            "dts.model-package/v1",
            "{}",
            "{}",
            "{}",
            "{}",
            "b".repeat(64),
            "{}",
            "c".repeat(64),
            "preview-hash",
            ModelSpecImportPreviewContract.RunStatus.PREVIEWED,
            "{}",
            Instant.now().plusSeconds(600),
            "actor-a",
            Instant.now()
        );
    }
}
