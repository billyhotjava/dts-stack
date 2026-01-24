package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.ingestion")
public class IngestionProperties {

    private boolean enabled = true;
    private String trustedServiceName = "dts-platform";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTrustedServiceName() {
        return trustedServiceName;
    }

    public void setTrustedServiceName(String trustedServiceName) {
        this.trustedServiceName = trustedServiceName;
    }
}
