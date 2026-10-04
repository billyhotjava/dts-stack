package com.yuzhi.dts.platform.service.visualization.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class BiReportLinkRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String title;

    @NotBlank
    private String url;

    private String engine;

    private String reportType;

    private List<String> deptCodes;

    private List<String> roleCodes;

    @NotBlank
    private String classification;

    private Boolean enabled;

    private Integer sortOrder;

    private String queryDatasetId;

    private Integer queryDatasetVersion;

    private String expiresAt;

    private String bizDomain;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public List<String> getDeptCodes() {
        return deptCodes;
    }

    public void setDeptCodes(List<String> deptCodes) {
        this.deptCodes = deptCodes;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getQueryDatasetId() {
        return queryDatasetId;
    }

    public void setQueryDatasetId(String queryDatasetId) {
        this.queryDatasetId = queryDatasetId;
    }

    public Integer getQueryDatasetVersion() {
        return queryDatasetVersion;
    }

    public void setQueryDatasetVersion(Integer queryDatasetVersion) {
        this.queryDatasetVersion = queryDatasetVersion;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getBizDomain() {
        return bizDomain;
    }

    public void setBizDomain(String bizDomain) {
        this.bizDomain = bizDomain;
    }
}
