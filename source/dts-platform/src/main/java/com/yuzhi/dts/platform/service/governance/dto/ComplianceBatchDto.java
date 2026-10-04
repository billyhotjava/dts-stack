package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ComplianceBatchDto {

    private UUID id;
    private String name;
    private String templateCode;
    private String status;
    private Integer progressPct;
    private Boolean evidenceRequired;
    private String dataLevel;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String triggeredBy;
    private String triggeredType;
    private String ownerDept;
    private String summary;
    private String metadataJson;
    private Instant createdDate;
    private String createdBy;
    private Integer totalItems;
    private Integer completedItems;
    private Integer passedItems;
    private Integer failedItems;
    private Integer pendingItems;
    private Boolean hasFailure;
    private Instant lastUpdated;
    private List<ComplianceBatchItemDto> items;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(Integer progressPct) {
        this.progressPct = progressPct;
    }

    public Boolean getEvidenceRequired() {
        return evidenceRequired;
    }

    public void setEvidenceRequired(Boolean evidenceRequired) {
        this.evidenceRequired = evidenceRequired;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getTriggeredType() {
        return triggeredType;
    }

    public void setTriggeredType(String triggeredType) {
        this.triggeredType = triggeredType;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public Integer getCompletedItems() {
        return completedItems;
    }

    public void setCompletedItems(Integer completedItems) {
        this.completedItems = completedItems;
    }

    public Integer getPassedItems() {
        return passedItems;
    }

    public void setPassedItems(Integer passedItems) {
        this.passedItems = passedItems;
    }

    public Integer getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(Integer failedItems) {
        this.failedItems = failedItems;
    }

    public Integer getPendingItems() {
        return pendingItems;
    }

    public void setPendingItems(Integer pendingItems) {
        this.pendingItems = pendingItems;
    }

    public Boolean getHasFailure() {
        return hasFailure;
    }

    public void setHasFailure(Boolean hasFailure) {
        this.hasFailure = hasFailure;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<ComplianceBatchItemDto> getItems() {
        return items;
    }

    public void setItems(List<ComplianceBatchItemDto> items) {
        this.items = items;
    }
}
