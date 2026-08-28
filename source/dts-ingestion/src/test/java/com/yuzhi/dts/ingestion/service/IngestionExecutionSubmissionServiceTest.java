package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IngestionExecutionSubmissionServiceTest {

    @Mock private IngestionExecutionRepository executionRepository;
    @Mock private IngestionTaskService taskService;

    private IngestionExecutionSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionExecutionSubmissionService(
            executionRepository,
            new IngestionExecutionMapper(),
            taskService
        );
    }

    @Test
    void repeatedKeyReturnsTheExistingTaskExecution() {
        IngestionExecution existing = execution(90L, 13L);
        when(executionRepository.findByBatchIdWithTask(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.of(existing));

        IngestionExecutionDTO result = service.submit(13L, "browser-command-1");

        assertThat(result.getId()).isEqualTo(90L);
        assertThat(result.getTaskId()).isEqualTo(13L);
        assertThat(service.submitCommand(13L, "browser-command-1").replayed()).isTrue();
    }

    @Test
    void firstSubmissionHashesTheBrowserKeyBeforePersistingIt() {
        when(executionRepository.findByBatchIdWithTask(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty());
        IngestionExecutionDTO submitted = new IngestionExecutionDTO();
        submitted.setId(91L);
        when(taskService.executeWithBatchId(eq(13L), org.mockito.ArgumentMatchers.anyString())).thenReturn(submitted);

        IngestionExecutionSubmissionService.SubmissionResult result = service.submitCommand(13L, "browser-command-2");

        assertThat(result.execution().getId()).isEqualTo(91L);
        assertThat(result.replayed()).isFalse();

        ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
        verify(taskService).executeWithBatchId(eq(13L), batch.capture());
        assertThat(batch.getValue()).startsWith("idem-task-13-").doesNotContain("browser-command-2");
    }

    @Test
    void concurrentUniqueConflictReturnsTheCommittedWinner() {
        IngestionExecution winner = execution(92L, 13L);
        when(executionRepository.findByBatchIdWithTask(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty(), Optional.of(winner));
        when(taskService.executeWithBatchId(eq(13L), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.submit(13L, "browser-command-3").getId()).isEqualTo(92L);
    }

    @Test
    void taskLockConflictReturnsTheCommittedIdempotencyWinner() {
        IngestionExecution winner = execution(93L, 13L);
        when(executionRepository.findByBatchIdWithTask(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty(), Optional.of(winner));
        when(taskService.executeWithBatchId(eq(13L), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalStateException("任务仍在运行中，请稍后重试"));

        IngestionExecutionSubmissionService.SubmissionResult result = service.submitCommand(
            13L,
            "browser-command-4"
        );

        assertThat(result.execution().getId()).isEqualTo(93L);
        assertThat(result.replayed()).isTrue();
    }

    @Test
    void missingKeyIsRejected() {
        assertThatThrownBy(() -> service.submit(13L, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IDEMPOTENCY_KEY_INVALID");
    }

    private IngestionExecution execution(Long executionId, Long taskId) {
        IngestionTask task = new IngestionTask();
        task.setId(taskId);
        task.setName("task-" + taskId);
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("preparing");
        return execution;
    }
}
