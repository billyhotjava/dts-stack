package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Exposes durable audit delivery backlog and dead-letter depth to platform operations. */
@Component
public class AuditOutboxMetrics {

    public AuditOutboxMetrics(
        MeterRegistry meterRegistry,
        PlatformAuditOutboxRepository repository
    ) {
        meterRegistry.gauge(
            "dts.platform.audit.outbox.pending",
            repository,
            PlatformAuditOutboxRepository::countPending
        );
        meterRegistry.gauge(
            "dts.platform.audit.outbox.dead",
            repository,
            PlatformAuditOutboxRepository::countDead
        );
    }
}
