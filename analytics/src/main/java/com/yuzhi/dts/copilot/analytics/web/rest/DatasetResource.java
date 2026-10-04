package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsDatabaseAliasResolver;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.FederatedQueryGuardrail;
import com.yuzhi.dts.copilot.analytics.service.QueryCacheService;
import com.yuzhi.dts.copilot.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.copilot.analytics.service.QueryPermissionService;
import com.yuzhi.dts.copilot.analytics.web.rest.errors.ApiError;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dataset")
public class DatasetResource {
    private static final String ERROR_CODE_HEADER = "X-Error-Code";
    private static final String ERROR_RETRYABLE_HEADER = "X-Error-Retryable";

    private final AnalyticsSessionService sessionService;
    private final QueryCacheService queryCacheService;
    private final QueryPermissionService queryPermissionService;
    private final QueryExecutionFacade queryExecutionFacade;
    private final AnalyticsDatabaseAliasResolver databaseAliasResolver;
    private final FederatedQueryGuardrail federatedQueryGuardrail;

    public DatasetResource(
            AnalyticsSessionService sessionService,
            QueryCacheService queryCacheService,
            QueryPermissionService queryPermissionService,
            QueryExecutionFacade queryExecutionFacade,
            AnalyticsDatabaseAliasResolver databaseAliasResolver,
            FederatedQueryGuardrail federatedQueryGuardrail) {
        this.sessionService = sessionService;
        this.queryCacheService = queryCacheService;
        this.queryPermissionService = queryPermissionService;
        this.queryExecutionFacade = queryExecutionFacade;
        this.databaseAliasResolver = databaseAliasResolver;
        this.federatedQueryGuardrail = federatedQueryGuardrail;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> run(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return buildApiError(
                    HttpStatus.UNAUTHORIZED,
                    "SEC_UNAUTHORIZED",
                    "Authentication required",
                    false,
                    request);
        }

        if (body == null || !body.isObject()) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "Invalid dataset query",
                    false,
                    request);
        }

        long databaseId;
        try {
            databaseId = databaseAliasResolver.resolveDatabaseId(body.path("database"));
        } catch (IllegalArgumentException e) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    e.getMessage(),
                    false,
                    request);
        }
        ObjectNode normalizedBody = body.deepCopy();
        normalizedBody.put("database", databaseId);
        String type = body.path("type").asText(null);
        if (type == null || type.isBlank()) {
            if (body.has("native")) {
                type = "native";
            } else if (body.has("query")) {
                type = "query";
            }
        }

        if (databaseId <= 0) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "database is required",
                    false,
                    request);
        }

        // Check query permissions
        Long userId = MetabaseAuth.getUserId(sessionService, request).orElse(null);
        QueryPermissionService.QueryPermissionCheck permissionCheck = queryPermissionService
                .checkQueryPermission(userId, databaseId, normalizedBody);
        if (!permissionCheck.allowed()) {
            return buildApiError(
                    HttpStatus.FORBIDDEN,
                    "SEC_FORBIDDEN",
                    permissionCheck.denialReason(),
                    false,
                    request);
        }

        DatasetQueryService.DatasetConstraints constraints = parseConstraints(normalizedBody);
        OffsetDateTime startedAt = OffsetDateTime.now();
        long startedMillis = System.currentTimeMillis();

        // Check if caching should be skipped
        boolean skipCache = body.path("cache").path("skip").asBoolean(false);

        try {
            QueryExecutionFacade.PreparedQuery prepared =
                    queryExecutionFacade.prepare(normalizedBody, normalizedBody, null, constraints);
            databaseId = prepared.databaseId();
            FederatedQueryGuardrail.ValidationResult federatedSource =
                    federatedQueryGuardrail.validate(databaseId, prepared.type(), prepared.sql());

            Map<String, Object> jsonQuery = new LinkedHashMap<>();
            jsonQuery.put("database", databaseId);
            jsonQuery.put(
                    "middleware",
                    Map.of(
                            "js-int-to-string?", true,
                            "add-default-userland-constraints?", true));
            jsonQuery.put("type", prepared.type());
            if ("native".equalsIgnoreCase(prepared.type())) {
                jsonQuery.put("native", Map.of("query", prepared.sql()));
            } else {
                jsonQuery.put("query", prepared.mbql());
            }

            // Try to get from cache first (unless skipping cache)
            DatasetQueryService.DatasetResult result;
            boolean cached = false;
            if (!skipCache) {
                Optional<DatasetQueryService.DatasetResult> cachedResult = queryCacheService.get(databaseId, normalizedBody,
                        userId);
                if (cachedResult.isPresent()) {
                    result = cachedResult.get();
                    cached = true;
                } else {
                    result = queryExecutionFacade.executeRaw(prepared);
                    queryCacheService.put(databaseId, normalizedBody, userId, result);
                }
            } else {
                result = queryExecutionFacade.executeRaw(prepared);
            }

            result = queryExecutionFacade.applyCompliance(result);

            long runningTimeMs = System.currentTimeMillis() - startedMillis;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.rows());
            data.put("cols", result.cols());
            data.put("native_form", Map.of("query", prepared.sql()));
            data.put("results_timezone", result.resultsTimezone());
            data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
            data.put("insights", null);
            if (federatedSource.federated()) {
                data.put("source_metadata", federatedSource.toResponseMetadata());
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("database_id", databaseId);
            response.put("started_at", startedAt);
            response.put("json_query", jsonQuery);
            response.put("average_execution_time", null);
            response.put("status", "completed");
            response.put("context", body.path("context").asText("ad-hoc"));
            response.put("row_count", result.rows().size());
            response.put("running_time", runningTimeMs);
            response.put("cached", cached);
            if (federatedSource.federated()) {
                response.put("source", federatedSource.toResponseMetadata());
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    e.getMessage(),
                    false,
                    request);
        } catch (SQLException e) {
            return buildApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "QUERY_EXEC_FAILED",
                    "Error executing query: " + e.getMessage(),
                    false,
                    request);
        }
    }

    @PostMapping(path = "/native", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> nativeQuery(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    @PostMapping(path = "/pivot", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivot(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    @PostMapping(path = "/duration", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> duration(@RequestBody JsonNode body, HttpServletRequest request) {
        return run(body, request);
    }

    /**
     * Get cache statistics.
     */
    @GetMapping(path = "/cache/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCacheStats(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        QueryCacheService.CacheStats stats = queryCacheService.getStats();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("size", stats.size());
        response.put("hit_count", stats.hitCount());
        response.put("miss_count", stats.missCount());
        response.put("hit_rate", stats.hitRate());
        response.put("eviction_count", stats.evictionCount());
        return ResponseEntity.ok(response);
    }

    /**
     * Clear all cached query results.
     */
    @DeleteMapping(path = "/cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> clearCache(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        queryCacheService.clearAll();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Cache cleared"));
    }
    @GetMapping(path = "/cache/policy/{databaseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCachePolicy(@PathVariable("databaseId") long databaseId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        if (databaseId <= 0) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "databaseId must be positive",
                    false,
                    request);
        }

        QueryCacheService.CacheStrategy strategy = queryCacheService.getCacheStrategy(databaseId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("databaseId", databaseId);
        response.put("enabled", strategy.enabled());
        response.put("ttlSeconds", strategy.ttl().toSeconds());
        response.put("cacheNativeQueries", strategy.cacheNativeQueries());
        return ResponseEntity.ok(response);
    }
    @PostMapping(path = "/cache/policy/{databaseId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setCachePolicy(
            @PathVariable("databaseId") long databaseId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        if (databaseId <= 0) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "databaseId must be positive",
                    false,
                    request);
        }

        boolean enabled = body == null || !body.has("enabled") || body.path("enabled").asBoolean(true);
        long ttlSeconds = body != null && body.has("ttlSeconds") ? body.path("ttlSeconds").asLong(300) : 300;
        boolean cacheNativeQueries = body == null || !body.has("cacheNativeQueries") || body.path("cacheNativeQueries").asBoolean(true);

        if (ttlSeconds < 1) {
            ttlSeconds = 1;
        }
        if (ttlSeconds > 86400) {
            ttlSeconds = 86400;
        }

        QueryCacheService.CacheStrategy strategy = enabled
                ? new QueryCacheService.CacheStrategy(true, Duration.ofSeconds(ttlSeconds), cacheNativeQueries)
                : QueryCacheService.CacheStrategy.DISABLED;

        queryCacheService.setCacheStrategy(databaseId, strategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("databaseId", databaseId);
        response.put("enabled", strategy.enabled());
        response.put("ttlSeconds", strategy.ttl().toSeconds());
        response.put("cacheNativeQueries", strategy.cacheNativeQueries());
        return ResponseEntity.ok(response);
    }
    @PostMapping(path = "/cache/warmup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> warmupCache(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        if (body == null || body.isNull()) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "request body is required",
                    false,
                    request);
        }

        JsonNode queriesNode = body.has("queries") ? body.path("queries") : body;
        List<JsonNode> queries = new ArrayList<>();
        if (queriesNode.isArray()) {
            for (JsonNode query : queriesNode) {
                if (query != null && query.isObject()) {
                    queries.add(query);
                }
            }
        } else if (queriesNode.isObject()) {
            queries.add(queriesNode);
        }

        if (queries.isEmpty()) {
            return buildApiError(
                    HttpStatus.BAD_REQUEST,
                    "REQ_INVALID_ARGUMENT",
                    "at least one dataset query is required",
                    false,
                    request);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < queries.size(); i++) {
            JsonNode query = queries.get(i);
            ResponseEntity<?> result = run(query, request);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("databaseId", query.path("database").asLong(0));
            item.put("httpStatus", result.getStatusCode().value());

            Object responseBody = result.getBody();
            if (result.getStatusCode().is2xxSuccessful() && responseBody instanceof Map<?, ?> map) {
                Object status = map.get("status");
                if ("completed".equals(status)) {
                    success++;
                    item.put("status", "ok");
                    item.put("rowCount", map.get("row_count"));
                    item.put("runningTime", map.get("running_time"));
                    item.put("cached", map.get("cached"));
                } else {
                    item.put("status", "failed");
                    Object err = map.containsKey("error") ? map.get("error") : extractErrorMessage(responseBody);
                    item.put("error", err);
                }
            } else {
                item.put("status", "failed");
                item.put("error", extractErrorMessage(responseBody));
            }

            items.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", queries.size());
        response.put("success", success);
        response.put("failed", queries.size() - success);
        response.put("items", items);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ApiError> buildApiError(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            HttpServletRequest request) {
        String resolvedMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = MDC.get("requestId");
        }
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                retryable,
                resolvedMessage,
                request.getRequestURI(),
                requestId);
        return ResponseEntity.status(status)
                .header(ERROR_CODE_HEADER, code)
                .header(ERROR_RETRYABLE_HEADER, String.valueOf(retryable))
                .body(payload);
    }

    private static String extractErrorMessage(Object responseBody) {
        if (responseBody instanceof ApiError apiError) {
            return apiError.message();
        }
        if (responseBody instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
            }
            Object error = map.get("error");
            if (error != null && !String.valueOf(error).isBlank()) {
                return String.valueOf(error);
            }
        }
        return responseBody == null ? "warmup failed" : String.valueOf(responseBody);
    }

    private static DatasetQueryService.DatasetConstraints parseConstraints(JsonNode body) {
        int maxResults = 2000;
        int timeoutSeconds = 60;
        String timezone = ZoneId.systemDefault().getId();

        JsonNode constraintsNode = body.path("constraints");
        if (constraintsNode != null && constraintsNode.isObject()) {
            JsonNode maxResultsNode = constraintsNode.get("max-results");
            if (maxResultsNode != null && maxResultsNode.canConvertToInt()) {
                maxResults = maxResultsNode.asInt();
            }
        }

        JsonNode queryTimeoutNode = body.path("query_timeout");
        if (queryTimeoutNode != null && queryTimeoutNode.canConvertToInt()) {
            timeoutSeconds = queryTimeoutNode.asInt();
        }

        JsonNode requestedTz = body.path("requested_timezone");
        if (requestedTz != null && requestedTz.isTextual() && !requestedTz.asText().isBlank()) {
            timezone = requestedTz.asText();
        }

        if (maxResults <= 0) {
            maxResults = 2000;
        }
        if (maxResults > 100_000) {
            maxResults = 100_000;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = (int) Duration.ofSeconds(60).toSeconds();
        }
        if (timeoutSeconds > 600) {
            timeoutSeconds = 600;
        }

        return new DatasetQueryService.DatasetConstraints(maxResults, timeoutSeconds, timezone);
    }
}
