package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.ingestion")
public class IngestionIntegrationProperties {

    private boolean enabled = true;
    private String baseUrl = "http://dts-ingestion:8083";
    private String infraApiPath = "/api/infra";
    private String serviceName = "dts-admin";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInfraApiPath() {
        return infraApiPath;
    }

    public void setInfraApiPath(String infraApiPath) {
        this.infraApiPath = infraApiPath;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
