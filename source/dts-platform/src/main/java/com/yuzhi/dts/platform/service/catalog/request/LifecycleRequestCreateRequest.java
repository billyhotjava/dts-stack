package com.yuzhi.dts.platform.service.catalog.request;

import java.time.Instant;
import java.util.UUID;

public class LifecycleRequestCreateRequest {

    private UUID datasetId;
    private String requestType;
    private String notes;
    private Integer retentionDays;
    private Instant expiresAt;

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}

