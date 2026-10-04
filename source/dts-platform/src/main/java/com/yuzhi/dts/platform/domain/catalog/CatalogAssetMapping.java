package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_asset_mapping")
public class CatalogAssetMapping extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "om_entity_id", length = 128)
    private String omEntityId;

    @NotBlank
    @Column(name = "fqn", length = 1024, nullable = false)
    private String fqn;

    @Column(name = "legacy_dataset_id", columnDefinition = "uuid")
    private UUID legacyDatasetId;

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "match_status", length = 32, nullable = false)
    private String matchStatus = "UNMATCHED";

    @Column(name = "match_reason", length = 512)
    private String matchReason;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOmEntityId() {
        return omEntityId;
    }

    public void setOmEntityId(String omEntityId) {
        this.omEntityId = omEntityId;
    }

    public String getFqn() {
        return fqn;
    }

    public void setFqn(String fqn) {
        this.fqn = fqn;
    }

    public UUID getLegacyDatasetId() {
        return legacyDatasetId;
    }

    public void setLegacyDatasetId(UUID legacyDatasetId) {
        this.legacyDatasetId = legacyDatasetId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
