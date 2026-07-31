package com.yuzhi.dts.ingestion.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.Type;

/**
 * 数据入湖任务执行历史实体
 * 记录每次任务执行的详细信息和结果
 */
@Entity
@Table(name = "ingestion_execution")
public class IngestionExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private IngestionTask task;

    @Size(max = 200)
    @Column(name = "execution_id", length = 200)
    private String executionId; // Addax或Airflow的执行ID

    @Column(name = "task_revision_id")
    private Long taskRevisionId;

    @Column(name = "revision_number")
    private Integer revisionNumber;

    @Size(max = 64)
    @Column(name = "effective_config_checksum", length = 64)
    private String effectiveConfigChecksum;

    @Size(max = 200)
    @Column(name = "quality_policy_ref", length = 200)
    private String qualityPolicyRef;

    @Size(max = 200)
    @Column(name = "quality_run_id", length = 200)
    private String qualityRunId;

    @Size(max = 200)
    @Column(name = "airflow_dag_id", length = 200)
    private String airflowDagId;

    @Size(max = 128)
    @Column(name = "batch_id", length = 128)
    private String batchId; // DTS入湖批次ID，同一次执行内保持一致

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status; // running, success, failed

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "rows_read")
    private Long rowsRead;

    @Column(name = "rows_written")
    private Long rowsWritten;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Size(max = 64)
    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Size(max = 500)
    @Column(name = "failure_advice", length = 500)
    private String failureAdvice;

    @Size(max = 500)
    @Column(name = "log_path", length = 500)
    private String logPath;

    @Size(max = 50)
    @Column(name = "replace_mode", length = 50)
    private String replaceMode;

    @Size(max = 32)
    @Column(name = "trigger_mode", length = 32)
    private String triggerMode;

    @Column(name = "backfill_window_start")
    private Instant backfillWindowStart;

    @Column(name = "backfill_window_end")
    private Instant backfillWindowEnd;

    @Size(max = 255)
    @Column(name = "backfill_column", length = 255)
    private String backfillColumn;

    @Column(name = "dropped_tables", columnDefinition = "TEXT")
    private String droppedTables;

    @Type(JsonType.class)
    @Column(name = "source_tables", columnDefinition = "jsonb")
    private JsonNode sourceTables;

    @Type(JsonType.class)
    @Column(name = "target_tables", columnDefinition = "jsonb")
    private JsonNode targetTables;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "retry_exhausted", nullable = false)
    private boolean retryExhausted = false;

    @Column(name = "parent_execution_id")
    private Long parentExecutionId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public IngestionTask getTask() {
        return task;
    }

    public void setTask(IngestionTask task) {
        this.task = task;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Long getTaskRevisionId() {
        return taskRevisionId;
    }

    public void setTaskRevisionId(Long taskRevisionId) {
        this.taskRevisionId = taskRevisionId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Integer revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public String getEffectiveConfigChecksum() {
        return effectiveConfigChecksum;
    }

    public void setEffectiveConfigChecksum(String effectiveConfigChecksum) {
        this.effectiveConfigChecksum = effectiveConfigChecksum;
    }

    public String getQualityPolicyRef() {
        return qualityPolicyRef;
    }

    public void setQualityPolicyRef(String qualityPolicyRef) {
        this.qualityPolicyRef = qualityPolicyRef;
    }

    public String getQualityRunId() {
        return qualityRunId;
    }

    public void setQualityRunId(String qualityRunId) {
        this.qualityRunId = qualityRunId;
    }

    public String getAirflowDagId() {
        return airflowDagId;
    }

    public void setAirflowDagId(String airflowDagId) {
        this.airflowDagId = airflowDagId;
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

    public Instant getBackfillWindowStart() {
        return backfillWindowStart;
    }

    public void setBackfillWindowStart(Instant backfillWindowStart) {
        this.backfillWindowStart = backfillWindowStart;
    }

    public Instant getBackfillWindowEnd() {
        return backfillWindowEnd;
    }

    public void setBackfillWindowEnd(Instant backfillWindowEnd) {
        this.backfillWindowEnd = backfillWindowEnd;
    }

    public String getBackfillColumn() {
        return backfillColumn;
    }

    public void setBackfillColumn(String backfillColumn) {
        this.backfillColumn = backfillColumn;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public boolean isRetryExhausted() {
        return retryExhausted;
    }

    public void setRetryExhausted(boolean retryExhausted) {
        this.retryExhausted = retryExhausted;
    }

    public Long getParentExecutionId() {
        return parentExecutionId;
    }

    public void setParentExecutionId(Long parentExecutionId) {
        this.parentExecutionId = parentExecutionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngestionExecution)) return false;
        IngestionExecution that = (IngestionExecution) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "IngestionExecution{" +
            "id=" + id +
            ", executionId='" + executionId + '\'' +
            ", batchId='" + batchId + '\'' +
            ", status='" + status + '\'' +
            ", startTime=" + startTime +
            '}';
    }
}
