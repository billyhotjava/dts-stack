package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.admin")
public class DtsAdminProperties {

    /** Enable calls to dts-admin service for shared resources. */
    private boolean enabled = true;

    /** Base URL to reach dts-admin service, e.g. http://dts-admin:8081. */
    private String baseUrl = "http://dts-admin:8081";

    /** Relative path for public portal APIs on dts-admin. */
    private String apiPath = "/api";

    /** Relative path for admin APIs on dts-admin. */
    private String adminApiPath = "/api/admin";

    /** Optional bearer token when calling dts-admin. */
    private String serviceToken;

    /** Logical service name expected on incoming internal calls. */
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

    public String getAdminApiPath() {
        return adminApiPath;
    }

    public void setAdminApiPath(String adminApiPath) {
        this.adminApiPath = adminApiPath;
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
