package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset_security_mapping")
public class CatalogDatasetSecurityMapping extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "data_level_field", length = 128)
    private String dataLevelField;

    @Column(name = "dept_field", length = 128)
    private String deptField;

    @Override
    public UUID getId() {
        return datasetId;
    }

    public void setId(UUID id) {
        this.datasetId = id;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getDataLevelField() {
        return dataLevelField;
    }

    public void setDataLevelField(String dataLevelField) {
        this.dataLevelField = dataLevelField;
    }

    public String getDeptField() {
        return deptField;
    }

    public void setDeptField(String deptField) {
        this.deptField = deptField;
    }
}

