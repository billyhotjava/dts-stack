package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOutbox;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient.RollbackCompletionDeliveryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RollbackCompletionOutboxService {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackCompletionOutboxService.class);
    private static final int MAX_ATTEMPTS = 12;

    private final IngestionRollbackOutboxRepository outboxRepository;
    private final IngestionRollbackOperationRepository operationRepository;
    private final PlatformInfraClient platformInfraClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final LongUnaryOperator jitterSource;

    @Autowired
    public RollbackCompletionOutboxService(
        IngestionRollbackOutboxRepository outboxRepository,
        IngestionRollbackOperationRepository operationRepository,
        PlatformInfraClient platformInfraClient,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this(
            outboxRepository,
            operationRepository,
            platformInfraClient,
            objectMapper,
            transactionManager,
            Clock.systemUTC(),
            bound -> ThreadLocalRandom.current().nextLong(bound)
        );
    }

    RollbackCompletionOutboxService(
        IngestionRollbackOutboxRepository outboxRepository,
        IngestionRollbackOperationRepository operationRepository,
        PlatformInfraClient platformInfraClient,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        Clock clock,
        LongUnaryOperator jitterSource
    ) {
        this.outboxRepository = outboxRepository;
        this.operationRepository = operationRepository;
        this.platformInfraClient = platformInfraClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.jitterSource = java.util.Objects.requireNonNull(jitterSource, "jitterSource");
    }

    @Scheduled(
        initialDelayString = "${dts.ingestion.rollback-outbox-initial-delay-ms:5000}",
        fixedDelayString = "${dts.ingestion.rollback-outbox-poll-ms:5000}"
    )
    public void dispatchPending() {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(ignored ->
            outboxRepository.releaseStaleClaims(now.minus(Duration.ofMinutes(5)), now)
        );
        List<IngestionRollbackOutbox> due = transactionTemplate.execute(ignored ->
            outboxRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                Set.of("PENDING", "RETRY"),
                now
            )
        );
        if (due == null) {
            return;
        }
        due.forEach(this::dispatchOne);
    }

    void dispatchOne(IngestionRollbackOutbox candidate) {
        Instant claimedAt = clock.instant();
        Integer claimed = transactionTemplate.execute(ignored -> outboxRepository.claim(candidate.getId(), claimedAt));
        if (claimed == null || claimed == 0) {
            return;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                candidate.getPayloadJson(),
                new TypeReference<Map<String, Object>>() {}
            );
            platformInfraClient.completeRollbackInvalidation(payload);
            transactionTemplate.executeWithoutResult(ignored -> markSent(candidate.getId()));
        } catch (Exception failure) {
            transactionTemplate.executeWithoutResult(ignored -> markFailed(candidate.getId(), failure));
        }
    }

    private void markSent(java.util.UUID eventId) {
        IngestionRollbackOutbox event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("ROLLBACK_OUTBOX_EVENT_NOT_FOUND: " + eventId));
        Instant now = clock.instant();
        event.setStatus("SENT");
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(null);
        event.setSentAt(now);
        event.setUpdatedAt(now);
        outboxRepository.save(event);
        operationRepository.findForUpdate(event.getOperationReceiptId()).ifPresent(operation -> {
            operation.setStatus(switch (event.getOutcome()) {
                case RollbackSagaService.OUTCOME_APPLY -> "COMPLETED";
                case "RESTORE" -> "RESTORED";
                default -> "ABORTED";
            });
            operation.setUpdatedAt(now);
            operationRepository.save(operation);
        });
    }

    private void markFailed(java.util.UUID eventId, Exception failure) {
        IngestionRollbackOutbox event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("ROLLBACK_OUTBOX_EVENT_NOT_FOUND: " + eventId));
        int attempts = event.getAttemptCount() + 1;
        boolean retryable = isRetryable(failure) && attempts < MAX_ATTEMPTS;
        Instant now = clock.instant();
        event.setAttemptCount(attempts);
        event.setStatus(retryable ? "RETRY" : "DEAD");
        event.setLastError(bound(failure.getMessage(), 4096));
        event.setNextAttemptAt(retryable ? now.plus(backoff(attempts)) : now);
        event.setUpdatedAt(now);
        outboxRepository.save(event);
        if (!retryable) {
            operationRepository.findForUpdate(event.getOperationReceiptId()).ifPresent(operation -> {
                operation.setStatus("RECONCILIATION_REQUIRED");
                operation.setFailureMessage(bound(failure.getMessage(), 4096));
                operation.setUpdatedAt(now);
                operationRepository.save(operation);
            });
        }
        LOG.warn(
            "event=rollback_completion_delivery_failed receiptId={} eventId={} attempts={} retryable={} error={}",
            event.getOperationReceiptId(),
            event.getEventId(),
            attempts,
            retryable,
            failure.getMessage()
        );
    }

    private boolean isRetryable(Exception failure) {
        if (failure instanceof RollbackCompletionDeliveryException delivery) {
            return delivery.statusCode() == 0 || delivery.statusCode() == 429 || delivery.statusCode() >= 500;
        }
        return true;
    }

    private Duration backoff(int attempts) {
        long ceilingSeconds = Math.min(900L, 5L << Math.min(attempts - 1, 8));
        long floorSeconds = ceilingSeconds / 2;
        long range = ceilingSeconds - floorSeconds + 1;
        long jitterSeconds = jitterSource.applyAsLong(range);
        if (jitterSeconds < 0 || jitterSeconds >= range) {
            throw new IllegalStateException("rollback completion jitter source returned an out-of-range value");
        }
        return Duration.ofSeconds(floorSeconds + jitterSeconds);
    }

    private String bound(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
