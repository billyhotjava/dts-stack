package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics")
public class DtsAnalyticsProperties {

    private boolean enabled = true;
    private String baseUrl = "http://dts-analytics:3000";
    private String serviceName = "dts-platform";
    private String serviceToken;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 20;

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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
