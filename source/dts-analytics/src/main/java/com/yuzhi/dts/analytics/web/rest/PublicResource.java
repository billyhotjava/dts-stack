package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.MbqlToSqlService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
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
    private final DatasetQueryService datasetQueryService;
    private final MbqlToSqlService mbqlToSqlService;
    private final ObjectMapper objectMapper;

    public PublicResource(
            AnalyticsSessionService sessionService,
            PublicLinkService publicLinkService,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            AnalyticsScreenRepository screenRepository,
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.publicLinkService = publicLinkService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.screenRepository = screenRepository;
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
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
        if (!publicLinkService.canAccess(link, ctx.dept(), ctx.classification())) {
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
        if (!publicLinkService.canAccess(link, ctx.dept(), ctx.classification())) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsCard card = cardRepository.findById(link.getModelId()).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return runCardDatasetQuery(card, body);
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
        if (!publicLinkService.canAccess(link, ctx.dept(), ctx.classification())) {
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
        if (!publicLinkService.canAccess(link, ctx.dept(), ctx.classification())) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        AnalyticsScreen screen = screenRepository.findById(link.getModelId()).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toPublicScreen(screen, link.getPublicUuid()));
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
        if (!publicLinkService.canAccess(link, ctx.dept(), ctx.classification())) {
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
        return runCardDatasetQuery(card, body);
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

    private ResponseEntity<?> runCardDatasetQuery(AnalyticsCard card, JsonNode body) {
        JsonNode datasetQuery;
        try {
            datasetQuery = objectMapper.readTree(card.getDatasetQueryJson());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Invalid saved dataset_query"));
        }

        String type = datasetQuery.path("type").asText(null);
        long databaseId = datasetQuery.path("database").asLong(0);
        if (databaseId <= 0) {
            return ResponseEntity.status(400).body(Map.of("error", "dataset_query.database is required"));
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        long startedMillis = System.currentTimeMillis();

        try {
            String sql;
            List<Object> bindings = List.of();
            Map<String, Object> jsonQuery = new LinkedHashMap<>();
            jsonQuery.put("constraints", Map.of("max-results", 10000, "max-results-bare-rows", 2000));
            jsonQuery.put("middleware", Map.of("js-int-to-string?", true, "ignore-cached-results?", false, "process-viz-settings?", false));
            jsonQuery.put("database", databaseId);
            jsonQuery.put("async?", true);
            jsonQuery.put("cache-ttl", null);

            if ("native".equalsIgnoreCase(type)) {
                sql = datasetQuery.path("native").path("query").asText(null);
                if (sql == null || sql.isBlank()) {
                    return ResponseEntity.status(400).body(Map.of("error", "dataset_query.native.query is required"));
                }
                jsonQuery.put("type", "native");
                jsonQuery.put("native", Map.of("query", sql));
            } else if ("query".equalsIgnoreCase(type)) {
                JsonNode mbql = datasetQuery.get("query");
                MbqlToSqlService.TranslationResult translated =
                        mbqlToSqlService.translateSelect(databaseId, mbql, DatasetQueryService.DatasetConstraints.defaults());
                sql = translated.sql();
                bindings = translated.bindings();
                jsonQuery.put("type", "query");
                jsonQuery.put("query", mbql);
            } else {
                return ResponseEntity.status(400).body(Map.of("error", "Only native and query (MBQL) queries are supported"));
            }

            DatasetQueryService.DatasetResult result =
                    datasetQueryService.runNative(databaseId, sql, DatasetQueryService.DatasetConstraints.defaults(), bindings);
            long runningTimeMs = System.currentTimeMillis() - startedMillis;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.rows());
            data.put("cols", result.cols());
            data.put("native_form", Map.of("query", sql));
            data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
            data.put("rows_truncated", false);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "completed");
            response.put("json_query", jsonQuery);
            response.put("data", data);
            response.put("row_count", result.rows().size());
            response.put("running_time", runningTimeMs);
            response.put("started_at", startedAt.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("class", e.getClass().getName());
            error.put("message", e.getMessage());
            error.put("stacktrace", null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "failed");
            response.put("error", error);
            response.put("via", List.of());
            return ResponseEntity.accepted().body(response);
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

    private Map<String, Object> toPublicScreen(AnalyticsScreen screen, String publicUuid) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", screen.getId());
        map.put("name", screen.getName());
        map.put("width", screen.getWidth());
        map.put("height", screen.getHeight());
        map.put("backgroundColor", screen.getBackgroundColor());
        map.put("backgroundImage", screen.getBackgroundImage());
        map.put("components", parseJsonArray(screen.getComponentsJson()));
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
