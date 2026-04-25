package com.yuzhi.dts.platform.service.workbench;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Per-user rate limiter for the {@code POST /workbench/audit} ingest endpoint.
 *
 * <p><b>Limits</b>: caps client-side audit events at 30/min/user/JVM — well
 * above realistic UI usage but enough to stop a misbehaving (or malicious)
 * authenticated client from flooding the audit table from a single instance.
 *
 * <p><b>Multi-instance behaviour (P1-1 known limitation)</b>: the counter is
 * Caffeine-backed and therefore JVM-local. With N replicas behind a load
 * balancer the effective ceiling becomes {@code N * MAX_PER_WINDOW}. We
 * keep the per-instance ceiling tight (30 instead of 60) so the cluster
 * total still tracks a realistic UI ceiling, and rely on auditing visibility
 * to surface anomalies. A future task should replace this implementation
 * with a Redis-backed counter (see {@code RateLimiter} interface). The
 * cache size and TTL are sized so an exhausted Caffeine entry cannot be
 * abused by token-rotating attackers — the entry is keyed on the
 * authenticated user login (not the JWT id) and entries expire after the
 * window regardless of activity.
 */
@Component
public class WorkbenchAuditRateLimiter {

    /** P1-1: tightened from 60→30 to keep N-instance cluster total bounded. */
    private static final int MAX_PER_WINDOW = 30;
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
