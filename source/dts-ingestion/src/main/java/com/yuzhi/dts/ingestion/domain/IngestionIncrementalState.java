package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(
    name = "ingestion_incremental_state",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ingestion_incremental_task_table", columnNames = { "task_id", "source_table" }),
    }
)
public class IngestionIncrementalState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @NotNull
    @Size(max = 255)
    @Column(name = "source_table", nullable = false, length = 255)
    private String sourceTable;

    @Size(max = 512)
    @Column(name = "last_success_watermark", length = 512)
    private String lastSuccessWatermark;

    @Size(max = 255)
    @Column(name = "last_run_id", length = 255)
    private String lastRunId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getLastSuccessWatermark() {
        return lastSuccessWatermark;
    }

    public void setLastSuccessWatermark(String lastSuccessWatermark) {
        this.lastSuccessWatermark = lastSuccessWatermark;
    }

    public String getLastRunId() {
        return lastRunId;
    }

    public void setLastRunId(String lastRunId) {
        this.lastRunId = lastRunId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
