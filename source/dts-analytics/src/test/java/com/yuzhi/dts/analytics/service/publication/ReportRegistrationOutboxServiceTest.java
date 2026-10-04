package com.yuzhi.dts.analytics.service.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsReportRegistrationOutbox;
import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsReportRegistrationOutboxRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.observability.BiObservabilityMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportRegistrationOutboxServiceTest {

    @Test
    void staleEventCannotOverwriteTheCurrentDashboardRegistrationState() throws Exception {
        AnalyticsReportRegistrationOutboxRepository outbox = mock(AnalyticsReportRegistrationOutboxRepository.class);
        AnalyticsDashboardRepository dashboards = mock(AnalyticsDashboardRepository.class);
        AnalyticsRevisionRepository revisions = mock(AnalyticsRevisionRepository.class);
        PlatformReportRegistrationClient client = mock(PlatformReportRegistrationClient.class);
        BiObservabilityMetrics metrics = mock(BiObservabilityMetrics.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ReportRegistrationOutboxService service = new ReportRegistrationOutboxService(
            outbox, dashboards, revisions, client, objectMapper, metrics
        );

        ReportRegistrationCommand command = new ReportRegistrationCommand(
            "DTS_BI", "DASHBOARD", "dashboard-22", 1L, "项目驾驶舱", "DASHBOARD",
            "/bi/dashboards/22", null, null, List.of("D1"), List.of(), "DATA_INTERNAL", null, true
        );
        AnalyticsReportRegistrationOutbox event = new AnalyticsReportRegistrationOutbox();
        event.setId(UUID.randomUUID());
        event.setAggregateType("DASHBOARD");
        event.setAggregateId(22L);
        event.setAssetVersion(1L);
        event.setEventType("UPSERT");
        event.setPayloadJson(objectMapper.writeValueAsString(command));
        event.setStatus("PENDING");
        event.setNextAttemptAt(Instant.EPOCH);
        when(outbox.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(anyList(), any()))
            .thenReturn(List.of(event));

        AnalyticsDashboard dashboard = new AnalyticsDashboard();
        dashboard.setId(22L);
        dashboard.setPublishedRevisionId(102L);
        dashboard.setRegistrationStatus("PENDING_REGISTRATION");
        when(dashboards.findById(22L)).thenReturn(Optional.of(dashboard));
        AnalyticsRevision current = new AnalyticsRevision();
        current.setId(102L);
        current.setVersionNo(2);
        when(revisions.findById(102L)).thenReturn(Optional.of(current));

        service.reconcile();

        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(dashboard.getRegistrationStatus()).isEqualTo("PENDING_REGISTRATION");
        verify(client).register(command);
        verify(metrics).recordRegistration("success", "NONE");
        verify(dashboards, never()).save(dashboard);
    }
}
