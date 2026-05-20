package com.yuzhi.dts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.admin")
public class DtsAdminProperties {

    /** Enable forwarding audit events to dts-admin. */
    private boolean enabled = true;

    /** Base URL to reach dts-admin service, e.g. http://dts-admin:8081. */
    private String baseUrl = "http://dts-admin:8081";

    /** Relative path for public API on dts-admin. */
    private String apiPath = "/api";

    /** Optional service token presented when calling dts-admin (audit ingest etc.). */
    private String serviceToken;

    /** Logical service name announced via the X-DTS-Service header. */
    private String serviceName = "dts-analytics";

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

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
