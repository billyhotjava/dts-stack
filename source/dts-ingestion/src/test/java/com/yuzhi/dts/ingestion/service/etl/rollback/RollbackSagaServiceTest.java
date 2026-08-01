package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackAffectedObjectRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RollbackSagaServiceTest {

    private static final UUID RECEIPT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SOURCE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final IngestionRollbackOperationRepository operationRepository = mock(
        IngestionRollbackOperationRepository.class
    );
    private final IngestionRollbackAffectedObjectRepository affectedObjectRepository = mock(
        IngestionRollbackAffectedObjectRepository.class
    );
    private final IngestionRollbackOutboxRepository outboxRepository = mock(IngestionRollbackOutboxRepository.class);
    private final IngestionTaskRepository taskRepository = mock(IngestionTaskRepository.class);
    private final RollbackAuditService auditService = mock(RollbackAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RollbackSagaService service;

    @BeforeEach
    void setUp() {
        service = new RollbackSagaService(
            operationRepository,
            affectedObjectRepository,
            outboxRepository,
            taskRepository,
            auditService,
            objectMapper
        );
    }

    @Test
    void fullCascadeSoftDeletesTaskWhenExternalArtifactsWereAlreadyAbsent() {
        RollbackRequest request = request();
        IngestionRollbackOperation operation = operation(request);
        IngestionTask task = new IngestionTask();
        task.setId(41L);
        task.setStatus("active");
        when(operationRepository.findForUpdate(RECEIPT)).thenReturn(java.util.Optional.of(operation));
        when(taskRepository.findAllById(List.of(41L))).thenReturn(List.of(task));

        RollbackResult physical = new RollbackResult(
            true,
            List.of("ADDAX_JOB_NOT_FOUND: task:41", "DAG_NOT_FOUND: task:41"),
            List.of(),
            List.of(41L),
            false,
            "NONE",
            null,
            null,
            null,
            false,
            false
        );

        RollbackResult result = service.complete(request, impact(), "operator", physical, List.of());

        assertThat(result.success()).isTrue();
        assertThat(result.sideEffectsApplied()).isTrue();
        assertThat(result.outcome()).isEqualTo(RollbackSagaService.OUTCOME_APPLY);
        assertThat(result.actions()).contains("TASK_SOFT_DELETED: 41");
        assertThat(task.getStatus()).isEqualTo("deleted");
        verify(taskRepository).saveAll(List.of(task));
        verify(affectedObjectRepository).saveAll(argThat(items ->
            items.iterator().hasNext()
                && items.iterator().next().getObjectType().equals("INGESTION_TASK")
                && items.iterator().next().getStatus().equals("APPLIED")
        ));
        verify(outboxRepository).save(argThat(event ->
            event.getOperationReceiptId().equals(RECEIPT)
                && event.getOutcome().equals(RollbackSagaService.OUTCOME_APPLY)
                && event.getSourceSequence() == 8L
        ));
    }

    @Test
    void sameIdempotentCommandReplaysStoredResultWithoutExecutingAgain() throws Exception {
        RollbackRequest request = request();
        IngestionRollbackOperation operation = operation(request);
        RollbackResult stored = new RollbackResult(
            true,
            List.of("TASK_SOFT_DELETED: 41"),
            List.of(),
            List.of(41L),
            true,
            "APPLIED",
            RECEIPT,
            RollbackSagaService.OUTCOME_APPLY,
            "rollback:" + RECEIPT + ":8",
            false,
            false
        );
        operation.setResultJson(objectMapper.writeValueAsString(stored));
        operation.setOutcome(RollbackSagaService.OUTCOME_APPLY);
        operation.setCompletionEventId("rollback:" + RECEIPT + ":8");
        operation.setStatus("COMPLETED");
        when(operationRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(java.util.Optional.of(operation));

        RollbackSagaService.BeginDecision decision = service.begin(request, impact(), "operator", List.of());

        assertThat(decision.execute()).isFalse();
        assertThat(decision.replay().replayed()).isTrue();
        assertThat(decision.replay().completionPending()).isFalse();
    }

    @Test
    void missingTaskStateAfterPhysicalDropForcesReconciliation() {
        RollbackRequest request = request();
        IngestionRollbackOperation operation = operation(request);
        when(operationRepository.findForUpdate(RECEIPT)).thenReturn(java.util.Optional.of(operation));
        when(taskRepository.findAllById(List.of(41L))).thenReturn(List.of());
        RollbackResult physical = new RollbackResult(
            true,
            List.of("DROPPED: public.orders"),
            List.of(),
            List.of(41L),
            true,
            "APPLIED",
            null,
            null,
            null,
            false,
            false
        );

        RollbackResult result = service.complete(request, impact(), "operator", physical, List.of());

        assertThat(result.success()).isFalse();
        assertThat(result.sideEffectStatus()).isEqualTo("PARTIAL");
        assertThat(result.outcome()).isEqualTo(RollbackSagaService.OUTCOME_APPLY);
        assertThat(result.errors()).contains("ROLLBACK_TASK_STATE_MISSING: [41]");
    }

    private IngestionRollbackOperation operation(RollbackRequest request) {
        IngestionRollbackOperation operation = new IngestionRollbackOperation();
        operation.setReceiptId(RECEIPT);
        operation.setIdempotencyKey(request.idempotencyKey());
        operation.setRequestHash(request.requestHash());
        operation.setPayloadHash(service.hashJson(request));
        operation.setLevel(3);
        operation.setScope("task");
        operation.setTaskId(41L);
        operation.setSourceDataSourceId(SOURCE);
        operation.setFenceSequence(7L);
        operation.setOperator("operator");
        operation.setStatus("EXECUTING");
        operation.setSideEffectsApplied(false);
        operation.setRequestJson("{}");
        operation.setImpactJson("{}");
        operation.setCreatedAt(Instant.now());
        operation.setUpdatedAt(Instant.now());
        return operation;
    }

    private RollbackRequest request() {
        return new RollbackRequest(
            3,
            "task",
            41L,
            null,
            List.of(),
            false,
            RECEIPT,
            "platform:" + RECEIPT,
            "0".repeat(64),
            new RollbackRequest.AvailabilityFence(RECEIPT, SOURCE, 7L, "PREPARED", 1)
        );
    }

    private RollbackImpact impact() {
        return new RollbackImpact(3, "task", 41L, null, List.of(), List.of(), 0, "PHRASE", List.of(41L), List.of());
    }
}
