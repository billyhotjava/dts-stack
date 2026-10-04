package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_rollback_operation")
public class IngestionRollbackOperation {

    @Id
    @Column(name = "receipt_id", nullable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;

    @Column(name = "level", nullable = false, updatable = false)
    private int level;

    @Column(name = "scope", nullable = false, length = 32, updatable = false)
    private String scope;

    @Column(name = "task_id", updatable = false)
    private Long taskId;

    @Column(name = "source_data_source_id", nullable = false, updatable = false)
    private UUID sourceDataSourceId;

    @Column(name = "fence_sequence", nullable = false, updatable = false)
    private long fenceSequence;

    @Column(name = "completion_sequence")
    private Long completionSequence;

    @Column(name = "operator", nullable = false, length = 128, updatable = false)
    private String operator;

    @Column(name = "status", nullable = false, length = 48)
    private String status;

    @Column(name = "outcome", length = 48)
    private String outcome;

    @Column(name = "side_effects_applied", nullable = false)
    private boolean sideEffectsApplied;

    @Column(name = "request_json", nullable = false, columnDefinition = "text", updatable = false)
    private String requestJson;

    @Column(name = "impact_json", nullable = false, columnDefinition = "text", updatable = false)
    private String impactJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "completion_event_id", length = 128)
    private String completionEventId;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public UUID getSourceDataSourceId() { return sourceDataSourceId; }
    public void setSourceDataSourceId(UUID sourceDataSourceId) { this.sourceDataSourceId = sourceDataSourceId; }
    public long getFenceSequence() { return fenceSequence; }
    public void setFenceSequence(long fenceSequence) { this.fenceSequence = fenceSequence; }
    public Long getCompletionSequence() { return completionSequence; }
    public void setCompletionSequence(Long completionSequence) { this.completionSequence = completionSequence; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public boolean isSideEffectsApplied() { return sideEffectsApplied; }
    public void setSideEffectsApplied(boolean sideEffectsApplied) { this.sideEffectsApplied = sideEffectsApplied; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public String getImpactJson() { return impactJson; }
    public void setImpactJson(String impactJson) { this.impactJson = impactJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getCompletionEventId() { return completionEventId; }
    public void setCompletionEventId(String completionEventId) { this.completionEventId = completionEventId; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getLockVersion() { return lockVersion; }
}
