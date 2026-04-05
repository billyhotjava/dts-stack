package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * 暂存表元数据实体
 * 用于跟踪暂存表的生命周期和自动清理
 */
@Entity
@Table(name = "staging_table_metadata")
public class StagingTableMetadata {

    @Id
    @Column(name = "id")
    private UUID id;

    @NotNull
    @Size(max = 100)
    @Column(name = "table_name", length = 100, unique = true, nullable = false)
    private String tableName;

    @Column(name = "task_id")
    private Long taskId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;

    @NotNull
    @Column(name = "ttl_hours", nullable = false)
    private Integer ttlHours;

    @NotNull
    @Size(max = 20)
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    public StagingTableMetadata() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Integer getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(Integer ttlHours) {
        this.ttlHours = ttlHours;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "StagingTableMetadata{" +
                "id=" + id +
                ", tableName='" + tableName + '\'' +
                ", taskId=" + taskId +
                ", createdAt=" + createdAt +
                ", lastAccessedAt=" + lastAccessedAt +
                ", ttlHours=" + ttlHours +
                ", status='" + status + '\'' +
                '}';
    }
}
