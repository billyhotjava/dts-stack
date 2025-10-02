package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class QualityRuleBindingDto {

    private UUID id;
    private UUID ruleVersionId;
    private UUID datasetId;
    private String datasetAlias;
    private String scopeType;
    private List<String> fieldRefs;
    private String filterExpression;
    private String scheduleOverride;
    private Instant createdDate;
    private String createdBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(UUID ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetAlias() {
        return datasetAlias;
    }

    public void setDatasetAlias(String datasetAlias) {
        this.datasetAlias = datasetAlias;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public List<String> getFieldRefs() {
        return fieldRefs;
    }

    public void setFieldRefs(List<String> fieldRefs) {
        this.fieldRefs = fieldRefs;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public String getScheduleOverride() {
        return scheduleOverride;
    }

    public void setScheduleOverride(String scheduleOverride) {
        this.scheduleOverride = scheduleOverride;
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
}

