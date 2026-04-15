package com.yuzhi.dts.platform.service.scheduling;

import com.yuzhi.dts.platform.service.sql.SqlSubqueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SqlSubqueryViewCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(SqlSubqueryViewCleaner.class);
    private final SqlSubqueryService service;

    public SqlSubqueryViewCleaner(SqlSubqueryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT5M")  // every 5 minutes
    public void cleanup() {
        int dropped = service.cleanupExpired();
        if (dropped > 0) LOG.info("[subquery-cleaner] dropped {} expired views", dropped);
    }
}
