package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_schema_drift_event")
public class CatalogSchemaDriftEvent extends AbstractAuditingEntity<UUID> implements Serializable {

    public static final String POLICY_REVIEW = "REVIEW";
    public static final String POLICY_AUTO_APPLY = "AUTO_APPLY";
    public static final String POLICY_BLOCK = "BLOCK";

    public static final String TICKET_OPEN = "OPEN";
    public static final String TICKET_IN_REVIEW = "IN_REVIEW";
    public static final String TICKET_RESOLVED = "RESOLVED";
    public static final String TICKET_IGNORED = "IGNORED";
    public static final String TICKET_REJECTED = "REJECTED";

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "run_id", columnDefinition = "uuid")
    private UUID runId;

    @Column(name = "integration", length = 32, nullable = false)
    private String integration;

    @Column(name = "dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID datasetId;

    @Column(name = "hive_database", length = 256)
    private String hiveDatabase;

    @Column(name = "hive_table", length = 256)
    private String hiveTable;

    @Column(name = "added_count")
    private Integer addedCount;

    @Column(name = "removed_count")
    private Integer removedCount;

    @Column(name = "changed_count")
    private Integer changedCount;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "policy_mode", length = 32)
    private String policyMode;

    @Column(name = "ticket_status", length = 32)
    private String ticketStatus;

    @Column(name = "ticket_assignee", length = 100)
    private String ticketAssignee;

    @Column(name = "workflow_note", columnDefinition = "text")
    private String workflowNote;

    @Column(name = "handled_at")
    private java.time.Instant handledAt;

    @Column(name = "handled_by", length = 50)
    private String handledBy;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getHiveDatabase() {
        return hiveDatabase;
    }

    public void setHiveDatabase(String hiveDatabase) {
        this.hiveDatabase = hiveDatabase;
    }

    public String getHiveTable() {
        return hiveTable;
    }

    public void setHiveTable(String hiveTable) {
        this.hiveTable = hiveTable;
    }

    public Integer getAddedCount() {
        return addedCount;
    }

    public void setAddedCount(Integer addedCount) {
        this.addedCount = addedCount;
    }

    public Integer getRemovedCount() {
        return removedCount;
    }

    public void setRemovedCount(Integer removedCount) {
        this.removedCount = removedCount;
    }

    public Integer getChangedCount() {
        return changedCount;
    }

    public void setChangedCount(Integer changedCount) {
        this.changedCount = changedCount;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getPolicyMode() {
        return policyMode;
    }

    public void setPolicyMode(String policyMode) {
        this.policyMode = policyMode;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public String getTicketAssignee() {
        return ticketAssignee;
    }

    public void setTicketAssignee(String ticketAssignee) {
        this.ticketAssignee = ticketAssignee;
    }

    public String getWorkflowNote() {
        return workflowNote;
    }

    public void setWorkflowNote(String workflowNote) {
        this.workflowNote = workflowNote;
    }

    public java.time.Instant getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(java.time.Instant handledAt) {
        this.handledAt = handledAt;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }
}
