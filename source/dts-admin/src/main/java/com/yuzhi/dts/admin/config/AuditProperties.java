package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auditing")
public class AuditProperties {

    private boolean enabled = true;
    private int queueCapacity = 5000;
    private int retentionDays = 180;
    private String encryptionKey;
    private String hmacKey;
    private boolean forwardEnabled = false;
    private long forwardRetryIntervalMs = 60000;
    private int maxRetryAttempts = 5;
    private long retryBackoffMs = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public boolean isForwardEnabled() {
        return forwardEnabled;
    }

    public void setForwardEnabled(boolean forwardEnabled) {
        this.forwardEnabled = forwardEnabled;
    }

    public long getForwardRetryIntervalMs() {
        return forwardRetryIntervalMs;
    }

    public void setForwardRetryIntervalMs(long forwardRetryIntervalMs) {
        this.forwardRetryIntervalMs = forwardRetryIntervalMs;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }
}
