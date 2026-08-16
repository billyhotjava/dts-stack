package com.yuzhi.dts.platform.service.modeling.serving;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Self-healing driver for Catalog serving projection to Analytics convergence. */
@Component
@ConditionalOnProperty(prefix = "dts.analytics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogModelSemanticSyncWorker {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogModelSemanticSyncWorker.class);

    private final CatalogModelSemanticSyncService service;

    public CatalogModelSemanticSyncWorker(CatalogModelSemanticSyncService service) {
        this.service = service;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        tick();
    }

    @Scheduled(fixedDelayString = "${dts.modeling.catalog.semantic-sync-delay-ms:30000}")
    public void tick() {
        try {
            CatalogModelSemanticSyncService.SyncResult result = service.synchronizeOnce();
            if (result.claimed() > 0) {
                LOG.info(
                    "Catalog semantic sync claimed={} succeeded={} failed={} stale={}",
                    result.claimed(),
                    result.succeeded(),
                    result.failed(),
                    result.stale()
                );
            }
        } catch (RuntimeException failure) {
            LOG.error("Catalog semantic sync worker failed", failure);
        }
    }
}
