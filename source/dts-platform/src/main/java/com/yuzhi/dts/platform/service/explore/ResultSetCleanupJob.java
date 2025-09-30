package com.yuzhi.dts.platform.service.explore;

import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ResultSetCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ResultSetCleanupJob.class);
    private final ResultSetRepository resultSetRepository;
    private final QueryExecutionRepository executionRepository;

    public ResultSetCleanupJob(ResultSetRepository resultSetRepository, QueryExecutionRepository executionRepository) {
        this.resultSetRepository = resultSetRepository;
        this.executionRepository = executionRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpired() {
        var now = Instant.now();
        var expired = resultSetRepository.findByExpiresAtBefore(now);
        if (expired.isEmpty()) return;
        log.info("Cleaning up {} expired result sets", expired.size());
        expired.forEach(rs -> {
            executionRepository.clearResultSetReferences(rs.getId());
            resultSetRepository.deleteById(rs.getId());
        });
    }
}

