package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class RollbackExecutionWatchdogServiceTest {

    @Test
    void springCreatesWatchdogUsingTheProductionConstructor() {
        IngestionRollbackOperationRepository repository = mock(IngestionRollbackOperationRepository.class);
        RollbackAuditService auditService = mock(RollbackAuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("watchdog-test", Map.of("dts.ingestion.rollback-execution-timeout-ms", "60000"))
            );
            context.registerBean(IngestionRollbackOperationRepository.class, () -> repository);
            context.registerBean(RollbackAuditService.class, () -> auditService);
            context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
            context.registerBean(RollbackExecutionWatchdogService.class);

            context.refresh();

            assertThat(context.getBean(RollbackExecutionWatchdogService.class)).isNotNull();
        }
    }

    @Test
    void staleExecutionIsFencedForReconciliationWithoutReplayingPhysicalWork() {
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        UUID receipt = UUID.fromString("22222222-2222-2222-2222-222222222222");
        IngestionRollbackOperationRepository repository = mock(IngestionRollbackOperationRepository.class);
        RollbackAuditService auditService = mock(RollbackAuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());

        IngestionRollbackOperation operation = new IngestionRollbackOperation();
        operation.setReceiptId(receipt);
        operation.setStatus("EXECUTING");
        operation.setLevel(3);
        operation.setScope("task");
        operation.setTaskId(41L);
        operation.setSourceDataSourceId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        operation.setUpdatedAt(now.minus(Duration.ofMinutes(31)));
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc("EXECUTING", now.minus(Duration.ofMinutes(30))))
            .thenReturn(List.of(operation));
        when(repository.findForUpdate(receipt)).thenReturn(Optional.of(operation));

        RollbackExecutionWatchdogService service = new RollbackExecutionWatchdogService(
            repository,
            auditService,
            new TransactionTemplate(transactionManager),
            Duration.ofMinutes(30),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        service.markAbandonedExecutions();

        assertThat(operation.getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(operation.getFailureMessage()).isEqualTo("ROLLBACK_EXECUTION_OUTCOME_UNKNOWN_AFTER_TIMEOUT");
        verify(repository).save(operation);
        verify(auditService).recordCommitted(
            org.mockito.ArgumentMatchers.eq(receipt),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(RollbackAuditReason.ROLLBACK_FAILED),
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }
}
