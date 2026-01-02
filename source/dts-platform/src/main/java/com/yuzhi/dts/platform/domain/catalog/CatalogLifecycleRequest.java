package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_lifecycle_request")
public class CatalogLifecycleRequest extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID datasetId;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "request_type", length = 32, nullable = false)
    private String requestType;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "notes", length = 1024)
    private String notes;

    @Column(name = "previous_lifecycle_status", length = 32)
    private String previousLifecycleStatus;

    @Column(name = "requested_lifecycle_status", length = 32)
    private String requestedLifecycleStatus;

    @Column(name = "previous_retention_days")
    private Integer previousRetentionDays;

    @Column(name = "requested_retention_days")
    private Integer requestedRetentionDays;

    @Column(name = "previous_expires_at")
    private Instant previousExpiresAt;

    @Column(name = "requested_expires_at")
    private Instant requestedExpiresAt;

    @Column(name = "decided_by", length = 50)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", length = 1024)
    private String decisionNotes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPreviousLifecycleStatus() {
        return previousLifecycleStatus;
    }

    public void setPreviousLifecycleStatus(String previousLifecycleStatus) {
        this.previousLifecycleStatus = previousLifecycleStatus;
    }

    public String getRequestedLifecycleStatus() {
        return requestedLifecycleStatus;
    }

    public void setRequestedLifecycleStatus(String requestedLifecycleStatus) {
        this.requestedLifecycleStatus = requestedLifecycleStatus;
    }

    public Integer getPreviousRetentionDays() {
        return previousRetentionDays;
    }

    public void setPreviousRetentionDays(Integer previousRetentionDays) {
        this.previousRetentionDays = previousRetentionDays;
    }

    public Integer getRequestedRetentionDays() {
        return requestedRetentionDays;
    }

    public void setRequestedRetentionDays(Integer requestedRetentionDays) {
        this.requestedRetentionDays = requestedRetentionDays;
    }

    public Instant getPreviousExpiresAt() {
        return previousExpiresAt;
    }

    public void setPreviousExpiresAt(Instant previousExpiresAt) {
        this.previousExpiresAt = previousExpiresAt;
    }

    public Instant getRequestedExpiresAt() {
        return requestedExpiresAt;
    }

    public void setRequestedExpiresAt(Instant requestedExpiresAt) {
        this.requestedExpiresAt = requestedExpiresAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }
}

