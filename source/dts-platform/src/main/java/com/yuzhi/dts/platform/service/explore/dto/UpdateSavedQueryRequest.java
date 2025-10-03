package com.yuzhi.dts.platform.service.explore.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating a saved query. Fields are optional but validated when present.
 */
public class UpdateSavedQueryRequest {

    @Size(max = 128)
    private String name;

    @Size(max = 4096)
    private String sqlText;

    @Size(max = 64)
    private String datasetId;

    @JsonIgnore
    private boolean datasetIdPresent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getDatasetId() {
        return datasetId;
    }

    @JsonSetter("datasetId")
    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
        this.datasetIdPresent = true;
    }

    @JsonIgnore
    public boolean isDatasetIdPresent() {
        return datasetIdPresent;
    }
}
