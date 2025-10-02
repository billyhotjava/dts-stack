package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.catalog")
public class CatalogFeatureProperties {

    /**
     * Whether catalog module allows selecting multiple upstream data sources.
     * Default false: only the bundled StarRocks TDS / Hive(Inceptor) source is exposed.
     */
    private boolean multiSourceEnabled = false;

    /**
     * Default source type used when multi-source is disabled.
     */
    private String defaultSourceType = "HIVE";

    public boolean isMultiSourceEnabled() {
        return multiSourceEnabled;
    }

    public void setMultiSourceEnabled(boolean multiSourceEnabled) {
        this.multiSourceEnabled = multiSourceEnabled;
    }

    public String getDefaultSourceType() {
        return defaultSourceType;
    }

    public void setDefaultSourceType(String defaultSourceType) {
        this.defaultSourceType = defaultSourceType;
    }
}
