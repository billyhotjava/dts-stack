package com.yuzhi.dts.platform.domain.catalog;

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
@Table(name = "catalog_classification_propagation_job")
public class CatalogClassificationPropagationJob extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "target_dataset_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID targetDatasetId;

    @Column(name = "target_asset_key", nullable = false, length = 512, updatable = false)
    private String targetAssetKey;

    @Column(name = "trigger_type", nullable = false, length = 48, updatable = false)
    private String triggerType;

    @Column(name = "trigger_ref", length = 512, updatable = false)
    private String triggerRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "affected_subjects", nullable = false)
    private int affectedSubjects;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 2048)
    private String lastError;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getTargetDatasetId() {
        return targetDatasetId;
    }

    public void setTargetDatasetId(UUID targetDatasetId) {
        this.targetDatasetId = targetDatasetId;
    }

    public String getTargetAssetKey() {
        return targetAssetKey;
    }

    public void setTargetAssetKey(String targetAssetKey) {
        this.targetAssetKey = targetAssetKey;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerRef() {
        return triggerRef;
    }

    public void setTriggerRef(String triggerRef) {
        this.triggerRef = triggerRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getAffectedSubjects() {
        return affectedSubjects;
    }

    public void setAffectedSubjects(int affectedSubjects) {
        this.affectedSubjects = affectedSubjects;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
