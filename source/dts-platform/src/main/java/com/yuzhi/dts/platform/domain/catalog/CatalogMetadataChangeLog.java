package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_metadata_change_log")
public class CatalogMetadataChangeLog extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "object_type", length = 16, nullable = false)
    private String objectType; // TABLE/COLUMN

    @Column(name = "object_id", columnDefinition = "uuid", nullable = false)
    private UUID objectId; // table_id or column_id

    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "table_id", columnDefinition = "uuid")
    private UUID tableId; // when objectType=COLUMN

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Lob
    @Column(name = "before_value")
    private String beforeValue;

    @Lob
    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "change_summary", length = 256)
    private String changeSummary;

    @Column(name = "actor_dept", length = 64)
    private String actorDept;

    @Column(name = "source", length = 32)
    private String source; // MANUAL/SYNC/IMPORT

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public void setTableId(UUID tableId) {
        this.tableId = tableId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getActorDept() {
        return actorDept;
    }

    public void setActorDept(String actorDept) {
        this.actorDept = actorDept;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

