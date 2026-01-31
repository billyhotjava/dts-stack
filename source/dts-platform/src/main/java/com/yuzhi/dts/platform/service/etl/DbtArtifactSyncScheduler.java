package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtArtifactSyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DbtArtifactSyncScheduler.class);
    private static final String MANIFEST_PATH = "/target/manifest.json";
    private static final String RUN_RESULTS_PATH = "/target/run_results.json";

    private final DbtConfigService dbtConfigService;
    private final DbtProperties dbtProperties;
    private final DbtAssetSyncService dbtAssetSyncService;
    private final DbtRunResultService dbtRunResultService;
    private final DbtArtifactSyncState syncState;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastManifestModified = new AtomicLong(0L);
    private final AtomicLong lastRunResultsModified = new AtomicLong(0L);

    public DbtArtifactSyncScheduler(
        DbtConfigService dbtConfigService,
        DbtProperties dbtProperties,
        DbtAssetSyncService dbtAssetSyncService,
        DbtRunResultService dbtRunResultService,
        DbtArtifactSyncState syncState
    ) {
        this.dbtConfigService = dbtConfigService;
        this.dbtProperties = dbtProperties;
        this.dbtAssetSyncService = dbtAssetSyncService;
        this.dbtRunResultService = dbtRunResultService;
        this.syncState = syncState;
    }

    @Scheduled(fixedDelayString = "${dts.dbt.sync-interval-ms:60000}")
    public void syncArtifacts() {
        if (!dbtProperties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            String projectDir = resolveProjectDir();
            if (!StringUtils.hasText(projectDir)) {
                return;
            }
            syncManifestIfChanged(projectDir);
            syncRunResultsIfChanged(projectDir);
        } catch (Exception ex) {
            LOG.warn("[dbt-sync] failed to sync dbt artifacts: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    private void syncManifestIfChanged(String projectDir) {
        File manifest = new File(projectDir + MANIFEST_PATH);
        if (!manifest.exists()) {
            return;
        }
        long modified = manifest.lastModified();
        if (modified <= 0 || modified <= lastManifestModified.get()) {
            return;
        }
        DbtAssetSyncService.DbtAssetSyncResult result = dbtAssetSyncService.syncFromManifest();
        syncState.recordManifest(modified, result != null && result.synced(), result != null ? result.message() : null);
        if (result != null && result.stats() != null) {
            syncState.recordStats(result.stats(), result.message());
        }
        if (result != null && result.synced()) {
            lastManifestModified.set(modified);
        }
    }

    private void syncRunResultsIfChanged(String projectDir) {
        File runResults = new File(projectDir + RUN_RESULTS_PATH);
        if (!runResults.exists()) {
            return;
        }
        long modified = runResults.lastModified();
        if (modified <= 0 || modified <= lastRunResultsModified.get()) {
            return;
        }
        DbtRunResultService.DbtRunSyncResult result = dbtRunResultService.syncFromRunResults();
        syncState.recordRunResults(modified, result != null && result.synced(), result != null ? result.message() : null);
        if (result != null && result.synced()) {
            lastRunResultsModified.set(modified);
        }
    }

    private String resolveProjectDir() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            return view.config().projectDir().trim();
        }
        return dbtProperties.getProjectDir();
    }
}
