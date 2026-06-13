package com.yuzhi.dts.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Durable replacement for the {@code MetricModelLifecycleService.modelStates} {@code ConcurrentHashMap}.
 *
 * <p>The legacy in-memory Map stored a different key-set per lifecycle phase. The flat columns capture
 * the stable attributes; {@code transient_state} (jsonb) absorbs the variant per-status keys
 * (reviewReference / releaseGate / submittedAt / checkedAt / validatedAt / graphStatus / diagnostics /
 * warnings / meta / version / activeVersion / previousVersion / platformPublishReference /
 * releaseDecision / rollbackAvailable / publishedAt / rollback result keys) so the public Map returned
 * by every lifecycle method is reassembled losslessly (T04).
 */
@Entity
@Table(name = "metric_model_state")
public class MetricModelState extends AbstractAuditingEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "model_name", length = 256)
    private String modelName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "artifact_ref", length = 512)
    private String artifactRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artifacts", columnDefinition = "jsonb")
    private Map<String, Object> artifacts;

    @Column(name = "applied_policy_source", length = 128)
    private String appliedPolicySource;

    @Column(name = "applied_predicate_hash", length = 128)
    private String appliedPredicateHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "platform_validation", columnDefinition = "jsonb")
    private Map<String, Object> platformValidation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transient_state", columnDefinition = "jsonb")
    private Map<String, Object> transientState;

    @Column(name = "active_version", length = 32)
    private String activeVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MetricModelState() {}

    @Override
    public String getId() {
        return modelId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
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

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public Map<String, Object> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Map<String, Object> artifacts) {
        this.artifacts = artifacts;
    }

    public String getAppliedPolicySource() {
        return appliedPolicySource;
    }

    public void setAppliedPolicySource(String appliedPolicySource) {
        this.appliedPolicySource = appliedPolicySource;
    }

    public String getAppliedPredicateHash() {
        return appliedPredicateHash;
    }

    public void setAppliedPredicateHash(String appliedPredicateHash) {
        this.appliedPredicateHash = appliedPredicateHash;
    }

    public Map<String, Object> getPlatformValidation() {
        return platformValidation;
    }

    public void setPlatformValidation(Map<String, Object> platformValidation) {
        this.platformValidation = platformValidation;
    }

    public Map<String, Object> getTransientState() {
        return transientState;
    }

    public void setTransientState(Map<String, Object> transientState) {
        this.transientState = transientState;
    }

    public String getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(String activeVersion) {
        this.activeVersion = activeVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
