package com.yuzhi.dts.ingestion.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditGateway.Outcome;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditGateway.SubmissionResult;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository.ClaimedEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionSecretRestoreAuditDispatcherTest {

    @Mock private IngestionSecretRestoreAuditOutboxRepository outbox;
    @Mock private IngestionSecretRestoreAuditGateway gateway;

    @Test
    void shouldAcknowledgeRecordedCentralAuditEvent() {
        UUID id = UUID.randomUUID();
        when(outbox.claimNext(any())).thenReturn(Optional.of(new ClaimedEvent(id, "event-1", "{}", 1)));
        when(gateway.submit("event-1", "{}")).thenReturn(SubmissionResult.recorded());

        var result = new IngestionSecretRestoreAuditDispatcher(outbox, gateway).dispatchNext().orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.RECORDED);
        verify(outbox).markDelivered(id, 1);
    }

    @Test
    void shouldRetainRetryableEventInDurableOutbox() {
        UUID id = UUID.randomUUID();
        when(outbox.claimNext(any())).thenReturn(Optional.of(new ClaimedEvent(id, "event-2", "{}", 2)));
        when(gateway.submit("event-2", "{}")).thenReturn(SubmissionResult.retryable("HTTP_503"));

        var result = new IngestionSecretRestoreAuditDispatcher(outbox, gateway).dispatchNext().orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.RETRYABLE);
        verify(outbox).markRetry(eq(id), eq(2), any(), eq("HTTP_503"));
    }

    @Test
    void shouldFencePermanentFailureWithClaimGeneration() {
        UUID id = UUID.randomUUID();
        when(outbox.claimNext(any())).thenReturn(Optional.of(new ClaimedEvent(id, "event-3", "{}", 4)));
        when(gateway.submit("event-3", "{}")).thenReturn(SubmissionResult.permanent("HTTP_422"));

        var result = new IngestionSecretRestoreAuditDispatcher(outbox, gateway).dispatchNext().orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.PERMANENT);
        verify(outbox).markDead(id, 4, "HTTP_422");
    }
}
