package com.yuzhi.dts.copilot.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class PlatformIndicatorMetricsService {

    private final MeterRegistry meterRegistry;

    public PlatformIndicatorMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private PlatformIndicatorMetricsService() {
        this.meterRegistry = null;
    }

    static PlatformIndicatorMetricsService noop() {
        return new PlatformIndicatorMetricsService();
    }

    public void recordApiCall(String operation, String result, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String safeOperation = sanitize(operation, "unknown");
        String safeResult = sanitize(result, "unknown");
        Timer.builder("platform.indicator.api.duration")
                .description("Latency of dts-platform indicator API calls")
                .tag("operation", safeOperation)
                .tag("result", safeResult)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(durationNanos, 0L), TimeUnit.NANOSECONDS);
        Counter.builder("platform.indicator.api.total")
                .description("Total count of dts-platform indicator API calls")
                .tag("operation", safeOperation)
                .tag("result", safeResult)
                .register(meterRegistry)
                .increment();
    }

    public void recordCache(String mode, boolean hit) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("platform.indicator.cache.total")
                .description("Platform indicator value cache hits and misses")
                .tag("mode", sanitize(mode, "unknown"))
                .tag("result", hit ? "hit" : "miss")
                .register(meterRegistry)
                .increment();
    }

    private String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
