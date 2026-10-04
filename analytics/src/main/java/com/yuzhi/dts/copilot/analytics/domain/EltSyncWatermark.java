package com.yuzhi.dts.copilot.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "elt_sync_watermark")
public class EltSyncWatermark implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "target_table", nullable = false, unique = true, length = 128)
    private String targetTable;

    @Column(name = "last_watermark")
    private Instant lastWatermark;

    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    @Column(name = "last_sync_rows")
    private Integer lastSyncRows;

    @Column(name = "last_sync_duration_ms")
    private Integer lastSyncDurationMs;

    @Column(name = "sync_status", nullable = false, length = 16)
    private String syncStatus;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public Instant getLastWatermark() {
        return lastWatermark;
    }

    public void setLastWatermark(Instant lastWatermark) {
        this.lastWatermark = lastWatermark;
    }

    public Instant getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(Instant lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public Integer getLastSyncRows() {
        return lastSyncRows;
    }

    public void setLastSyncRows(Integer lastSyncRows) {
        this.lastSyncRows = lastSyncRows;
    }

    public Integer getLastSyncDurationMs() {
        return lastSyncDurationMs;
    }

    public void setLastSyncDurationMs(Integer lastSyncDurationMs) {
        this.lastSyncDurationMs = lastSyncDurationMs;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
