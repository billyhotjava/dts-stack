package com.yuzhi.dts.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable replacement for the {@code MetricModelLifecycleService.modelVersions} entries.
 *
 * <p>Surrogate UUID PK + a plain {@code model_id} FK column (indexed). {@code version_ordinal} is the
 * 1-based insertion order that reproduces the legacy ordered {@code List} and
 * {@code nextVersion() = "v" + (size + 1)}. {@code @Version version_lock} is the spec-required optimistic
 * lock; combined with {@code uk_metric_model_version (model_id, version)} it serializes concurrent
 * publishes so the second loser maps to 409 {@code metric_version_conflict} (T04).
 */
@Entity
@Table(
    name = "metric_model_version",
    uniqueConstraints = { @UniqueConstraint(name = "uk_metric_model_version", columnNames = { "model_id", "version" }) }
)
public class MetricModelVersion extends AbstractAuditingEntity<UUID> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "version_ordinal", nullable = false)
    private int versionOrdinal;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "model_name", length = 256)
    private String modelName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "platform_publish_reference", length = 512)
    private String platformPublishReference;

    @Column(name = "release_decision", length = 64)
    private String releaseDecision;

    @Column(name = "artifact_ref", length = 512)
    private String artifactRef;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "version_lock", nullable = false)
    private Long versionLock;

    public MetricModelVersion() {}

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getVersionOrdinal() {
        return versionOrdinal;
    }

    public void setVersionOrdinal(int versionOrdinal) {
        this.versionOrdinal = versionOrdinal;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlatformPublishReference() {
        return platformPublishReference;
    }

    public void setPlatformPublishReference(String platformPublishReference) {
        this.platformPublishReference = platformPublishReference;
    }

    public String getReleaseDecision() {
        return releaseDecision;
    }

    public void setReleaseDecision(String releaseDecision) {
        this.releaseDecision = releaseDecision;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getVersionLock() {
        return versionLock;
    }

    public void setVersionLock(Long versionLock) {
        this.versionLock = versionLock;
    }
}
