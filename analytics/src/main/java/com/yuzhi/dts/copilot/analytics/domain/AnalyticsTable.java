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
@Table(name = "analytics_table")
public class AnalyticsTable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "database_id", nullable = false)
    private Long databaseId;

    @Column(name = "schema_name", nullable = false, length = 255)
    private String schemaName = "";

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "visibility_type", nullable = false, length = 32)
    private String visibilityType = "normal";

    @Column(name = "semantic_domain", length = 32)
    private String semanticDomain;

    @Column(name = "default_time_field", length = 128)
    private String defaultTimeField;

    @Column(name = "default_sort_field", length = 128)
    private String defaultSortField;

    @Column(name = "semantic_description", columnDefinition = "text")
    private String semanticDescription;

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

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getSemanticDomain() {
        return semanticDomain;
    }

    public void setSemanticDomain(String semanticDomain) {
        this.semanticDomain = semanticDomain;
    }

    public String getDefaultTimeField() {
        return defaultTimeField;
    }

    public void setDefaultTimeField(String defaultTimeField) {
        this.defaultTimeField = defaultTimeField;
    }

    public String getDefaultSortField() {
        return defaultSortField;
    }

    public void setDefaultSortField(String defaultSortField) {
        this.defaultSortField = defaultSortField;
    }

    public String getSemanticDescription() {
        return semanticDescription;
    }

    public void setSemanticDescription(String semanticDescription) {
        this.semanticDescription = semanticDescription;
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
