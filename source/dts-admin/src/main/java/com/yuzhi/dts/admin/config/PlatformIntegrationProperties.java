package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform")
public class PlatformIntegrationProperties {

    /** Enable calls back to the dts-platform service. */
    private boolean enabled = true;

    /** Base URL for the platform service, e.g. http://dts-platform:8081 */
    private String baseUrl = "http://dts-platform:8081";

    /** API path prefix, default /api */
    private String apiPath = "/api";

    /** Logical service name presented to platform when performing calls. */
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

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
