package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(
    name = "infra_connector",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_infra_connector_key", columnNames = { "connector_key" })
    }
)
public class InfraConnector extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "connector_key", length = 64, nullable = false)
    private String connectorKey;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "default_engine", length = 64)
    private String defaultEngine;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "display_order")
    private Integer displayOrder = 1000;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "capabilities_payload", columnDefinition = "text")
    private String capabilitiesPayload;

    @Column(name = "config_schema_payload", columnDefinition = "text")
    private String configSchemaPayload;

    @Column(name = "sensitive_fields_payload", columnDefinition = "text")
    private String sensitiveFieldsPayload;

    @Column(name = "compatibility_payload", columnDefinition = "text")
    private String compatibilityPayload;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getConnectorKey() {
        return connectorKey;
    }

    public void setConnectorKey(String connectorKey) {
        this.connectorKey = connectorKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getDefaultEngine() {
        return defaultEngine;
    }

    public void setDefaultEngine(String defaultEngine) {
        this.defaultEngine = defaultEngine;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCapabilitiesPayload() {
        return capabilitiesPayload;
    }

    public void setCapabilitiesPayload(String capabilitiesPayload) {
        this.capabilitiesPayload = capabilitiesPayload;
    }

    public String getConfigSchemaPayload() {
        return configSchemaPayload;
    }

    public void setConfigSchemaPayload(String configSchemaPayload) {
        this.configSchemaPayload = configSchemaPayload;
    }

    public String getSensitiveFieldsPayload() {
        return sensitiveFieldsPayload;
    }

    public void setSensitiveFieldsPayload(String sensitiveFieldsPayload) {
        this.sensitiveFieldsPayload = sensitiveFieldsPayload;
    }

    public String getCompatibilityPayload() {
        return compatibilityPayload;
    }

    public void setCompatibilityPayload(String compatibilityPayload) {
        this.compatibilityPayload = compatibilityPayload;
    }
}
