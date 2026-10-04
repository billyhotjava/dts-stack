package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackAffectedObject;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackAffectedObjectRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RollbackRecoveryServiceTest {

    private static final UUID RECEIPT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SOURCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ROLLBACK_COMPLETED_AT = Instant.parse("2026-07-31T10:00:00Z");

    private final IngestionRollbackOperationRepository operationRepository = mock(
        IngestionRollbackOperationRepository.class
    );
    private final IngestionRollbackAffectedObjectRepository affectedObjectRepository = mock(
        IngestionRollbackAffectedObjectRepository.class
    );
    private final IngestionRollbackOutboxRepository outboxRepository = mock(IngestionRollbackOutboxRepository.class);
    private final RollbackAuditService auditService = mock(RollbackAuditService.class);
    private final RollbackSagaService sagaService = mock(RollbackSagaService.class);
    private RollbackRecoveryService service;

    @BeforeEach
    void setUp() {
        when(sagaService.hashJson(any())).thenReturn("a".repeat(64));
        service = new RollbackRecoveryService(
            operationRepository,
            affectedObjectRepository,
            outboxRepository,
            auditService,
            sagaService,
            new ObjectMapper()
        );
    }

    @Test
    void successfulTaskLandingAfterRollbackEmitsMonotonicRestoreCompletion() {
        IngestionRollbackOperation operation = operation("task", 41L);
        stubOperation(operation);
        when(affectedObjectRepository.findByOperationReceiptIdOrderByIdAsc(RECEIPT))
            .thenReturn(List.of(plannedTask(41L), plannedTable(41L, "public.orders")));

        service.recordSuccessfulRevalidation(task(41L), successfulExecution(41L, 91L, "run-91"));

        assertThat(operation.getStatus()).isEqualTo("RESTORE_PENDING");
        assertThat(operation.getCompletionSequence()).isEqualTo(9L);
        verify(affectedObjectRepository).saveAll(any());
        verify(outboxRepository).save(argThat(event ->
            event.getOperationReceiptId().equals(RECEIPT)
                && event.getOutcome().equals("RESTORE")
                && event.getSourceSequence() == 9L
        ));
        verify(auditService).recordCommitted(
            org.mockito.ArgumentMatchers.eq(RECEIPT),
            org.mockito.ArgumentMatchers.eq("a".repeat(64)),
            org.mockito.ArgumentMatchers.eq(RollbackAuditReason.SOURCE_AVAILABILITY_RESTORE),
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void successFromAnotherTaskOnTheSameSourceMustNotRestoreTaskScopedReceipt() {
        IngestionRollbackOperation operation = operation("task", 41L);
        stubOperation(operation);
        when(affectedObjectRepository.findByOperationReceiptIdOrderByIdAsc(RECEIPT))
            .thenReturn(List.of(plannedTask(41L), plannedTable(41L, "public.orders")));

        service.recordSuccessfulRevalidation(task(42L), successfulExecution(42L, 92L, "run-92"));

        assertThat(operation.getStatus()).isEqualTo("COMPLETED");
        verifyNoInteractions(outboxRepository, auditService);
        verify(affectedObjectRepository, never()).saveAll(any());
    }

    @Test
    void datasourceReceiptRemainsFencedUntilEveryTaskAndTableIsRevalidated() {
        IngestionRollbackOperation operation = operation("datasource", null);
        stubOperation(operation);
        when(affectedObjectRepository.findByOperationReceiptIdOrderByIdAsc(RECEIPT))
            .thenReturn(
                List.of(
                    plannedTask(41L),
                    plannedTable(41L, "public.orders"),
                    plannedTask(42L),
                    plannedTable(42L, "public.customers")
                )
            );

        service.recordSuccessfulRevalidation(task(41L), successfulExecution(41L, 91L, "run-91"));

        assertThat(operation.getStatus()).isEqualTo("COMPLETED");
        verify(outboxRepository, never()).save(any());
        verify(affectedObjectRepository).saveAll(any());
        verify(auditService).recordCommitted(
            org.mockito.ArgumentMatchers.eq(RECEIPT),
            any(),
            org.mockito.ArgumentMatchers.eq(RollbackAuditReason.SOURCE_REVALIDATION_PROGRESS),
            any(), any(), any(), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.eq("PENDING"),
            org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void datasourceReceiptRestoresOnlyAfterPriorTaskAndTableEvidenceExists() {
        IngestionRollbackOperation operation = operation("datasource", null);
        stubOperation(operation);
        when(affectedObjectRepository.findByOperationReceiptIdOrderByIdAsc(RECEIPT))
            .thenReturn(
                List.of(
                    plannedTask(41L),
                    plannedTable(41L, "public.orders"),
                    plannedTask(42L),
                    plannedTable(42L, "public.customers"),
                    revalidatedTask(41L),
                    revalidatedTable(41L, "public.orders")
                )
            );

        service.recordSuccessfulRevalidation(task(42L), successfulExecution(42L, 92L, "run-92"));

        assertThat(operation.getStatus()).isEqualTo("RESTORE_PENDING");
        verify(outboxRepository).save(argThat(event -> "RESTORE".equals(event.getOutcome())));
    }

    private void stubOperation(IngestionRollbackOperation operation) {
        when(operationRepository.findFirstBySourceDataSourceIdAndStatusInOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq(SOURCE),
            any(Collection.class)
        )).thenReturn(Optional.of(operation));
    }

    private IngestionRollbackOperation operation(String scope, Long taskId) {
        IngestionRollbackOperation operation = new IngestionRollbackOperation();
        operation.setReceiptId(RECEIPT);
        operation.setSourceDataSourceId(SOURCE);
        operation.setCompletionSequence(8L);
        operation.setLevel(2);
        operation.setScope(scope);
        operation.setTaskId(taskId);
        operation.setStatus("COMPLETED");
        operation.setUpdatedAt(ROLLBACK_COMPLETED_AT);
        return operation;
    }

    private IngestionTask task(Long id) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setSourceDataSourceId(SOURCE);
        return task;
    }

    private IngestionExecution successfulExecution(Long taskId, Long id, String executionId) {
        IngestionExecution execution = new IngestionExecution();
        execution.setId(id);
        execution.setExecutionId(executionId);
        execution.setStatus("success");
        execution.setTask(task(taskId));
        execution.setEndTime(Instant.parse("2026-07-31T10:05:00Z"));
        return execution;
    }

    private IngestionRollbackAffectedObject plannedTask(Long taskId) {
        return evidence("INGESTION_TASK", String.valueOf(taskId), "STATE_CHANGE", "PLANNED", null);
    }

    private IngestionRollbackAffectedObject plannedTable(Long taskId, String table) {
        return evidence("TABLE", table, "DROP", "PLANNED", "{\"taskId\":" + taskId + "}");
    }

    private IngestionRollbackAffectedObject revalidatedTask(Long taskId) {
        return evidence(
            "INGESTION_TASK",
            String.valueOf(taskId),
            "SOURCE_REVALIDATION",
            "REVALIDATED",
            "{\"taskId\":" + taskId + "}"
        );
    }

    private IngestionRollbackAffectedObject revalidatedTable(Long taskId, String table) {
        return evidence(
            "TABLE",
            table,
            "SOURCE_REVALIDATION",
            "REVALIDATED",
            "{\"taskId\":" + taskId + "}"
        );
    }

    private IngestionRollbackAffectedObject evidence(
        String type,
        String ref,
        String action,
        String status,
        String evidenceJson
    ) {
        IngestionRollbackAffectedObject evidence = new IngestionRollbackAffectedObject();
        evidence.setOperationReceiptId(RECEIPT);
        evidence.setObjectType(type);
        evidence.setObjectRef(ref);
        evidence.setAction(action);
        evidence.setPhase("PLANNED".equals(status) ? "PLANNED" : "RESULT");
        evidence.setStatus(status);
        evidence.setEvidenceJson(evidenceJson);
        evidence.setEvidenceHash("b".repeat(64));
        evidence.setRecordedAt(ROLLBACK_COMPLETED_AT);
        return evidence;
    }
}
