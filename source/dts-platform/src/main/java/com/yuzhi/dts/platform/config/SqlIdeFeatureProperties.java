package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reserved for future use — backend does not currently gate behavior on this flag;
 * frontend routing is controlled via webapp-side {@code WEBAPP_ENABLE_SQL_IDE_V2}
 * runtime config (emitted by {@code docker-entrypoint.sh} into
 * {@code window.__RUNTIME_CONFIG__.enableSqlIdeV2}).
 *
 * <p>Retained so that future sprint work (e.g., F4/T16 {@code /api/sql/v2/*} endpoints)
 * can easily add backend flag-gating without a rename. The binding is pinned by
 * {@code SqlIdeFeaturePropertiesIT}.
 *
 * <p>Environment variable: {@code DTS_SQL_IDE_V2_ENABLED} (platform container).
 * Webapp env variable: {@code WEBAPP_ENABLE_SQL_IDE_V2} (webapp container).
 */
@ConfigurationProperties(prefix = "dts.sql-ide.v2")
public class SqlIdeFeatureProperties {

    /** Master switch for the new SQL IDE (Sprint-11). Default false. */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
