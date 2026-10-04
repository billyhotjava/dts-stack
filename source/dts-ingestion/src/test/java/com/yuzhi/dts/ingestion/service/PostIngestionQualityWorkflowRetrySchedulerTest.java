package com.yuzhi.dts.ingestion.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PostIngestionQualityWorkflowRetrySchedulerTest {

    @Mock private IngestionExecutionRepository executionRepository;
    @Mock private PostIngestionQualityWorkflowService workflowService;

    @Test
    void continuesWithRemainingDueExecutionsWhenOneRetryFails() {
        IngestionExecution first = new IngestionExecution();
        first.setId(101L);
        IngestionExecution second = new IngestionExecution();
        second.setId(102L);
        when(executionRepository.findDueQualityWorkflowRetries(any(), any(Pageable.class)))
            .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary failure")).when(workflowService).trigger(101L);

        new PostIngestionQualityWorkflowRetryScheduler(executionRepository, workflowService).retryDueWorkflows();

        verify(workflowService).trigger(101L);
        verify(workflowService).trigger(102L);
        verify(executionRepository).findDueQualityWorkflowRetries(any(), eq(Pageable.ofSize(50)));
    }
}
