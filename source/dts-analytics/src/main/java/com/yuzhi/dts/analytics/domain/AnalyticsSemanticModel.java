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

@Entity
@Table(name = "analytics_semantic_model")
public class AnalyticsSemanticModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "database_id", nullable = false)
    private Long databaseId;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "schema_name", length = 255)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "subject_area", length = 255)
    private String subjectArea;

    @Column(name = "security_level", length = 64)
    private String securityLevel;

    @Column(name = "grain", length = 255)
    private String grain;

    @Column(name = "row_security_predicate", columnDefinition = "text")
    private String rowSecurityPredicate;

    @Column(name = "spec_version", length = 32)
    private String specVersion;

    @Column(name = "exposed_to_modeler", nullable = false)
    private boolean exposedToModeler = true;

    @Column(name = "meta_json", columnDefinition = "text")
    private String metaJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSubjectArea() {
        return subjectArea;
    }

    public void setSubjectArea(String subjectArea) {
        this.subjectArea = subjectArea;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getGrain() {
        return grain;
    }

    public void setGrain(String grain) {
        this.grain = grain;
    }

    public String getRowSecurityPredicate() {
        return rowSecurityPredicate;
    }

    public void setRowSecurityPredicate(String rowSecurityPredicate) {
        this.rowSecurityPredicate = rowSecurityPredicate;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public boolean isExposedToModeler() {
        return exposedToModeler;
    }

    public void setExposedToModeler(boolean exposedToModeler) {
        this.exposedToModeler = exposedToModeler;
    }

    public String getMetaJson() {
        return metaJson;
    }

    public void setMetaJson(String metaJson) {
        this.metaJson = metaJson;
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
