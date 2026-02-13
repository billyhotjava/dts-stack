package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(name = "ingestion_incremental_audit")
public class IngestionIncrementalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Size(max = 255)
    @Column(name = "execution_run_id", length = 255)
    private String executionRunId;

    @NotNull
    @Size(max = 255)
    @Column(name = "source_table", nullable = false, length = 255)
    private String sourceTable;

    @Size(max = 255)
    @Column(name = "incremental_column", length = 255)
    private String incrementalColumn;

    @Size(max = 512)
    @Column(name = "before_watermark", length = 512)
    private String beforeWatermark;

    @Size(max = 512)
    @Column(name = "after_watermark", length = 512)
    private String afterWatermark;

    @NotNull
    @Column(name = "advanced", nullable = false)
    private Boolean advanced = Boolean.FALSE;

    @NotNull
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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getExecutionRunId() {
        return executionRunId;
    }

    public void setExecutionRunId(String executionRunId) {
        this.executionRunId = executionRunId;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getIncrementalColumn() {
        return incrementalColumn;
    }

    public void setIncrementalColumn(String incrementalColumn) {
        this.incrementalColumn = incrementalColumn;
    }

    public String getBeforeWatermark() {
        return beforeWatermark;
    }

    public void setBeforeWatermark(String beforeWatermark) {
        this.beforeWatermark = beforeWatermark;
    }

    public String getAfterWatermark() {
        return afterWatermark;
    }

    public void setAfterWatermark(String afterWatermark) {
        this.afterWatermark = afterWatermark;
    }

    public Boolean getAdvanced() {
        return advanced;
    }

    public void setAdvanced(Boolean advanced) {
        this.advanced = advanced;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
