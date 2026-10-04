package com.yuzhi.dts.platform.service.integration;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sprint-17 / F1 — drives {@link ScreenReportLinkSyncService}.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link ApplicationReadyEvent} — fires an async tick so the very
 *       first reconcile happens shortly after boot without blocking
 *       startup.</li>
 *   <li>{@code @Scheduled} cron — every hour at minute 17 thereafter.</li>
 * </ul>
 *
 * <p>Self-healing: catches all exceptions so a transient dts-bi outage
 * does not poison the scheduler thread; the next tick will retry.
 *
 * <p>Disabled when {@code dts.analytics.enabled=false} so dev/test
 * environments without the upstream service stay quiet.
 */
@Component
@ConditionalOnProperty(prefix = "dts.analytics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreenReportLinkSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScreenReportLinkSyncScheduler.class);

    private final ScreenReportLinkSyncService service;

    public ScreenReportLinkSyncScheduler(ScreenReportLinkSyncService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        CompletableFuture.runAsync(this::tick);
    }

    @Scheduled(cron = "0 17 * * * *")
    public void tick() {
        try {
            SyncResult r = service.reconcileOnce();
            if (r.skipped()) {
                log.info("screen reconcile skipped (not configured)");
            } else if (r.error() != null) {
                log.warn("screen reconcile failed: {}", r.error());
            } else {
                log.info("screen reconcile: created={} updated={} archived={}",
                    r.created(), r.updated(), r.archived());
            }
        } catch (Exception e) {
            log.error("screen reconcile crashed", e);
        }
    }
}
