package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.ingestion")
public class DtsIngestionProperties {

    private boolean enabled = true;
    private String baseUrl = "http://dts-ingestion:8083";
    private String serviceName = "dts-platform";

    private final Retry retry = new Retry();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Retry getRetry() {
        return retry;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public static class Retry {
        /** Maximum number of retry attempts (excluding initial call). */
        private int maxAttempts = 3;
        /** Initial wait between retries in milliseconds. */
        private long waitDurationMs = 1000;
        /** Multiplier for exponential backoff between retries. */
        private double multiplier = 2.0;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getWaitDurationMs() { return waitDurationMs; }
        public void setWaitDurationMs(long waitDurationMs) { this.waitDurationMs = waitDurationMs; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
    }

    public static class CircuitBreaker {
        /** Failure rate threshold (percent) to open the circuit. */
        private int failureRateThreshold = 50;
        /** Number of calls in the sliding window. */
        private int slidingWindowSize = 10;
        /** Seconds to wait in open state before transitioning to half-open. */
        private long waitDurationInOpenStateSeconds = 30;
        /** Number of permitted calls in half-open state. */
        private int permittedCallsInHalfOpenState = 3;

        public int getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(int failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public long getWaitDurationInOpenStateSeconds() { return waitDurationInOpenStateSeconds; }
        public void setWaitDurationInOpenStateSeconds(long waitDurationInOpenStateSeconds) { this.waitDurationInOpenStateSeconds = waitDurationInOpenStateSeconds; }
        public int getPermittedCallsInHalfOpenState() { return permittedCallsInHalfOpenState; }
        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) { this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState; }
    }
}
