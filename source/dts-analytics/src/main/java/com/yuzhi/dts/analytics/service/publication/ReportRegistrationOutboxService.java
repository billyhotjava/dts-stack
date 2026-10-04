package com.yuzhi.dts.analytics.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsReportRegistrationOutbox;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsReportRegistrationOutboxRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.observability.BiObservabilityMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReportRegistrationOutboxService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportRegistrationOutboxService.class);
    private static final String AGGREGATE_DASHBOARD = "DASHBOARD";

    private final AnalyticsReportRegistrationOutboxRepository outbox;
    private final AnalyticsDashboardRepository dashboards;
    private final AnalyticsRevisionRepository revisions;
    private final PlatformReportRegistrationClient client;
    private final ObjectMapper objectMapper;
    private final BiObservabilityMetrics metrics;

    public ReportRegistrationOutboxService(
        AnalyticsReportRegistrationOutboxRepository outbox,
        AnalyticsDashboardRepository dashboards,
        AnalyticsRevisionRepository revisions,
        PlatformReportRegistrationClient client,
        ObjectMapper objectMapper,
        BiObservabilityMetrics metrics
    ) {
        this.outbox = outbox;
        this.dashboards = dashboards;
        this.revisions = revisions;
        this.client = client;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public AnalyticsReportRegistrationOutbox enqueueDashboard(
        long dashboardId,
        long assetVersion,
        String eventType,
        ReportRegistrationCommand command
    ) {
        AnalyticsReportRegistrationOutbox existing = outbox
            .findByAggregateTypeAndAggregateIdAndAssetVersionAndEventType(
                AGGREGATE_DASHBOARD, dashboardId, assetVersion, eventType
            )
            .orElse(null);
        if (existing != null) return existing;
        AnalyticsReportRegistrationOutbox event = new AnalyticsReportRegistrationOutbox();
        event.setId(UUID.randomUUID());
        event.setAggregateType(AGGREGATE_DASHBOARD);
        event.setAggregateId(dashboardId);
        event.setAssetVersion(assetVersion);
        event.setEventType(eventType);
        event.setPayloadJson(write(command));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        return outbox.save(event);
    }

    @Transactional
    public boolean retryDashboard(long dashboardId) {
        AnalyticsDashboard dashboard = dashboards.findById(dashboardId).orElse(null);
        if (dashboard == null || dashboard.getPublishedRevisionId() == null) return false;
        Long publishedVersion = revisions.findById(dashboard.getPublishedRevisionId())
            .map(revision -> revision.getVersionNo() == null ? null : revision.getVersionNo().longValue())
            .orElse(null);
        if (publishedVersion == null) return false;
        AnalyticsReportRegistrationOutbox event = outbox
            .findFirstByAggregateTypeAndAggregateIdAndAssetVersionOrderByCreatedAtDesc(
                AGGREGATE_DASHBOARD, dashboardId, publishedVersion
            )
            .orElse(null);
        if (event == null || "SUCCEEDED".equals(event.getStatus())) return false;
        event.setStatus("PENDING");
        event.setLastError(null);
        event.setNextAttemptAt(Instant.now());
        outbox.save(event);
        dashboard.setRegistrationStatus("PENDING_REGISTRATION");
        dashboards.save(dashboard);
        return true;
    }

    @Scheduled(fixedDelayString = "${analytics.report-registration.reconcile-delay-ms:30000}")
    @Transactional
    public void reconcile() {
        List<AnalyticsReportRegistrationOutbox> pending = outbox
            .findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of("PENDING", "FAILED"), Instant.now()
            );
        for (AnalyticsReportRegistrationOutbox event : pending) {
            process(event);
        }
    }

    private void process(AnalyticsReportRegistrationOutbox event) {
        ReportRegistrationCommand command = null;
        try {
            command = objectMapper.readValue(
                event.getPayloadJson(), ReportRegistrationCommand.class
            );
            client.register(command);
            event.setStatus("SUCCEEDED");
            event.setLastError(null);
            updateDashboardRegistration(event, command.enabled() ? "AVAILABLE" : "DISABLED");
            metrics.recordRegistration("success", "NONE");
            LOG.info(
                "BI report registration reconciled eventId={} assetKey={} version={} outcome=SUCCESS",
                event.getId(), command.assetKey(), event.getAssetVersion()
            );
        } catch (Exception failure) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setStatus("FAILED");
            event.setLastError(safeError(failure));
            long delaySeconds = Math.min(300, 5L << Math.min(attempts, 6));
            event.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
            updateDashboardRegistration(event, "REGISTRATION_FAILED");
            metrics.recordRegistration("failure", failure.getClass().getSimpleName());
            LOG.warn(
                "BI report registration reconcile failed eventId={} aggregate={}:{} assetKey={} version={} attempts={} error={}",
                event.getId(), event.getAggregateType(), event.getAggregateId(),
                command == null ? "unknown" : command.assetKey(), event.getAssetVersion(), attempts, event.getLastError()
            );
        }
        outbox.save(event);
    }

    private void updateDashboardRegistration(AnalyticsReportRegistrationOutbox event, String status) {
        if (!AGGREGATE_DASHBOARD.equals(event.getAggregateType())) return;
        AnalyticsDashboard dashboard = dashboards.findById(event.getAggregateId()).orElse(null);
        if (dashboard == null || dashboard.getPublishedRevisionId() == null) return;
        boolean isCurrentPublishedVersion = revisions.findById(dashboard.getPublishedRevisionId())
            .map(revision -> revision.getVersionNo() != null
                && revision.getVersionNo().longValue() == event.getAssetVersion())
            .orElse(false);
        if (!isCurrentPublishedVersion) {
            LOG.info(
                "Ignore stale BI registration state eventId={} dashboardId={} assetVersion={}",
                event.getId(), event.getAggregateId(), event.getAssetVersion()
            );
            return;
        }
        dashboard.setRegistrationStatus(status);
        dashboards.save(dashboard);
    }

    private String write(ReportRegistrationCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception failure) {
            throw new IllegalArgumentException("report registration command cannot be serialized", failure);
        }
    }

    private String safeError(Exception failure) {
        String value = failure.getMessage();
        if (!StringUtils.hasText(value)) value = failure.getClass().getSimpleName();
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
