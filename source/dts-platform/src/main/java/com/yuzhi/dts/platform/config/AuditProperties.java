package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auditing")
public class AuditProperties {

    private boolean enabled = true;
    private TenancyMode tenancyMode;
    private String tenantId;
    private final Outbox outbox = new Outbox();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TenancyMode getTenancyMode() {
        return tenancyMode;
    }

    public void setTenancyMode(TenancyMode tenancyMode) {
        this.tenancyMode = tenancyMode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public enum TenancyMode {
        SINGLE_TENANT,
        MULTI_TENANT,
    }

    public static class Outbox {

        private long dispatchDelayMs = 2_000;
        private int batchSize = 100;
        private long maxDispatchCycleSeconds = 30;
        private long staleClaimSeconds = 120;
        private long initialRetrySeconds = 5;
        private long maxRetrySeconds = 300;
        private int maxAttempts = 20;
        private int retentionDays = 180;
        private int retentionBatchSize = 5_000;

        public long getDispatchDelayMs() {
            return dispatchDelayMs;
        }

        public void setDispatchDelayMs(long dispatchDelayMs) {
            this.dispatchDelayMs = dispatchDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getMaxDispatchCycleSeconds() {
            return maxDispatchCycleSeconds;
        }

        public void setMaxDispatchCycleSeconds(long maxDispatchCycleSeconds) {
            this.maxDispatchCycleSeconds = maxDispatchCycleSeconds;
        }

        public long getStaleClaimSeconds() {
            return staleClaimSeconds;
        }

        public void setStaleClaimSeconds(long staleClaimSeconds) {
            this.staleClaimSeconds = staleClaimSeconds;
        }

        public long getInitialRetrySeconds() {
            return initialRetrySeconds;
        }

        public void setInitialRetrySeconds(long initialRetrySeconds) {
            this.initialRetrySeconds = initialRetrySeconds;
        }

        public long getMaxRetrySeconds() {
            return maxRetrySeconds;
        }

        public void setMaxRetrySeconds(long maxRetrySeconds) {
            this.maxRetrySeconds = maxRetrySeconds;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getRetentionBatchSize() {
            return retentionBatchSize;
        }

        public void setRetentionBatchSize(int retentionBatchSize) {
            this.retentionBatchSize = retentionBatchSize;
        }
    }

}
