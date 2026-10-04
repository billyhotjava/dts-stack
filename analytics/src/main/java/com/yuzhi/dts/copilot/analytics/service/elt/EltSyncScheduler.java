package com.yuzhi.dts.copilot.analytics.service.elt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EltSyncScheduler.class);

    private final EltSyncService eltSyncService;

    public EltSyncScheduler(EltSyncService eltSyncService) {
        this.eltSyncService = eltSyncService;
    }

    @Scheduled(cron = "${dts.elt.cron:0 0 * * * *}")
    public void scheduledSync() {
        log.info("ELT scheduled sync triggered");
        eltSyncService.runAll();
    }
}
