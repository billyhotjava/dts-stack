package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsBookmark;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.ActivityService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.copilot.analytics.service.PublicLinkService;
import com.yuzhi.dts.copilot.analytics.service.QueryExportService;
import com.yuzhi.dts.copilot.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.copilot.analytics.service.QueryMetricsService;
import com.yuzhi.dts.copilot.analytics.service.QueryTraceService;
import com.yuzhi.dts.copilot.analytics.service.RevisionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/card")
public class CardResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsBookmarkRepository bookmarkRepository;
    private final AnalyticsUserRepository userRepository;
    private final ActivityService activityService;
    private final QueryExecutionFacade queryExecutionFacade;
    private final EntityIdGenerator entityIdGenerator;
    private final PublicLinkService publicLinkService;
    private final RevisionService revisionService;
    private final QueryExportService queryExportService;
    private final QueryMetricsService queryMetricsService;
    private final QueryTraceService queryTraceService;
    private final ObjectMapper objectMapper;

    public CardResource(
            AnalyticsSessionService sessionService,
            AnalyticsCardRepository cardRepository,
            AnalyticsBookmarkRepository bookmarkRepository,
            AnalyticsUserRepository userRepository,
            ActivityService activityService,
            QueryExecutionFacade queryExecutionFacade,
            EntityIdGenerator entityIdGenerator,
            PublicLinkService publicLinkService,
            RevisionService revisionService,
            QueryExportService queryExportService,
            QueryMetricsService queryMetricsService,
            QueryTraceService queryTraceService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.cardRepository = cardRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.queryExecutionFacade = queryExecutionFacade;
        this.entityIdGenerator = entityIdGenerator;
        this.publicLinkService = publicLinkService;
        this.revisionService = revisionService;
        this.queryExportService = queryExportService;
        this.queryMetricsService = queryMetricsService;
        this.queryTraceService = queryTraceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        Set<Long> favoriteCardIds = new HashSet<>();
        for (AnalyticsBookmark b : bookmarkRepository.findAllByUserIdAndModel(user.get().getId(), "card")) {
            if (b.getModelId() != null) {
                favoriteCardIds.add(b.getModelId());
            }
        }

        return ResponseEntity.ok(cardRepository.findAll().stream()
                .filter(card -> !card.isArchived())
                .map(card -> toCardResponse(card, null, favoriteCardIds.contains(card.getId())))
                .toList());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> create(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        String name = body == null ? null : trimToNull(body.path("name").asText(null));
        JsonNode datasetQuery = body == null ? null : body.get("dataset_query");
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }
        if (datasetQuery == null || !datasetQuery.isObject()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("dataset_query", "value must be a map.")));
        }

        Long databaseId = datasetQuery.path("database").canConvertToLong() ? datasetQuery.path("database").asLong() : null;
        if (databaseId == null || databaseId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("database", "dataset_query.database is required")));
        }

        AnalyticsCard card = new AnalyticsCard();
        card.setEntityId(entityIdGenerator.newEntityId());
        card.setName(name);
        card.setDescription(textOrNull(body, "description"));
        card.setArchived(body.path("archived").asBoolean(false));
        card.setCollectionId(body.path("collection_id").isNull() ? null : body.path("collection_id").asLong(0) > 0 ? body.path("collection_id").asLong() : null);
        card.setDatabaseId(databaseId);
        card.setDatasetQueryJson(datasetQuery.toString());
        card.setDisplay(Optional.ofNullable(trimToNull(body.path("display").asText(null))).orElse("table"));
        JsonNode vizSettings = body.get("visualization_settings");
        card.setVisualizationSettingsJson(vizSettings == null ? "{}" : vizSettings.toString());
        card.setCreatorId(user.get().getId());

        card = cardRepository.save(card);
        revisionService.recordCardRevision(card, user.get().getId(), false);

        List<Map<String, Object>> resultMetadata = computeResultMetadata(card);
        return ResponseEntity.ok(toCardResponse(card, resultMetadata, false));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        boolean favorite = bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "card", id).isPresent();
        return cardRepository.findById(id).map(card -> {
                    activityService.recordView(user.get().getId(), "card", id);
                    return ResponseEntity.ok(toCardResponse(card, computeResultMetadata(card), favorite));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        Optional<AnalyticsCard> existingOpt = cardRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsCard card = existingOpt.get();

        String name = body == null ? null : trimToNull(body.path("name").asText(null));
        if (name != null) {
            card.setName(name);
        }
        if (body != null && body.has("description")) {
            card.setDescription(textOrNull(body, "description"));
        }
        if (body != null && body.has("archived")) {
            card.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null && body.has("collection_id")) {
            card.setCollectionId(body.path("collection_id").isNull() ? null : body.path("collection_id").asLong(0) > 0 ? body.path("collection_id").asLong() : null);
        }
        if (body != null && body.has("display")) {
            card.setDisplay(Optional.ofNullable(trimToNull(body.path("display").asText(null))).orElse(card.getDisplay()));
        }
        if (body != null && body.has("visualization_settings")) {
            card.setVisualizationSettingsJson(body.path("visualization_settings").toString());
        }
        if (body != null && body.has("dataset_query")) {
            JsonNode datasetQuery = body.get("dataset_query");
            if (datasetQuery == null || !datasetQuery.isObject()) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("dataset_query", "value must be a map.")));
            }
            Long databaseId =
                    datasetQuery.path("database").canConvertToLong() ? datasetQuery.path("database").asLong() : null;
            if (databaseId == null || databaseId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("database", "dataset_query.database is required")));
            }
            card.setDatabaseId(databaseId);
            card.setDatasetQueryJson(datasetQuery.toString());
        }
        cardRepository.save(card);
        revisionService.recordCardRevision(card, user.get().getId(), false);

        boolean favorite = bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "card", id).isPresent();
        return ResponseEntity.ok(toCardResponse(card, computeResultMetadata(card), favorite));
    }

    @DeleteMapping(path = "/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return cardRepository.findById(id)
                .map(card -> {
                    card.setArchived(true);
                    cardRepository.save(card);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PostMapping(path = "/{cardId}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> query(@PathVariable("cardId") long cardId, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        long startedNanos = System.nanoTime();
        String metricResult = "error";
        String metricCode = "UNKNOWN";

        Long traceDatabaseId = null;
        String traceSql = null;
        Long traceMetricId = parseMetricId(body);
        String traceMetricVersion = parseMetricVersion(body);
        Object traceContext = parseTraceContext(body);
        List<QueryExecutionFacade.ExecutionAttempt> traceAttempts = new ArrayList<>();
        Long actorUserId = null;
        PlatformContext ctx = PlatformContext.from(request);

        try {
            Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
            if (auth.isPresent()) {
                metricCode = "UNAUTHENTICATED";
                metricResult = "rejected";
                return auth.get();
            }
            actorUserId = MetabaseAuth.currentUser(sessionService, request).map(AnalyticsUser::getId).orElse(null);

            AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
            if (card == null) {
                metricCode = "CARD_NOT_FOUND";
                metricResult = "rejected";
                return ResponseEntity.notFound().build();
            }

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

                DatasetQueryService.DatasetResult result =
                        queryExecutionFacade.executeWithCompliance(prepared, traceAttempts::add);
                long runningTimeMs = System.currentTimeMillis() - startedMillis;

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("rows", result.rows());
                data.put("cols", result.cols());
                data.put("native_form", Map.of("query", prepared.sql()));
                data.put("results_timezone", result.resultsTimezone());
                data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
                data.put("insights", null);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", data);
                response.put("database_id", databaseId);
                response.put("started_at", startedAt);
                response.put("json_query", jsonQuery);
                response.put("average_execution_time", null);
                response.put("status", "completed");
                response.put("context", "question");
                response.put("row_count", result.rows().size());
                response.put("running_time", runningTimeMs);
                response.put("requestId", resolveRequestId());

                metricResult = "success";
                metricCode = "NONE";
                return ResponseEntity.accepted().body(response);
            } catch (IllegalArgumentException e) {
                metricCode = "INVALID_ARGUMENT";
                metricResult = "rejected";
                return ResponseEntity.status(400).body(Map.of(
                        "error", e.getMessage(),
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
            queryMetricsService.record("card_query", metricResult, metricCode, durationNanos);
            queryTraceService.log(
                    "card_query",
                    cardId,
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PostMapping(path = "/pivot/{cardId}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivotQuery(@PathVariable("cardId") long cardId, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        return query(cardId, body, request);
    }

    /**
     * Export card query results to CSV format.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PostMapping(path = "/{cardId}/query/csv")
    public void exportCsv(
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        exportCard(cardId, body, request, response, QueryExportService.ExportFormat.CSV);
    }

    /**
     * Export card query results to Excel format.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PostMapping(path = "/{cardId}/query/xlsx")
    public void exportExcel(
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        exportCard(cardId, body, request, response, QueryExportService.ExportFormat.EXCEL);
    }

    /**
     * Export card query results to JSON format.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PostMapping(path = "/{cardId}/query/json")
    public void exportJson(
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        exportCard(cardId, body, request, response, QueryExportService.ExportFormat.JSON);
    }

    private void exportCard(
            long cardId,
            JsonNode body,
            HttpServletRequest request,
            HttpServletResponse response,
            QueryExportService.ExportFormat format) throws Exception {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            response.setStatus(401);
            response.getWriter().write("Unauthenticated");
            return;
        }

        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null) {
            response.setStatus(404);
            return;
        }

        JsonNode datasetQuery;
        try {
            datasetQuery = objectMapper.readTree(card.getDatasetQueryJson());
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("Invalid saved dataset_query");
            return;
        }

        try {
            DatasetQueryService.DatasetConstraints exportConstraints =
                    new DatasetQueryService.DatasetConstraints(100000, 300, "UTC");
            QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                    datasetQuery,
                    body,
                    null,
                    exportConstraints);
            DatasetQueryService.DatasetResult result = queryExecutionFacade.executeWithCompliance(prepared);

            // Set response headers
            String filename = sanitizeFilename(card.getName()) + queryExportService.getFileExtension(format);
            response.setContentType(queryExportService.getContentType(format));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // Export based on format
            QueryExportService.ExportOptions options = switch (format) {
                case CSV -> QueryExportService.ExportOptions.forCsv();
                case EXCEL -> QueryExportService.ExportOptions.forExcel().withSheetName(card.getName());
                case JSON -> QueryExportService.ExportOptions.forJson();
            };

            switch (format) {
                case CSV -> queryExportService.exportToCsv(result, response.getOutputStream(), options);
                case EXCEL -> queryExportService.exportToExcel(result, response.getOutputStream(), options);
                case JSON -> queryExportService.exportToJson(result, response.getOutputStream(), options);
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            response.getWriter().write(e.getMessage());
        } catch (SQLException e) {
            response.setStatus(500);
            response.getWriter().write("Query execution failed: " + e.getMessage());
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "export";
        }
        // Remove or replace invalid filename characters
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    @PostMapping(path = "/{id}/persist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> persist(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{id}/unpersist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> unpersist(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/{id}/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> refresh(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping(path = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publicCards(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPublicLink(@PathVariable("id") long cardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        String uuid;
        try {
            uuid = publicLinkService.getOrCreateScoped(
                    PublicLinkService.MODEL_CARD, cardId, user.get().getId(), ctx.dept(), ctx.classification());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        return ResponseEntity.ok(Map.of("uuid", uuid));
    }

    @DeleteMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePublicLink(@PathVariable("id") long cardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!cardRepository.existsById(cardId)) {
            return ResponseEntity.notFound().build();
        }
        publicLinkService.delete(PublicLinkService.MODEL_CARD, cardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/embeddable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embeddable(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{cardId}/related", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> related(@PathVariable("cardId") long cardId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/related", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> relatedGlobal(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    private Map<String, Object> toCardResponse(AnalyticsCard card, List<Map<String, Object>> resultMetadataColumns, boolean favorite) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", card.getDescription());
        response.put("archived", card.isArchived());
        response.put("collection_position", null);
        response.put("table_id", null);
        response.put("result_metadata", resultMetadataColumns == null ? List.of() : resultMetadataColumns);
        response.put("creator", card.getCreatorId() == null ? null : minimalCreator(card.getCreatorId()));
        response.put("can_write", true);
        response.put("favorite", favorite);
        response.put("database_id", card.getDatabaseId());
        response.put("enable_embedding", false);
        response.put("collection_id", card.getCollectionId());
        response.put("query_type", deriveQueryType(card));
        response.put("name", card.getName());
        response.put("last_query_start", null);
        response.put("dashboard_count", 0);
        response.put("average_query_time", null);
        response.put("creator_id", card.getCreatorId());
        response.put("moderation_reviews", List.of());
        response.put("updated_at", card.getUpdatedAt());
        response.put("made_public_by_id", null);
        response.put("public_uuid", publicLinkService.publicUuidFor(PublicLinkService.MODEL_CARD, card.getId()).orElse(null));
        response.put("embedding_params", null);
        response.put("cache_ttl", null);
        response.put("dataset_query", safeJson(card.getDatasetQueryJson()));
        response.put("id", card.getId());
        response.put("parameter_mappings", List.of());
        response.put("display", card.getDisplay());
        response.put("entity_id", card.getEntityId());
        response.put("collection_preview", true);
        response.put("last-edit-info", Map.of("timestamp", card.getUpdatedAt(), "id", card.getCreatorId()));
        response.put("visualization_settings", safeJson(card.getVisualizationSettingsJson()));
        return response;
    }

    private Map<String, Object> minimalCreator(Long creatorId) {
        return userRepository.findById(creatorId).map(this::toCreator).orElseGet(() -> Map.of("id", creatorId));
    }

    private Map<String, Object> toCreator(AnalyticsUser user) {
        Map<String, Object> creator = new LinkedHashMap<>();
        creator.put("email", user.getUsername());
        creator.put("first_name", user.getFirstName());
        creator.put("last_login", null);
        creator.put("is_qbnewb", true);
        creator.put("is_superuser", user.isSuperuser());
        creator.put("id", user.getId());
        creator.put("last_name", user.getLastName());
        creator.put("date_joined", null);
        creator.put("common_name", (user.getFirstName() + " " + user.getLastName()).trim());
        return creator;
    }

    private List<Map<String, Object>> computeResultMetadata(AnalyticsCard card) {
        try {
            JsonNode datasetQuery = objectMapper.readTree(card.getDatasetQueryJson());
            QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                    datasetQuery,
                    null,
                    null,
                    DatasetQueryService.DatasetConstraints.defaults());
            DatasetQueryService.DatasetResult result = queryExecutionFacade.executeRaw(prepared);
            return result.resultsMetadataColumns();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object safeJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String deriveQueryType(AnalyticsCard card) {
        try {
            JsonNode query = objectMapper.readTree(card.getDatasetQueryJson());
            String type = query.path("type").asText("native");
            if ("query".equalsIgnoreCase(type)) {
                return "query";
            }
            return "native";
        } catch (Exception e) {
            return "native";
        }
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String textOrNull(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return value == null || value.isBlank() ? null : value;
        }
        return node.toString();
    }
}
