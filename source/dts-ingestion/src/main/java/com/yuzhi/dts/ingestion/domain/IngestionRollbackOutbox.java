package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_rollback_outbox")
public class IngestionRollbackOutbox {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "operation_receipt_id", nullable = false, updatable = false)
    private UUID operationReceiptId;

    @Column(name = "event_id", nullable = false, length = 128, updatable = false)
    private String eventId;

    @Column(name = "event_hash", nullable = false, length = 64, updatable = false)
    private String eventHash;

    @Column(name = "source_sequence", nullable = false, updatable = false)
    private long sourceSequence;

    @Column(name = "outcome", nullable = false, length = 48, updatable = false)
    private String outcome;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text", updatable = false)
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOperationReceiptId() { return operationReceiptId; }
    public void setOperationReceiptId(UUID operationReceiptId) { this.operationReceiptId = operationReceiptId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public long getSourceSequence() { return sourceSequence; }
    public void setSourceSequence(long sourceSequence) { this.sourceSequence = sourceSequence; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public long getLockVersion() { return lockVersion; }
}
