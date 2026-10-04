package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Query result caching service with TTL-based expiration.
 *
 * <p>Provides caching for query results to improve performance and reduce database load.
 * Cache keys are generated from query parameters including database ID, query definition,
 * and user context for permission-aware caching.
 */
@Service
public class QueryCacheService {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheService.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long MAX_CACHE_SIZE = 1000;
    private static final long MAX_RESULT_SIZE_BYTES = 10 * 1024 * 1024; // 10MB max per result

    private final ObjectMapper objectMapper;
    private final Cache<String, CachedResult> cache;
    private final Map<Long, CacheStrategy> databaseCacheStrategies;

    public QueryCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(DEFAULT_TTL)
                .recordStats()
                .build();
        this.databaseCacheStrategies = new ConcurrentHashMap<>();
    }

    /**
     * Get a cached query result if available and not expired.
     *
     * @param databaseId the database ID
     * @param query the query definition (MBQL or native)
     * @param userId the user ID for permission-aware caching
     * @return the cached result if available
     */
    public Optional<DatasetQueryService.DatasetResult> get(long databaseId, JsonNode query, Long userId) {
        String cacheKey = generateCacheKey(databaseId, query, userId);
        CachedResult cached = cache.getIfPresent(cacheKey);

        if (cached == null) {
            log.debug("Cache miss for query on database {}", databaseId);
            return Optional.empty();
        }

        CacheStrategy strategy = getCacheStrategy(databaseId);
        if (isNativeQuery(query) && !strategy.cacheNativeQueries()) {
            log.debug("Cache bypass for native query on database {} (native caching disabled)", databaseId);
            return Optional.empty();
        }
        if (cached.isExpired(strategy.ttl())) {
            log.debug("Cache entry expired for query on database {}", databaseId);
            cache.invalidate(cacheKey);
            return Optional.empty();
        }

        log.debug("Cache hit for query on database {}", databaseId);
        return Optional.of(cached.result());
    }

    /**
     * Store a query result in the cache.
     *
     * @param databaseId the database ID
     * @param query the query definition
     * @param userId the user ID
     * @param result the query result to cache
     */
    public void put(long databaseId, JsonNode query, Long userId, DatasetQueryService.DatasetResult result) {
        CacheStrategy strategy = getCacheStrategy(databaseId);
        if (!strategy.enabled()) {
            log.debug("Caching disabled for database {}", databaseId);
            return;
        }

        if (isNativeQuery(query) && !strategy.cacheNativeQueries()) {
            log.debug("Skip caching native query for database {} (native caching disabled)", databaseId);
            return;
        }

        // Check result size before caching
        long estimatedSize = estimateResultSize(result);
        if (estimatedSize > MAX_RESULT_SIZE_BYTES) {
            log.debug("Result too large to cache: {} bytes (max {})", estimatedSize, MAX_RESULT_SIZE_BYTES);
            return;
        }

        String cacheKey = generateCacheKey(databaseId, query, userId);
        CachedResult cached = new CachedResult(result, Instant.now(), estimatedSize);
        cache.put(cacheKey, cached);
        log.debug("Cached query result for database {} (size: {} bytes)", databaseId, estimatedSize);
    }

    /**
     * Invalidate all cached results for a specific database.
     * Call this when database schema or data changes.
     *
     * @param databaseId the database ID
     */
    public void invalidateDatabase(long databaseId) {
        String prefix = "db:" + databaseId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        log.info("Invalidated cache for database {}", databaseId);
    }

    /**
     * Invalidate all cached results for a specific table.
     *
     * @param databaseId the database ID
     * @param tableId the table ID
     */
    public void invalidateTable(long databaseId, long tableId) {
        String tableMarker = ":t" + tableId + ":";
        String dbPrefix = "db:" + databaseId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(dbPrefix) && key.contains(tableMarker));
        log.info("Invalidated cache for table {} in database {}", tableId, databaseId);
    }

    /**
     * Invalidate all cached results for a specific user.
     *
     * @param userId the user ID
     */
    public void invalidateUser(long userId) {
        String userMarker = ":u" + userId + ":";
        cache.asMap().keySet().removeIf(key -> key.contains(userMarker));
        log.info("Invalidated cache for user {}", userId);
    }

    /**
     * Clear all cached results.
     */
    public void clearAll() {
        cache.invalidateAll();
        log.info("Cleared all cached query results");
    }

    /**
     * Configure caching strategy for a specific database.
     *
     * @param databaseId the database ID
     * @param strategy the cache strategy
     */
    public void setCacheStrategy(long databaseId, CacheStrategy strategy) {
        databaseCacheStrategies.put(databaseId, strategy);
        log.info("Set cache strategy for database {}: enabled={}, ttl={}", databaseId, strategy.enabled(), strategy.ttl());
    }

    /**
     * Get cache statistics.
     *
     * @return cache statistics
     */
    public CacheStats getStats() {
        var stats = cache.stats();
        return new CacheStats(
                cache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate(),
                stats.evictionCount());
    }

    public CacheStrategy getCacheStrategy(long databaseId) {
        return databaseCacheStrategies.getOrDefault(databaseId, CacheStrategy.DEFAULT);
    }

    private boolean isNativeQuery(JsonNode query) {
        if (query == null || query.isNull()) {
            return false;
        }
        String type = query.path("type").asText("");
        if ("native".equalsIgnoreCase(type)) {
            return true;
        }
        return query.has("native");
    }

    private String generateCacheKey(long databaseId, JsonNode query, Long userId) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("db:").append(databaseId).append(":");

        // Include user ID for permission-aware caching
        if (userId != null) {
            keyBuilder.append("u").append(userId).append(":");
        }

        // Extract table ID for targeted invalidation
        Long tableId = extractTableId(query);
        if (tableId != null) {
            keyBuilder.append("t").append(tableId).append(":");
        }

        // Hash the query to create a consistent key
        String queryHash = hashQuery(query);
        keyBuilder.append(queryHash);

        return keyBuilder.toString();
    }

    private Long extractTableId(JsonNode query) {
        if (query == null) return null;

        // Check for source-table in MBQL query
        JsonNode sourceTable = query.path("source-table");
        if (sourceTable.isNumber()) {
            return sourceTable.asLong();
        }

        // Check for nested query
        JsonNode innerQuery = query.path("query");
        if (innerQuery.isObject()) {
            return extractTableId(innerQuery);
        }

        return null;
    }

    private String hashQuery(JsonNode query) {
        try {
            String queryJson = objectMapper.writeValueAsString(query);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(queryJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.warn("Failed to hash query, using fallback", e);
            return String.valueOf(query.hashCode());
        }
    }

    private long estimateResultSize(DatasetQueryService.DatasetResult result) {
        if (result == null || result.rows() == null) {
            return 0;
        }

        long size = 0;
        for (List<Object> row : result.rows()) {
            if (row == null) continue;
            for (Object cell : row) {
                if (cell == null) {
                    size += 4;
                } else if (cell instanceof String s) {
                    size += s.length() * 2L;
                } else if (cell instanceof Number) {
                    size += 8;
                } else {
                    size += 32; // Estimate for other types
                }
            }
        }

        // Add overhead for column metadata
        if (result.cols() != null) {
            size += result.cols().size() * 100L;
        }

        return size;
    }

    /**
     * Cache strategy configuration for a database.
     */
    public record CacheStrategy(boolean enabled, Duration ttl, boolean cacheNativeQueries) {

        public static final CacheStrategy DEFAULT = new CacheStrategy(true, DEFAULT_TTL, true);
        public static final CacheStrategy DISABLED = new CacheStrategy(false, Duration.ZERO, false);

        public static CacheStrategy withTtl(Duration ttl) {
            return new CacheStrategy(true, ttl, true);
        }
    }

    /**
     * Cache statistics.
     */
    public record CacheStats(long size, long hitCount, long missCount, double hitRate, long evictionCount) {}

    private record CachedResult(DatasetQueryService.DatasetResult result, Instant cachedAt, long sizeBytes) {

        boolean isExpired(Duration ttl) {
            return Instant.now().isAfter(cachedAt.plus(ttl));
        }
    }
}
