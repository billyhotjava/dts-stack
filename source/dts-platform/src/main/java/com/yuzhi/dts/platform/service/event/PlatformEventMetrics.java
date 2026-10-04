package com.yuzhi.dts.platform.service.event;

import com.yuzhi.dts.platform.domain.event.PlatformEventOutbox;
import com.yuzhi.dts.platform.repository.event.PlatformEventOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PlatformEventMetrics {

    public PlatformEventMetrics(MeterRegistry meterRegistry, PlatformEventOutboxRepository repository) {
        registerDispatchGauge(meterRegistry, repository, "pending", PlatformEventOutbox.DISPATCH_PENDING);
        registerDispatchGauge(meterRegistry, repository, "sent", PlatformEventOutbox.DISPATCH_SENT);
        registerDispatchGauge(meterRegistry, repository, "failed", PlatformEventOutbox.DISPATCH_FAILED);
        registerDispatchGauge(meterRegistry, repository, "skipped", PlatformEventOutbox.DISPATCH_SKIPPED);
    }

    private void registerDispatchGauge(
        MeterRegistry meterRegistry,
        PlatformEventOutboxRepository repository,
        String state,
        String dispatchStatus
    ) {
        meterRegistry.gauge(
            "dts.platform.events.outbox." + state,
            repository,
            repo -> repo.countByDispatchStatus(dispatchStatus)
        );
    }
}
