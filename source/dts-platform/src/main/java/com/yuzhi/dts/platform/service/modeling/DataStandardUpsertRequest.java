package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersionStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public class DataStandardUpsertRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String domain;
    private String scope;
    private DataStandardStatus status;
    private DataSecurityLevel securityLevel;
    private String owner;
    private List<String> tags;
    private String version;
    private String versionNotes;
    private String changeSummary;
    private DataStandardVersionStatus versionStatus;
    private String description;
    private String reviewCycle;
    private Instant lastReviewAt;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionNotes() {
        return versionNotes;
    }

    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public DataStandardVersionStatus getVersionStatus() {
        return versionStatus;
    }

    public void setVersionStatus(DataStandardVersionStatus versionStatus) {
        this.versionStatus = versionStatus;
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
}

