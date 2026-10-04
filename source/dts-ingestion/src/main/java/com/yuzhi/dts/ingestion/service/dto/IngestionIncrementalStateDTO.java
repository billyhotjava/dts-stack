package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;

public class IngestionIncrementalStateDTO {

    private Long id;

    private Long taskId;

    private String sourceTable;

    private String lastSuccessWatermark;

    private String lastRunId;

    private Instant updatedAt;

    private Instant createdAt;

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
