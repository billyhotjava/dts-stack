package com.yuzhi.dts.platform.service.governance.request;

import java.util.List;
import java.util.UUID;

public class QualityRuleBindingRequest {

    private UUID datasetId;
    private String datasetAlias;
    private String scopeType;
    private List<String> fieldRefs;
    private String filterExpression;
    private String scheduleOverride;

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
}

