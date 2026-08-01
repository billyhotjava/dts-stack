package com.yuzhi.dts.ingestion.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.ingestion")
public class IngestionProperties {

    private boolean enabled = true;
    /** Pairwise inbound credential keyed by the exact producer service identity. */
    private Map<String, String> trustedServiceTokens = new LinkedHashMap<>();

    private final AutoRetry autoRetry = new AutoRetry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getTrustedServiceTokens() {
        return trustedServiceTokens;
    }

    public void setTrustedServiceTokens(Map<String, String> trustedServiceTokens) {
        this.trustedServiceTokens = trustedServiceTokens == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(trustedServiceTokens);
    }

    public void validateTrustedServiceTokens() {
        Set<String> supportedServices = Set.of("dts-platform", "dts-airflow");
        Set<String> uniqueTokens = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : trustedServiceTokens.entrySet()) {
            String service = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
            if (!supportedServices.contains(service)) {
                throw new IllegalStateException("Unsupported ingestion service credential: " + service);
            }
            String token = entry.getValue();
            if (token == null || token.isBlank()) {
                continue;
            }
            if (!token.equals(token.trim()) || token.length() < 32) {
                throw new IllegalStateException("Ingestion service credential must contain at least 32 characters: " + service);
            }
            if (!uniqueTokens.add(token)) {
                throw new IllegalStateException("Ingestion service credentials must be pairwise unique");
            }
        }
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
