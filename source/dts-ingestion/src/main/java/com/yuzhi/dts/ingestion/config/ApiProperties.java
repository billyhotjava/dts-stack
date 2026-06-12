package com.yuzhi.dts.ingestion.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dts.ingestion.api")
public class ApiProperties {

    public static final String DEFAULT_TABLE_PREFIX = "ods_api_";

    @Min(1)
    private int maxPages = 1000;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(60);

    @NotNull
    private Duration executionTimeout = Duration.ofMinutes(30);

    @NotNull
    private DataSize maxResponseBytes = DataSize.ofMegabytes(32);

    @NotBlank
    private String tablePrefix = DEFAULT_TABLE_PREFIX;

    private boolean allowHttp = false;

    private List<String> allowedHosts = new ArrayList<>();

    @Valid
    private final Retry retry = new Retry();

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    @Valid
    private final Landing landing = new Landing();

    @Valid
    private final Executor executor = new Executor();

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public DataSize getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(DataSize maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public boolean isAllowHttp() {
        return allowHttp;
    }

    public void setAllowHttp(boolean allowHttp) {
        this.allowHttp = allowHttp;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : new ArrayList<>(allowedHosts);
    }

    public Retry getRetry() {
        return retry;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Landing getLanding() {
        return landing;
    }

    public Executor getExecutor() {
        return executor;
    }

    public int maxResponseBytesAsInt() {
        long bytes = maxResponseBytes == null ? 0L : maxResponseBytes.toBytes();
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    @AssertTrue(message = "connect-timeout must be positive")
    public boolean isConnectTimeoutPositive() {
        return isPositive(connectTimeout);
    }

    @AssertTrue(message = "read-timeout must be positive")
    public boolean isReadTimeoutPositive() {
        return isPositive(readTimeout);
    }

    @AssertTrue(message = "execution-timeout must be positive")
    public boolean isExecutionTimeoutPositive() {
        return isPositive(executionTimeout);
    }

    @AssertTrue(message = "max-response-bytes must be positive and fit in int")
    public boolean isMaxResponseBytesValid() {
        long bytes = maxResponseBytes == null ? 0L : maxResponseBytes.toBytes();
        return bytes > 0 && bytes <= Integer.MAX_VALUE;
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    public static class Retry {

        @Min(0)
        private int maxRetries = 3;

        @NotNull
        private Duration baseBackoff = Duration.ofMillis(200);

        @NotNull
        private Duration backoffCap = Duration.ofSeconds(30);

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getBaseBackoff() {
            return baseBackoff;
        }

        public void setBaseBackoff(Duration baseBackoff) {
            this.baseBackoff = baseBackoff;
        }

        public Duration getBackoffCap() {
            return backoffCap;
        }

        public void setBackoffCap(Duration backoffCap) {
            this.backoffCap = backoffCap;
        }

        @AssertTrue(message = "retry.base-backoff must be positive")
        public boolean isBaseBackoffPositive() {
            return ApiProperties.isPositive(baseBackoff);
        }

        @AssertTrue(message = "retry.backoff-cap must be positive")
        public boolean isBackoffCapPositive() {
            return ApiProperties.isPositive(backoffCap);
        }
    }

    public static class RateLimit {

        @Min(0)
        private int defaultRps = 0;

        @Min(1)
        private int burstMultiplier = 2;

        @Min(1)
        private int maxConcurrency = 1;

        public int getDefaultRps() {
            return defaultRps;
        }

        public void setDefaultRps(int defaultRps) {
            this.defaultRps = defaultRps;
        }

        public int getBurstMultiplier() {
            return burstMultiplier;
        }

        public void setBurstMultiplier(int burstMultiplier) {
            this.burstMultiplier = burstMultiplier;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }
    }

    public static class Landing {

        @Min(1)
        private int batchSize = 500;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Executor {

        @Min(1)
        private int poolSize = 4;

        @Min(1)
        private int perTaskConcurrency = 1;

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public int getPerTaskConcurrency() {
            return perTaskConcurrency;
        }

        public void setPerTaskConcurrency(int perTaskConcurrency) {
            this.perTaskConcurrency = perTaskConcurrency;
        }
    }
}
