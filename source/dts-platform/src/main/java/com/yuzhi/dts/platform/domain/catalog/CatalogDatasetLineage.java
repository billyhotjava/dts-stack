package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset_lineage")
public class CatalogDatasetLineage extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "upstream_dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID upstreamDatasetId;

    @Column(name = "downstream_dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID downstreamDatasetId;

    @Column(name = "relation_type", length = 32)
    private String relationType;

    @Column(name = "notes")
    private String notes;

    @Column(name = "upstream_asset_type", length = 32)
    private String upstreamAssetType;

    @Column(name = "downstream_asset_type", length = 32)
    private String downstreamAssetType;

    // direction 枚举值如 "UPSTREAM_TO_DOWNSTREAM" 长度 22，原 16 会溢出；统一到 32 与其他枚举列对齐。
    @Column(name = "direction", length = 32)
    private String direction;

    @Column(name = "project_name", length = 128)
    private String projectName;

    @Column(name = "lineage_job_id", columnDefinition = "uuid")
    private UUID lineageJobId;

    @Column(name = "verification_status", length = 32)
    private String verificationStatus;

    @Column(name = "last_execution_id", length = 128)
    private String lastExecutionId;

    @Column(name = "last_execution_status", length = 32)
    private String lastExecutionStatus;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUpstreamDatasetId() {
        return upstreamDatasetId;
    }

    public void setUpstreamDatasetId(UUID upstreamDatasetId) {
        this.upstreamDatasetId = upstreamDatasetId;
    }

    public UUID getDownstreamDatasetId() {
        return downstreamDatasetId;
    }

    public void setDownstreamDatasetId(UUID downstreamDatasetId) {
        this.downstreamDatasetId = downstreamDatasetId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getUpstreamAssetType() {
        return upstreamAssetType;
    }

    public void setUpstreamAssetType(String upstreamAssetType) {
        this.upstreamAssetType = upstreamAssetType;
    }

    public String getDownstreamAssetType() {
        return downstreamAssetType;
    }

    public void setDownstreamAssetType(String downstreamAssetType) {
        this.downstreamAssetType = downstreamAssetType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public UUID getLineageJobId() {
        return lineageJobId;
    }

    public void setLineageJobId(UUID lineageJobId) {
        this.lineageJobId = lineageJobId;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
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

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }
}
