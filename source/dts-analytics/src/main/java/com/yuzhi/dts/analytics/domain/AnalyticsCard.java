package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_card")
public class AnalyticsCard implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_id", nullable = false, length = 32, unique = true)
    private String entityId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "database_id", nullable = false)
    private Long databaseId;

    @Column(name = "dataset_query_json", nullable = false, columnDefinition = "text")
    private String datasetQueryJson;

    @Column(name = "display", length = 64)
    private String display;

    @Column(name = "visualization_settings_json", columnDefinition = "text")
    private String visualizationSettingsJson;

    @Column(name = "card_type", nullable = false, length = 32)
    private String cardType = "question";

    @Column(name = "query_dataset_id", columnDefinition = "uuid")
    private UUID queryDatasetId;

    @Column(name = "query_dataset_version")
    private Integer queryDatasetVersion;

    @Column(name = "semantic_contract_version", length = 64)
    private String semanticContractVersion;

    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private String lifecycleStatus = "DRAFT";

    @Column(name = "published_revision_id")
    private Long publishedRevisionId;

    @Version
    @Column(name = "version_no", nullable = false)
    private Long analysisVersion = 0L;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(Long databaseId) {
        this.databaseId = databaseId;
    }

    public String getDatasetQueryJson() {
        return datasetQueryJson;
    }

    public void setDatasetQueryJson(String datasetQueryJson) {
        this.datasetQueryJson = datasetQueryJson;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getVisualizationSettingsJson() {
        return visualizationSettingsJson;
    }

    public void setVisualizationSettingsJson(String visualizationSettingsJson) {
        this.visualizationSettingsJson = visualizationSettingsJson;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public UUID getQueryDatasetId() {
        return queryDatasetId;
    }

    public void setQueryDatasetId(UUID queryDatasetId) {
        this.queryDatasetId = queryDatasetId;
    }

    public Integer getQueryDatasetVersion() {
        return queryDatasetVersion;
    }

    public void setQueryDatasetVersion(Integer queryDatasetVersion) {
        this.queryDatasetVersion = queryDatasetVersion;
    }

    public String getSemanticContractVersion() {
        return semanticContractVersion;
    }

    public void setSemanticContractVersion(String semanticContractVersion) {
        this.semanticContractVersion = semanticContractVersion;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Long getPublishedRevisionId() {
        return publishedRevisionId;
    }

    public void setPublishedRevisionId(Long publishedRevisionId) {
        this.publishedRevisionId = publishedRevisionId;
    }

    public Long getAnalysisVersion() {
        return analysisVersion;
    }

    public void setAnalysisVersion(Long analysisVersion) {
        this.analysisVersion = analysisVersion;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
