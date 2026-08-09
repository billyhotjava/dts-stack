package com.yuzhi.dts.platform.service.catalog;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Repairs incremental asset-statistics drift at least once every 24 hours. */
@Component
public class CatalogAssetStatsReconciliationScheduler {

    private final CatalogAssetRegistrationService registrationService;

    public CatalogAssetStatsReconciliationScheduler(CatalogAssetRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Scheduled(cron = "${dts.catalog.asset-stats-reconciliation-cron:0 23 2 * * *}", zone = "UTC")
    public void reconcile() {
        registrationService.reconcile();
    }
}
