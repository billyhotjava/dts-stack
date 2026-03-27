package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auditing.retention")
public class AuditRetentionProperties {

    /**
     * Whether automatic audit log cleanup is enabled.
     */
    private boolean enabled = true;

    /**
     * Number of days to retain audit entries. Entries older than this are deleted.
     */
    private int days = 180;

    /**
     * Maximum number of rows to delete per batch iteration (limits lock duration).
     */
    private int batchSize = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
