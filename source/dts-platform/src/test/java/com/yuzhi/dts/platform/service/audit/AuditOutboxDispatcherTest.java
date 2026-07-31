package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
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
        verify(outbox).markDead(claimed.id(), claimed.attempts(), "AUDIT_IDEMPOTENCY_CONFLICT", NOW);
        verify(outbox, never()).markRetry(any(), anyInt(), any(), any(), any());
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
        verify(outbox)
            .markRetry(
                eq(claimed.id()),
                eq(claimed.attempts()),
                eq("AUDIT_DELIVERY_RETRYABLE"),
                any(Instant.class),
                eq(NOW)
            );
        verify(outbox, never()).markSent(any(), anyInt(), any());
    }

    @Test
    void tamperedPayloadShouldBeDeadLetteredBeforeRemoteDelivery() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        ClaimedAudit claimed = new ClaimedAudit(
            UUID.fromString("8cd274de-45c0-4fd7-b6e8-d2a22714dd2c"),
            "audit-event-1",
            "dts-platform",
            "0".repeat(64),
            "{\"eventId\":\"audit-event-1\",\"producer\":\"dts-platform\",\"actor\":\"alice\"}",
            1
        );
        when(outbox.claimNext(eq(NOW), any())).thenReturn(Optional.of(claimed));
        AuditOutboxDispatcher dispatcher = dispatcher(outbox, gateway);

        AuditOutboxDispatcher.DispatchResult result = dispatcher.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("DEAD");
        verify(outbox).markDead(claimed.id(), claimed.attempts(), "AUDIT_PAYLOAD_INTEGRITY_FAILED", NOW);
        verify(gateway, never()).submitEvent(any());
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
        verify(outbox).markSent(claimed.id(), claimed.attempts(), NOW);
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
        String body = "{\"eventId\":\"audit-event-1\",\"producer\":\"dts-platform\",\"actor\":\"alice\"}";
        return new ClaimedAudit(
            UUID.fromString("8cd274de-45c0-4fd7-b6e8-d2a22714dd2c"),
            "audit-event-1",
            "dts-platform",
            sha256(body),
            body,
            1
        );
    }

    private String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
