package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gov_quality_workflow_run")
public class GovQualityWorkflowRun extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "task_id", columnDefinition = "uuid")
    private UUID taskId;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "rule_id", columnDefinition = "uuid")
    private UUID ruleId;

    @Column(name = "retry_of_id", columnDefinition = "uuid")
    private UUID retryOfId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 1;

    @Column(name = "retry_backoff_seconds", nullable = false)
    private Integer retryBackoffSeconds = 0;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "trigger_ref", nullable = false, length = 128)
    private String triggerRef;

    @Column(name = "idempotency_key", nullable = false, length = 160, unique = true)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "expected_run_count", nullable = false)
    private Integer expectedRunCount = 0;

    @Column(name = "completed_run_count", nullable = false)
    private Integer completedRunCount = 0;

    @Column(name = "passed_count", nullable = false)
    private Integer passedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "dispatch_failure_count", nullable = false)
    private Integer dispatchFailureCount = 0;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Column(name = "message", length = 4096)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    private String contextJson;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getRetryOfId() {
        return retryOfId;
    }

    public void setRetryOfId(UUID retryOfId) {
        this.retryOfId = retryOfId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public Integer getRetryBackoffSeconds() {
        return retryBackoffSeconds;
    }

    public void setRetryBackoffSeconds(Integer retryBackoffSeconds) {
        this.retryBackoffSeconds = retryBackoffSeconds;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getExpectedRunCount() {
        return expectedRunCount;
    }

    public void setExpectedRunCount(Integer expectedRunCount) {
        this.expectedRunCount = expectedRunCount;
    }

    public Integer getCompletedRunCount() {
        return completedRunCount;
    }

    public void setCompletedRunCount(Integer completedRunCount) {
        this.completedRunCount = completedRunCount;
    }

    public Integer getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Integer passedCount) {
        this.passedCount = passedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getDispatchFailureCount() {
        return dispatchFailureCount;
    }

    public void setDispatchFailureCount(Integer dispatchFailureCount) {
        this.dispatchFailureCount = dispatchFailureCount;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }
}
