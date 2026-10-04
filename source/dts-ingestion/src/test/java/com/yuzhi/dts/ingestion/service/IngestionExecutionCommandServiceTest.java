package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionExecutionCommandServiceTest {

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private AirflowClient airflowClient;

    private IngestionExecutionCommandService service;

    @BeforeEach
    void setUp() {
        service = new IngestionExecutionCommandService(
            executionRepository,
            new IngestionExecutionMapper(),
            airflowClient
        );
    }

    @Test
    void cancelPreparingExecutionPreventsBackgroundClaimWithoutCallingArbitraryDag() {
        IngestionExecution execution = execution("preparing", "preparing-1", null);
        when(executionRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(execution)).thenReturn(execution);

        IngestionExecutionDTO cancelled = service.cancel(13L, 90L);

        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
        assertThat(cancelled.getEndTime()).isNotNull();
        verify(airflowClient, never()).setDagRunStateStrict(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelRunningExecutionMarksOnlyItsOwnedAirflowRunFailedThenConvergesCancelled() {
        IngestionExecution execution = execution("running", "manual__2026-08-28T00:00:00Z", "ingestion_task_13_r4");
        when(executionRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(execution)).thenReturn(execution);

        IngestionExecutionDTO cancelled = service.cancel(13L, 90L);

        verify(airflowClient).setDagRunStateStrict(
            "ingestion_task_13_r4",
            "manual__2026-08-28T00:00:00Z",
            "failed"
        );
        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
    }

    @Test
    void cancelRejectsTerminalSuccessAndCrossTaskExecution() {
        IngestionExecution success = execution("success", "run-1", "task-13");
        when(executionRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(success));

        assertThatThrownBy(() -> service.cancel(13L, 90L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TASK_EXECUTION_NOT_CANCELLABLE");

        success.getTask().setId(14L);
        assertThatThrownBy(() -> service.cancel(13L, 90L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("执行实例不存在");
    }

    private IngestionExecution execution(String status, String executionId, String dagId) {
        IngestionTask task = new IngestionTask();
        task.setId(13L);
        task.setName("task-13");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(90L);
        execution.setTask(task);
        execution.setStatus(status);
        execution.setExecutionId(executionId);
        execution.setAirflowDagId(dagId);
        return execution;
    }
}
