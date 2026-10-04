package com.yuzhi.dts.analytics.service.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.config.AnalyticsBiFeatureProperties;
import com.yuzhi.dts.analytics.repository.AnalyticsReportRegistrationOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class BiObservabilityMetricsTest {

    @Test
    void exportsBoundedPublicationLegacyAndBacklogSignals() {
        AnalyticsReportRegistrationOutboxRepository outbox = mock(AnalyticsReportRegistrationOutboxRepository.class);
        when(outbox.countByStatusIn(List.of("PENDING", "FAILED"))).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalyticsBiFeatureProperties features = new AnalyticsBiFeatureProperties();
        BiObservabilityMetrics metrics = new BiObservabilityMetrics(registry, outbox, features);

        metrics.recordHttp("governed_dashboard", "POST", 503, 10L);
        metrics.recordLegacyCall("legacy_card", "POST");
        metrics.recordPublication("dashboard", "failure", 503);
        metrics.recordRegistration("failure", "ConnectException");

        assertThat(registry.get("analytics.bi.http.total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("analytics.bi.legacy.calls").counter().count()).isEqualTo(1D);
        assertThat(registry.get("analytics.bi.publication.total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("analytics.report.registration.total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("analytics.report.registration.backlog").gauge().value()).isEqualTo(3D);
    }

    @Test
    void healthTurnsDownAtTheDocumentedBacklogThreshold() {
        AnalyticsReportRegistrationOutboxRepository outbox = mock(AnalyticsReportRegistrationOutboxRepository.class);
        when(outbox.countByStatusIn(List.of("PENDING", "FAILED"))).thenReturn(100L);

        assertThat(new ReportRegistrationHealthIndicator(outbox).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
