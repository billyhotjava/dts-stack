package com.yuzhi.dts.platform.service.modeling.dto;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DataStandardDto {

    private UUID id;
    private String code;
    private String name;
    private String domain;
    private String scope;
    private DataStandardStatus status;
    private DataSecurityLevel securityLevel;
    private String owner;
    private List<String> tags;
    private String currentVersion;
    private String versionNotes;
    private String description;
    private String reviewCycle;
    private Instant lastReviewAt;
    private Instant createdDate;
    private String createdBy;
    private Instant lastModifiedDate;
    private String lastModifiedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public DataStandardStatus getStatus() {
        return status;
    }

    public void setStatus(DataStandardStatus status) {
        this.status = status;
    }

    public DataSecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(DataSecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getVersionNotes() {
        return versionNotes;
    }

    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReviewCycle() {
        return reviewCycle;
    }

    public void setReviewCycle(String reviewCycle) {
        this.reviewCycle = reviewCycle;
    }

    public Instant getLastReviewAt() {
        return lastReviewAt;
    }

    public void setLastReviewAt(Instant lastReviewAt) {
        this.lastReviewAt = lastReviewAt;
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

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }
}

