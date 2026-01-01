package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.service.infra.InceptorCatalogSyncService.CatalogSyncResult;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class InceptorIntegrationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(InceptorIntegrationCoordinator.class);
    private static final String SQL_CATALOG_CACHE = "sqlCatalogTree";

    private final CacheManager cacheManager;
    private final CatalogDatasetRepository datasetRepository;
    private final InceptorCatalogSyncService catalogSyncService;
    private final InfraCatalogSyncRunRepository syncRunRepository;
    private final ObjectMapper objectMapper;
    private final AtomicReference<IntegrationStatus> lastStatus = new AtomicReference<>(IntegrationStatus.empty());
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public InceptorIntegrationCoordinator(
        @Nullable CacheManager cacheManager,
        CatalogDatasetRepository datasetRepository,
        InceptorCatalogSyncService catalogSyncService,
        InfraCatalogSyncRunRepository syncRunRepository,
        ObjectMapper objectMapper
    ) {
        this.cacheManager = cacheManager;
        this.datasetRepository = datasetRepository;
        this.catalogSyncService = catalogSyncService;
        this.syncRunRepository = syncRunRepository;
        this.objectMapper = objectMapper;
    }

    public IntegrationStatus synchronize(String reason) {
        if (!syncing.compareAndSet(false, true)) {
            return lastStatus.get();
        }
        InfraCatalogSyncRun run = new InfraCatalogSyncRun();
        run.setIntegration("INCEPTOR");
        run.setReason(reason);
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run.setCatalogDatasetCountBefore(safeDatasetCount());
        try {
            syncRunRepository.save(run);
        } catch (Exception ignored) {}
        List<String> actions = new ArrayList<>();
        CatalogSyncResult syncResult = null;
        String error = null;
        try {
            if (cacheManager != null) {
                Cache cache = cacheManager.getCache(SQL_CATALOG_CACHE);
                if (cache != null) {
                    cache.clear();
                    actions.add("Cleared sqlCatalogTree cache");
                }
            }

            syncResult = catalogSyncService.synchronize();
            if (syncResult.error() != null) {
                actions.add("Sync error: " + syncResult.error());
            } else if (syncResult.tablesDiscovered() > 0) {
                actions.add(
                    String.format(
                        "Synced %d table(s) from %s",
                        syncResult.tablesDiscovered(),
                        syncResult.database() != null ? syncResult.database() : "default"
                    )
                );
            } else if (syncResult.database() != null) {
                actions.add(String.format("No tables found in %s during sync", syncResult.database()));
            } else {
                actions.add("Skipped catalog sync (no active Inceptor data source)");
            }
        } catch (Exception ex) {
            error = ex.getMessage();
            actions.add("Sync failed: " + error);
            LOG.warn("Inceptor integration sync failed. reason={}, error={}", reason, ex.getMessage());
        } finally {
            long datasetCount = safeDatasetCount();
            IntegrationStatus status = new IntegrationStatus(
                Instant.now(),
                reason,
                List.copyOf(actions),
                datasetCount,
                syncResult != null ? syncResult.database() : null,
                syncResult != null ? syncResult.tablesDiscovered() : 0,
                syncResult != null ? syncResult.datasetsCreated() : 0,
                syncResult != null ? syncResult.datasetsUpdated() : 0,
                syncResult != null ? syncResult.datasetsRemoved() : 0,
                syncResult != null ? syncResult.tablesCreated() : 0,
                syncResult != null ? syncResult.columnsImported() : 0,
                syncResult != null && syncResult.error() != null ? syncResult.error() : error
            );
            lastStatus.set(status);
            LOG.info("Inceptor integration synchronized. reason={}, actions={}, datasets={}", reason, actions, datasetCount);
            try {
                run.setFinishedAt(Instant.now());
                run.setCatalogDatasetCountAfter(datasetCount);
                run.setTablesDiscovered(syncResult != null ? syncResult.tablesDiscovered() : null);
                run.setDatasetsCreated(syncResult != null ? syncResult.datasetsCreated() : null);
                run.setDatasetsUpdated(syncResult != null ? syncResult.datasetsUpdated() : null);
                run.setDatasetsRemoved(syncResult != null ? syncResult.datasetsRemoved() : null);
                run.setTablesCreated(syncResult != null ? syncResult.tablesCreated() : null);
                run.setColumnsImported(syncResult != null ? syncResult.columnsImported() : null);
                run.setError(status.error());
                run.setStatus(status.error() != null ? "FAILED" : "SUCCESS");
                run.setDetailsJson(objectMapper.writeValueAsString(status));
                syncRunRepository.save(run);
            } catch (Exception ex) {
                LOG.debug("Failed to persist inceptor sync run: {}", ex.getMessage());
            }
            syncing.set(false);
        }
        return lastStatus.get();
    }

    public void synchronizeAsync(String reason) {
        Thread t = new Thread(() -> {
            try {
                synchronize(reason);
            } catch (Exception ex) {
                LOG.debug("Async inceptor catalog sync failed: {}", ex.getMessage());
            }
        }, "inceptor-catalog-sync");
        t.setDaemon(true);
        t.start();
    }

    @EventListener
    public void handlePublished(InceptorDataSourcePublishedEvent event) {
        // Avoid blocking the publishing request thread; run sync asynchronously.
        if (syncing.get()) {
            LOG.info("Inceptor publish event received but sync already in progress; skipping immediate re-sync");
            return;
        }
        synchronizeAsync("publish-event");
    }

    public IntegrationStatus currentStatus() {
        return lastStatus.get();
    }

    public boolean isSyncInProgress() {
        return syncing.get();
    }

    /**
     * Trigger a best-effort catalog synchronization once the application is ready.
     * Runs asynchronously to avoid blocking startup, and safely no-ops when no active data source.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        synchronizeAsync("startup-auto");
    }

    private long safeDatasetCount() {
        try {
            return datasetRepository.count();
        } catch (Exception ex) {
            LOG.debug("Failed to query catalog dataset count: {}", ex.getMessage());
            return 0L;
        }
    }

    public record IntegrationStatus(
        Instant timestamp,
        String reason,
        List<String> actions,
        long catalogDatasetCount,
        String database,
        int tablesDiscovered,
        int datasetsCreated,
        int datasetsUpdated,
        int datasetsRemoved,
        int tablesCreated,
        int columnsImported,
        String error
    ) {
        public static IntegrationStatus empty() {
            return new IntegrationStatus(null, null, Collections.emptyList(), 0L, null, 0, 0, 0, 0, 0, 0, null);
        }
    }
}
