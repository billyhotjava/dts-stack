package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_standard")
public class DataStandard extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "domain", length = 64)
    private String domain;

    @Column(name = "scope", length = 256)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DataStandardStatus status = DataStandardStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_level", length = 32, nullable = false)
    private DataSecurityLevel securityLevel = DataSecurityLevel.INTERNAL;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "tags", length = 512)
    private String tags;

    @Column(name = "current_version", length = 32, nullable = false)
    private String currentVersion = "v1";

    @Column(name = "version_notes", length = 1024)
    private String versionNotes;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "review_cycle", length = 32)
    private String reviewCycle;

    @Column(name = "last_review_at")
    private Instant lastReviewAt;

    @Override
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

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
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
}

