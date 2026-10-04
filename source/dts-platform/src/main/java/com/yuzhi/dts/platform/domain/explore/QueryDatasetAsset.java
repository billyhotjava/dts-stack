package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "query_dataset_asset")
public class QueryDatasetAsset extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "source_datasource_id", columnDefinition = "uuid")
    private UUID sourceDatasourceId;

    @Column(name = "source_datasource_name", length = 256)
    private String sourceDatasourceName;

    @Column(name = "source_model_spec_id", columnDefinition = "uuid")
    private UUID sourceModelSpecId;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "DRAFT";

    @Column(name = "refresh_strategy", length = 32, nullable = false)
    private String refreshStrategy = "MANUAL";

    @Column(name = "sql_text", columnDefinition = "text", nullable = false)
    private String sqlText;

    @Column(name = "parameters_json", columnDefinition = "text")
    private String parametersJson;

    @Column(name = "latest_execution_id", columnDefinition = "uuid")
    private UUID latestExecutionId;

    @Column(name = "latest_result_set_id", columnDefinition = "uuid")
    private UUID latestResultSetId;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getSourceDatasourceId() {
        return sourceDatasourceId;
    }

    public void setSourceDatasourceId(UUID sourceDatasourceId) {
        this.sourceDatasourceId = sourceDatasourceId;
    }

    public String getSourceDatasourceName() {
        return sourceDatasourceName;
    }

    public void setSourceDatasourceName(String sourceDatasourceName) {
        this.sourceDatasourceName = sourceDatasourceName;
    }

    public UUID getSourceModelSpecId() {
        return sourceModelSpecId;
    }

    public void setSourceModelSpecId(UUID sourceModelSpecId) {
        this.sourceModelSpecId = sourceModelSpecId;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRefreshStrategy() {
        return refreshStrategy;
    }

    public void setRefreshStrategy(String refreshStrategy) {
        this.refreshStrategy = refreshStrategy;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public UUID getLatestExecutionId() {
        return latestExecutionId;
    }

    public void setLatestExecutionId(UUID latestExecutionId) {
        this.latestExecutionId = latestExecutionId;
    }

    public UUID getLatestResultSetId() {
        return latestResultSetId;
    }

    public void setLatestResultSetId(UUID latestResultSetId) {
        this.latestResultSetId = latestResultSetId;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
