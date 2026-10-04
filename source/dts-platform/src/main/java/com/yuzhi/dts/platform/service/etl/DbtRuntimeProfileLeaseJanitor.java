package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Performs bounded metadata expiry and orphan profile cleanup. */
@Component
public class DbtRuntimeProfileLeaseJanitor {

    private static final int DIRECTORY_BATCH_SIZE = 100;

    private final ModelMaterializationProperties properties;
    private final DbtRuntimeProfileLeaseRepository repository;
    private final DbtRuntimeProfileLeaseFileStore fileStore;
    private final Clock clock;
    private DirectoryStream<Path> orphanDirectoryStream;
    private Iterator<Path> orphanDirectoryIterator;
    private Path orphanDirectoryRoot;

    @Autowired
    public DbtRuntimeProfileLeaseJanitor(
        ModelMaterializationProperties properties,
        DbtRuntimeProfileLeaseRepository repository,
        DbtRuntimeProfileLeaseFileStore fileStore
    ) {
        this(
            properties,
            repository,
            fileStore,
            Clock.systemUTC()
        );
    }

    DbtRuntimeProfileLeaseJanitor(
        ModelMaterializationProperties properties,
        DbtRuntimeProfileLeaseRepository repository,
        DbtRuntimeProfileLeaseFileStore fileStore,
        Clock clock
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
        this.repository = Objects.requireNonNull(
            repository,
            "repository is required"
        );
        this.fileStore = Objects.requireNonNull(
            fileStore,
            "fileStore is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.materialization.runtime-profile-janitor-delay-ms:60000}"
    )
    public void cleanupExpired() {
        Instant now = clock.instant();
        try {
            for (LeaseRecord lease : repository.findExpired(100)) {
                expireAndDelete(lease);
            }
        } catch (RuntimeException ignored) {
            // Fail closed and retry on the next janitor pass.
        }
        cleanupOrphans(now);
    }

    private synchronized void cleanupOrphans(Instant now) {
        Path root;
        try {
            root = fileStore.runtimeRoot();
            if (
                !Files.isDirectory(
                    root,
                    LinkOption.NOFOLLOW_LINKS
                )
            ) {
                closeOrphanDirectoryCursor();
                return;
            }
            List<Path> entries = nextOrphanDirectoryBatch(root);
            Map<UUID, LeaseRecord> trackedById = trackedById(entries);
            for (Path entry : entries) {
                UUID id = parseLeaseId(
                    entry.getFileName().toString()
                );
                LeaseRecord tracked = id == null
                    ? null
                    : trackedById.get(id);
                if (tracked != null) {
                    if (
                        tracked.status() == LeaseStatus.RELEASED ||
                        tracked.status() == LeaseStatus.EXPIRED
                    ) {
                        fileStore.deleteLeaseDirectory(entry, false);
                    }
                    continue;
                }
                Instant modified = Files.getLastModifiedTime(
                    entry,
                    LinkOption.NOFOLLOW_LINKS
                )
                    .toInstant();
                if (!now.isBefore(modified.plus(leaseTtl()))) {
                    fileStore.deleteLeaseDirectory(entry, false);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            closeOrphanDirectoryCursor();
            // Readiness remains fail-closed; the next pass retries cleanup.
        }
    }

    private Map<UUID, LeaseRecord> trackedById(List<Path> entries) {
        List<UUID> ids = new ArrayList<>(entries.size());
        for (Path entry : entries) {
            UUID id = parseLeaseId(entry.getFileName().toString());
            if (id != null) {
                ids.add(id);
            }
        }
        Map<UUID, LeaseRecord> trackedById = new HashMap<>();
        for (LeaseRecord tracked : repository.findAllByIds(ids)) {
            trackedById.put(tracked.id(), tracked);
        }
        return trackedById;
    }

    private List<Path> nextOrphanDirectoryBatch(Path root)
        throws IOException {
        if (
            orphanDirectoryStream == null ||
            !root.equals(orphanDirectoryRoot)
        ) {
            closeOrphanDirectoryCursor();
            orphanDirectoryStream = Files.newDirectoryStream(root);
            orphanDirectoryIterator = orphanDirectoryStream.iterator();
            orphanDirectoryRoot = root;
        }
        List<Path> batch = new ArrayList<>(DIRECTORY_BATCH_SIZE);
        int inspected = 0;
        while (
            inspected < DIRECTORY_BATCH_SIZE &&
            orphanDirectoryIterator.hasNext()
        ) {
            Path entry = orphanDirectoryIterator.next();
            inspected++;
            if (
                Files.isDirectory(
                    entry,
                    LinkOption.NOFOLLOW_LINKS
                )
            ) {
                batch.add(entry);
            }
        }
        if (!orphanDirectoryIterator.hasNext()) {
            closeOrphanDirectoryCursor();
        }
        return batch;
    }

    private boolean expireAndDelete(LeaseRecord lease) {
        if (
            !repository.expire(
                lease.id(),
                lease.expiresAt()
            )
        ) {
            return false;
        }
        return fileStore.deleteLeaseDirectory(lease.id(), false);
    }

    private Duration leaseTtl() {
        Duration ttl = properties.getRuntimeProfileLeaseTtl();
        if (
            ttl == null ||
            ttl.isZero() ||
            ttl.isNegative() ||
            ttl.compareTo(Duration.ofHours(1)) > 0
        ) {
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_TTL_INVALID",
                "Runtime profile lease TTL must be between one nanosecond and one hour"
            );
        }
        return ttl;
    }

    private static UUID parseLeaseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @PreDestroy
    synchronized void closeOrphanDirectoryCursor() {
        DirectoryStream<Path> stream = orphanDirectoryStream;
        orphanDirectoryStream = null;
        orphanDirectoryIterator = null;
        orphanDirectoryRoot = null;
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // The next pass always opens a fresh bounded cursor.
        }
    }
}
