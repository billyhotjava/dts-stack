package com.yuzhi.dts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics.bi-features")
public class AnalyticsBiFeatureProperties {

    private boolean governedBiEnabled = true;
    private boolean legacyCardWriteEnabled = true;

    public boolean isGovernedBiEnabled() {
        return governedBiEnabled;
    }

    public void setGovernedBiEnabled(boolean governedBiEnabled) {
        this.governedBiEnabled = governedBiEnabled;
    }

    public boolean isLegacyCardWriteEnabled() {
        return legacyCardWriteEnabled;
    }

    public void setLegacyCardWriteEnabled(boolean legacyCardWriteEnabled) {
        this.legacyCardWriteEnabled = legacyCardWriteEnabled;
    }
}
