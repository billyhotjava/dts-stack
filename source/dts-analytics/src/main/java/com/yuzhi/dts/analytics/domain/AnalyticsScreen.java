package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * Entity for storing screen designer data (大屏编辑器).
 */
@Entity
@Table(name = "analytics_screen")
public class AnalyticsScreen implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "width", nullable = false)
    private Integer width = 1920;

    @Column(name = "height", nullable = false)
    private Integer height = 1080;

    @Column(name = "background_color", length = 32)
    private String backgroundColor;

    @Column(name = "background_image", columnDefinition = "text")
    private String backgroundImage;

    @Column(name = "theme", length = 32)
    private String theme;

    @Column(name = "components_json", columnDefinition = "text")
    private String componentsJson;
    @Column(name = "variables_json", columnDefinition = "text")
    private String variablesJson;
    @Column(name = "pages_json", columnDefinition = "text")
    private String pagesJson;
    @Column(name = "carousel_json", columnDefinition = "text")
    private String carouselJson;

    /**
     * Sprint-12 v2 ScreenConfig 扩展字段（schemaVersion / layout / referenceViewport 等）。
     * v1 大屏为 null。存原始 JSON，不拆字段；前端负责 shape。
     */
    @Column(name = "v2_spec_json", columnDefinition = "text")
    private String v2SpecJson;

    @Column(name = "classification", length = 32)
    private String classification;

    @Column(name = "manual_classification_floor", length = 32)
    private String manualClassificationFloor;

    @Column(name = "classification_snapshot_id", length = 36)
    private String classificationSnapshotId;

    @Column(name = "classification_snapshot_version")
    private Long classificationSnapshotVersion;

    @Column(name = "classification_derived_at")
    private Instant classificationDerivedAt;

    @Column(name = "classification_evidence_json", columnDefinition = "text")
    private String classificationEvidenceJson;

    @Column(name = "domain_id", length = 64)
    private String domainId;

    @Column(name = "owner_dept_code", length = 64)
    private String ownerDeptCode;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

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

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getComponentsJson() {
        return componentsJson;
    }

    public void setComponentsJson(String componentsJson) {
        this.componentsJson = componentsJson;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getPagesJson() {
        return pagesJson;
    }

    public void setPagesJson(String pagesJson) {
        this.pagesJson = pagesJson;
    }

    public String getCarouselJson() {
        return carouselJson;
    }

    public void setCarouselJson(String carouselJson) {
        this.carouselJson = carouselJson;
    }

    public String getV2SpecJson() {
        return v2SpecJson;
    }

    public void setV2SpecJson(String v2SpecJson) {
        this.v2SpecJson = v2SpecJson;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getManualClassificationFloor() {
        return manualClassificationFloor;
    }

    public void setManualClassificationFloor(String manualClassificationFloor) {
        this.manualClassificationFloor = manualClassificationFloor;
    }

    public String getClassificationSnapshotId() {
        return classificationSnapshotId;
    }

    public void setClassificationSnapshotId(String classificationSnapshotId) {
        this.classificationSnapshotId = classificationSnapshotId;
    }

    public Long getClassificationSnapshotVersion() {
        return classificationSnapshotVersion;
    }

    public void setClassificationSnapshotVersion(Long classificationSnapshotVersion) {
        this.classificationSnapshotVersion = classificationSnapshotVersion;
    }

    public Instant getClassificationDerivedAt() {
        return classificationDerivedAt;
    }

    public void setClassificationDerivedAt(Instant classificationDerivedAt) {
        this.classificationDerivedAt = classificationDerivedAt;
    }

    public String getClassificationEvidenceJson() {
        return classificationEvidenceJson;
    }

    public void setClassificationEvidenceJson(String classificationEvidenceJson) {
        this.classificationEvidenceJson = classificationEvidenceJson;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getOwnerDeptCode() {
        return ownerDeptCode;
    }

    public void setOwnerDeptCode(String ownerDeptCode) {
        this.ownerDeptCode = ownerDeptCode;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
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
