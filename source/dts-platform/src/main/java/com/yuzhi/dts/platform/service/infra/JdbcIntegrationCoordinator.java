package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
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
    private final CatalogDatasetRepository datasetRepository;
    private final InfraCatalogSyncRunRepository syncRunRepository;
    private final ObjectMapper objectMapper;
    private final AtomicReference<JdbcIntegrationStatus> lastStatus = new AtomicReference<>(JdbcIntegrationStatus.empty());
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public JdbcIntegrationCoordinator(
        JdbcCatalogSyncService syncService,
        CatalogFeatureProperties catalogFeatures,
        CatalogDatasetRepository datasetRepository,
        InfraCatalogSyncRunRepository syncRunRepository,
        ObjectMapper objectMapper
    ) {
        this.syncService = syncService;
        this.catalogFeatures = catalogFeatures;
        this.datasetRepository = datasetRepository;
        this.syncRunRepository = syncRunRepository;
        this.objectMapper = objectMapper;
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
        InfraCatalogSyncRun run = new InfraCatalogSyncRun();
        run.setIntegration("JDBC");
        run.setReason(reason);
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run.setCatalogDatasetCountBefore(safeDatasetCount());
        try {
            syncRunRepository.save(run);
        } catch (Exception ignored) {}
        try {
            List<JdbcSyncResult> results = syncService.synchronizeAllActive(run.getId());
            JdbcIntegrationStatus status = new JdbcIntegrationStatus(Instant.now(), reason, results, null);
            lastStatus.set(status);
            persistRun(run, status);
            return status;
        } catch (Exception ex) {
            JdbcIntegrationStatus failed = new JdbcIntegrationStatus(Instant.now(), reason, List.of(), ex.getMessage());
            lastStatus.set(failed);
            persistRun(run, failed);
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

    private void persistRun(InfraCatalogSyncRun run, JdbcIntegrationStatus status) {
        if (run == null) {
            return;
        }
        try {
            run.setFinishedAt(Instant.now());
            run.setCatalogDatasetCountAfter(safeDatasetCount());
            run.setError(status != null ? status.error() : null);
            run.setStatus(status != null && status.error() != null ? "FAILED" : "SUCCESS");
            if (status != null) {
                run.setDetailsJson(objectMapper.writeValueAsString(status));
                int created = 0;
                int updated = 0;
                int removed = 0;
                int tablesCreated = 0;
                int columnsImported = 0;
                if (status.results() != null) {
                    for (JdbcSyncResult r : status.results()) {
                        if (r == null) continue;
                        created += safeInt(r.datasetsCreated());
                        updated += safeInt(r.datasetsUpdated());
                        removed += safeInt(r.datasetsRemoved());
                        tablesCreated += safeInt(r.tablesCreated());
                        columnsImported += safeInt(r.columnsImported());
                    }
                }
                run.setDatasetsCreated(created);
                run.setDatasetsUpdated(updated);
                run.setDatasetsRemoved(removed);
                run.setTablesCreated(tablesCreated);
                run.setColumnsImported(columnsImported);
            }
            syncRunRepository.save(run);
        } catch (Exception ex) {
            LOG.debug("Failed to persist JDBC sync run: {}", ex.getMessage());
        }
    }

    private long safeDatasetCount() {
        try {
            return datasetRepository.count();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int safeInt(Integer value) {
        return value != null ? value.intValue() : 0;
    }
}
