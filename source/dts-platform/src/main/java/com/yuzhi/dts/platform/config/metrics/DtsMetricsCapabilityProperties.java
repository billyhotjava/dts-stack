package com.yuzhi.dts.platform.config.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.metrics")
public class DtsMetricsCapabilityProperties {

    private String edition = "foundation";

    private String apiBasePath = "/api/metrics";

    private String serviceName = "dts-metrics";

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
