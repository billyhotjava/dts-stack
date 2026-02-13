package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsBookmark;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.ActivityService;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.MbqlToSqlService;
import com.yuzhi.dts.analytics.service.NativeQueryTemplateService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.QueryExportService;
import com.yuzhi.dts.analytics.service.RevisionService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@Transactional
public class CardResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsBookmarkRepository bookmarkRepository;
    private final AnalyticsUserRepository userRepository;
    private final ActivityService activityService;
    private final DatasetQueryService datasetQueryService;
    private final MbqlToSqlService mbqlToSqlService;
    private final NativeQueryTemplateService nativeQueryTemplateService;
    private final EntityIdGenerator entityIdGenerator;
    private final PublicLinkService publicLinkService;
    private final RevisionService revisionService;
    private final QueryExportService queryExportService;
    private final ObjectMapper objectMapper;

    public CardResource(
            AnalyticsSessionService sessionService,
            AnalyticsCardRepository cardRepository,
            AnalyticsBookmarkRepository bookmarkRepository,
            AnalyticsUserRepository userRepository,
            ActivityService activityService,
            DatasetQueryService datasetQueryService,
            MbqlToSqlService mbqlToSqlService,
            NativeQueryTemplateService nativeQueryTemplateService,
            EntityIdGenerator entityIdGenerator,
            PublicLinkService publicLinkService,
            RevisionService revisionService,
            QueryExportService queryExportService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.cardRepository = cardRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.datasetQueryService = datasetQueryService;
        this.mbqlToSqlService = mbqlToSqlService;
        this.nativeQueryTemplateService = nativeQueryTemplateService;
        this.entityIdGenerator = entityIdGenerator;
        this.publicLinkService = publicLinkService;
        this.revisionService = revisionService;
        this.queryExportService = queryExportService;
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

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    @PostMapping(path = "/{cardId}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> query(@PathVariable("cardId") long cardId, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null) {
            return ResponseEntity.notFound().build();
        }

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
                JsonNode parametersNode = body == null ? null : body.get("parameters");
                if (parametersNode != null && !parametersNode.isNull() && !parametersNode.isMissingNode() && sql.contains("{{")) {
                    NativeQueryTemplateService.RenderedQuery rendered = nativeQueryTemplateService.render(sql, parametersNode);
                    sql = rendered.sql();
                    bindings = rendered.bindings();
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

            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (SQLException e) {
            return ResponseEntity.accepted()
                    .body(Map.of("database_id", databaseId, "started_at", startedAt, "error", e.getMessage(), "data", Map.of("rows", List.of(), "cols", List.of())));
        }
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    @PostMapping(path = "/pivot/{cardId}/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pivotQuery(@PathVariable("cardId") long cardId, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        return query(cardId, body, request);
    }

    /**
     * Export card query results to CSV format.
     */
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
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
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
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
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
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

        String type = datasetQuery.path("type").asText(null);
        long databaseId = datasetQuery.path("database").asLong(0);
        if (databaseId <= 0) {
            response.setStatus(400);
            response.getWriter().write("dataset_query.database is required");
            return;
        }

        try {
            String sql;
            List<Object> bindings = List.of();

            if ("native".equalsIgnoreCase(type)) {
                sql = datasetQuery.path("native").path("query").asText(null);
                if (sql == null || sql.isBlank()) {
                    response.setStatus(400);
                    response.getWriter().write("dataset_query.native.query is required");
                    return;
                }
                JsonNode parametersNode = body == null ? null : body.get("parameters");
                if (parametersNode != null && !parametersNode.isNull() && !parametersNode.isMissingNode() && sql.contains("{{")) {
                    NativeQueryTemplateService.RenderedQuery rendered = nativeQueryTemplateService.render(sql, parametersNode);
                    sql = rendered.sql();
                    bindings = rendered.bindings();
                }
            } else if ("query".equalsIgnoreCase(type)) {
                JsonNode mbql = datasetQuery.get("query");
                // Use higher limits for exports
                DatasetQueryService.DatasetConstraints exportConstraints =
                        new DatasetQueryService.DatasetConstraints(100000, 300, "UTC");
                MbqlToSqlService.TranslationResult translated = mbqlToSqlService.translateSelect(databaseId, mbql, exportConstraints);
                sql = translated.sql();
                bindings = translated.bindings();
            } else {
                response.setStatus(400);
                response.getWriter().write("Only native and query (MBQL) queries are supported");
                return;
            }

            // Use higher limits for exports
            DatasetQueryService.DatasetConstraints exportConstraints =
                    new DatasetQueryService.DatasetConstraints(100000, 300, "UTC");
            DatasetQueryService.DatasetResult result = datasetQueryService.runNative(databaseId, sql, exportConstraints, bindings);

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
        creator.put("email", user.getEmail());
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
            JsonNode query = objectMapper.readTree(card.getDatasetQueryJson());
            long databaseId = query.path("database").asLong(0);
            if (databaseId <= 0) {
                return List.of();
            }

            String sql;
            String type = query.path("type").asText("native");
            if ("native".equalsIgnoreCase(type)) {
                sql = query.path("native").path("query").asText(null);
                if (sql == null || sql.isBlank()) {
                    return List.of();
                }
            } else if ("query".equalsIgnoreCase(type)) {
                MbqlToSqlService.TranslationResult translated =
                        mbqlToSqlService.translateSelect(databaseId, query.get("query"), DatasetQueryService.DatasetConstraints.defaults());
                sql = translated.sql();
                DatasetQueryService.DatasetResult result = datasetQueryService.runNative(
                        databaseId, sql, DatasetQueryService.DatasetConstraints.defaults(), translated.bindings());
                return result.resultsMetadataColumns();
            } else {
                return List.of();
            }

            DatasetQueryService.DatasetResult result =
                    datasetQueryService.runNative(databaseId, sql, DatasetQueryService.DatasetConstraints.defaults(), List.of());
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
