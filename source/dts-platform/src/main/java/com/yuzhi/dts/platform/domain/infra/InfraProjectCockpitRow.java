package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_project_cockpit_row")
public class InfraProjectCockpitRow extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "batch_id", columnDefinition = "uuid", nullable = false)
    private UUID batchId;

    @Column(name = "row_no", nullable = false)
    private Integer rowNo;

    @Column(name = "parse_status", length = 32, nullable = false)
    private String parseStatus = "PARSED";

    @Column(name = "project_no", length = 128)
    private String projectNo;

    @Column(name = "subsystem", length = 255)
    private String subsystem;

    @Column(name = "node_task", length = 512)
    private String nodeTask;

    @Column(name = "dept", length = 128)
    private String dept;

    @Column(name = "owner", length = 128)
    private String owner;

    @Column(name = "project_manager", length = 128)
    private String projectManager;

    @Column(name = "plan_date_raw", length = 128)
    private String planDateRaw;

    @Column(name = "actual_date_raw", length = 128)
    private String actualDateRaw;

    @Column(name = "completion_status_raw", length = 128)
    private String completionStatusRaw;

    @Column(name = "risk_level_raw", length = 64)
    private String riskLevelRaw;

    @Column(name = "raw_payload", columnDefinition = "text", nullable = false)
    private String rawPayload;

    @Column(name = "props", columnDefinition = "text")
    private String props;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getNodeTask() {
        return nodeTask;
    }

    public void setNodeTask(String nodeTask) {
        this.nodeTask = nodeTask;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getProjectManager() {
        return projectManager;
    }

    public void setProjectManager(String projectManager) {
        this.projectManager = projectManager;
    }

    public String getPlanDateRaw() {
        return planDateRaw;
    }

    public void setPlanDateRaw(String planDateRaw) {
        this.planDateRaw = planDateRaw;
    }

    public String getActualDateRaw() {
        return actualDateRaw;
    }

    public void setActualDateRaw(String actualDateRaw) {
        this.actualDateRaw = actualDateRaw;
    }

    public String getCompletionStatusRaw() {
        return completionStatusRaw;
    }

    public void setCompletionStatusRaw(String completionStatusRaw) {
        this.completionStatusRaw = completionStatusRaw;
    }

    public String getRiskLevelRaw() {
        return riskLevelRaw;
    }

    public void setRiskLevelRaw(String riskLevelRaw) {
        this.riskLevelRaw = riskLevelRaw;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getProps() {
        return props;
    }

    public void setProps(String props) {
        this.props = props;
    }
}
