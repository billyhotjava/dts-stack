package com.yuzhi.dts.admin.service.infra.dto;

import java.util.ArrayList;
import java.util.List;

public class IntegrationStatus {

    private String lastSyncAt;

    private String reason;

    private List<String> actions = new ArrayList<>();

    private Integer catalogDatasetCount;

    public String getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(String lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public Integer getCatalogDatasetCount() {
        return catalogDatasetCount;
    }

    public void setCatalogDatasetCount(Integer catalogDatasetCount) {
        this.catalogDatasetCount = catalogDatasetCount;
    }
}
