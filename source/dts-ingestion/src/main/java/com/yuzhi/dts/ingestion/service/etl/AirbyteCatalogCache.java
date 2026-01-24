package com.yuzhi.dts.ingestion.service.etl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirbyteCatalogCache {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, CachedCatalog> cache = new ConcurrentHashMap<>();

    public Optional<Map<String, Object>> getIfFresh(String sourceId) {
        if (!StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        CachedCatalog entry = cache.get(sourceId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            cache.remove(sourceId.trim());
            return Optional.empty();
        }
        return Optional.ofNullable(entry.payload);
    }

    public void put(String sourceId, Map<String, Object> payload) {
        if (!StringUtils.hasText(sourceId) || payload == null || payload.isEmpty()) {
            return;
        }
        String key = sourceId.trim();
        cache.put(key, new CachedCatalog(payload, Instant.now().plus(TTL)));
    }

    private static final class CachedCatalog {

        private final Map<String, Object> payload;
        private final Instant expiresAt;

        private CachedCatalog(Map<String, Object> payload, Instant expiresAt) {
            this.payload = payload;
            this.expiresAt = expiresAt;
        }
    }
}
