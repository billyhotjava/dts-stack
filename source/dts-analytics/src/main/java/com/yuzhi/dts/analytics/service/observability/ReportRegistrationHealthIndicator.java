package com.yuzhi.dts.analytics.service.observability;

import com.yuzhi.dts.analytics.repository.AnalyticsReportRegistrationOutboxRepository;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("biReportRegistration")
public class ReportRegistrationHealthIndicator implements HealthIndicator {

    private static final long DOWN_BACKLOG = 100L;
    private final AnalyticsReportRegistrationOutboxRepository outbox;

    public ReportRegistrationHealthIndicator(AnalyticsReportRegistrationOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public Health health() {
        try {
            long backlog = outbox.countByStatusIn(List.of("PENDING", "FAILED"));
            Health.Builder result = backlog >= DOWN_BACKLOG ? Health.down() : Health.up();
            return result
                .withDetail("actionableBacklog", backlog)
                .withDetail("downThreshold", DOWN_BACKLOG)
                .build();
        } catch (RuntimeException failure) {
            return Health.down(failure).withDetail("reason", "registration outbox unavailable").build();
        }
    }
}
