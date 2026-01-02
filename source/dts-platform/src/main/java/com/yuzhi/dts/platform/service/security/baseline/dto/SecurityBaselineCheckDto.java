package com.yuzhi.dts.platform.service.security.baseline.dto;

import java.time.Instant;

public class SecurityBaselineCheckDto {

    private String checkKey;
    private String title;
    private String category;
    private String severity;
    private String type;
    private String description;
    private String expected;
    private String remediationStatus;
    private String remediationNotes;
    private Instant lastUpdatedAt;
    private String lastUpdatedBy;

    public String getCheckKey() {
        return checkKey;
    }

    public void setCheckKey(String checkKey) {
        this.checkKey = checkKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getRemediationStatus() {
        return remediationStatus;
    }

    public void setRemediationStatus(String remediationStatus) {
        this.remediationStatus = remediationStatus;
    }

    public String getRemediationNotes() {
        return remediationNotes;
    }

    public void setRemediationNotes(String remediationNotes) {
        this.remediationNotes = remediationNotes;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
}

