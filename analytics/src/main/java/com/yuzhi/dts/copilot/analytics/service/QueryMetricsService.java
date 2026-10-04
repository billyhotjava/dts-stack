package com.yuzhi.dts.copilot.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class QueryMetricsService {

    private final MeterRegistry meterRegistry;

    public QueryMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String chain, String result, String errorCode, long durationNanos) {
        String safeChain = sanitize(chain, "unknown");
        String safeResult = sanitize(result, "unknown");
        String safeErrorCode = sanitize(errorCode, "NONE");

        Timer.builder("analytics.query.duration")
                .description("Latency of analytics query chains")
                .tag("chain", safeChain)
                .tag("result", safeResult)
                .tag("error_code", safeErrorCode)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(durationNanos, 0L), TimeUnit.NANOSECONDS);

        Counter.builder("analytics.query.total")
                .description("Total count of analytics query chains")
                .tag("chain", safeChain)
                .tag("result", safeResult)
                .tag("error_code", safeErrorCode)
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
