package com.yuzhi.dts.copilot.analytics.domain;

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

@Entity
@Table(name = "analytics_field")
public class AnalyticsField implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "database_id", nullable = false)
    private Long databaseId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "base_type", length = 64)
    private String baseType;

    @Column(name = "effective_type", length = 64)
    private String effectiveType;

    @Column(name = "semantic_type", length = 64)
    private String semanticType;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "visibility_type", nullable = false, length = 32)
    private String visibilityType = "normal";

    @Column(name = "fingerprint_json", columnDefinition = "text")
    private String fingerprintJson;

    @Column(name = "field_role", length = 16)
    private String fieldRole;

    @Column(name = "is_filterable")
    private Boolean isFilterable = false;

    @Column(name = "sample_values", length = 512)
    private String sampleValues;

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

    public Long getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(Long databaseId) {
        this.databaseId = databaseId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBaseType() {
        return baseType;
    }

    public void setBaseType(String baseType) {
        this.baseType = baseType;
    }

    public String getEffectiveType() {
        return effectiveType;
    }

    public void setEffectiveType(String effectiveType) {
        this.effectiveType = effectiveType;
    }

    public String getSemanticType() {
        return semanticType;
    }

    public void setSemanticType(String semanticType) {
        this.semanticType = semanticType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(String visibilityType) {
        this.visibilityType = visibilityType;
    }

    public String getFingerprintJson() {
        return fingerprintJson;
    }

    public void setFingerprintJson(String fingerprintJson) {
        this.fingerprintJson = fingerprintJson;
    }

    public String getFieldRole() {
        return fieldRole;
    }

    public void setFieldRole(String fieldRole) {
        this.fieldRole = fieldRole;
    }

    public Boolean getIsFilterable() {
        return isFilterable;
    }

    public void setIsFilterable(Boolean isFilterable) {
        this.isFilterable = isFilterable;
    }

    public String getSampleValues() {
        return sampleValues;
    }

    public void setSampleValues(String sampleValues) {
        this.sampleValues = sampleValues;
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

