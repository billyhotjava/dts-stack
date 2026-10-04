package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.EmbedTokenService;
import com.yuzhi.dts.copilot.analytics.service.MbqlToSqlService;
import com.yuzhi.dts.copilot.analytics.service.ScreenComplianceService;
import com.yuzhi.dts.copilot.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Transactional
public class EmbedResource {

    private final AnalyticsSessionService sessionService;
    private final EmbedTokenService embedTokenService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final DatasetQueryService datasetQueryService;
    private final MbqlToSqlService mbqlToSqlService;
    private final ScreenComplianceService screenComplianceService;
    private final QueryExecutionFacade queryExecutionFacade;
    private final ObjectMapper objectMapper;

    public EmbedResource(
            AnalyticsSessionService sessionService,
            EmbedTokenService embedTokenService,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            ScreenComplianceService screenComplianceService,
            QueryExecutionFacade queryExecutionFacade,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.embedTokenService = embedTokenService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
        this.screenComplianceService = screenComplianceService;
        this.queryExecutionFacade = queryExecutionFacade;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/embed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getEmbedSettings(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return ResponseEntity.ok(embedSettingsResponse());
    }

    @PutMapping(path = "/embed", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateEmbedSettings(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        if (body != null && body.has("enabled")) {
            embedTokenService.setEmbeddingEnabled(body.path("enabled").asBoolean(false));
        }

        boolean enabled = embedTokenService.isEmbeddingEnabled();
        if (enabled) {
            embedTokenService.getOrCreateSecretKey();
        }

        if (body != null && body.has("rotate_secret_key") && body.path("rotate_secret_key").asBoolean(false)) {
            embedTokenService.rotateSecretKey();
        }
        if (body != null && body.has("secret_key")) {
            if (body.path("secret_key").isNull()) {
                embedTokenService.rotateSecretKey();
            } else if (body.path("secret_key").isTextual()) {
                String secret = body.path("secret_key").asText();
                if (secret == null || secret.isBlank()) {
                    embedTokenService.rotateSecretKey();
                } else {
                    embedTokenService.setSecretKey(secret);
                }
            }
        }
        if (body != null && body.has("embedding_params")) {
            embedTokenService.setEmbeddingParams(body.get("embedding_params"));
        }

        return ResponseEntity.ok(embedSettingsResponse());
    }

    @GetMapping(path = "/preview_embed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewEmbedInfo(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of("enabled", embedTokenService.isEmbeddingEnabled()));
    }

    @PostMapping(path = "/preview_embed", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewEmbed(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        if (!embedTokenService.isEmbeddingEnabled()) {
            return ResponseEntity.status(400).body(Map.of("error", "Embedding is disabled"));
        }
        String secret = embedTokenService.getOrCreateSecretKey();

        long exp = body != null && body.has("exp") && body.path("exp").canConvertToLong()
                ? body.path("exp").asLong()
                : embedTokenService.defaultPreviewExpiryEpochSeconds();

        JsonNode resourceNode = body == null ? null : body.path("resource");
        long questionId = resourceNode != null && resourceNode.path("question").canConvertToLong() ? resourceNode.path("question").asLong() : 0;
        long dashboardId = resourceNode != null && resourceNode.path("dashboard").canConvertToLong() ? resourceNode.path("dashboard").asLong() : 0;
        if (questionId <= 0 && dashboardId <= 0 && body != null) {
            if (body.path("question").canConvertToLong()) {
                questionId = body.path("question").asLong();
            } else if (body.path("card_id").canConvertToLong()) {
                questionId = body.path("card_id").asLong();
            } else if (body.path("dashboard_id").canConvertToLong()) {
                dashboardId = body.path("dashboard_id").asLong();
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> resource = new LinkedHashMap<>();
        String url;
        if (questionId > 0) {
            resource.put("question", questionId);
            url = "/embed/question/";
        } else if (dashboardId > 0) {
            resource.put("dashboard", dashboardId);
            url = "/embed/dashboard/";
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "resource.question or resource.dashboard is required"));
        }
        payload.put("resource", resource);
        payload.put("params", body == null ? Map.of() : (body.has("params") ? body.get("params") : Map.of()));
        payload.put("exp", exp);

        String token = embedTokenService.signJwtHs256(payload, secret);
        return ResponseEntity.ok(Map.of("token", token, "url", url + token, "iframe_url", url + token));
    }

    @GetMapping(path = "/embed/card/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedCard(@PathVariable("token") String token, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!embedTokenService.isEmbeddingEnabled()) {
            return ResponseEntity.status(404).build();
        }
        JsonNode claims;
        try {
            claims = embedTokenService.verifyAndDecodeJwtHs256(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }

        long cardId = extractResourceId(claims, "question");
        if (cardId <= 0) {
            return ResponseEntity.status(404).build();
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toEmbedCard(card, token));
    }

    @PostMapping(path = "/embed/card/{token}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedCardQuery(
            @PathVariable("token") String token, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!embedTokenService.isEmbeddingEnabled()) {
            return ResponseEntity.status(404).build();
        }
        JsonNode claims;
        try {
            claims = embedTokenService.verifyAndDecodeJwtHs256(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
        long cardId = extractResourceId(claims, "question");
        if (cardId <= 0) {
            return ResponseEntity.status(404).build();
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return runCardDatasetQuery(card, body);
    }

    @PostMapping(path = "/embed/pivot/card/{token}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedPivotCardQuery(
            @PathVariable("token") String token, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        return embedCardQuery(token, body, request);
    }

    @GetMapping(path = "/embed/dashboard/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedDashboard(@PathVariable("token") String token, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!embedTokenService.isEmbeddingEnabled()) {
            return ResponseEntity.status(404).build();
        }
        JsonNode claims;
        try {
            claims = embedTokenService.verifyAndDecodeJwtHs256(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
        long dashboardId = extractResourceId(claims, "dashboard");
        if (dashboardId <= 0) {
            return ResponseEntity.status(404).build();
        }

        AnalyticsDashboard dashboard = dashboardRepository.findById(dashboardId).orElse(null);
        if (dashboard == null || dashboard.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboardId);
        return ResponseEntity.ok(toEmbedDashboard(dashboard, dashcards, token));
    }

    @PostMapping(
            path = "/embed/dashboard/{token}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedDashboardDashcardQuery(
            @PathVariable("token") String token,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!embedTokenService.isEmbeddingEnabled()) {
            return ResponseEntity.status(404).build();
        }
        JsonNode claims;
        try {
            claims = embedTokenService.verifyAndDecodeJwtHs256(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
        long dashboardId = extractResourceId(claims, "dashboard");
        if (dashboardId <= 0) {
            return ResponseEntity.status(404).build();
        }
        return runDashboardDashcardQuery(dashboardId, dashcardId, cardId, body);
    }

    @PostMapping(
            path = "/embed/pivot/dashboard/{token}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embedPivotDashboardDashcardQuery(
            @PathVariable("token") String token,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return embedDashboardDashcardQuery(token, dashcardId, cardId, body, request);
    }

    private Map<String, Object> embedSettingsResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", embedTokenService.isEmbeddingEnabled());
        response.put("secret_key", embedTokenService.getSecretKey().orElse(null));
        response.put("embedding_params", embedTokenService.embeddingParamsOrNull());
        return response;
    }

    private static long extractResourceId(JsonNode claims, String resourceKey) {
        if (claims == null) {
            return 0;
        }
        JsonNode resource = claims.path("resource");
        if (resource != null && resource.has(resourceKey) && resource.path(resourceKey).canConvertToLong()) {
            return resource.path(resourceKey).asLong();
        }
        return 0;
    }

    private ResponseEntity<?> runDashboardDashcardQuery(long dashboardId, long dashcardId, long cardId, JsonNode body) {
        AnalyticsDashboardCard dashcard = dashboardCardRepository.findById(dashcardId).orElse(null);
        if (dashcard == null || dashcard.getDashboardId() == null || dashcard.getDashboardId() != dashboardId || dashcard.getCardId() == null || dashcard.getCardId() != cardId) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return runCardDatasetQuery(card, body);
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
            QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                    datasetQuery,
                    body,
                    null,
                    DatasetQueryService.DatasetConstraints.defaults());

            Map<String, Object> jsonQuery = new LinkedHashMap<>();
            jsonQuery.put("constraints", Map.of("max-results", 10000, "max-results-bare-rows", 2000));
            jsonQuery.put("middleware", Map.of("js-int-to-string?", true, "ignore-cached-results?", false, "process-viz-settings?", false));
            jsonQuery.put("database", databaseId);
            jsonQuery.put("async?", true);
            jsonQuery.put("cache-ttl", null);
            jsonQuery.put("type", prepared.type());
            if ("native".equalsIgnoreCase(prepared.type())) {
                jsonQuery.put("native", Map.of("query", prepared.sql()));
            } else {
                jsonQuery.put("query", prepared.mbql());
            }

            DatasetQueryService.DatasetResult result = queryExecutionFacade.executeWithCompliance(prepared);
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

    private Map<String, Object> toEmbedCard(AnalyticsCard card, String token) {
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
        map.put("public_uuid", token);
        return map;
    }

    private Map<String, Object> toEmbedDashboard(AnalyticsDashboard dashboard, List<AnalyticsDashboardCard> dashcards, String token) {
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
        map.put("public_uuid", token);
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
            map.put("card", card == null ? null : toEmbedCard(card, null));
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
