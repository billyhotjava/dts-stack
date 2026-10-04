package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
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
@Table(name = "infra_external_artifact")
public class InfraExternalArtifact extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "entry_key", length = 64, nullable = false)
    private String entryKey;

    @Column(name = "artifact_type", length = 32, nullable = false)
    private String artifactType;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "external_id", length = 256)
    private String externalId;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "classification", length = 32, nullable = false)
    private String classification = SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code();

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "tags", length = 1024)
    private String tags;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_message", length = 1024)
    private String lastMessage;

    @Column(name = "props", columnDefinition = "text")
    private String props;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getProps() {
        return props;
    }

    public void setProps(String props) {
        this.props = props;
    }
}
