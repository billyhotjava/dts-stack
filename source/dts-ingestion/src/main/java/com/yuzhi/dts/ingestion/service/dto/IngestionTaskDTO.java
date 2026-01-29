package com.yuzhi.dts.ingestion.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO for {@link com.yuzhi.dts.ingestion.domain.IngestionTask}
 */
public class IngestionTaskDTO {

    private Long id;

    @NotNull
    @Size(min = 1, max = 200)
    private String name;

    private String description;

    @NotNull
    @Size(min = 1, max = 50)
    private String sourceType;

    private JsonNode sourceConfig;

    private java.util.UUID sourceDataSourceId;

    private String destinationType;

    private JsonNode destinationConfig;

    @NotNull
    @Size(min = 1, max = 50)
    private String syncMode;

    private String syncSchedule;

    private String syncPrefix;

    private JsonNode tableMapping;

    private String addaxJobPath;

    private JsonNode addaxConfig;

    private Boolean airflowEnabled;

    private String airflowDagId;

    private String dbtModelSelector;

    private String dbtDagSelector;

    private String status;

    private Instant lastExecutedAt;

    private String lastExecutionStatus;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public JsonNode getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(JsonNode sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public java.util.UUID getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(java.util.UUID sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public JsonNode getDestinationConfig() {
        return destinationConfig;
    }

    public void setDestinationConfig(JsonNode destinationConfig) {
        this.destinationConfig = destinationConfig;
    }

    public String getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(String syncMode) {
        this.syncMode = syncMode;
    }

    public String getSyncSchedule() {
        return syncSchedule;
    }

    public void setSyncSchedule(String syncSchedule) {
        this.syncSchedule = syncSchedule;
    }

    public String getSyncPrefix() {
        return syncPrefix;
    }

    public void setSyncPrefix(String syncPrefix) {
        this.syncPrefix = syncPrefix;
    }

    public JsonNode getTableMapping() {
        return tableMapping;
    }

    public void setTableMapping(JsonNode tableMapping) {
        this.tableMapping = tableMapping;
    }

    public String getAddaxJobPath() {
        return addaxJobPath;
    }

    public void setAddaxJobPath(String addaxJobPath) {
        this.addaxJobPath = addaxJobPath;
    }

    public JsonNode getAddaxConfig() {
        return addaxConfig;
    }

    public void setAddaxConfig(JsonNode addaxConfig) {
        this.addaxConfig = addaxConfig;
    }

    public Boolean getAirflowEnabled() {
        return airflowEnabled;
    }

    public void setAirflowEnabled(Boolean airflowEnabled) {
        this.airflowEnabled = airflowEnabled;
    }

    public String getAirflowDagId() {
        return airflowDagId;
    }

    public void setAirflowDagId(String airflowDagId) {
        this.airflowDagId = airflowDagId;
    }

    public String getDbtModelSelector() {
        return dbtModelSelector;
    }

    public void setDbtModelSelector(String dbtModelSelector) {
        this.dbtModelSelector = dbtModelSelector;
    }

    public String getDbtDagSelector() {
        return dbtDagSelector;
    }

    public void setDbtDagSelector(String dbtDagSelector) {
        this.dbtDagSelector = dbtDagSelector;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }

    public void setLastExecutionStatus(String lastExecutionStatus) {
        this.lastExecutionStatus = lastExecutionStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    @Override
    public String toString() {
        return "IngestionTaskDTO{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", sourceType='" + sourceType + '\'' +
            ", syncMode='" + syncMode + '\'' +
            ", status='" + status + '\'' +
            '}';
    }
}
