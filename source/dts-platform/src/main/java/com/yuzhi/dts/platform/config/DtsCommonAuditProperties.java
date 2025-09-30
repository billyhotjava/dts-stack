package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.common.audit")
public class DtsCommonAuditProperties {
    /** Whether to forward audit events to dts-common service */
    private boolean enabled = false;
    /** Base URL of dts-common, e.g., http://dts-common:8080 */
    private String baseUrl;
    /** Optional service token for calling dts-common */
    private String serviceToken;

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

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}

