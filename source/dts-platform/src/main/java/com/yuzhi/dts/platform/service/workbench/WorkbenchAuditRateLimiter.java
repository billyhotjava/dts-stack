package com.yuzhi.dts.platform.service.workbench;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Per-user rate limiter for the {@code POST /workbench/audit} ingest endpoint.
 *
 * <p>Caps client-side audit events at 60/min/user — far above realistic UI
 * usage but enough to stop a misbehaving (or malicious) authenticated client
 * from flooding the audit table.
 */
@Component
public class WorkbenchAuditRateLimiter {

    private static final int MAX_PER_WINDOW = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Cache<String, AtomicInteger> counters = Caffeine
        .newBuilder()
        .expireAfterWrite(WINDOW)
        .maximumSize(10_000)
        .build();

    /** Returns true if user is allowed to proceed (and increments). */
    public boolean tryAcquire(String userLogin) {
        AtomicInteger counter = counters.get(userLogin, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_PER_WINDOW;
    }
}
