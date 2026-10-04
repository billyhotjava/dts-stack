package com.yuzhi.dts.analytics.service.observability;

import com.yuzhi.dts.analytics.config.AnalyticsBiFeatureProperties;
import com.yuzhi.dts.analytics.repository.AnalyticsReportRegistrationOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BiObservabilityMetrics {

    private final MeterRegistry registry;

    public BiObservabilityMetrics(
        MeterRegistry registry,
        AnalyticsReportRegistrationOutboxRepository outbox,
        AnalyticsBiFeatureProperties features
    ) {
        this.registry = registry;
        Gauge.builder("analytics.report.registration.backlog", outbox, this::backlog)
            .description("Pending or failed BI report registration events")
            .register(registry);
        Gauge.builder("analytics.bi.feature.enabled", features, value -> value.isGovernedBiEnabled() ? 1 : 0)
            .tag("feature", "governed_bi")
            .register(registry);
        Gauge.builder("analytics.bi.feature.enabled", features, value -> value.isLegacyCardWriteEnabled() ? 1 : 0)
            .tag("feature", "legacy_card_write")
            .register(registry);
    }

    public void recordHttp(String surface, String operation, int status, long durationNanos) {
        String outcome = status < 400 ? "success" : status < 500 ? "client_error" : "server_error";
        Counter.builder("analytics.bi.http.total")
            .tag("surface", surface)
            .tag("operation", operation)
            .tag("outcome", outcome)
            .tag("status", String.valueOf(status))
            .register(registry)
            .increment();
        Timer.builder("analytics.bi.http.duration")
            .tag("surface", surface)
            .tag("operation", operation)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(registry)
            .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
        if (status == 401 || status == 403) {
            Counter.builder("analytics.bi.unauthorized.total")
                .tag("surface", surface)
                .tag("status", String.valueOf(status))
                .register(registry)
                .increment();
        }
    }

    public void recordLegacyCall(String surface, String operation) {
        Counter.builder("analytics.bi.legacy.calls")
            .tag("surface", surface)
            .tag("operation", operation)
            .register(registry)
            .increment();
    }

    public void recordPublication(String assetType, String outcome, int status) {
        Counter.builder("analytics.bi.publication.total")
            .tag("asset_type", assetType)
            .tag("outcome", outcome)
            .tag("status", String.valueOf(status))
            .register(registry)
            .increment();
    }

    public void recordRegistration(String outcome, String errorCode) {
        Counter.builder("analytics.report.registration.total")
            .tag("outcome", outcome)
            .tag("error_code", safe(errorCode, "NONE"))
            .register(registry)
            .increment();
    }

    private double backlog(AnalyticsReportRegistrationOutboxRepository outbox) {
        try {
            return outbox.countByStatusIn(List.of("PENDING", "FAILED"));
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
