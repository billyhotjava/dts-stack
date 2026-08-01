package com.yuzhi.dts.ingestion.service.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable, endpoint-specific audit ledger and delivery outbox for credential compatibility restore. */
@Repository
public class IngestionSecretRestoreAuditOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public IngestionSecretRestoreAuditOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID enqueue(EnqueueCommand command) {
        int inserted = jdbcTemplate.update(
            "INSERT INTO ingestion_secret_restore_audit_outbox(" +
                "id, event_id, tenant_id, actor, event_type, classification, batch_id, task_id, " +
                "revision_id, config_checksum, outcome, error_code, payload_hash, payload_json, " +
                "delivery_status, delivery_attempts, next_attempt_at, created_at, modified_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, " +
                "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (event_id) DO NOTHING",
            command.id(),
            command.eventId(),
            command.tenantId(),
            command.actor(),
            command.eventType(),
            command.classification(),
            command.batchId(),
            command.taskId(),
            command.revisionId(),
            command.configChecksum(),
            command.outcome(),
            command.errorCode(),
            command.payloadHash(),
            command.payloadJson()
        );
        if (inserted == 1) {
            return command.id();
        }
        List<ExistingEvent> existing = jdbcTemplate.query(
            "SELECT id, tenant_id, payload_hash FROM ingestion_secret_restore_audit_outbox WHERE event_id=?",
            (rows, rowNum) -> new ExistingEvent(
                rows.getObject(1, UUID.class),
                rows.getString(2),
                rows.getString(3)
            ),
            command.eventId()
        );
        if (existing.size() != 1
            || !command.tenantId().equals(existing.get(0).tenantId())
            || !command.payloadHash().equals(existing.get(0).payloadHash())) {
            throw new IllegalStateException("INGESTION_SECRET_RESTORE_AUDIT_IDEMPOTENCY_CONFLICT");
        }
        return existing.get(0).id();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedEvent> claimNext(Instant staleBefore) {
        List<ClaimedEvent> claimed = jdbcTemplate.query(
            "WITH candidate AS (" +
                " SELECT id FROM ingestion_secret_restore_audit_outbox" +
                " WHERE ((delivery_status IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP)" +
                " OR (delivery_status='CLAIMED' AND claimed_at<?))" +
                " ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1" +
                ") UPDATE ingestion_secret_restore_audit_outbox event" +
                " SET delivery_status='CLAIMED', claimed_at=CURRENT_TIMESTAMP," +
                " delivery_attempts=event.delivery_attempts+1, modified_at=CURRENT_TIMESTAMP" +
                " FROM candidate WHERE event.id=candidate.id" +
                " RETURNING event.id, event.event_id, event.payload_json, event.delivery_attempts",
            (rows, rowNum) -> new ClaimedEvent(
                rows.getObject(1, UUID.class),
                rows.getString(2),
                rows.getString(3),
                rows.getInt(4)
            ),
            Timestamp.from(staleBefore)
        );
        return claimed.stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDelivered(UUID id, int expectedDeliveryAttempts) {
        requireClaimGenerationUpdate(
            jdbcTemplate.update(
                "UPDATE ingestion_secret_restore_audit_outbox SET delivery_status='DELIVERED', " +
                    "delivered_at=CURRENT_TIMESTAMP, claimed_at=NULL, last_error=NULL, modified_at=CURRENT_TIMESTAMP " +
                    "WHERE id=? AND delivery_status='CLAIMED' AND delivery_attempts=?",
                id,
                expectedDeliveryAttempts
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID id, int expectedDeliveryAttempts, Instant nextAttemptAt, String errorCode) {
        requireClaimGenerationUpdate(
            jdbcTemplate.update(
                "UPDATE ingestion_secret_restore_audit_outbox SET delivery_status='RETRY', claimed_at=NULL, " +
                    "next_attempt_at=?, last_error=?, modified_at=CURRENT_TIMESTAMP " +
                    "WHERE id=? AND delivery_status='CLAIMED' AND delivery_attempts=?",
                Timestamp.from(nextAttemptAt),
                errorCode,
                id,
                expectedDeliveryAttempts
            )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDead(UUID id, int expectedDeliveryAttempts, String errorCode) {
        requireClaimGenerationUpdate(
            jdbcTemplate.update(
                "UPDATE ingestion_secret_restore_audit_outbox SET delivery_status='DEAD', claimed_at=NULL, " +
                    "last_error=?, modified_at=CURRENT_TIMESTAMP " +
                    "WHERE id=? AND delivery_status='CLAIMED' AND delivery_attempts=?",
                errorCode,
                id,
                expectedDeliveryAttempts
            )
        );
    }

    private void requireClaimGenerationUpdate(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("INGESTION_SECRET_RESTORE_AUDIT_STALE_CLAIM");
        }
    }

    public record EnqueueCommand(
        UUID id,
        String eventId,
        String tenantId,
        String actor,
        String eventType,
        String classification,
        String batchId,
        Long taskId,
        Long revisionId,
        String configChecksum,
        String outcome,
        String errorCode,
        String payloadHash,
        String payloadJson
    ) {}

    public record ClaimedEvent(UUID id, String eventId, String payloadJson, int deliveryAttempts) {}

    private record ExistingEvent(UUID id, String tenantId, String payloadHash) {}
}
