package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PostIngestionQualityWorkflowServiceTest {

    @Mock private IngestionExecutionRepository executionRepository;
    @Mock private PlatformInfraClient platformInfraClient;
    @Mock private AuditService auditService;

    private PostIngestionQualityWorkflowService service;
    private IngestionExecution execution;

    @BeforeEach
    void setUp() {
        var attemptStore = new PostIngestionQualityWorkflowAttemptStore(executionRepository, auditService);
        service = new PostIngestionQualityWorkflowService(attemptStore, platformInfraClient);
        IngestionTask task = new IngestionTask();
        task.setId(21L);
        task.setName("PJM ODS 接入");
        execution = new IngestionExecution();
        execution.setId(203L);
        execution.setTask(task);
        execution.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000021");
        when(executionRepository.findByIdForQualityWorkflowUpdate(203L)).thenReturn(Optional.of(execution));
        when(executionRepository.save(any(IngestionExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatchesPersistedPendingIntentAndStoresWorkflowIdentity() throws Exception {
        for (String method : new String[] { "prepare", "complete", "fail" }) {
            var signature = switch (method) {
                case "prepare" -> new Class<?>[] { Long.class };
                case "complete" -> new Class<?>[] { Long.class, PlatformInfraClient.QualityWorkflowReceipt.class };
                default -> new Class<?>[] { Long.class, RuntimeException.class };
            };
            Transactional transaction = PostIngestionQualityWorkflowAttemptStore.class
                .getMethod(method, signature)
                .getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        }
        when(
            platformInfraClient.triggerQualityWorkflowByPolicyRef(
                "dataset:00000000-0000-0000-0000-000000000021",
                "INGESTION",
                "203"
            )
        ).thenReturn(new PlatformInfraClient.QualityWorkflowReceipt("workflow-21", "run-21"));
        execution.setStatus("success");
        execution.setQualityWorkflowStatus("PENDING");
        execution.setQualityWorkflowNextRetryAt(Instant.now().minusSeconds(1));

        var result = service.trigger(203L);

        assertThat(result.status()).isEqualTo("TRIGGERED");
        assertThat(result.workflowId()).isEqualTo("workflow-21");
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.nextRetryAt()).isNull();
        assertThat(execution.getQualityWorkflowError()).isNull();
        verify(auditService).auditAction(eq("INGESTION_TASK_QUALITY_TRIGGER"), eq(AuditStage.SUCCESS), eq("PJM ODS 接入"), any());
    }

    @Test
    void registersBoundedAutomaticRetriesWithoutChangingTheIngestionOutcome() {
        execution.setStatus("success");
        when(platformInfraClient.triggerQualityWorkflowByPolicyRef(any(), any(), any()))
            .thenThrow(new IllegalStateException("platform unavailable"));

        var first = service.trigger(203L);
        var deferred = service.trigger(203L);

        assertThat(first.status()).isEqualTo("RETRY_WAIT");
        assertThat(first.nextRetryAt()).isNotNull();
        assertThat(deferred.attemptCount()).isEqualTo(1);
        verify(platformInfraClient, times(1)).triggerQualityWorkflowByPolicyRef(any(), any(), any());

        execution.setQualityWorkflowNextRetryAt(Instant.now().minusSeconds(1));
        var second = service.trigger(203L);
        assertThat(second.status()).isEqualTo("RETRY_WAIT");
        execution.setQualityWorkflowNextRetryAt(Instant.now().minusSeconds(1));
        var third = service.trigger(203L);

        assertThat(third.status()).isEqualTo("EXHAUSTED");
        assertThat(third.attemptCount()).isEqualTo(3);
        assertThat(third.error()).contains("平台质量验证启动失败");
        assertThat(execution.getStatus()).isEqualTo("success");
        assertThat(execution.getQualityWorkflowNextRetryAt()).isNull();
        verify(platformInfraClient, times(3)).triggerQualityWorkflowByPolicyRef(any(), any(), any());
    }

    @Test
    void replaysAStaleTriggerLeaseWithTheSameExecutionIdentity() {
        execution.setStatus("success");
        execution.setQualityWorkflowStatus("TRIGGERING");
        execution.setQualityWorkflowAttemptCount(1);
        execution.setQualityWorkflowNextRetryAt(Instant.now().minusSeconds(1));
        when(platformInfraClient.triggerQualityWorkflowByPolicyRef(any(), any(), eq("203")))
            .thenReturn(new PlatformInfraClient.QualityWorkflowReceipt("workflow-replayed", "run-replayed"));

        var result = service.trigger(203L);

        assertThat(result.status()).isEqualTo("TRIGGERED");
        assertThat(result.workflowId()).isEqualTo("workflow-replayed");
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(execution.getStatus()).isEqualTo("success");
    }

}
