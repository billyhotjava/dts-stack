package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 数据入湖任务变更记录
 * 用于记录任务配置、连接参数、同步范围等变更历史
 */
@Entity
@Table(name = "ingestion_task_change_log")
public class IngestionTaskChangeLog extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Size(max = 200)
    @Column(name = "task_name", length = 200)
    private String taskName;

    @NotNull
    @Size(max = 50)
    @Column(name = "obj_type", length = 50, nullable = false)
    private String objType = "INGEST_JOB";

    @NotNull
    @Size(max = 50)
    @Column(name = "change_type", length = 50, nullable = false)
    private String changeType;

    @Size(max = 512)
    @Column(name = "summary", length = 512)
    private String summary;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Size(max = 10)
    @Column(name = "risk_level", length = 10)
    private String riskLevel;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Size(max = 100)
    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "approval_comment", columnDefinition = "TEXT")
    private String approvalComment;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Size(max = 50)
    @Column(name = "handled_by", length = 50)
    private String handledBy;

    @Override
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

    public String getObjType() {
        return objType;
    }

    public void setObjType(String objType) {
        this.objType = objType;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(Instant handledAt) {
        this.handledAt = handledAt;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngestionTaskChangeLog)) return false;
        IngestionTaskChangeLog that = (IngestionTaskChangeLog) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "IngestionTaskChangeLog{" +
            "id=" + id +
            ", taskId=" + taskId +
            ", changeType='" + changeType + '\'' +
            '}';
    }
}
