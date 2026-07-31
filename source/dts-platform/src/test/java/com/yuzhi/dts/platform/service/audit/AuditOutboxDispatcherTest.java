package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ClaimedAudit;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway.AuditSubmissionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");

    @Test
    void recordedAndDuplicateResponsesShouldBothCompleteTheOutboxRow() {
        assertTerminalSuccess(AuditSubmissionResult.RECORDED);
        assertTerminalSuccess(AuditSubmissionResult.DUPLICATE);
    }

    @Test
    void idempotencyConflictShouldBeDeadLetteredWithoutRetry() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        ClaimedAudit claimed = claimedAudit();
        when(outbox.claimNext(eq(NOW), any())).thenReturn(Optional.of(claimed));
        when(gateway.submitEvent(any())).thenReturn(AuditSubmissionResult.IDEMPOTENCY_CONFLICT);
        AuditOutboxDispatcher dispatcher = dispatcher(outbox, gateway);

        AuditOutboxDispatcher.DispatchResult result = dispatcher.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("DEAD");
        verify(outbox).markDead(claimed.id(), "AUDIT_IDEMPOTENCY_CONFLICT", NOW);
        verify(outbox, never()).markRetry(any(), any(), any(), any());
    }

    @Test
    void retryableFailureShouldRemainDurableWithBackoff() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        ClaimedAudit claimed = claimedAudit();
        when(outbox.claimNext(eq(NOW), any())).thenReturn(Optional.of(claimed));
        when(gateway.submitEvent(any())).thenReturn(AuditSubmissionResult.RETRYABLE_FAILURE);
        AuditOutboxDispatcher dispatcher = dispatcher(outbox, gateway);

        AuditOutboxDispatcher.DispatchResult result = dispatcher.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("RETRY");
        verify(outbox).markRetry(eq(claimed.id()), eq("AUDIT_DELIVERY_RETRYABLE"), any(Instant.class), eq(NOW));
        verify(outbox, never()).markSent(any(), any());
    }

    private void assertTerminalSuccess(AuditSubmissionResult submissionResult) {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        ClaimedAudit claimed = claimedAudit();
        when(outbox.claimNext(eq(NOW), any())).thenReturn(Optional.of(claimed));
        when(gateway.submitEvent(any())).thenReturn(submissionResult);
        AuditOutboxDispatcher dispatcher = dispatcher(outbox, gateway);

        AuditOutboxDispatcher.DispatchResult result = dispatcher.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("SENT");
        verify(outbox).markSent(claimed.id(), NOW);
    }

    private AuditOutboxDispatcher dispatcher(PlatformAuditOutboxRepository outbox, AdminAuditGateway gateway) {
        return new AuditOutboxDispatcher(
            outbox,
            gateway,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ClaimedAudit claimedAudit() {
        return new ClaimedAudit(
            UUID.fromString("8cd274de-45c0-4fd7-b6e8-d2a22714dd2c"),
            "audit-event-1",
            "{\"eventId\":\"audit-event-1\",\"actor\":\"alice\"}",
            1
        );
    }
}
