package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_ods_table_mapping")
public class InfraOdsTableMapping extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "connection_id", columnDefinition = "uuid", nullable = false)
    private UUID connectionId;

    @Column(name = "stream_name", length = 256, nullable = false)
    private String streamName;

    @Column(name = "stream_namespace", length = 256)
    private String streamNamespace;

    @Column(name = "system_code", length = 64, nullable = false)
    private String systemCode;

    @Column(name = "biz_code", length = 64, nullable = false)
    private String bizCode;

    @Column(name = "entity_code", length = 128, nullable = false)
    private String entityCode;

    @Column(name = "ods_schema", length = 128, nullable = false)
    private String odsSchema;

    @Column(name = "ods_table", length = 128, nullable = false)
    private String odsTable;

    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "description", length = 512)
    private String description;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(UUID connectionId) {
        this.connectionId = connectionId;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getStreamNamespace() {
        return streamNamespace;
    }

    public void setStreamNamespace(String streamNamespace) {
        this.streamNamespace = streamNamespace;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public String getOdsSchema() {
        return odsSchema;
    }

    public void setOdsSchema(String odsSchema) {
        this.odsSchema = odsSchema;
    }

    public String getOdsTable() {
        return odsTable;
    }

    public void setOdsTable(String odsTable) {
        this.odsTable = odsTable;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
