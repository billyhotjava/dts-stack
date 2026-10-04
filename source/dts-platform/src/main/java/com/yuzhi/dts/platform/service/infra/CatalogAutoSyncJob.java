package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CatalogAutoSyncJob {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogAutoSyncJob.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration INITIAL_LOOKBACK = Duration.ofMinutes(2);
    private static final String DEFAULT_CRON = "0 0 3 * * *";

    private final CatalogFeatureProperties catalogFeatures;
    private final InceptorIntegrationCoordinator inceptorCoordinator;
    private final JdbcIntegrationCoordinator jdbcCoordinator;
    private volatile String compiledCronText;
    private volatile CronExpression compiledCron;
    private volatile Instant lastTriggeredAt;

    public CatalogAutoSyncJob(
        CatalogFeatureProperties catalogFeatures,
        InceptorIntegrationCoordinator inceptorCoordinator,
        JdbcIntegrationCoordinator jdbcCoordinator
    ) {
        this.catalogFeatures = catalogFeatures;
        this.inceptorCoordinator = inceptorCoordinator;
        this.jdbcCoordinator = jdbcCoordinator;
    }

    @Scheduled(fixedDelayString = "${dts.platform.catalog.auto-sync-check-delay-ms:10000}")
    public synchronized void runScheduledCatalogSync() {
        if (!catalogFeatures.isAutoSyncEnabled()) {
            return;
        }
        CronExpression expression = resolveCronExpression();
        if (expression == null || !shouldTrigger(expression, Instant.now())) {
            return;
        }
        lastTriggeredAt = Instant.now();
        if (!inceptorCoordinator.isSyncInProgress()) {
            Thread t = new Thread(() -> {
                try {
                    inceptorCoordinator.synchronize("scheduled");
                } catch (Exception ex) {
                    LOG.warn("Scheduled inceptor sync failed: {}", ex.getMessage());
                }
            }, "inceptor-scheduled-sync");
            t.setDaemon(true);
            t.start();
        }
        if (!jdbcCoordinator.isSyncInProgress()) {
            jdbcCoordinator.synchronizeAsync("scheduled");
        }
    }

    private CronExpression resolveCronExpression() {
        String cron = normalizeCron(catalogFeatures.getAutoSyncCron());
        if (!StringUtils.hasText(cron)) {
            cron = DEFAULT_CRON;
        }
        if (cron.equals(compiledCronText) && compiledCron != null) {
            return compiledCron;
        }
        try {
            CronExpression parsed = CronExpression.parse(cron);
            compiledCronText = cron;
            compiledCron = parsed;
            // Cron changed, restart lookback window from now.
            lastTriggeredAt = null;
            return parsed;
        } catch (IllegalArgumentException ex) {
            LOG.warn("Invalid catalog auto-sync cron: {} ({})", cron, ex.getMessage());
            return null;
        }
    }

    private boolean shouldTrigger(CronExpression expression, Instant now) {
        ZonedDateTime nowZdt = now.atZone(DEFAULT_ZONE);
        Instant lookback = lastTriggeredAt == null ? now.minus(INITIAL_LOOKBACK) : lastTriggeredAt;
        ZonedDateTime base = lookback.atZone(DEFAULT_ZONE).minusNanos(1);
        ZonedDateTime next = expression.next(base);
        return next != null && !next.isAfter(nowZdt);
    }

    private String normalizeCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            return null;
        }
        String text = cron.trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
