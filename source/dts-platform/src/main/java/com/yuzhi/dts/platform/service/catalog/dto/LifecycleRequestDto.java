package com.yuzhi.dts.platform.service.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public class LifecycleRequestDto {

    private UUID id;
    private UUID datasetId;
    private String datasetName;
    private String ownerDept;
    private String requestType;
    private String status;
    private String notes;
    private String previousLifecycleStatus;
    private String requestedLifecycleStatus;
    private Integer requestedRetentionDays;
    private Instant requestedExpiresAt;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionNotes;
    private String createdBy;
    private Instant createdDate;

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

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
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

    public Integer getRequestedRetentionDays() {
        return requestedRetentionDays;
    }

    public void setRequestedRetentionDays(Integer requestedRetentionDays) {
        this.requestedRetentionDays = requestedRetentionDays;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
}

