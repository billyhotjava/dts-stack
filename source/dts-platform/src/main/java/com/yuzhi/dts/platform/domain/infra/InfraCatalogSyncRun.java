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
@Table(name = "infra_catalog_sync_run")
public class InfraCatalogSyncRun extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "integration", length = 32, nullable = false)
    private String integration;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "catalog_dataset_count_before")
    private Long catalogDatasetCountBefore;

    @Column(name = "catalog_dataset_count_after")
    private Long catalogDatasetCountAfter;

    @Column(name = "tables_discovered")
    private Integer tablesDiscovered;

    @Column(name = "datasets_created")
    private Integer datasetsCreated;

    @Column(name = "datasets_updated")
    private Integer datasetsUpdated;

    @Column(name = "datasets_removed")
    private Integer datasetsRemoved;

    @Column(name = "tables_created")
    private Integer tablesCreated;

    @Column(name = "columns_imported")
    private Integer columnsImported;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getCatalogDatasetCountBefore() {
        return catalogDatasetCountBefore;
    }

    public void setCatalogDatasetCountBefore(Long catalogDatasetCountBefore) {
        this.catalogDatasetCountBefore = catalogDatasetCountBefore;
    }

    public Long getCatalogDatasetCountAfter() {
        return catalogDatasetCountAfter;
    }

    public void setCatalogDatasetCountAfter(Long catalogDatasetCountAfter) {
        this.catalogDatasetCountAfter = catalogDatasetCountAfter;
    }

    public Integer getTablesDiscovered() {
        return tablesDiscovered;
    }

    public void setTablesDiscovered(Integer tablesDiscovered) {
        this.tablesDiscovered = tablesDiscovered;
    }

    public Integer getDatasetsCreated() {
        return datasetsCreated;
    }

    public void setDatasetsCreated(Integer datasetsCreated) {
        this.datasetsCreated = datasetsCreated;
    }

    public Integer getDatasetsUpdated() {
        return datasetsUpdated;
    }

    public void setDatasetsUpdated(Integer datasetsUpdated) {
        this.datasetsUpdated = datasetsUpdated;
    }

    public Integer getDatasetsRemoved() {
        return datasetsRemoved;
    }

    public void setDatasetsRemoved(Integer datasetsRemoved) {
        this.datasetsRemoved = datasetsRemoved;
    }

    public Integer getTablesCreated() {
        return tablesCreated;
    }

    public void setTablesCreated(Integer tablesCreated) {
        this.tablesCreated = tablesCreated;
    }

    public Integer getColumnsImported() {
        return columnsImported;
    }

    public void setColumnsImported(Integer columnsImported) {
        this.columnsImported = columnsImported;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

