package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;

public class IngestionIncrementalAuditDTO {

    private Long id;

    private Long taskId;

    private Long executionId;

    private String executionRunId;

    private String sourceTable;

    private String incrementalColumn;

    private String beforeWatermark;

    private String afterWatermark;

    private Boolean advanced;

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
