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
}

