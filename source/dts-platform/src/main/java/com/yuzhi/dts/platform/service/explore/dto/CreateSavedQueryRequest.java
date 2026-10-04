package com.yuzhi.dts.platform.service.explore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a saved query from the explore module.
 */
public class CreateSavedQueryRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 4096)
    private String sqlText;

    @Size(max = 64)
    private String datasetId;

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

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }
}
