package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOutbox;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RollbackCompletionOutboxServiceTest {

    private final IngestionRollbackOutboxRepository outboxRepository = mock(IngestionRollbackOutboxRepository.class);
    private final IngestionRollbackOperationRepository operationRepository = mock(
        IngestionRollbackOperationRepository.class
    );
    private final PlatformInfraClient platformInfraClient = mock(PlatformInfraClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private RollbackCompletionOutboxService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        service = new RollbackCompletionOutboxService(
            outboxRepository,
            operationRepository,
            platformInfraClient,
            new ObjectMapper(),
            transactionManager
        );
    }

    @Test
    void successfulDeliveryCompletesTheOperation() {
        IngestionRollbackOutbox event = event();
        IngestionRollbackOperation operation = operation();
        when(outboxRepository.claim(any(), any())).thenReturn(1);
        when(outboxRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(operationRepository.findForUpdate(event.getOperationReceiptId())).thenReturn(Optional.of(operation));

        service.dispatchOne(event);

        assertThat(event.getStatus()).isEqualTo("SENT");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(operation.getStatus()).isEqualTo("COMPLETED");
        verify(platformInfraClient).completeRollbackInvalidation(any());
    }

    @Test
    void springCreatesServiceUsingTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IngestionRollbackOutboxRepository.class, () -> outboxRepository);
            context.registerBean(IngestionRollbackOperationRepository.class, () -> operationRepository);
            context.registerBean(PlatformInfraClient.class, () -> platformInfraClient);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
            context.registerBean(RollbackCompletionOutboxService.class);

            context.refresh();

            assertThat(context.getBean(RollbackCompletionOutboxService.class)).isNotNull();
        }
    }

    @Test
    void nonRetryableDeliveryFailureRequiresReconciliation() {
        IngestionRollbackOutbox event = event();
        IngestionRollbackOperation operation = operation();
        when(outboxRepository.claim(any(), any())).thenReturn(1);
        when(outboxRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(operationRepository.findForUpdate(event.getOperationReceiptId())).thenReturn(Optional.of(operation));
        doThrow(new PlatformInfraClient.RollbackCompletionDeliveryException(400, "invalid completion"))
            .when(platformInfraClient)
            .completeRollbackInvalidation(any());

        service.dispatchOne(event);

        assertThat(event.getStatus()).isEqualTo("DEAD");
        assertThat(operation.getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(operation.getFailureMessage()).contains("invalid completion");
    }

    @Test
    void retryBackoffUsesBoundedEqualJitterToDesynchronizeWorkers() {
        Instant now = Instant.parse("2026-08-01T02:03:04Z");
        IngestionRollbackOutbox minimumDelay = event();
        when(outboxRepository.claim(any(), any())).thenReturn(1);
        when(outboxRepository.findById(minimumDelay.getId())).thenReturn(Optional.of(minimumDelay));
        doThrow(new PlatformInfraClient.RollbackCompletionDeliveryException(503, "temporarily unavailable"))
            .when(platformInfraClient)
            .completeRollbackInvalidation(any());
        service = serviceAt(now, ignored -> 0L);

        service.dispatchOne(minimumDelay);

        assertThat(minimumDelay.getStatus()).isEqualTo("RETRY");
        assertThat(minimumDelay.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));

        IngestionRollbackOutbox maximumDelay = event();
        when(outboxRepository.findById(maximumDelay.getId())).thenReturn(Optional.of(maximumDelay));
        service = serviceAt(now, upperBound -> upperBound - 1);

        service.dispatchOne(maximumDelay);

        assertThat(maximumDelay.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
    }

    private RollbackCompletionOutboxService serviceAt(Instant now, java.util.function.LongUnaryOperator jitterSource) {
        return new RollbackCompletionOutboxService(
            outboxRepository,
            operationRepository,
            platformInfraClient,
            new ObjectMapper(),
            transactionManager,
            Clock.fixed(now, ZoneOffset.UTC),
            jitterSource
        );
    }

    private IngestionRollbackOutbox event() {
        UUID receipt = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant now = Instant.now();
        IngestionRollbackOutbox event = new IngestionRollbackOutbox();
        event.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        event.setOperationReceiptId(receipt);
        event.setEventId("rollback:" + receipt + ":8");
        event.setEventHash("0".repeat(64));
        event.setSourceSequence(8L);
        event.setOutcome(RollbackSagaService.OUTCOME_APPLY);
        event.setPayloadJson("{\"receiptId\":\"" + receipt + "\",\"outcome\":\"APPLY\"}");
        event.setStatus("PENDING");
        event.setAttemptCount(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private IngestionRollbackOperation operation() {
        IngestionRollbackOperation operation = new IngestionRollbackOperation();
        operation.setStatus("COMPLETION_PENDING");
        operation.setUpdatedAt(Instant.now());
        return operation;
    }
}
