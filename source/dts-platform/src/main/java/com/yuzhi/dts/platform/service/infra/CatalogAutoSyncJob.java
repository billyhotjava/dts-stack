package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogAutoSyncJob {

    private final CatalogFeatureProperties catalogFeatures;
    private final InceptorIntegrationCoordinator inceptorCoordinator;
    private final JdbcIntegrationCoordinator jdbcCoordinator;

    public CatalogAutoSyncJob(
        CatalogFeatureProperties catalogFeatures,
        InceptorIntegrationCoordinator inceptorCoordinator,
        JdbcIntegrationCoordinator jdbcCoordinator
    ) {
        this.catalogFeatures = catalogFeatures;
        this.inceptorCoordinator = inceptorCoordinator;
        this.jdbcCoordinator = jdbcCoordinator;
    }

    @Scheduled(cron = "${dts.platform.catalog.auto-sync-cron:0 0 3 * * *}")
    public void runScheduledCatalogSync() {
        if (!catalogFeatures.isAutoSyncEnabled()) {
            return;
        }
        if (!inceptorCoordinator.isSyncInProgress()) {
            Thread t = new Thread(() -> {
                try {
                    inceptorCoordinator.synchronize("scheduled");
                } catch (Exception ignore) {}
            }, "inceptor-scheduled-sync");
            t.setDaemon(true);
            t.start();
        }
        if (!jdbcCoordinator.isSyncInProgress()) {
            jdbcCoordinator.synchronizeAsync("scheduled");
        }
    }
}

