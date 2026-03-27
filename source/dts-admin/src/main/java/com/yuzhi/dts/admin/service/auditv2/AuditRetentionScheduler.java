package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.config.AuditRetentionProperties;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that purges audit entries older than the configured retention period.
 * Runs daily at 03:00 and deletes in batches to avoid long-running locks.
 */
@Component
public class AuditRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final AuditRetentionProperties properties;
    private final AuditEntryRepository repository;

    public AuditRetentionScheduler(AuditRetentionProperties properties, AuditEntryRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpiredEntries() {
        if (!properties.isEnabled()) {
            LOG.debug("Audit retention is disabled, skipping cleanup");
            return;
        }

        int retentionDays = properties.getDays();
        int batchSize = properties.getBatchSize();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        LOG.info("Audit retention: starting cleanup of entries older than {} days (cutoff={})", retentionDays, cutoff);

        long totalTargets = 0;
        long totalDetails = 0;
        long totalEntries = 0;

        try {
            // Phase 1: delete child target rows in batches
            totalTargets = deleteBatched(() -> deleteTargetsBatch(cutoff, batchSize));

            // Phase 2: delete child detail rows in batches
            totalDetails = deleteBatched(() -> deleteDetailsBatch(cutoff, batchSize));

            // Phase 3: delete parent entry rows in batches
            totalEntries = deleteBatched(() -> deleteEntriesBatch(cutoff, batchSize));

            LOG.info(
                "Audit retention: completed — deleted {} entries, {} targets, {} details (older than {} days)",
                totalEntries, totalTargets, totalDetails, retentionDays
            );
        } catch (Exception ex) {
            LOG.error(
                "Audit retention: error during cleanup (deleted so far: {} entries, {} targets, {} details)",
                totalEntries, totalTargets, totalDetails, ex
            );
        }
    }

    @Transactional
    public int deleteTargetsBatch(Instant cutoff, int batchSize) {
        return repository.deleteTargetsByCutoff(cutoff, batchSize);
    }

    @Transactional
    public int deleteDetailsBatch(Instant cutoff, int batchSize) {
        return repository.deleteDetailsByCutoff(cutoff, batchSize);
    }

    @Transactional
    public int deleteEntriesBatch(Instant cutoff, int batchSize) {
        return repository.deleteEntriesByCutoff(cutoff, batchSize);
    }

    /**
     * Repeatedly calls the batch operation until it returns 0 (no more rows to delete).
     * Returns the total number of rows deleted.
     */
    private long deleteBatched(BatchOperation operation) {
        long total = 0;
        int deleted;
        do {
            deleted = operation.execute();
            total += deleted;
        } while (deleted > 0);
        return total;
    }

    @FunctionalInterface
    private interface BatchOperation {
        int execute();
    }
}
