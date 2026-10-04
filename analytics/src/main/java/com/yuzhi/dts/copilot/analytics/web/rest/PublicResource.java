package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenVersionRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.PublicLinkService;
import com.yuzhi.dts.copilot.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.copilot.analytics.service.QueryMetricsService;
import com.yuzhi.dts.copilot.analytics.service.QueryTraceService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicResource {

    private final AnalyticsSessionService sessionService;
    private final PublicLinkService publicLinkService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsScreenVersionRepository screenVersionRepository;
    private final QueryExecutionFacade queryExecutionFacade;
    private final QueryMetricsService queryMetricsService;
    private final QueryTraceService queryTraceService;
    private final ObjectMapper objectMapper;

    public PublicResource(
            AnalyticsSessionService sessionService,
            PublicLinkService publicLinkService,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            QueryExecutionFacade queryExecutionFacade,
            QueryMetricsService queryMetricsService,
            QueryTraceService queryTraceService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.publicLinkService = publicLinkService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.queryExecutionFacade = queryExecutionFacade;
        this.queryMetricsService = queryMetricsService;
        this.queryTraceService = queryTraceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> info(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping(path = "/card/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> card(@PathVariable("uuid") String uuid, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPublicLink link = publicLinkService.findByPublicUuid(uuid).orElse(null);
        if (link == null || !PublicLinkService.MODEL_CARD.equals(link.getModel())) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        if (!publicLinkService.canAccess(
                link,
                ctx.dept(),
                ctx.classification(),
                resolveClientIp(request),
                resolveSharePassword(request))) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsCard card = cardRepository.findById(link.getModelId()).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toPublicCard(card, link.getPublicUuid()));
    }

    @PostMapping(path = "/card/{uuid}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cardQuery(
            @PathVariable("uuid") String uuid, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPublicLink link = publicLinkService.findByPublicUuid(uuid).orElse(null);
        if (link == null || !PublicLinkService.MODEL_CARD.equals(link.getModel())) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        if (!publicLinkService.canAccess(
                link,
                ctx.dept(),
                ctx.classification(),
                resolveClientIp(request),
                resolveSharePassword(request))) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsCard card = cardRepository.findById(link.getModelId()).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return runCardDatasetQuery(card, body, request);
    }

    @PostMapping(path = "/pivot/card/{uuid}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivotCardQuery(
            @PathVariable("uuid") String uuid, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        return cardQuery(uuid, body, request);
    }

    @GetMapping(path = "/dashboard/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashboard(@PathVariable("uuid") String uuid, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPublicLink link = publicLinkService.findByPublicUuid(uuid).orElse(null);
        if (link == null || !PublicLinkService.MODEL_DASHBOARD.equals(link.getModel())) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        if (!publicLinkService.canAccess(
                link,
                ctx.dept(),
                ctx.classification(),
                resolveClientIp(request),
                resolveSharePassword(request))) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsDashboard dashboard = dashboardRepository.findById(link.getModelId()).orElse(null);
        if (dashboard == null || dashboard.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboard.getId());
        return ResponseEntity.ok(toPublicDashboard(dashboard, dashcards, link.getPublicUuid()));
    }

    @GetMapping(path = "/screen/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screen(@PathVariable("uuid") String uuid, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPublicLink link = publicLinkService.findByPublicUuid(uuid).orElse(null);
        if (link == null || !PublicLinkService.MODEL_SCREEN.equals(link.getModel())) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        if (!publicLinkService.canAccess(
                link,
                ctx.dept(),
                ctx.classification(),
                resolveClientIp(request),
                resolveSharePassword(request))) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsScreen screen = screenRepository.findById(link.getModelId()).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsScreenVersion publishedVersion =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        if (publishedVersion == null) {
            return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN).body("No published version");
        }
        return ResponseEntity.ok(toPublicScreen(screen, publishedVersion, link.getPublicUuid()));
    }

    @PostMapping(
            path = "/dashboard/{uuid}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashboardDashcardQuery(
            @PathVariable("uuid") String uuid,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsPublicLink link = publicLinkService.findByPublicUuid(uuid).orElse(null);
        if (link == null || !PublicLinkService.MODEL_DASHBOARD.equals(link.getModel())) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        if (!publicLinkService.canAccess(
                link,
                ctx.dept(),
                ctx.classification(),
                resolveClientIp(request),
                resolveSharePassword(request))) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsDashboard dashboard = dashboardRepository.findById(link.getModelId()).orElse(null);
        if (dashboard == null || dashboard.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsDashboardCard dashcard = dashboardCardRepository.findById(dashcardId).orElse(null);
        if (dashcard == null || dashcard.getDashboardId() == null || dashcard.getDashboardId() != dashboard.getId() || dashcard.getCardId() == null || dashcard.getCardId() != cardId) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return runCardDatasetQuery(card, body, request);
    }

    @PostMapping(
            path = "/pivot/dashboard/{uuid}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivotDashboardDashcardQuery(
            @PathVariable("uuid") String uuid,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return dashboardDashcardQuery(uuid, dashcardId, cardId, body, request);
    }

    private ResponseEntity<?> runCardDatasetQuery(AnalyticsCard card, JsonNode body, HttpServletRequest request) {
        long startedNanos = System.nanoTime();
        String metricResult = "error";
        String metricCode = "UNKNOWN";

        Long traceDatabaseId = null;
        String traceSql = null;
        Long traceMetricId = parseMetricId(body);
        String traceMetricVersion = parseMetricVersion(body);
        Object traceContext = parseTraceContext(body);
        List<QueryExecutionFacade.ExecutionAttempt> traceAttempts = new ArrayList<>();
        PlatformContext ctx = PlatformContext.from(request);
        Long actorUserId = MetabaseAuth.currentUser(sessionService, request).map(u -> u.getId()).orElse(null);

        try {
            JsonNode datasetQuery;
            try {
                datasetQuery = objectMapper.readTree(card.getDatasetQueryJson());
            } catch (Exception e) {
                metricCode = "INVALID_DATASET_QUERY";
                metricResult = "failed";
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Invalid saved dataset_query",
                        "code", metricCode,
                        "requestId", resolveRequestId()));
            }

            OffsetDateTime startedAt = OffsetDateTime.now();
            long startedMillis = System.currentTimeMillis();
            long databaseId = 0;

            try {
                QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                        datasetQuery,
                        body,
                        null,
                        DatasetQueryService.DatasetConstraints.defaults());
                databaseId = prepared.databaseId();
                traceDatabaseId = databaseId;
                traceSql = prepared.sql();

                Map<String, Object> jsonQuery = new LinkedHashMap<>();
                jsonQuery.put("constraints", Map.of("max-results", 10000, "max-results-bare-rows", 2000));
                jsonQuery.put(
                        "middleware",
                        Map.of("js-int-to-string?", true, "ignore-cached-results?", false, "process-viz-settings?", false));
                jsonQuery.put("database", databaseId);
                jsonQuery.put("async?", true);
                jsonQuery.put("cache-ttl", null);
                jsonQuery.put("type", prepared.type());
                if ("native".equalsIgnoreCase(prepared.type())) {
                    jsonQuery.put("native", Map.of("query", prepared.sql()));
                } else {
                    jsonQuery.put("query", prepared.mbql());
                }

                DatasetQueryService.DatasetResult result =
                        queryExecutionFacade.executeWithCompliance(prepared, traceAttempts::add);
                long runningTimeMs = System.currentTimeMillis() - startedMillis;

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("rows", result.rows());
                data.put("cols", result.cols());
                data.put("native_form", Map.of("query", prepared.sql()));
                data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
                data.put("rows_truncated", false);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "completed");
                response.put("json_query", jsonQuery);
                response.put("data", data);
                response.put("row_count", result.rows().size());
                response.put("running_time", runningTimeMs);
                response.put("started_at", startedAt.toString());
                response.put("requestId", resolveRequestId());

                metricResult = "success";
                metricCode = "NONE";
                return ResponseEntity.ok(response);
            } catch (IllegalArgumentException e) {
                metricCode = "INVALID_ARGUMENT";
                metricResult = "rejected";
                return ResponseEntity.status(400).body(Map.of(
                        "error", rootCauseMessage(e),
                        "code", metricCode,
                        "requestId", resolveRequestId()));
            } catch (SQLException e) {
                metricCode = classifySqlErrorCode(e);
                metricResult = "failed";
                return ResponseEntity.accepted().body(Map.of(
                        "database_id", databaseId,
                        "started_at", startedAt,
                        "status", "failed",
                        "error", rootCauseMessage(e),
                        "code", metricCode,
                        "requestId", resolveRequestId(),
                        "data", Map.of("rows", List.of(), "cols", List.of())));
            } catch (RuntimeException e) {
                metricCode = classifyRuntimeErrorCode(e);
                metricResult = "failed";
                return ResponseEntity.accepted().body(Map.of(
                        "database_id", databaseId,
                        "started_at", startedAt,
                        "status", "failed",
                        "error", rootCauseMessage(e),
                        "code", metricCode,
                        "requestId", resolveRequestId(),
                        "data", Map.of("rows", List.of(), "cols", List.of())));
            }
        } finally {
            long durationNanos = System.nanoTime() - startedNanos;
            Object tracePayload = enrichTraceContext(traceContext, traceAttempts, metricResult, metricCode);
            queryMetricsService.record("public_card_query", metricResult, metricCode, durationNanos);
            queryTraceService.log(
                    "public_card_query",
                    card.getId(),
                    traceDatabaseId,
                    traceMetricId,
                    traceMetricVersion,
                    traceSql,
                    metricResult,
                    metricCode,
                    resolveRequestId(),
                    actorUserId,
                    ctx.dept(),
                    ctx.classification(),
                    durationNanos / 1_000_000,
                    tracePayload);
        }
    }

    private Map<String, Object> toPublicCard(AnalyticsCard card, String publicUuid) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", card.getId());
        map.put("entity_id", card.getEntityId());
        map.put("name", card.getName());
        map.put("description", card.getDescription());
        map.put("archived", card.isArchived());
        map.put("collection_id", card.getCollectionId());
        map.put("database_id", card.getDatabaseId());
        map.put("display", card.getDisplay());
        map.put("dataset_query", parseJsonObject(card.getDatasetQueryJson()));
        map.put("visualization_settings", parseJsonObject(card.getVisualizationSettingsJson()));
        map.put("creator_id", card.getCreatorId());
        map.put("created_at", card.getCreatedAt());
        map.put("updated_at", card.getUpdatedAt());
        map.put("public_uuid", publicUuid);
        return map;
    }

    private Map<String, Object> toPublicDashboard(AnalyticsDashboard dashboard, List<AnalyticsDashboardCard> dashcards, String publicUuid) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dashboard.getId());
        map.put("entity_id", dashboard.getEntityId());
        map.put("name", dashboard.getName());
        map.put("description", dashboard.getDescription());
        map.put("archived", dashboard.isArchived());
        map.put("collection_id", dashboard.getCollectionId());
        map.put("creator_id", dashboard.getCreatorId());
        map.put("created_at", dashboard.getCreatedAt());
        map.put("updated_at", dashboard.getUpdatedAt());
        map.put("can_write", false);
        map.put("public_uuid", publicUuid);
        map.put("parameters", parseJsonArray(dashboard.getParametersJson()));
        map.put("dashcards", dashcards.stream().map(dc -> toDashcardResponse(dc, false)).toList());
        map.put("ordered_cards", dashcards.stream().map(dc -> toDashcardResponse(dc, true)).toList());
        map.put("tabs", List.of());
        return map;
    }

    private Map<String, Object> toDashcardResponse(AnalyticsDashboardCard dashcard, boolean includeCard) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dashcard.getId());
        map.put("dashboard_id", dashcard.getDashboardId());
        map.put("card_id", dashcard.getCardId());
        map.put("row", dashcard.getRow());
        map.put("col", dashcard.getCol());
        map.put("size_x", dashcard.getSizeX());
        map.put("size_y", dashcard.getSizeY());
        map.put("parameter_mappings", parseJsonArray(dashcard.getParameterMappingsJson()));
        map.put("visualization_settings", parseJsonObject(dashcard.getVisualizationSettingsJson()));
        map.put("series", List.of());

        if (includeCard) {
            AnalyticsCard card = dashcard.getCardId() == null ? null : cardRepository.findById(dashcard.getCardId()).orElse(null);
            map.put("card", card == null ? null : toPublicCard(card, null));
        }
        return map;
    }
    private String classifySqlErrorCode(SQLException e) {
        if (e == null) {
            return "DB_QUERY_FAILED";
        }
        String message = rootCauseMessage(e).toLowerCase();
        if (message.contains("connection refused")) {
            return "EXT_DB_CONNECTION_REFUSED";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "EXT_DB_TIMEOUT";
        }
        return "DB_QUERY_FAILED";
    }

    private String classifyRuntimeErrorCode(RuntimeException e) {
        if (e == null) {
            return "QUERY_RUNTIME_ERROR";
        }
        if (hasCause(e, "java.net.ConnectException")) {
            return "EXT_DB_CONNECTION_REFUSED";
        }
        if (hasCause(e, "com.zaxxer.hikari.pool.HikariPool")) {
            return "EXT_DB_CONNECT_FAILED";
        }
        String message = rootCauseMessage(e).toLowerCase();
        if (message.contains("connection refused")) {
            return "EXT_DB_CONNECTION_REFUSED";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "EXT_DB_TIMEOUT";
        }
        return "QUERY_RUNTIME_ERROR";
    }

    private String rootCauseMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }

    private boolean hasCause(Throwable error, String className) {
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getName().contains(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    private Long parseMetricId(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }

        JsonNode direct = body.get("metricId");
        if (direct != null && direct.canConvertToLong()) {
            long id = direct.asLong();
            return id > 0 ? id : null;
        }

        JsonNode snake = body.get("metric_id");
        if (snake != null && snake.canConvertToLong()) {
            long id = snake.asLong();
            return id > 0 ? id : null;
        }

        JsonNode semantic = body.get("semantic");
        if (semantic != null && semantic.isObject()) {
            JsonNode nested = semantic.get("metricId");
            if (nested != null && nested.canConvertToLong()) {
                long id = nested.asLong();
                return id > 0 ? id : null;
            }
            JsonNode nestedSnake = semantic.get("metric_id");
            if (nestedSnake != null && nestedSnake.canConvertToLong()) {
                long id = nestedSnake.asLong();
                return id > 0 ? id : null;
            }
        }

        return null;
    }

    private String parseMetricVersion(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }

        String direct = trimToNull(body.path("metricVersion").asText(null));
        if (direct != null) {
            return direct;
        }

        String snake = trimToNull(body.path("metric_version").asText(null));
        if (snake != null) {
            return snake;
        }

        JsonNode semantic = body.get("semantic");
        if (semantic != null && semantic.isObject()) {
            String nested = trimToNull(semantic.path("metricVersion").asText(null));
            if (nested != null) {
                return nested;
            }
            return trimToNull(semantic.path("metric_version").asText(null));
        }

        return null;
    }

    private Object parseTraceContext(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }

        JsonNode context = body.get("queryContext");
        if (context == null || context.isNull() || context.isMissingNode()) {
            return null;
        }
        return context;
    }

    private Object enrichTraceContext(
            Object originalContext,
            List<QueryExecutionFacade.ExecutionAttempt> attempts,
            String finalStatus,
            String finalCode) {
        if ((originalContext == null || originalContext instanceof JsonNode node && (node.isNull() || node.isMissingNode()))
                && (attempts == null || attempts.isEmpty())) {
            return null;
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        Object normalizedContext = normalizeTraceContext(originalContext);
        if (normalizedContext instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    merged.put(entry.getKey().toString(), entry.getValue());
                }
            }
        } else if (normalizedContext != null) {
            merged.put("queryContext", normalizedContext);
        }

        Map<String, Object> executionTrace = new LinkedHashMap<>();
        executionTrace.put("status", finalStatus);
        executionTrace.put("code", finalCode);
        executionTrace.put(
                "attempts",
                attempts == null ? List.of() : attempts.stream().map(this::toAttemptTrace).toList());
        executionTrace.put("attemptCount", attempts == null ? 0 : attempts.size());
        merged.put("executionTrace", executionTrace);
        return merged;
    }

    private Object normalizeTraceContext(Object context) {
        if (context == null) {
            return null;
        }
        if (context instanceof JsonNode node) {
            return objectMapper.convertValue(node, Object.class);
        }
        return context;
    }

    private Map<String, Object> toAttemptTrace(QueryExecutionFacade.ExecutionAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attemptNo", attempt.attemptNo());
        map.put("success", attempt.success());
        map.put("fromAutoFix", attempt.fromAutoFix());
        map.put("durationMs", attempt.durationMs());
        map.put("retryPlanned", attempt.retryPlanned());
        map.put("errorCategory", trimToNull(attempt.errorCategory()));
        map.put("errorMessage", trimToNull(attempt.errorMessage()));
        map.put("sql", truncate(attempt.sql(), 2000));
        map.put("rewrittenSql", truncate(attempt.rewrittenSql(), 2000));
        return map;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String resolveRequestId() {
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId == null || requestId.isBlank()) {
            return "unknown";
        }
        return requestId;
    }
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String resolveSharePassword(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-DTS-Link-Password");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String query = request.getParameter("password");
        if (query != null && !query.isBlank()) {
            return query.trim();
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && parts[0] != null && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return null;
        }
        return remote.trim();
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return null;
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toPublicScreen(
            AnalyticsScreen screen, AnalyticsScreenVersion publishedVersion, String publicUuid) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", screen.getId());
        map.put("name", publishedVersion.getName());
        map.put("width", publishedVersion.getWidth());
        map.put("height", publishedVersion.getHeight());
        map.put("backgroundColor", publishedVersion.getBackgroundColor());
        map.put("backgroundImage", publishedVersion.getBackgroundImage());
        map.put("theme", publishedVersion.getTheme());
        map.put("components", parseJsonArray(publishedVersion.getComponentsJson()));
        map.put("globalVariables", parseJsonArray(publishedVersion.getVariablesJson()));
        map.put("publishedVersionNo", publishedVersion.getVersionNo());
        map.put("public_uuid", publicUuid);
        return map;
    }

    private Object parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return List.of();
            }
            return node.isArray() ? node : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
