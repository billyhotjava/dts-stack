package com.yuzhi.dts.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable replacement for the {@code MetricModelLifecycleService.rollbackEvents} entries.
 *
 * <p>Surrogate UUID PK + plain {@code model_id} FK column (indexed). {@code event_ordinal} is the
 * 1-based order reproducing the legacy ordered {@code List} and
 * {@code eventVersion = "rollback-" + (size + 1)}. Persists only the 7 event-level keys
 * appendRollbackEvent() wrote; the current-state-after-rollback keys live in
 * {@link MetricModelState#getTransientState()} (T04).
 */
@Entity
@Table(name = "metric_rollback_event")
public class MetricRollbackEvent extends AbstractAuditingEntity<UUID> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "event_ordinal", nullable = false)
    private int eventOrdinal;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "rollback_from_version", length = 32)
    private String rollbackFromVersion;

    @Column(name = "rollback_to_version", length = 32)
    private String rollbackToVersion;

    @Column(name = "platform_rollback_reference", length = 512)
    private String platformRollbackReference;

    @Column(name = "reason", length = 1024)
    private String reason;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    public MetricRollbackEvent() {}

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

    public int getEventOrdinal() {
        return eventOrdinal;
    }

    public void setEventOrdinal(int eventOrdinal) {
        this.eventOrdinal = eventOrdinal;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRollbackFromVersion() {
        return rollbackFromVersion;
    }

    public void setRollbackFromVersion(String rollbackFromVersion) {
        this.rollbackFromVersion = rollbackFromVersion;
    }

    public String getRollbackToVersion() {
        return rollbackToVersion;
    }

    public void setRollbackToVersion(String rollbackToVersion) {
        this.rollbackToVersion = rollbackToVersion;
    }

    public String getPlatformRollbackReference() {
        return platformRollbackReference;
    }

    public void setPlatformRollbackReference(String platformRollbackReference) {
        this.platformRollbackReference = platformRollbackReference;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getRolledBackAt() {
        return rolledBackAt;
    }

    public void setRolledBackAt(Instant rolledBackAt) {
        this.rolledBackAt = rolledBackAt;
    }
}
