package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.infra.InceptorCatalogSyncService.CatalogSyncResult;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class InceptorIntegrationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(InceptorIntegrationCoordinator.class);
    private static final String SQL_CATALOG_CACHE = "sqlCatalogTree";

    private final CacheManager cacheManager;
    private final CatalogDatasetRepository datasetRepository;
    private final InceptorCatalogSyncService catalogSyncService;
    private final AtomicReference<IntegrationStatus> lastStatus = new AtomicReference<>(IntegrationStatus.empty());

    public InceptorIntegrationCoordinator(
        @Nullable CacheManager cacheManager,
        CatalogDatasetRepository datasetRepository,
        InceptorCatalogSyncService catalogSyncService
    ) {
        this.cacheManager = cacheManager;
        this.datasetRepository = datasetRepository;
        this.catalogSyncService = catalogSyncService;
    }

    public IntegrationStatus synchronize(String reason) {
        List<String> actions = new ArrayList<>();
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(SQL_CATALOG_CACHE);
            if (cache != null) {
                cache.clear();
                actions.add("Cleared sqlCatalogTree cache");
            }
        }
        CatalogSyncResult syncResult = catalogSyncService.synchronize();
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
            actions.add(
                String.format(
                    "No tables found in %s during sync",
                    syncResult.database()
                )
            );
        } else {
            actions.add("Skipped catalog sync (no active Inceptor data source)");
        }

        long datasetCount = safeDatasetCount();
        IntegrationStatus status = new IntegrationStatus(
            Instant.now(),
            reason,
            List.copyOf(actions),
            datasetCount,
            syncResult.database(),
            syncResult.tablesDiscovered(),
            syncResult.datasetsCreated(),
            syncResult.tablesCreated(),
            syncResult.columnsImported(),
            syncResult.error()
        );
        lastStatus.set(status);
        LOG.info("Inceptor integration synchronized. reason={}, actions={}, datasets={}", reason, actions, datasetCount);
        return status;
    }

    @EventListener
    public void handlePublished(InceptorDataSourcePublishedEvent event) {
        synchronize("publish-event");
    }

    public IntegrationStatus currentStatus() {
        return lastStatus.get();
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
        int tablesCreated,
        int columnsImported,
        String error
    ) {
        public static IntegrationStatus empty() {
            return new IntegrationStatus(null, null, Collections.emptyList(), 0L, null, 0, 0, 0, 0, null);
        }
    }
}
