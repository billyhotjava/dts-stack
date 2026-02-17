package com.yuzhi.dts.platform.domain.modeling;

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
@Table(name = "modeling_sql_model")
public class ModelingSqlModel extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "plan_id", columnDefinition = "uuid")
    private UUID planId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "alias", length = 128)
    private String alias;

    @Column(name = "layer", length = 16)
    private String layer;

    @Column(name = "source_data_source_id", columnDefinition = "uuid", nullable = false)
    private UUID sourceDataSourceId;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "materialized", length = 32)
    private String materialized;

    @Column(name = "tags", length = 1024)
    private String tags;

    @Column(name = "dag_selector", length = 128)
    private String dagSelector;

    @Column(name = "model_path", length = 512)
    private String modelPath;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "description")
    private String description;

    @Column(name = "sql_text")
    private String sqlText;

    @Column(name = "enabled")
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "semantic_contract", columnDefinition = "text")
    private String semanticContract;

    @Column(name = "contract_version", length = 64)
    private String contractVersion;

    @Column(name = "contract_updated_at")
    private Instant contractUpdatedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public UUID getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(UUID sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getMaterialized() {
        return materialized;
    }

    public void setMaterialized(String materialized) {
        this.materialized = materialized;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDagSelector() {
        return dagSelector;
    }

    public void setDagSelector(String dagSelector) {
        this.dagSelector = dagSelector;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getSemanticContract() {
        return semanticContract;
    }

    public void setSemanticContract(String semanticContract) {
        this.semanticContract = semanticContract;
    }

    public String getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(String contractVersion) {
        this.contractVersion = contractVersion;
    }

    public Instant getContractUpdatedAt() {
        return contractUpdatedAt;
    }

    public void setContractUpdatedAt(Instant contractUpdatedAt) {
        this.contractUpdatedAt = contractUpdatedAt;
    }
}
