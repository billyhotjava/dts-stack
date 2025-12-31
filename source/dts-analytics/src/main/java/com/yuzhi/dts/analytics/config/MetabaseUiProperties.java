package com.yuzhi.dts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metabase.ui", ignoreUnknownFields = false)
public record MetabaseUiProperties(String targetVersion, String bundleVersion) {

    public MetabaseUiProperties() {
        this("0.45.6", "0.45.4.3");
    }
}

