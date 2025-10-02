package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class QualityRuleDto {

    private UUID id;
    private String code;
    private String name;
    private String type;
    private String category;
    private String description;
    private String owner;
    private String severity;
    private String dataLevel;
    private String executor;
    private String frequencyCron;
    private Boolean template;
    private Boolean enabled;
    private UUID datasetId;
    private UUID latestVersionId;
    private Instant createdDate;
    private String createdBy;
    private Instant lastModifiedDate;
    private String lastModifiedBy;
    private List<QualityRuleBindingDto> bindings;
    private QualityRuleVersionDto latestVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getFrequencyCron() {
        return frequencyCron;
    }

    public void setFrequencyCron(String frequencyCron) {
        this.frequencyCron = frequencyCron;
    }

    public Boolean getTemplate() {
        return template;
    }

    public void setTemplate(Boolean template) {
        this.template = template;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getLatestVersionId() {
        return latestVersionId;
    }

    public void setLatestVersionId(UUID latestVersionId) {
        this.latestVersionId = latestVersionId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public List<QualityRuleBindingDto> getBindings() {
        return bindings;
    }

    public void setBindings(List<QualityRuleBindingDto> bindings) {
        this.bindings = bindings;
    }

    public QualityRuleVersionDto getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(QualityRuleVersionDto latestVersion) {
        this.latestVersion = latestVersion;
    }
}

