package com.yuzhi.dts.platform.service.etl;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class DbtArtifactSyncState {

    private final AtomicReference<ArtifactStatus> manifest = new AtomicReference<>();
    private final AtomicReference<ArtifactStatus> runResults = new AtomicReference<>();
    private final AtomicReference<SyncStatsSnapshot> stats = new AtomicReference<>();
    private final AtomicReference<DbtRunResultService.DbtRunSummary> latestRun = new AtomicReference<>();

    public void recordManifest(Long modifiedAt, boolean synced, String message) {
        manifest.set(new ArtifactStatus(Instant.now(), modifiedAt, synced, message));
    }

    public void recordRunResults(Long modifiedAt, boolean synced, String message) {
        runResults.set(new ArtifactStatus(Instant.now(), modifiedAt, synced, message));
    }

    public void recordStats(DbtAssetSyncService.SyncStats syncStats, String message) {
        if (syncStats == null) {
            return;
        }
        stats.set(
            new SyncStatsSnapshot(
                Instant.now(),
                syncStats.getCreated(),
                syncStats.getUpdated(),
                syncStats.getOdsUpdated(),
                syncStats.getColumnsUpdated(),
                syncStats.getLineageCreated(),
                syncStats.getLineageRemoved(),
                message
            )
        );
    }

    public void recordLatestRun(DbtRunResultService.DbtRunSummary runSummary) {
        if (runSummary == null) {
            return;
        }
        latestRun.set(runSummary);
    }

    public DbtArtifactSyncStatus snapshot() {
        return new DbtArtifactSyncStatus(manifest.get(), runResults.get(), stats.get(), latestRun.get());
    }

    public record ArtifactStatus(Instant lastSyncAt, Long lastModifiedAt, boolean synced, String message) {}

    public record SyncStatsSnapshot(
        Instant lastSyncAt,
        Integer datasetsCreated,
        Integer datasetsUpdated,
        Integer odsUpdated,
        Integer columnsUpdated,
        Integer lineageCreated,
        Integer lineageRemoved,
        String message
    ) {}

    public record DbtArtifactSyncStatus(
        ArtifactStatus manifest,
        ArtifactStatus runResults,
        SyncStatsSnapshot stats,
        DbtRunResultService.DbtRunSummary latestRun
    ) {}
}
