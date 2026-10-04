package com.yuzhi.dts.platform.service.services;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Best-effort in-memory QPS limiter (per instance).
 * Daily limit enforcement should rely on database metrics; QPS is approximate and not cluster-safe.
 */
@Component
public class SvcApiRateLimiter {

    private static final class SecondBucket {
        volatile long epochSecond;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final Map<UUID, SecondBucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(UUID apiId, int qpsLimit) {
        if (apiId == null) return true;
        if (qpsLimit <= 0) return true;
        long nowSec = Instant.now().getEpochSecond();
        SecondBucket bucket = buckets.computeIfAbsent(apiId, id -> new SecondBucket());
        if (bucket.epochSecond != nowSec) {
            synchronized (bucket) {
                if (bucket.epochSecond != nowSec) {
                    bucket.epochSecond = nowSec;
                    bucket.count.set(0);
                }
            }
        }
        int next = bucket.count.incrementAndGet();
        return next <= qpsLimit;
    }
}

