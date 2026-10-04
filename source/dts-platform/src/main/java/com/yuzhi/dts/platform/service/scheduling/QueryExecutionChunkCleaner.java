package com.yuzhi.dts.platform.service.scheduling;

import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job that removes query_execution_chunk rows older than 30 days.
 * Runs at 03:00 each day.
 */
@Component
public class QueryExecutionChunkCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExecutionChunkCleaner.class);
    private static final long RETENTION_DAYS = 30L;

    private final QueryExecutionChunkRepository chunkRepository;

    public QueryExecutionChunkCleaner(QueryExecutionChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOldChunks() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        try {
            int deleted = chunkRepository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                LOG.info("QueryExecutionChunkCleaner: deleted {} chunks older than {} days", deleted, RETENTION_DAYS);
            }
        } catch (Exception ex) {
            LOG.error("QueryExecutionChunkCleaner: failed to clean old chunks", ex);
        }
    }
}
