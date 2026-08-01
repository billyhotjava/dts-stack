package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.IngestionRequiresNewExecutor;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngestionRetryServiceTest {

    private IngestionExecutionRepository executionRepository;
    private IngestionTaskService taskService;
    private EntityManager entityManager;
    private TypedQuery<IngestionExecution> childQuery;
    private IngestionRetryService retryService;
    private IngestionExecution failed;

    @BeforeEach
    void setUp() {
        executionRepository = mock(IngestionExecutionRepository.class);
        taskService = mock(IngestionTaskService.class);
        entityManager = mock(EntityManager.class);
        childQuery = mock(TypedQuery.class);
        IngestionProperties properties = new IngestionProperties();
        properties.getAutoRetry().setEnabled(true);
        properties.getAutoRetry().setMaxRetries(3);
        properties.getAutoRetry().setInitialDelaySeconds(10);
        properties.getAutoRetry().setMaxDelaySeconds(60);
        properties.getAutoRetry().setBackoffMultiplier(2.0);
        properties.getAutoRetry().setRetryableCategories("NETWORK,TIMEOUT");
        retryService = new IngestionRetryService(
            executionRepository,
            taskService,
            properties,
            entityManager,
            new IngestionRequiresNewExecutor()
        );

        IngestionTask task = new IngestionTask();
        task.setId(18L);
        task.setStatus("active");
        failed = new IngestionExecution();
        failed.setId(4142L);
        failed.setTask(task);
        failed.setStatus("failed");
        failed.setFailureCategory("NETWORK");
        failed.setRetryCount(0);
        failed.setMaxRetries(3);
        failed.setNextRetryAt(Instant.now().minusSeconds(1));

        when(entityManager.find(IngestionExecution.class, 4142L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(failed);
        when(entityManager.createQuery(anyString(), eq(IngestionExecution.class))).thenReturn(childQuery);
        when(childQuery.setParameter("parentId", 4142L)).thenReturn(childQuery);
        when(childQuery.setMaxResults(1)).thenReturn(childQuery);
        when(executionRepository.saveAndFlush(failed)).thenReturn(failed);
    }

    @Test
    void failedDispatchIsRescheduledWithoutConsumingAttempt() {
        when(childQuery.getResultStream()).thenAnswer(ignored -> Stream.empty());
        when(taskService.retryExecution(18L, 4142L, "FAILED_ONLY"))
            .thenThrow(new IllegalStateException("temporary dispatch failure"));

        assertThatThrownBy(() -> retryService.retryExecution(failed))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("temporary dispatch failure");

        assertThat(failed.getRetryCount()).isZero();
        assertThat(failed.getNextRetryAt()).isAfter(Instant.now());
        assertThat(failed.isRetryExhausted()).isFalse();
    }

    @Test
    void acceptedChildClearsScheduleAndConsumesExactlyOneAttempt() {
        IngestionExecution child = new IngestionExecution();
        child.setId(4143L);
        child.setParentExecutionId(4142L);
        child.setRetryCount(1);
        when(childQuery.getResultStream()).thenAnswer(ignored -> Stream.of(child));
        when(taskService.retryExecution(18L, 4142L, "FAILED_ONLY")).thenReturn(mock(IngestionExecutionDTO.class));

        retryService.retryExecution(failed);

        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isNull();
        assertThat(failed.isRetryExhausted()).isFalse();
        verify(taskService).retryExecution(18L, 4142L, "FAILED_ONLY");
    }

    @Test
    void activeClaimPreventsSecondSchedulerFromDispatchingSameParent() {
        failed.setNextRetryAt(Instant.now().plusSeconds(120));

        retryService.retryExecution(failed);

        assertThat(failed.getRetryCount()).isZero();
        verify(taskService, org.mockito.Mockito.never()).retryExecution(18L, 4142L, "FAILED_ONLY");
    }
}
