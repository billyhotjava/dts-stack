package com.yuzhi.dts.admin.service.infra.dto;

import java.util.HashMap;
import java.util.Map;

public class PlatformDataLakeDestinationUpdateRequest {

    private String destinationId;
    private String destinationName;
    private String destinationDefinitionId;
    private Map<String, Object> destinationConfig = new HashMap<>();

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getDestinationDefinitionId() {
        return destinationDefinitionId;
    }

    public void setDestinationDefinitionId(String destinationDefinitionId) {
        this.destinationDefinitionId = destinationDefinitionId;
    }

    public Map<String, Object> getDestinationConfig() {
        return destinationConfig;
    }

    public void setDestinationConfig(Map<String, Object> destinationConfig) {
        this.destinationConfig = destinationConfig != null ? new HashMap<>(destinationConfig) : new HashMap<>();
    }
}
