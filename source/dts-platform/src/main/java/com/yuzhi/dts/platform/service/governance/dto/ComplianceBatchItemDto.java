package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.UUID;

public class ComplianceBatchItemDto {

    private UUID id;
    private UUID batchId;
    private UUID ruleId;
    private UUID ruleVersionId;
    private UUID datasetId;
    private UUID qualityRunId;
    private String status;
    private String severity;
    private String conclusion;
    private String evidenceRef;
    private Instant createdDate;
    private String createdBy;
    private String ruleName;
    private String ruleCode;
    private Integer ruleVersion;
    private String ruleSeverity;
    private String datasetAlias;
    private String qualityRunStatus;
    private Instant qualityRunStartedAt;
    private Instant qualityRunFinishedAt;
    private Long qualityRunDurationMs;
    private String qualityRunMessage;
    private Instant lastUpdated;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(UUID ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getQualityRunId() {
        return qualityRunId;
    }

    public void setQualityRunId(UUID qualityRunId) {
        this.qualityRunId = qualityRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public void setEvidenceRef(String evidenceRef) {
        this.evidenceRef = evidenceRef;
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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(Integer ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getRuleSeverity() {
        return ruleSeverity;
    }

    public void setRuleSeverity(String ruleSeverity) {
        this.ruleSeverity = ruleSeverity;
    }

    public String getDatasetAlias() {
        return datasetAlias;
    }

    public void setDatasetAlias(String datasetAlias) {
        this.datasetAlias = datasetAlias;
    }

    public String getQualityRunStatus() {
        return qualityRunStatus;
    }

    public void setQualityRunStatus(String qualityRunStatus) {
        this.qualityRunStatus = qualityRunStatus;
    }

    public Instant getQualityRunStartedAt() {
        return qualityRunStartedAt;
    }

    public void setQualityRunStartedAt(Instant qualityRunStartedAt) {
        this.qualityRunStartedAt = qualityRunStartedAt;
    }

    public Instant getQualityRunFinishedAt() {
        return qualityRunFinishedAt;
    }

    public void setQualityRunFinishedAt(Instant qualityRunFinishedAt) {
        this.qualityRunFinishedAt = qualityRunFinishedAt;
    }

    public Long getQualityRunDurationMs() {
        return qualityRunDurationMs;
    }

    public void setQualityRunDurationMs(Long qualityRunDurationMs) {
        this.qualityRunDurationMs = qualityRunDurationMs;
    }

    public String getQualityRunMessage() {
        return qualityRunMessage;
    }

    public void setQualityRunMessage(String qualityRunMessage) {
        this.qualityRunMessage = qualityRunMessage;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
