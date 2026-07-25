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
@Table(name = "catalog_asset_tag")
public class CatalogAssetTag extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tag_id", nullable = false, columnDefinition = "uuid")
    private UUID tagId;

    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    @Column(name = "asset_key", nullable = false, length = 512)
    private String assetKey;

    @Column(name = "tagged_by", nullable = false, length = 64)
    private String taggedBy;

    @Column(name = "tagged_at", nullable = false)
    private Instant taggedAt;

    @Column(name = "migration_batch", length = 64)
    private String migrationBatch;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public void setAssetKey(String assetKey) {
        this.assetKey = assetKey;
    }

    public String getTaggedBy() {
        return taggedBy;
    }

    public void setTaggedBy(String taggedBy) {
        this.taggedBy = taggedBy;
    }

    public Instant getTaggedAt() {
        return taggedAt;
    }

    public void setTaggedAt(Instant taggedAt) {
        this.taggedAt = taggedAt;
    }

    public String getMigrationBatch() {
        return migrationBatch;
    }

    public void setMigrationBatch(String migrationBatch) {
        this.migrationBatch = migrationBatch;
    }
}
