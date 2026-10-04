package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditIngestIdempotencyService {

    public enum IngestStatus {
        RECORDED,
        DUPLICATE,
        IDEMPOTENCY_CONFLICT,
    }

    private final AuditEntryRepository repository;
    private final AuditIngestPersistenceService persistence;
    private final AuditIngestFingerprint fingerprint;

    public AuditIngestIdempotencyService(
        AuditEntryRepository repository,
        AuditIngestPersistenceService persistence,
        AuditIngestFingerprint fingerprint
    ) {
        this.repository = repository;
        this.persistence = persistence;
        this.fingerprint = fingerprint;
    }

    public IngestResult record(String authenticatedProducer, Map<String, Object> body, AuditActionRequest request) {
        String eventId = fingerprint.eventId(body);
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("audit eventId is required");
        }

        String producer = normalizeProducer(authenticatedProducer);
        String payloadHash = fingerprint.payloadHash(body);
        Optional<AuditEntry> existing = repository.findByIngestProducerAndIngestEventId(producer, eventId);
        if (existing.isPresent()) {
            return classify(existing.orElseThrow(), eventId, payloadHash);
        }

        try {
            persistence.persistIdempotent(producer, eventId, payloadHash, request);
            return new IngestResult(IngestStatus.RECORDED, eventId);
        } catch (DataIntegrityViolationException race) {
            AuditEntry winner = repository
                .findByIngestProducerAndIngestEventId(producer, eventId)
                .orElseThrow(() -> race);
            return classify(winner, eventId, payloadHash);
        }
    }

    private IngestResult classify(AuditEntry existing, String eventId, String payloadHash) {
        IngestStatus status = payloadHash.equals(existing.getIngestPayloadHash())
            ? IngestStatus.DUPLICATE
            : IngestStatus.IDEMPOTENCY_CONFLICT;
        return new IngestResult(status, eventId);
    }

    private String normalizeProducer(String producer) {
        if (!StringUtils.hasText(producer)) {
            throw new IllegalArgumentException("authenticated audit producer is required");
        }
        String normalized = producer.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("authenticated audit producer exceeds 64 characters");
        }
        return normalized;
    }

    public record IngestResult(IngestStatus status, String eventId) {}
}
