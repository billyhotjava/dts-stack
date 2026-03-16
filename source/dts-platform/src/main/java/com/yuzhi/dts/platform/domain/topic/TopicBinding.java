package com.yuzhi.dts.platform.domain.topic;

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
@Table(name = "topic_binding")
public class TopicBinding extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "template_id", columnDefinition = "uuid", nullable = false)
    private UUID templateId;

    @Column(name = "entity_id", columnDefinition = "uuid", nullable = false)
    private UUID entityId;

    @Column(name = "scope_key", length = 64, nullable = false)
    private String scopeKey;

    @Column(name = "binding_mode", length = 32, nullable = false)
    private String bindingMode;

    @Column(name = "data_source_id", columnDefinition = "uuid")
    private UUID dataSourceId;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "table_name", length = 128)
    private String tableName;

    @Column(name = "view_name", length = 128)
    private String viewName;

    @Column(name = "ods_mapping_id", columnDefinition = "uuid")
    private UUID odsMappingId;

    @Column(name = "batch_id", columnDefinition = "uuid")
    private UUID batchId;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "bound_by", length = 128)
    private String boundBy;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "notes", length = 1024)
    private String notes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public String getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(String bindingMode) {
        this.bindingMode = bindingMode;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(UUID dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
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

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public UUID getOdsMappingId() {
        return odsMappingId;
    }

    public void setOdsMappingId(UUID odsMappingId) {
        this.odsMappingId = odsMappingId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBoundBy() {
        return boundBy;
    }

    public void setBoundBy(String boundBy) {
        this.boundBy = boundBy;
    }

    public Instant getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(Instant boundAt) {
        this.boundAt = boundAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
