package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.ingestion")
public class IngestionProperties {

    private boolean enabled = true;
    /**
     * Comma-separated service names allowed via X-DTS-Service header (case-insensitive).
     */
    private String trustedServiceName = "dts-platform";
    /** Pairwise credential required from trusted callers before forwarded identity is accepted. */
    private String trustedServiceToken;

    private final AutoRetry autoRetry = new AutoRetry();

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

    public String getTrustedServiceToken() {
        return trustedServiceToken;
    }

    public void setTrustedServiceToken(String trustedServiceToken) {
        this.trustedServiceToken = trustedServiceToken;
    }

    public AutoRetry getAutoRetry() {
        return autoRetry;
    }

    public static class AutoRetry {
        /** Enable automatic retry of failed executions. */
        private boolean enabled = true;
        /** Maximum number of automatic retries per execution. */
        private int maxRetries = 3;
        /** Initial delay before first retry in seconds. */
        private long initialDelaySeconds = 60;
        /** Maximum backoff delay in seconds. */
        private long maxDelaySeconds = 3600;
        /** Exponential backoff multiplier. */
        private double backoffMultiplier = 2.0;
        /** Comma-separated failure categories eligible for auto-retry. */
        private String retryableCategories = "CONNECTION_ERROR,GOVERNANCE_QUEUE_TIMEOUT,RUNTIME_ERROR";
        /** How often the retry scheduler scans for due retries (ms). */
        private long scanIntervalMs = 30000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public long getInitialDelaySeconds() { return initialDelaySeconds; }
        public void setInitialDelaySeconds(long initialDelaySeconds) { this.initialDelaySeconds = initialDelaySeconds; }
        public long getMaxDelaySeconds() { return maxDelaySeconds; }
        public void setMaxDelaySeconds(long maxDelaySeconds) { this.maxDelaySeconds = maxDelaySeconds; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
        public String getRetryableCategories() { return retryableCategories; }
        public void setRetryableCategories(String retryableCategories) { this.retryableCategories = retryableCategories; }
        public long getScanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

        public java.util.Set<String> getRetryableCategorySet() {
            if (retryableCategories == null || retryableCategories.isBlank()) {
                return java.util.Set.of();
            }
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            for (String cat : retryableCategories.split(",")) {
                String trimmed = cat.trim();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
            return set;
        }
    }
}
