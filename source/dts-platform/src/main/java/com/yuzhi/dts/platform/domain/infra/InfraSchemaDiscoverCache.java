package com.yuzhi.dts.platform.domain.infra;

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
@Table(name = "infra_schema_discover_cache")
public class InfraSchemaDiscoverCache extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "data_source_id", columnDefinition = "uuid", nullable = false)
    private UUID dataSourceId;

    @Column(name = "cache_key", length = 128, nullable = false)
    private String cacheKey;

    @Column(name = "schema_name", length = 256)
    private String schemaName;

    @Column(name = "table_pattern", length = 256)
    private String tablePattern;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "table_count")
    private Integer tableCount;

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "drift_added_tables")
    private Integer driftAddedTables;

    @Column(name = "drift_removed_tables")
    private Integer driftRemovedTables;

    @Column(name = "drift_changed_tables")
    private Integer driftChangedTables;

    @Column(name = "drift_details_json", columnDefinition = "text")
    private String driftDetailsJson;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    @Column(name = "refreshed_at")
    private Instant refreshedAt;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(UUID dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTablePattern() {
        return tablePattern;
    }

    public void setTablePattern(String tablePattern) {
        this.tablePattern = tablePattern;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTableCount() {
        return tableCount;
    }

    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    public Integer getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }

    public Integer getDriftAddedTables() {
        return driftAddedTables;
    }

    public void setDriftAddedTables(Integer driftAddedTables) {
        this.driftAddedTables = driftAddedTables;
    }

    public Integer getDriftRemovedTables() {
        return driftRemovedTables;
    }

    public void setDriftRemovedTables(Integer driftRemovedTables) {
        this.driftRemovedTables = driftRemovedTables;
    }

    public Integer getDriftChangedTables() {
        return driftChangedTables;
    }

    public void setDriftChangedTables(Integer driftChangedTables) {
        this.driftChangedTables = driftChangedTables;
    }

    public String getDriftDetailsJson() {
        return driftDetailsJson;
    }

    public void setDriftDetailsJson(String driftDetailsJson) {
        this.driftDetailsJson = driftDetailsJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(Instant refreshedAt) {
        this.refreshedAt = refreshedAt;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
