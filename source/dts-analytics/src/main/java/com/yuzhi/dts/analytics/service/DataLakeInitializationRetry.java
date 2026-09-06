package com.yuzhi.dts.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovers the built-in source registration when platform starts after analytics. */
@Component
public class DataLakeInitializationRetry {

    private static final Logger LOG = LoggerFactory.getLogger(DataLakeInitializationRetry.class);
    private static final int MAX_ATTEMPTS = 6;

    private final DataLakeDatabaseInitializer initializer;
    private boolean ready;
    private boolean registered;
    private int attempts;

    public DataLakeInitializationRetry(DataLakeDatabaseInitializer initializer) {
        this.initializer = initializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        ready = true;
        retryInitialization();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public synchronized void retryInitialization() {
        if (!ready || registered || attempts >= MAX_ATTEMPTS) {
            return;
        }
        attempts++;
        try {
            // The separate bean's transactional proxy must commit before we stop retrying.
            registered = initializer.initializeDataLake();
        } catch (RuntimeException ex) {
            LOG.warn("[data-lake-init] Registration transaction failed attempt={} failureType={}", attempts, ex.getClass().getSimpleName());
        }
        if (registered) {
            LOG.info("[data-lake-init] Source registration ready attempt={}", attempts);
        } else if (attempts >= MAX_ATTEMPTS) {
            LOG.error("[data-lake-init] Source registration retries exhausted attempts={}; restore platform connectivity and restart analytics", attempts);
        } else {
            LOG.warn("[data-lake-init] Source registration pending attempt={} maxAttempts={} retryDelaySeconds=30", attempts, MAX_ATTEMPTS);
        }
    }
}
