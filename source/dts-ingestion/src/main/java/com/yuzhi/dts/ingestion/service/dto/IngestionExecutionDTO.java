package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * DTO for {@link com.yuzhi.dts.ingestion.domain.IngestionExecution}
 */
public class IngestionExecutionDTO {

    private Long id;

    private Long taskId;

    private String taskName;

    private String executionId;

    private String batchId;

    private String status;

    private Instant startTime;

    private Instant endTime;

    private Long rowsRead;

    private Long rowsWritten;

    private String errorMessage;

    private String failureCategory;

    private String failureAdvice;

    private String logPath;

    private String replaceMode;

    private String triggerMode;

    private String droppedTables;

    private JsonNode sourceTables;

    private JsonNode targetTables;

    private Long queueWaitSeconds;

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

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Long getRowsRead() {
        return rowsRead;
    }

    public void setRowsRead(Long rowsRead) {
        this.rowsRead = rowsRead;
    }

    public Long getRowsWritten() {
        return rowsWritten;
    }

    public void setRowsWritten(Long rowsWritten) {
        this.rowsWritten = rowsWritten;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFailureCategory() {
        return failureCategory;
    }

    public void setFailureCategory(String failureCategory) {
        this.failureCategory = failureCategory;
    }

    public String getFailureAdvice() {
        return failureAdvice;
    }

    public void setFailureAdvice(String failureAdvice) {
        this.failureAdvice = failureAdvice;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public String getReplaceMode() {
        return replaceMode;
    }

    public void setReplaceMode(String replaceMode) {
        this.replaceMode = replaceMode;
    }

    public String getDroppedTables() {
        return droppedTables;
    }

    public void setDroppedTables(String droppedTables) {
        this.droppedTables = droppedTables;
    }

    public JsonNode getSourceTables() {
        return sourceTables;
    }

    public void setSourceTables(JsonNode sourceTables) {
        this.sourceTables = sourceTables;
    }

    public JsonNode getTargetTables() {
        return targetTables;
    }

    public void setTargetTables(JsonNode targetTables) {
        this.targetTables = targetTables;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public Long getQueueWaitSeconds() {
        return queueWaitSeconds;
    }

    public void setQueueWaitSeconds(Long queueWaitSeconds) {
        this.queueWaitSeconds = queueWaitSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "IngestionExecutionDTO{" +
            "id=" + id +
            ", taskId=" + taskId +
            ", executionId='" + executionId + '\'' +
            ", batchId='" + batchId + '\'' +
            ", status='" + status + '\'' +
            '}';
    }
}
