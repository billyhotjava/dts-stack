package com.yuzhi.dts.platform.domain.topic;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "topic_template_entity")
public class TopicTemplateEntity extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "template_id", columnDefinition = "uuid", nullable = false)
    private UUID templateId;

    @Column(name = "entity_code", length = 128, nullable = false)
    private String entityCode;

    @Column(name = "entity_name", length = 256, nullable = false)
    private String entityName;

    @Column(name = "entity_type", length = 64, nullable = false)
    private String entityType;

    @Column(name = "required_flag", nullable = false)
    private Boolean required = Boolean.TRUE;

    @Column(name = "source_name", length = 128, nullable = false)
    private String sourceName;

    @Column(name = "table_name", length = 128, nullable = false)
    private String tableName;

    @Column(name = "expected_schema", length = 128)
    private String expectedSchema;

    @Column(name = "description", length = 1024)
    private String description;

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

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getExpectedSchema() {
        return expectedSchema;
    }

    public void setExpectedSchema(String expectedSchema) {
        this.expectedSchema = expectedSchema;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
