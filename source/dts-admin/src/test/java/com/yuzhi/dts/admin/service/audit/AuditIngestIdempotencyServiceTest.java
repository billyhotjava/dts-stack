package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuditIngestIdempotencyServiceTest {

    private static final String PRODUCER = "dts-platform";
    private static final String EVENT_ID = "audit-event-81-0001";

    private final AuditEntryRepository repository = mock(AuditEntryRepository.class);
    private final AuditIngestPersistenceService persistence = mock(AuditIngestPersistenceService.class);
    private final AuditIngestFingerprint fingerprint = new AuditIngestFingerprint(new ObjectMapper());
    private final AuditIngestIdempotencyService service = new AuditIngestIdempotencyService(
        repository,
        persistence,
        fingerprint
    );

    @Test
    void firstIdempotentEventIsRecorded() {
        AuditActionRequest request = request();
        when(repository.findByIngestProducerAndIngestEventId(PRODUCER, EVENT_ID)).thenReturn(Optional.empty());

        AuditIngestIdempotencyService.IngestResult result = service.record(PRODUCER, body(), request);

        assertThat(result.status()).isEqualTo(AuditIngestIdempotencyService.IngestStatus.RECORDED);
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        verify(persistence).persistIdempotent(eq(PRODUCER), eq(EVENT_ID), any(String.class), eq(request));
    }

    @Test
    void committedReplayWithSameHashIsDuplicate() {
        String hash = fingerprint.payloadHash(body());
        AuditEntry existing = existing(hash);
        when(repository.findByIngestProducerAndIngestEventId(PRODUCER, EVENT_ID)).thenReturn(Optional.of(existing));

        AuditIngestIdempotencyService.IngestResult result = service.record(PRODUCER, body(), request());

        assertThat(result.status()).isEqualTo(AuditIngestIdempotencyService.IngestStatus.DUPLICATE);
        verify(persistence, never()).persistIdempotent(any(), any(), any(), any());
    }

    @Test
    void committedReplayWithDifferentHashIsConflict() {
        AuditEntry existing = existing("0".repeat(64));
        when(repository.findByIngestProducerAndIngestEventId(PRODUCER, EVENT_ID)).thenReturn(Optional.of(existing));

        AuditIngestIdempotencyService.IngestResult result = service.record(PRODUCER, body(), request());

        assertThat(result.status()).isEqualTo(AuditIngestIdempotencyService.IngestStatus.IDEMPOTENCY_CONFLICT);
        verify(persistence, never()).persistIdempotent(any(), any(), any(), any());
    }

    @Test
    void uniqueConstraintRaceIsResolvedAgainstCommittedWinner() {
        String hash = fingerprint.payloadHash(body());
        AuditEntry winner = existing(hash);
        when(repository.findByIngestProducerAndIngestEventId(PRODUCER, EVENT_ID))
            .thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("unique conflict"))
            .when(persistence)
            .persistIdempotent(eq(PRODUCER), eq(EVENT_ID), eq(hash), any(AuditActionRequest.class));

        AuditIngestIdempotencyService.IngestResult result = service.record(PRODUCER, body(), request());

        assertThat(result.status()).isEqualTo(AuditIngestIdempotencyService.IngestStatus.DUPLICATE);
    }

    @Test
    void missingEventIdIsRejectedInsteadOfFallingBackToNonIdempotentPersistence() {
        Map<String, Object> bodyWithoutEventId = Map.of("summary", "创建模型");

        assertThatThrownBy(() -> service.record(PRODUCER, bodyWithoutEventId, request()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventId");
        verify(persistence, never()).persistIdempotent(any(), any(), any(), any());
    }

    private AuditEntry existing(String hash) {
        AuditEntry entry = new AuditEntry();
        entry.setIngestProducer(PRODUCER);
        entry.setIngestEventId(EVENT_ID);
        entry.setIngestPayloadHash(hash);
        return entry;
    }

    private Map<String, Object> body() {
        return Map.of(
            "eventId",
            EVENT_ID,
            "producer",
            "forged-producer",
            "summary",
            "创建模型",
            "metadata",
            Map.of("tenantId", "tenant-a")
        );
    }

    private AuditActionRequest request() {
        return AuditActionRequest
            .builder("alice", "MODEL_SPEC_CREATE")
            .sourceSystem("platform")
            .moduleOverride("modeling", "数据建模")
            .operationOverride("MODEL_SPEC_CREATE", "创建模型", AuditOperationKind.CREATE)
            .target("MODEL_SPEC", "model-81", null)
            .build();
    }
}
