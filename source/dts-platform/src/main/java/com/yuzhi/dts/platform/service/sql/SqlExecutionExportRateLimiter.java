package com.yuzhi.dts.platform.service.sql;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SqlExecutionExportRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
        .expireAfterWrite(WINDOW)
        .maximumSize(10_000)
        .build();

    /** Returns true if user is allowed to proceed (and increments). */
    public boolean tryAcquire(String userLogin) {
        AtomicInteger counter = counters.get(userLogin, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_PER_WINDOW;
    }

    public int currentCount(String userLogin) {
        AtomicInteger counter = counters.getIfPresent(userLogin);
        return counter == null ? 0 : counter.get();
    }
}
