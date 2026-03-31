package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirflowExecutionSyncServiceTest {

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private AirflowClient airflowClient;

    @Mock
    private IngestionSettingsService settingsService;

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Mock
    private IncrementalSyncService incrementalSyncService;

    @Mock
    private AuditService auditService;

    private AirflowExecutionSyncService syncService;

    @BeforeEach
    void setUp() {
        AirflowProperties airflowProperties = new AirflowProperties();
        syncService = new AirflowExecutionSyncService(
            executionRepository,
            taskRepository,
            airflowClient,
            airflowProperties,
            settingsService,
            platformInfraClient,
            incrementalSyncService,
            auditService
        );
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of("executionPollEnabled", true, "executionPollBatchSize", 20)));
        when(executionRepository.save(any(IngestionExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(IngestionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldMarkFailureWithDetailedMessageAndAudit() {
        IngestionTask task = task(10L, "task-demo", "dag-demo");
        IngestionExecution execution = runningExecution(101L, "manual__001", task);
        when(executionRepository.findByStatusWithTask("running")).thenReturn(List.of(execution));
        when(airflowClient.getDagRunLookup("dag-demo", "manual__001"))
            .thenReturn(AirflowClient.DagRunLookupResult.found(200, Map.of("state", "failed")));

        Map<String, Object> taskInstance = new LinkedHashMap<>();
        taskInstance.put("task_id", "addax_ods_demo");
        taskInstance.put("state", "failed");
        taskInstance.put("try_number", 1);
        when(airflowClient.listTaskInstances("dag-demo", "manual__001")).thenReturn(Optional.of(List.of(taskInstance)));
        when(airflowClient.getTaskLog("dag-demo", "manual__001", "addax_ods_demo", 1))
            .thenReturn(Optional.of("java.lang.RuntimeException: permission denied for table ods_demo"));

        syncService.syncRunningExecutions();

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        IngestionExecution savedExecution = executionCaptor.getValue();
        assertThat(savedExecution.getStatus()).isEqualTo("failed");
        assertThat(savedExecution.getErrorMessage()).contains("permission denied");
        assertThat(savedExecution.getFailureCategory()).isEqualTo(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        assertThat(savedExecution.getFailureAdvice()).isEqualTo(ExecutionFailureClassifier.advice(ExecutionFailureClassifier.CATEGORY_PERMISSION));

        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_EXECUTE"),
            eq(AuditStage.FAIL),
            eq("task-demo"),
            metaCaptor.capture()
        );
        assertThat(metaCaptor.getValue()).containsEntry("failureCategory", ExecutionFailureClassifier.CATEGORY_PERMISSION);
        verify(incrementalSyncService, never()).updateCheckpointOnSuccess(any(), any());
    }

    @Test
    void shouldUpdateCheckpointWhenDagSucceeded() {
        IngestionTask task = task(20L, "task-success", "dag-success");
        IngestionExecution execution = runningExecution(202L, "manual__002", task);
        when(executionRepository.findByStatusWithTask("running")).thenReturn(List.of(execution));
        when(airflowClient.getDagRunLookup("dag-success", "manual__002"))
            .thenReturn(AirflowClient.DagRunLookupResult.found(200, Map.of("state", "success")));

        syncService.syncRunningExecutions();

        verify(incrementalSyncService).updateCheckpointOnSuccess(any(IngestionTask.class), any(IngestionExecution.class));
        verify(auditService, never()).auditAction(eq("INGESTION_TASK_EXECUTE"), eq(AuditStage.FAIL), any(), any());
    }

    @Test
    void shouldMarkExecutionFailedWhenDagRunIsMissingBeyondGraceWindow() {
        IngestionTask task = task(30L, "task-missing", "dag-missing");
        IngestionExecution execution = runningExecution(303L, "manual__404", task);
        execution.setStartTime(Instant.now().minusSeconds(10 * 60));

        when(executionRepository.findByStatusWithTask("running")).thenReturn(List.of(execution));
        when(airflowClient.getDagRunLookup("dag-missing", "manual__404"))
            .thenReturn(AirflowClient.DagRunLookupResult.notFound(404, "DAGRun not found"));

        syncService.syncRunningExecutions();

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        IngestionExecution savedExecution = executionCaptor.getValue();
        assertThat(savedExecution.getStatus()).isEqualTo("failed");
        assertThat(savedExecution.getErrorMessage()).contains("DAGRun not found");
        assertThat(savedExecution.getErrorMessage()).contains("dag-missing");
        assertThat(savedExecution.getErrorMessage()).contains("manual__404");
        verify(incrementalSyncService, never()).updateCheckpointOnSuccess(any(), any());
    }

    private IngestionTask task(Long id, String name, String dagId) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setName(name);
        task.setAirflowEnabled(true);
        task.setAirflowDagId(dagId);
        return task;
    }

    private IngestionExecution runningExecution(Long id, String runId, IngestionTask task) {
        IngestionExecution execution = new IngestionExecution();
        execution.setId(id);
        execution.setTask(task);
        execution.setExecutionId(runId);
        execution.setStatus("running");
        return execution;
    }
}
