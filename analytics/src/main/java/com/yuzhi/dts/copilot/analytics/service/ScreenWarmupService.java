package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScreenWarmupService {

    private static final Logger log = LoggerFactory.getLogger(ScreenWarmupService.class);

    private final ObjectMapper objectMapper;
    private final DatasetQueryService datasetQueryService;
    private final QueryCacheService queryCacheService;
    private final QueryPermissionService queryPermissionService;
    private final AnalyticsDatabaseAliasResolver databaseAliasResolver;

    public ScreenWarmupService(
            ObjectMapper objectMapper,
            DatasetQueryService datasetQueryService,
            QueryCacheService queryCacheService,
            QueryPermissionService queryPermissionService,
            AnalyticsDatabaseAliasResolver databaseAliasResolver) {
        this.objectMapper = objectMapper;
        this.datasetQueryService = datasetQueryService;
        this.queryCacheService = queryCacheService;
        this.queryPermissionService = queryPermissionService;
        this.databaseAliasResolver = databaseAliasResolver;
    }

    public WarmupSummary warmupForPublishedScreen(AnalyticsScreen screen, Long userId) {
        if (screen == null || screen.getComponentsJson() == null || screen.getComponentsJson().isBlank()) {
            return new WarmupSummary(0, 0, 0, 0, List.of());
        }

        JsonNode components;
        try {
            components = objectMapper.readTree(screen.getComponentsJson());
        } catch (Exception e) {
            log.warn("[screen-warmup] failed to parse componentsJson, screenId={}", screen.getId(), e);
            return new WarmupSummary(0, 0, 0, 1, List.of(Map.of("status", "failed", "reason", "invalid componentsJson")));
        }

        if (components == null || !components.isArray()) {
            return new WarmupSummary(0, 0, 0, 0, List.of());
        }

        int total = 0;
        int warmed = 0;
        int skipped = 0;
        int failed = 0;

        Set<String> dedupe = new HashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (int i = 0; i < components.size(); i++) {
            JsonNode component = components.get(i);
            JsonNode dataSource = component.path("dataSource");
            if (dataSource == null || !dataSource.isObject()) {
                continue;
            }
            String sourceType = resolveSourceType(dataSource);
            if (!"sql".equalsIgnoreCase(sourceType)) {
                continue;
            }

            total++;
            String componentName = component.path("name").asText(component.path("id").asText("component-" + i));
            JsonNode dbConfig = resolveSqlConfig(dataSource);

            long databaseId;
            try {
                databaseId = parseDatabaseId(dbConfig);
            } catch (IllegalArgumentException e) {
                skipped++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("component", componentName);
                item.put("databaseId", 0L);
                item.put("status", "skipped");
                item.put("reason", e.getMessage());
                items.add(item);
                continue;
            }
            String query = trimToNull(dbConfig.path("query").asText(null));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("component", componentName);
            item.put("databaseId", databaseId);

            if (databaseId <= 0) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "missing databaseId");
                items.add(item);
                continue;
            }
            if (query == null) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "missing query");
                items.add(item);
                continue;
            }
            if (query.contains("{{")) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "query contains template variables");
                items.add(item);
                continue;
            }

            String key = databaseId + "::" + query;
            if (!dedupe.add(key)) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "duplicate query");
                items.add(item);
                continue;
            }

            QueryCacheService.CacheStrategy strategy = queryCacheService.getCacheStrategy(databaseId);
            if (!strategy.enabled()) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "cache disabled for database");
                items.add(item);
                continue;
            }
            if (!strategy.cacheNativeQueries()) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "native cache disabled for database");
                items.add(item);
                continue;
            }

            ObjectNode queryRequest = objectMapper.createObjectNode();
            queryRequest.put("database", databaseId);
            queryRequest.put("type", "native");
            ObjectNode nativeNode = objectMapper.createObjectNode();
            nativeNode.put("query", query);
            queryRequest.set("native", nativeNode);

            QueryPermissionService.QueryPermissionCheck permission =
                    queryPermissionService.checkQueryPermission(userId, databaseId, queryRequest);
            if (!permission.allowed()) {
                skipped++;
                item.put("status", "skipped");
                item.put("reason", "permission denied");
                item.put("detail", permission.denialReason());
                items.add(item);
                continue;
            }

            try {
                DatasetQueryService.DatasetResult result = datasetQueryService.runNative(
                        databaseId,
                        query,
                        DatasetQueryService.DatasetConstraints.defaults(),
                        List.of());
                queryCacheService.put(databaseId, queryRequest, userId, result);
                warmed++;
                item.put("status", "warmed");
                item.put("rowCount", result.rows() == null ? 0 : result.rows().size());
                items.add(item);
            } catch (SQLException | RuntimeException e) {
                failed++;
                item.put("status", "failed");
                item.put("reason", e.getMessage());
                items.add(item);
                log.warn("[screen-warmup] warmup failed, screenId={}, component={}, databaseId={}",
                        screen.getId(), componentName, databaseId, e);
            }
        }

        return new WarmupSummary(total, warmed, skipped, failed, items);
    }

    private long parseDatabaseId(JsonNode dbConfig) {
        if (dbConfig == null || dbConfig.isMissingNode()) {
            return 0L;
        }

        long dbId = databaseAliasResolver.resolveDatabaseId(dbConfig.path("databaseAlias"));
        if (dbId > 0) {
            return dbId;
        }

        dbId = databaseAliasResolver.resolveDatabaseId(dbConfig.path("databaseId"));
        if (dbId > 0) {
            return dbId;
        }
        return databaseAliasResolver.resolveDatabaseId(dbConfig.path("connectionId"));
    }

    private static String resolveSourceType(JsonNode dataSource) {
        if (dataSource == null || !dataSource.isObject()) {
            return "";
        }
        String sourceType = trimToNull(dataSource.path("sourceType").asText(null));
        if (sourceType != null) {
            return sourceType;
        }
        String type = trimToNull(dataSource.path("type").asText(null));
        if ("database".equalsIgnoreCase(type)) {
            return "sql";
        }
        return type == null ? "" : type;
    }

    private static JsonNode resolveSqlConfig(JsonNode dataSource) {
        if (dataSource == null || !dataSource.isObject()) {
            return null;
        }
        JsonNode sqlConfig = dataSource.path("sqlConfig");
        if (sqlConfig != null && sqlConfig.isObject()) {
            return sqlConfig;
        }
        JsonNode legacy = dataSource.path("databaseConfig");
        if (legacy != null && legacy.isObject()) {
            return legacy;
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }

    public record WarmupSummary(
            int totalDatabaseSources,
            int warmed,
            int skipped,
            int failed,
            List<Map<String, Object>> items) {}
}
