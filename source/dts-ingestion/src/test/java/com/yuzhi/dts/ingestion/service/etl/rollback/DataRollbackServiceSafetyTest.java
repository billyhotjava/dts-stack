package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.TableOperationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataRollbackServiceSafetyTest {

    @Mock private IngestionTaskRepository taskRepository;
    @Mock private IngestionExecutionRepository executionRepository;
    @Mock private TableOperationService tableOperationService;
    @Mock private ConfirmationPolicy confirmationPolicy;
    @Mock private RollbackAuditService auditService;
    @Mock private FileUploadService fileUploadService;
    @Mock private IncrementalSyncService incrementalSyncService;
    @Mock private IngestionTaskChangeLogService changeLogService;
    @Mock private AddaxJobService addaxJobService;
    @Mock private AirflowDagService airflowDagService;

    private DataRollbackService service;

    @BeforeEach
    void setUp() {
        service = new DataRollbackService(
            taskRepository,
            executionRepository,
            tableOperationService,
            confirmationPolicy,
            auditService,
            new ObjectMapper(),
            fileUploadService,
            incrementalSyncService,
            changeLogService,
            addaxJobService,
            airflowDagService
        );
    }

    @Test
    void executeMustRejectDryRunBeforeResolvingOrMutatingAnything() {
        RollbackRequest request = new RollbackRequest(3, "task", 41L, null, List.of(), false, true);

        assertThatThrownBy(() -> service.execute(request, "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ROLLBACK_EXECUTE_DRY_RUN_FORBIDDEN");

        verifyNoInteractions(
            taskRepository,
            executionRepository,
            tableOperationService,
            auditService,
            fileUploadService,
            incrementalSyncService,
            changeLogService,
            addaxJobService,
            airflowDagService
        );
    }

    @Test
    void analyzeMustRemainReadOnlyEvenWhenCallerSendsDryRunFalse() {
        IngestionTask task = new IngestionTask();
        task.setId(42L);
        task.setStatus("active");
        task.setSourceType("mysqlreader");
        when(taskRepository.findById(42L)).thenReturn(Optional.of(task));
        when(tableOperationService.resolveTargetTables(task)).thenReturn(List.of("public.ods_orders"));
        when(executionRepository.countByTaskIdAndStatusesIgnoreCase(42L, List.of("success", "failed", "running")))
            .thenReturn(2L);
        when(confirmationPolicy.confirmationType(RollbackLevel.TRUNCATE_DATA)).thenReturn("TEXT_CONFIRM");

        RollbackImpact impact = service.analyze(
            new RollbackRequest(1, "task", 42L, null, List.of(), false, false)
        );

        assertThat(impact.affectedTables()).containsExactly("public.ods_orders");
        verify(tableOperationService, never()).truncateTable(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(tableOperationService, never()).dropTable(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(fileUploadService, incrementalSyncService, changeLogService, addaxJobService, airflowDagService);
    }

    @Test
    void levelThreeMustStopExternalCleanupWhenCriticalDatabaseCleanupFails() {
        IngestionTask task = new IngestionTask();
        task.setId(43L);
        task.setStatus("active");
        task.setSourceType("excelreader");
        when(taskRepository.findById(43L)).thenReturn(Optional.of(task));
        when(tableOperationService.resolveTargetTables(task)).thenReturn(List.of("public.ods_orders"));
        when(executionRepository.countByTaskIdAndStatusesIgnoreCase(43L, List.of("success", "failed", "running")))
            .thenReturn(1L);
        when(confirmationPolicy.confirmationType(RollbackLevel.FULL_CASCADE)).thenReturn("TEXT_CONFIRM");
        doThrow(new IllegalStateException("execution delete failed"))
            .when(executionRepository).deleteByTaskId(43L);

        RollbackResult result = service.execute(
            new RollbackRequest(3, "task", 43L, null, List.of(), false, false),
            "operator"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.actions()).contains("DROPPED: public.ods_orders", "LOCAL_DB_ROLLED_BACK: task 43");
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("EXEC_DELETE_FAILED"));
        verify(incrementalSyncService, never()).clearCheckpointByTaskId(43L);
        verify(changeLogService, never()).deleteByTaskId(43L);
        verify(taskRepository, never()).save(any(IngestionTask.class));
        verifyNoInteractions(fileUploadService, addaxJobService, airflowDagService);
    }
}
