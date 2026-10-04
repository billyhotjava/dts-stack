package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_lineage_job")
public class CatalogLineageJob extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "job_key", length = 256, nullable = false, unique = true)
    private String jobKey;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "job_type", length = 64)
    private String jobType;

    @Column(name = "engine", length = 64)
    private String engine;

    @Column(name = "relation_type", length = 32)
    private String relationType;

    @Column(name = "project_name", length = 128)
    private String projectName;

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "external_id", length = 256)
    private String externalId;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_execution_id", length = 128)
    private String lastExecutionId;

    @Column(name = "last_execution_status", length = 32)
    private String lastExecutionStatus;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Lob
    @Column(name = "detail_payload")
    private String detailPayload;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastExecutionId() {
        return lastExecutionId;
    }

    public void setLastExecutionId(String lastExecutionId) {
        this.lastExecutionId = lastExecutionId;
    }

    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }

    public void setLastExecutionStatus(String lastExecutionStatus) {
        this.lastExecutionStatus = lastExecutionStatus;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(Instant lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getDetailPayload() {
        return detailPayload;
    }

    public void setDetailPayload(String detailPayload) {
        this.detailPayload = detailPayload;
    }
}
