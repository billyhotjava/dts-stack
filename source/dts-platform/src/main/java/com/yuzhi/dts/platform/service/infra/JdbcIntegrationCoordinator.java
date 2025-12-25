package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService.JdbcSyncResult;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class JdbcIntegrationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcIntegrationCoordinator.class);

    private final JdbcCatalogSyncService syncService;
    private final CatalogFeatureProperties catalogFeatures;
    private final AtomicReference<JdbcIntegrationStatus> lastStatus = new AtomicReference<>(JdbcIntegrationStatus.empty());
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public JdbcIntegrationCoordinator(JdbcCatalogSyncService syncService, CatalogFeatureProperties catalogFeatures) {
        this.syncService = syncService;
        this.catalogFeatures = catalogFeatures;
    }

    public JdbcIntegrationStatus synchronize(String reason) {
        if (!catalogFeatures.isMultiSourceEnabled()) {
            JdbcIntegrationStatus skipped = new JdbcIntegrationStatus(Instant.now(), reason, List.of(), "multi-source-disabled");
            lastStatus.set(skipped);
            return skipped;
        }
        if (!syncing.compareAndSet(false, true)) {
            return lastStatus.get();
        }
        try {
            List<JdbcSyncResult> results = syncService.synchronizeAllActive();
            JdbcIntegrationStatus status = new JdbcIntegrationStatus(Instant.now(), reason, results, null);
            lastStatus.set(status);
            return status;
        } catch (Exception ex) {
            JdbcIntegrationStatus failed = new JdbcIntegrationStatus(Instant.now(), reason, List.of(), ex.getMessage());
            lastStatus.set(failed);
            return failed;
        } finally {
            syncing.set(false);
        }
    }

    public void synchronizeAsync(String reason) {
        Thread t = new Thread(() -> {
            try {
                synchronize(reason);
            } catch (Exception ex) {
                LOG.debug("Async JDBC catalog sync failed: {}", ex.getMessage());
            }
        }, "jdbc-catalog-sync");
        t.setDaemon(true);
        t.start();
    }

    public JdbcIntegrationStatus currentStatus() {
        return lastStatus.get();
    }

    public boolean isSyncInProgress() {
        return syncing.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        synchronizeAsync("startup-auto");
    }

    public record JdbcIntegrationStatus(
        Instant timestamp,
        String reason,
        List<JdbcSyncResult> results,
        String error
    ) {
        public static JdbcIntegrationStatus empty() {
            return new JdbcIntegrationStatus(null, null, Collections.emptyList(), null);
        }
    }
}
