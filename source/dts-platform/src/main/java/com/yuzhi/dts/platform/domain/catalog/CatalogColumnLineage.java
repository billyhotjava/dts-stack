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
@Table(name = "catalog_column_lineage")
public class CatalogColumnLineage extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dataset_lineage_id", columnDefinition = "uuid")
    private UUID datasetLineageId;

    @Column(name = "upstream_dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID upstreamDatasetId;

    @Column(name = "downstream_dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID downstreamDatasetId;

    @Column(name = "upstream_column_id", columnDefinition = "uuid")
    private UUID upstreamColumnId;

    @Column(name = "downstream_column_id", columnDefinition = "uuid")
    private UUID downstreamColumnId;

    @Column(name = "upstream_column", length = 128, nullable = false)
    private String upstreamColumn;

    @Column(name = "downstream_column", length = 128, nullable = false)
    private String downstreamColumn;

    @Column(name = "relation_type", length = 32)
    private String relationType;

    @Column(name = "lineage_type", length = 32)
    private String lineageType;

    @Column(name = "expression", columnDefinition = "text")
    private String expression;

    @Column(name = "confidence", length = 32)
    private String confidence;

    @Column(name = "project_name", length = 128)
    private String projectName;

    @Column(name = "lineage_job_id", columnDefinition = "uuid")
    private UUID lineageJobId;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

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

    public UUID getDatasetLineageId() {
        return datasetLineageId;
    }

    public void setDatasetLineageId(UUID datasetLineageId) {
        this.datasetLineageId = datasetLineageId;
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

    public UUID getUpstreamColumnId() {
        return upstreamColumnId;
    }

    public void setUpstreamColumnId(UUID upstreamColumnId) {
        this.upstreamColumnId = upstreamColumnId;
    }

    public UUID getDownstreamColumnId() {
        return downstreamColumnId;
    }

    public void setDownstreamColumnId(UUID downstreamColumnId) {
        this.downstreamColumnId = downstreamColumnId;
    }

    public String getUpstreamColumn() {
        return upstreamColumn;
    }

    public void setUpstreamColumn(String upstreamColumn) {
        this.upstreamColumn = upstreamColumn;
    }

    public String getDownstreamColumn() {
        return downstreamColumn;
    }

    public void setDownstreamColumn(String downstreamColumn) {
        this.downstreamColumn = downstreamColumn;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getLineageType() {
        return lineageType;
    }

    public void setLineageType(String lineageType) {
        this.lineageType = lineageType;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
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

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(Instant lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
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
