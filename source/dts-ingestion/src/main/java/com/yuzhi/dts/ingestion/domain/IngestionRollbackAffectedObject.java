package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_rollback_affected_object")
public class IngestionRollbackAffectedObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_receipt_id", nullable = false, updatable = false)
    private UUID operationReceiptId;

    @Column(name = "object_type", nullable = false, length = 48, updatable = false)
    private String objectType;

    @Column(name = "object_ref", nullable = false, length = 1024, updatable = false)
    private String objectRef;

    @Column(name = "action", nullable = false, length = 48, updatable = false)
    private String action;

    @Column(name = "phase", nullable = false, length = 32, updatable = false)
    private String phase;

    @Column(name = "status", nullable = false, length = 32, updatable = false)
    private String status;

    @Column(name = "evidence_json", columnDefinition = "text", updatable = false)
    private String evidenceJson;

    @Column(name = "evidence_hash", nullable = false, length = 64, updatable = false)
    private String evidenceHash;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public Long getId() { return id; }
    public UUID getOperationReceiptId() { return operationReceiptId; }
    public void setOperationReceiptId(UUID operationReceiptId) { this.operationReceiptId = operationReceiptId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
