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
    private String defaultSourceType = "INCEPTOR";

    /**
     * Schema name in PostgreSQL used when platform falls back to its own OLAP workspace.
     */
    private String postgresSchema = "olap";

    /**
     * Whether to perform Inceptor catalog synchronization. When disabled, the platform
     * falls back to the PostgreSQL metadata extractor.
     */
    private boolean inceptorSyncEnabled = false;

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

    public String getPostgresSchema() {
        return postgresSchema;
    }

    public void setPostgresSchema(String postgresSchema) {
        this.postgresSchema = postgresSchema;
    }

    public boolean isInceptorSyncEnabled() {
        return inceptorSyncEnabled;
    }

    public void setInceptorSyncEnabled(boolean inceptorSyncEnabled) {
        this.inceptorSyncEnabled = inceptorSyncEnabled;
    }
}
