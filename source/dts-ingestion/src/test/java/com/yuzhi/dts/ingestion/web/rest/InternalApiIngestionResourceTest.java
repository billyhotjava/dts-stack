package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalApiIngestionResourceTest {

    @Mock
    private IngestionTaskService taskService;

    @Mock
    private IngestionExecutionRepository executionRepository;

    private InternalApiIngestionResource resource;

    @BeforeEach
    void setUp() {
        resource = new InternalApiIngestionResource(taskService, executionRepository);
    }

    @Test
    void startExecution_shouldSubmitInternalApiExecutionWithBatchAndBackfill() {
        Instant windowStart = Instant.parse("2026-06-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-06-02T00:00:00Z");
        IngestionExecutionDTO submitted = new IngestionExecutionDTO();
        submitted.setId(42L);
        submitted.setTaskId(99L);
        submitted.setBatchId("airflow-batch-001");
        submitted.setStatus("preparing");
        submitted.setTriggerMode("BACKFILL_RANGE");
        when(taskService.executeInternalApi(
            eq(99L),
            eq("airflow-batch-001"),
            eq("BACKFILL_RANGE"),
            eq(windowStart),
            eq(windowEnd),
            eq("updated_at")
        )).thenReturn(submitted);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskId", 99L);
        request.put("batchId", "airflow-batch-001");
        request.put("mode", "BACKFILL_RANGE");
        request.put("backfillWindowStart", windowStart.toString());
        request.put("backfillWindowEnd", windowEnd.toString());
        request.put("backfillCursorColumn", "updated_at");

        Map<String, Object> body = resource.startExecution(request).getBody();

        assertThat(body)
            .containsEntry("id", 42L)
            .containsEntry("taskId", 99L)
            .containsEntry("batchId", "airflow-batch-001")
            .containsEntry("status", "preparing")
            .containsEntry("triggerMode", "BACKFILL_RANGE");
        verify(taskService).executeInternalApi(99L, "airflow-batch-001", "BACKFILL_RANGE", windowStart, windowEnd, "updated_at");
    }

    @Test
    void startExecution_shouldBindAirflowRunToExactEmbeddedRevision() {
        IngestionExecutionDTO submitted = new IngestionExecutionDTO();
        submitted.setId(43L);
        submitted.setTaskId(99L);
        submitted.setExecutionId("scheduled__r12");
        submitted.setStatus("preparing");
        when(taskService.executeInternalApiForRevision(
            99L, "batch-r12", null, null, null, null,
            12L, "checksum-r12", "orders_revision_12", "scheduled__r12"
        )).thenReturn(submitted);

        Map<String, Object> body = resource.startExecution(Map.of(
            "taskId", 99L,
            "batchId", "batch-r12",
            "revisionId", 12L,
            "configChecksum", "checksum-r12",
            "airflowDagId", "orders_revision_12",
            "airflowRunId", "scheduled__r12"
        )).getBody();

        assertThat(body).containsEntry("id", 43L).containsEntry("executionId", "scheduled__r12");
        verify(taskService).executeInternalApiForRevision(
            99L, "batch-r12", null, null, null, null,
            12L, "checksum-r12", "orders_revision_12", "scheduled__r12"
        );
    }

    @Test
    void registerScheduledExecution_shouldPersistExactContractBeforeAddaxRuns() {
        IngestionExecutionDTO submitted = new IngestionExecutionDTO();
        submitted.setId(44L);
        submitted.setTaskId(99L);
        submitted.setExecutionId("scheduled__r12");
        submitted.setStatus("running");
        when(taskService.registerScheduledExecution(
            99L, 12L, "checksum-r12", "orders_revision_12", "scheduled__r12"
        )).thenReturn(submitted);

        Map<String, Object> body = resource.registerScheduledExecution(Map.of(
            "taskId", 99L,
            "revisionId", 12L,
            "configChecksum", "checksum-r12",
            "airflowDagId", "orders_revision_12",
            "airflowRunId", "scheduled__r12"
        )).getBody();

        assertThat(body).containsEntry("id", 44L).containsEntry("status", "running");
        verify(taskService).registerScheduledExecution(
            99L, 12L, "checksum-r12", "orders_revision_12", "scheduled__r12"
        );
    }

    @Test
    void getExecution_shouldReturnStatusByStableExecutionDatabaseId() {
        IngestionTask task = new IngestionTask();
        task.setId(99L);
        task.setName("api-orders");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(42L);
        execution.setTask(task);
        execution.setExecutionId("api-abc123");
        execution.setBatchId("airflow-batch-001");
        execution.setStatus("success");
        execution.setRowsRead(10L);
        execution.setRowsWritten(9L);
        execution.setEndTime(Instant.parse("2026-06-02T00:01:00Z"));
        when(executionRepository.findById(42L)).thenReturn(Optional.of(execution));

        Map<String, Object> body = resource.getExecution("42").getBody();

        assertThat(body)
            .containsEntry("id", 42L)
            .containsEntry("executionId", "api-abc123")
            .containsEntry("taskId", 99L)
            .containsEntry("batchId", "airflow-batch-001")
            .containsEntry("status", "success")
            .containsEntry("rowsRead", 10L)
            .containsEntry("rowsWritten", 9L);
    }
}
