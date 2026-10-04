package com.yuzhi.dts.ingestion.service.audit;

import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditGateway.Outcome;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditGateway.SubmissionResult;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository.ClaimedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Asynchronously forwards durable restore audit facts to the central admin audit ledger. */
@Service
public class IngestionSecretRestoreAuditDispatcher {

    private static final int MAX_BATCH = 20;
    private static final int MAX_ATTEMPTS = 20;

    private final IngestionSecretRestoreAuditOutboxRepository outbox;
    private final IngestionSecretRestoreAuditGateway gateway;

    public IngestionSecretRestoreAuditDispatcher(
        IngestionSecretRestoreAuditOutboxRepository outbox,
        IngestionSecretRestoreAuditGateway gateway
    ) {
        this.outbox = outbox;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${dts.ingestion.secret-restore-audit.dispatch-interval-ms:5000}")
    public void dispatchPending() {
        for (int index = 0; index < MAX_BATCH; index++) {
            if (dispatchNext().isEmpty()) {
                return;
            }
        }
    }

    public Optional<DispatchResult> dispatchNext() {
        Optional<ClaimedEvent> claimed = outbox.claimNext(Instant.now().minus(Duration.ofMinutes(5)));
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        ClaimedEvent event = claimed.orElseThrow();
        SubmissionResult submission = gateway.submit(event.eventId(), event.payloadJson());
        if (submission.outcome() == Outcome.RECORDED || submission.outcome() == Outcome.DUPLICATE) {
            outbox.markDelivered(event.id(), event.deliveryAttempts());
        } else if (submission.outcome() == Outcome.PERMANENT || event.deliveryAttempts() >= MAX_ATTEMPTS) {
            outbox.markDead(event.id(), event.deliveryAttempts(), submission.errorCode());
        } else {
            outbox.markRetry(
                event.id(),
                event.deliveryAttempts(),
                Instant.now().plus(backoff(event.deliveryAttempts())),
                submission.errorCode()
            );
        }
        return Optional.of(new DispatchResult(event.id(), submission.outcome(), submission.errorCode()));
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(300L, 5L << Math.min(Math.max(attempts - 1, 0), 6));
        return Duration.ofSeconds(seconds);
    }

    public record DispatchResult(java.util.UUID id, Outcome outcome, String errorCode) {}
}
