package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsBookmark;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.copilot.analytics.service.ActivityService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService;
import com.yuzhi.dts.copilot.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.copilot.analytics.service.FieldValuesService;
import com.yuzhi.dts.copilot.analytics.service.PublicLinkService;
import com.yuzhi.dts.copilot.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.copilot.analytics.service.RevisionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/dashboard")
@Transactional
public class DashboardResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final AnalyticsBookmarkRepository bookmarkRepository;
    private final AnalyticsCardRepository cardRepository;
    private final ActivityService activityService;
    private final EntityIdGenerator entityIdGenerator;
    private final PublicLinkService publicLinkService;
    private final RevisionService revisionService;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsTableRepository tableRepository;
    private final FieldValuesService fieldValuesService;
    private final QueryExecutionFacade queryExecutionFacade;
    private final ObjectMapper objectMapper;

    public DashboardResource(
            AnalyticsSessionService sessionService,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            AnalyticsBookmarkRepository bookmarkRepository,
            AnalyticsCardRepository cardRepository,
            ActivityService activityService,
            EntityIdGenerator entityIdGenerator,
            PublicLinkService publicLinkService,
            RevisionService revisionService,
            AnalyticsFieldRepository fieldRepository,
            AnalyticsTableRepository tableRepository,
            FieldValuesService fieldValuesService,
            QueryExecutionFacade queryExecutionFacade,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.cardRepository = cardRepository;
        this.activityService = activityService;
        this.entityIdGenerator = entityIdGenerator;
        this.publicLinkService = publicLinkService;
        this.revisionService = revisionService;
        this.fieldRepository = fieldRepository;
        this.tableRepository = tableRepository;
        this.fieldValuesService = fieldValuesService;
        this.queryExecutionFacade = queryExecutionFacade;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        Set<Long> favoriteDashboardIds = new HashSet<>();
        for (AnalyticsBookmark b : bookmarkRepository.findAllByUserIdAndModel(user.get().getId(), "dashboard")) {
            if (b.getModelId() != null) {
                favoriteDashboardIds.add(b.getModelId());
            }
        }

        return ResponseEntity.ok(dashboardRepository.findAllByArchivedFalseOrderByIdAsc().stream()
                .map(dashboard -> toDashboardListItem(dashboard, favoriteDashboardIds.contains(dashboard.getId())))
                .toList());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        String name = body == null ? null : trimToNull(body.path("name").asText(null));
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }

        AnalyticsDashboard dashboard = new AnalyticsDashboard();
        dashboard.setEntityId(entityIdGenerator.newEntityId());
        dashboard.setName(name);
        dashboard.setDescription(textOrNull(body, "description"));
        dashboard.setArchived(body.path("archived").asBoolean(false));
        dashboard.setCollectionId(body.path("collection_id").isNull() ? null : body.path("collection_id").asLong(0) > 0 ? body.path("collection_id").asLong() : null);
        dashboard.setCreatorId(user.get().getId());
        if (body != null && body.has("parameters")) {
            dashboard.setParametersJson(body.path("parameters").toString());
        }

        dashboard = dashboardRepository.save(dashboard);
        revisionService.recordDashboardRevision(dashboard, List.of(), user.get().getId(), false);
        return ResponseEntity.ok(toDashboardDetail(dashboard, List.of(), false));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsDashboard dashboard = dashboardRepository.findById(id).orElse(null);
        if (dashboard == null) {
            return ResponseEntity.notFound().build();
        }
        boolean favorite = bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "dashboard", id).isPresent();
        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(id);
        activityService.recordView(user.get().getId(), "dashboard", id);
        return ResponseEntity.ok(toDashboardDetail(dashboard, dashcards, favorite));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        AnalyticsDashboard dashboard = dashboardRepository.findById(id).orElse(null);
        if (dashboard == null) {
            return ResponseEntity.notFound().build();
        }

        if (body != null && body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name == null) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
            }
            dashboard.setName(name);
        }
        if (body != null && body.has("description")) {
            dashboard.setDescription(textOrNull(body, "description"));
        }
        if (body != null && body.has("archived")) {
            dashboard.setArchived(body.path("archived").asBoolean(false));
        }
        if (body != null && body.has("collection_id")) {
            dashboard.setCollectionId(body.path("collection_id").isNull() ? null : body.path("collection_id").asLong(0) > 0 ? body.path("collection_id").asLong() : null);
        }
        if (body != null && body.has("parameters")) {
            dashboard.setParametersJson(body.path("parameters").toString());
        }

        dashboardRepository.save(dashboard);
        boolean favorite = bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "dashboard", id).isPresent();
        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(id);
        revisionService.recordDashboardRevision(dashboard, dashcards, user.get().getId(), false);
        return ResponseEntity.ok(toDashboardDetail(dashboard, dashcards, favorite));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsDashboard dashboard = dashboardRepository.findById(id).orElse(null);
        if (dashboard == null) {
            return ResponseEntity.notFound().build();
        }
        dashboard.setArchived(true);
        dashboardRepository.save(dashboard);
        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(id);
        MetabaseAuth.currentUser(sessionService, request).ifPresent(u -> revisionService.recordDashboardRevision(dashboard, dashcards, u.getId(), false));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/save", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        JsonNode dashboardNode = body != null && body.has("dashboard") ? body.path("dashboard") : body;
        Long dashboardId = dashboardNode != null && dashboardNode.path("id").canConvertToLong() ? dashboardNode.path("id").asLong() : null;
        if (dashboardId == null || dashboardId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "dashboard.id is required"));
        }

        AnalyticsDashboard dashboard = dashboardRepository.findById(dashboardId).orElse(null);
        if (dashboard == null) {
            return ResponseEntity.notFound().build();
        }

        if (dashboardNode != null && dashboardNode.has("name")) {
            String name = trimToNull(dashboardNode.path("name").asText(null));
            if (name == null) {
                return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
            }
            dashboard.setName(name);
        }
        if (dashboardNode != null && dashboardNode.has("description")) {
            dashboard.setDescription(textOrNull(dashboardNode, "description"));
        }
        if (dashboardNode != null && dashboardNode.has("archived")) {
            dashboard.setArchived(dashboardNode.path("archived").asBoolean(false));
        }
        if (dashboardNode != null && dashboardNode.has("collection_id")) {
            dashboard.setCollectionId(dashboardNode.path("collection_id").isNull()
                    ? null
                    : dashboardNode.path("collection_id").asLong(0) > 0 ? dashboardNode.path("collection_id").asLong() : null);
        }
        if (dashboardNode != null && dashboardNode.has("parameters")) {
            dashboard.setParametersJson(dashboardNode.path("parameters").toString());
        }
        dashboardRepository.save(dashboard);

        JsonNode dashcardsNode = null;
        if (body != null) {
            if (body.has("dashcards")) {
                dashcardsNode = body.path("dashcards");
            } else if (body.has("ordered_cards")) {
                dashcardsNode = body.path("ordered_cards");
            } else if (body.has("cards")) {
                dashcardsNode = body.path("cards");
            }
        }

        if (dashcardsNode != null && dashcardsNode.isArray()) {
            List<AnalyticsDashboardCard> existing = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboardId);
            Set<Long> incomingIds = collectLongIds(dashcardsNode);
            for (AnalyticsDashboardCard dashcard : existing) {
                if (!incomingIds.contains(dashcard.getId())) {
                    dashboardCardRepository.delete(dashcard);
                }
            }

            for (JsonNode dc : dashcardsNode) {
                Long dashcardId = dc.path("id").canConvertToLong() ? dc.path("id").asLong() : null;
                AnalyticsDashboardCard dashcard = dashcardId == null || dashcardId <= 0 ? null : dashboardCardRepository.findById(dashcardId).orElse(null);

                Long cardId = dc.path("card_id").canConvertToLong()
                        ? dc.path("card_id").asLong()
                        : dc.has("card") && dc.path("card").path("id").canConvertToLong() ? dc.path("card").path("id").asLong() : null;

                if (dashcard == null) {
                    if (cardId == null || cardId <= 0) {
                        continue;
                    }
                    dashcard = new AnalyticsDashboardCard();
                    dashcard.setDashboardId(dashboardId);
                    dashcard.setCardId(cardId);
                } else if (dashcard.getDashboardId() == null || dashcard.getDashboardId() != dashboardId) {
                    continue;
                }

                if (cardId != null && cardId > 0) {
                    dashcard.setCardId(cardId);
                }
                if (dc.has("row")) {
                    dashcard.setRow(dc.path("row").isNull() ? null : dc.path("row").asInt());
                }
                if (dc.has("col")) {
                    dashcard.setCol(dc.path("col").isNull() ? null : dc.path("col").asInt());
                }
                if (dc.has("size_x")) {
                    dashcard.setSizeX(dc.path("size_x").isNull() ? null : dc.path("size_x").asInt());
                }
                if (dc.has("size_y")) {
                    dashcard.setSizeY(dc.path("size_y").isNull() ? null : dc.path("size_y").asInt());
                }
                if (dc.has("parameter_mappings")) {
                    dashcard.setParameterMappingsJson(dc.path("parameter_mappings").toString());
                }
                if (dc.has("visualization_settings")) {
                    dashcard.setVisualizationSettingsJson(dc.path("visualization_settings").toString());
                }
                dashboardCardRepository.save(dashcard);
            }
        }

        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboardId);
        boolean favorite = bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "dashboard", dashboardId).isPresent();
        revisionService.recordDashboardRevision(dashboard, dashcards, user.get().getId(), false);
        return ResponseEntity.ok(toDashboardDetail(dashboard, dashcards, favorite));
    }

    @PostMapping(path = "/{dashboardId}/favorite")
    public ResponseEntity<?> favorite(@PathVariable("dashboardId") long dashboardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!dashboardRepository.existsById(dashboardId)) {
            return ResponseEntity.notFound().build();
        }
        bookmarkRepository.findByUserIdAndModelAndModelId(user.get().getId(), "dashboard", dashboardId).orElseGet(() -> {
            int maxOrdering = bookmarkRepository.findMaxOrderingByUserId(user.get().getId());
            AnalyticsBookmark bookmark = new AnalyticsBookmark();
            bookmark.setUserId(user.get().getId());
            bookmark.setModel("dashboard");
            bookmark.setModelId(dashboardId);
            bookmark.setOrdering(maxOrdering + 1);
            return bookmarkRepository.save(bookmark);
        });
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{dashboardId}/favorite")
    public ResponseEntity<?> unfavorite(@PathVariable("dashboardId") long dashboardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        bookmarkRepository.deleteByUserIdAndModelAndModelId(user.get().getId(), "dashboard", dashboardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{dashboardId}/copy", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> copy(@PathVariable("dashboardId") long dashboardId, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        AnalyticsDashboard source = dashboardRepository.findById(dashboardId).orElse(null);
        if (source == null) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsDashboard copy = new AnalyticsDashboard();
        copy.setEntityId(entityIdGenerator.newEntityId());
        copy.setName(Optional.ofNullable(trimToNull(body == null ? null : body.path("name").asText(null))).orElse(source.getName() + " copy"));
        copy.setDescription(source.getDescription());
        copy.setArchived(false);
        copy.setCollectionId(body != null && body.has("collection_id") ? body.path("collection_id").isNull() ? null : body.path("collection_id").asLong(0) > 0 ? body.path("collection_id").asLong() : null : source.getCollectionId());
        copy.setCreatorId(user.get().getId());
        copy.setParametersJson(source.getParametersJson());
        copy = dashboardRepository.save(copy);

        List<AnalyticsDashboardCard> sourceDashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(source.getId());
        for (AnalyticsDashboardCard src : sourceDashcards) {
            AnalyticsDashboardCard dc = new AnalyticsDashboardCard();
            dc.setDashboardId(copy.getId());
            dc.setCardId(src.getCardId());
            dc.setRow(src.getRow());
            dc.setCol(src.getCol());
            dc.setSizeX(src.getSizeX());
            dc.setSizeY(src.getSizeY());
            dc.setParameterMappingsJson(src.getParameterMappingsJson());
            dc.setVisualizationSettingsJson(src.getVisualizationSettingsJson());
            dashboardCardRepository.save(dc);
        }

        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(copy.getId());
        return ResponseEntity.ok(toDashboardDetail(copy, dashcards, false));
    }

    @GetMapping(path = "/{dashboardId}/cards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cards(@PathVariable("dashboardId") long dashboardId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!dashboardRepository.existsById(dashboardId)) {
            return ResponseEntity.notFound().build();
        }

        List<AnalyticsDashboardCard> dashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboardId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnalyticsDashboardCard dashcard : dashcards) {
            result.add(toDashcardResponse(dashcard, true));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/{dashboardId}/cards", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addCard(@PathVariable("dashboardId") long dashboardId, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        if (!dashboardRepository.existsById(dashboardId)) {
            return ResponseEntity.notFound().build();
        }
        Long cardId = body == null ? null : resolveCardId(body);
        if (cardId == null || cardId <= 0 || cardRepository.findById(cardId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "card_id is required"));
        }

        AnalyticsDashboardCard dashcard = new AnalyticsDashboardCard();
        dashcard.setDashboardId(dashboardId);
        dashcard.setCardId(cardId);
        dashcard.setRow(body != null && body.has("row") && !body.path("row").isNull() ? body.path("row").asInt() : null);
        dashcard.setCol(body != null && body.has("col") && !body.path("col").isNull() ? body.path("col").asInt() : null);
        dashcard.setSizeX(body != null && body.has("size_x") && !body.path("size_x").isNull() ? body.path("size_x").asInt() : null);
        dashcard.setSizeY(body != null && body.has("size_y") && !body.path("size_y").isNull() ? body.path("size_y").asInt() : null);
        if (body != null && body.has("parameter_mappings")) {
            dashcard.setParameterMappingsJson(body.path("parameter_mappings").toString());
        }
        if (body != null && body.has("visualization_settings")) {
            dashcard.setVisualizationSettingsJson(body.path("visualization_settings").toString());
        }

        dashcard = dashboardCardRepository.save(dashcard);
        return ResponseEntity.ok(toDashcardResponse(dashcard, true));
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    @PostMapping(
            path = "/{dashboardId}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashcardQuery(
            @PathVariable("dashboardId") long dashboardId,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return runDashcardQuery(dashboardId, dashcardId, cardId, body, request);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    @PostMapping(
            path = "/pivot/{dashboardId}/dashcard/{dashcardId}/card/{cardId}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashcardQueryPivot(
            @PathVariable("dashboardId") long dashboardId,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return runDashcardQuery(dashboardId, dashcardId, cardId, body, request);
    }

    @PostMapping(
            path = "/{dashboardId}/dashcard/{dashcardId}/card/{cardId}/query/{exportFormat}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashcardQueryExport(
            @PathVariable("dashboardId") long dashboardId,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("cardId") long cardId,
            @PathVariable("exportFormat") String exportFormat,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.status(501).body(Map.of("error", "Export format is not implemented: " + exportFormat));
    }

    @PostMapping(path = "/{dashboardId}/dashcard/{dashcardId}/execute/{slug}")
    public ResponseEntity<?> dashcardExecute(
            @PathVariable("dashboardId") long dashboardId,
            @PathVariable("dashcardId") long dashcardId,
            @PathVariable("slug") String slug,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(path = "/params/valid-filter-fields", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validFilterFields(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        List<Map<String, Object>> fields = fieldRepository.findAllByActiveTrueOrderByTableIdAscPositionAscIdAsc().stream()
                .filter(f -> !"hidden".equalsIgnoreCase(f.getVisibilityType()))
                .limit(500)
                .map(this::toFilterField)
                .toList();
        return ResponseEntity.ok(fields);
    }

    @GetMapping(path = "/{dashId}/params/{paramId}/values", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parameterValues(
            @PathVariable("dashId") long dashId,
            @PathVariable("paramId") String paramId,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(loadParameterValues(dashId, paramId, null));
    }

    @GetMapping(path = "/{dashId}/params/{paramId}/search/{query}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parameterSearch(
            @PathVariable("dashId") long dashId,
            @PathVariable("paramId") String paramId,
            @PathVariable("query") String query,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(loadParameterValues(dashId, paramId, query));
    }

    private List<String> loadParameterValues(long dashId, String paramId, String search) {
        AnalyticsDashboard dashboard = dashboardRepository.findById(dashId).orElse(null);
        if (dashboard == null) {
            return List.of();
        }

        Long fieldId = resolveDashboardParamFieldId(dashboard, paramId);
        if (fieldId == null) {
            return List.of();
        }

        AnalyticsField field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null) {
            return List.of();
        }
        AnalyticsTable table = tableRepository.findById(field.getTableId()).orElse(null);
        if (table == null) {
            return List.of();
        }

        try {
            return fieldValuesService.distinctValues(
                    field.getDatabaseId(),
                    table.getSchemaName(),
                    table.getName(),
                    field.getName(),
                    200,
                    search);
        } catch (SQLException e) {
            return List.of();
        }
    }

    private Long resolveDashboardParamFieldId(AnalyticsDashboard dashboard, String paramId) {
        if (dashboard.getParametersJson() == null || dashboard.getParametersJson().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(dashboard.getParametersJson());
            if (node == null || !node.isArray()) {
                return null;
            }
            for (JsonNode param : node) {
                if (param == null || !param.isObject()) {
                    continue;
                }
                if (!paramId.equals(param.path("id").asText(""))) {
                    continue;
                }
                Long id = extractFieldIdFromParam(param);
                if (id != null) {
                    return id;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Long extractFieldIdFromParam(JsonNode param) {
        JsonNode dim = param.get("dimension");
        Long fromDim = extractFieldIdFromDimension(dim);
        if (fromDim != null) {
            return fromDim;
        }
        JsonNode target = param.get("target");
        if (target != null && target.isArray() && target.size() >= 2 && "dimension".equalsIgnoreCase(target.get(0).asText(""))) {
            Long fromTarget = extractFieldIdFromDimension(target.get(1));
            if (fromTarget != null) {
                return fromTarget;
            }
        }
        JsonNode vsc = param.get("values_source_config");
        if (vsc != null && vsc.isObject()) {
            long fieldId = vsc.path("field_id").asLong(0);
            if (fieldId > 0) {
                return fieldId;
            }
        }
        return null;
    }

    private static Long extractFieldIdFromDimension(JsonNode dim) {
        if (dim == null || !dim.isArray() || dim.size() < 2) {
            return null;
        }
        if (!"field".equalsIgnoreCase(dim.get(0).asText(""))) {
            return null;
        }
        long fieldId = dim.get(1).asLong(0);
        return fieldId > 0 ? fieldId : null;
    }

    private Map<String, Object> toFilterField(AnalyticsField field) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", field.getId());
        out.put("name", field.getName());
        out.put("display_name", field.getDisplayName() == null || field.getDisplayName().isBlank() ? field.getName() : field.getDisplayName());
        out.put("table_id", field.getTableId());
        out.put("database_id", field.getDatabaseId());
        out.put("base_type", field.getBaseType());
        out.put("effective_type", field.getEffectiveType() == null ? field.getBaseType() : field.getEffectiveType());
        out.put("semantic_type", field.getSemanticType());
        return out;
    }

    @GetMapping(path = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listPublic(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        Set<Long> favoriteDashboardIds = new HashSet<>();
        if (user.isPresent()) {
            for (AnalyticsBookmark b : bookmarkRepository.findAllByUserIdAndModel(user.get().getId(), "dashboard")) {
                if (b.getModelId() != null) {
                    favoriteDashboardIds.add(b.getModelId());
                }
            }
        }

        return ResponseEntity.ok(dashboardRepository.findAllByArchivedFalseOrderByIdAsc().stream()
                .map(dashboard -> toDashboardListItem(dashboard, favoriteDashboardIds.contains(dashboard.getId())))
                .filter(item -> item.get("public_uuid") != null)
                .toList());
    }

    @GetMapping(path = "/embeddable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listEmbeddable(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPublicLink(@PathVariable("id") long dashboardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!dashboardRepository.existsById(dashboardId)) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        String uuid;
        try {
            uuid = publicLinkService.getOrCreateScoped(
                    PublicLinkService.MODEL_DASHBOARD, dashboardId, user.get().getId(), ctx.dept(), ctx.classification());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        return ResponseEntity.ok(Map.of("uuid", uuid));
    }

    @DeleteMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePublicLink(@PathVariable("id") long dashboardId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!dashboardRepository.existsById(dashboardId)) {
            return ResponseEntity.notFound().build();
        }
        publicLinkService.delete(PublicLinkService.MODEL_DASHBOARD, dashboardId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> runDashcardQuery(long dashboardId, long dashcardId, long cardId, JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        AnalyticsDashboardCard dashcard = dashboardCardRepository.findById(dashcardId).orElse(null);
        if (dashcard == null || dashcard.getDashboardId() == null || dashcard.getDashboardId() != dashboardId || dashcard.getCardId() == null || dashcard.getCardId() != cardId) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsCard card = cardRepository.findById(cardId).orElse(null);
        if (card == null || card.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        JsonNode datasetQuery;
        try {
            datasetQuery = objectMapper.readTree(card.getDatasetQueryJson());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Invalid saved dataset_query"));
        }

        long startedMillis = System.currentTimeMillis();
        try {
            JsonNode mbqlOverride = null;
            if ("query".equalsIgnoreCase(datasetQuery.path("type").asText(null))) {
                mbqlOverride = applyDashcardParametersToMbql(datasetQuery.get("query"), dashcard, body);
            }
            QueryExecutionFacade.PreparedQuery prepared = queryExecutionFacade.prepare(
                    datasetQuery,
                    body,
                    mbqlOverride,
                    DatasetQueryService.DatasetConstraints.defaults());
            long databaseId = prepared.databaseId();

            Map<String, Object> jsonQuery = new LinkedHashMap<>();
            jsonQuery.put("database", databaseId);
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
            data.put("results_timezone", result.resultsTimezone());
            data.put("results_metadata", Map.of("columns", result.resultsMetadataColumns()));
            data.put("insights", null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("database_id", databaseId);
            response.put("started_at", java.time.OffsetDateTime.now());
            response.put("json_query", jsonQuery);
            response.put("status", "completed");
            response.put("context", "question");
            response.put("row_count", result.rows().size());
            response.put("running_time", runningTimeMs);
            response.put("error", null);

            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "failed");
            error.put("class", e.getClass().getName());
            error.put("message", Optional.ofNullable(e.getMessage()).orElse("Query failed"));
            error.put("stacktrace", null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "failed");
            response.put("error", error);
            response.put("via", List.of());
            return ResponseEntity.accepted().body(response);
        }
    }

    private JsonNode applyDashcardParametersToMbql(JsonNode mbql, AnalyticsDashboardCard dashcard, JsonNode body) {
        if (mbql == null || !mbql.isObject()) {
            return mbql;
        }
        if (dashcard.getParameterMappingsJson() == null || dashcard.getParameterMappingsJson().isBlank()) {
            return mbql;
        }
        if (body == null || !body.isObject()) {
            return mbql;
        }

        JsonNode parametersNode = body.get("parameters");
        if (parametersNode == null || !parametersNode.isArray() || parametersNode.isEmpty()) {
            return mbql;
        }

        JsonNode mappingsNode;
        try {
            mappingsNode = objectMapper.readTree(dashcard.getParameterMappingsJson());
        } catch (Exception e) {
            return mbql;
        }
        if (mappingsNode == null || !mappingsNode.isArray() || mappingsNode.isEmpty()) {
            return mbql;
        }

        Map<String, JsonNode> providedValues = new LinkedHashMap<>();
        for (JsonNode p : parametersNode) {
            if (p == null || !p.isObject()) {
                continue;
            }
            String id = p.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            JsonNode value = p.get("value");
            if (value == null || value.isMissingNode()) {
                continue;
            }
            providedValues.put(id, value);
        }
        if (providedValues.isEmpty()) {
            return mbql;
        }

        List<JsonNode> injectedFilters = new ArrayList<>();
        for (JsonNode mapping : mappingsNode) {
            if (mapping == null || !mapping.isObject()) {
                continue;
            }
            String paramId = mapping.path("parameter_id").asText("");
            if (paramId.isBlank()) {
                continue;
            }
            JsonNode value = providedValues.get(paramId);
            if (value == null || value.isNull()) {
                continue;
            }

            JsonNode target = mapping.get("target");
            Long fieldId = null;
            if (target != null && target.isArray() && target.size() >= 2 && "dimension".equalsIgnoreCase(target.get(0).asText(""))) {
                fieldId = extractFieldIdFromDimension(target.get(1));
            }
            if (fieldId == null || fieldId <= 0) {
                continue;
            }

            ArrayNode fieldRef = objectMapper.createArrayNode().add("field").add(fieldId).addNull();
            injectedFilters.add(buildEqualityOrInFilter(fieldRef, value));
        }

        if (injectedFilters.isEmpty()) {
            return mbql;
        }

        ObjectNode merged = mbql.deepCopy();
        JsonNode existing = merged.get("filter");
        if (existing == null || existing.isNull() || existing.isMissingNode()) {
            if (injectedFilters.size() == 1) {
                merged.set("filter", injectedFilters.get(0));
            } else {
                ArrayNode and = objectMapper.createArrayNode().add("and");
                injectedFilters.forEach(and::add);
                merged.set("filter", and);
            }
            return merged;
        }

        ArrayNode and = objectMapper.createArrayNode().add("and");
        and.add(existing);
        injectedFilters.forEach(and::add);
        merged.set("filter", and);
        return merged;
    }

    private JsonNode buildEqualityOrInFilter(ArrayNode fieldRef, JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createArrayNode().add("is-null").add(fieldRef);
        }
        if (value.isArray()) {
            ArrayNode values = objectMapper.createArrayNode();
            for (JsonNode v : value) {
                values.add(v);
            }
            return objectMapper.createArrayNode().add("in").add(fieldRef).add(values);
        }
        return objectMapper.createArrayNode().add("=").add(fieldRef).add(value);
    }

    private Map<String, Object> toDashboardListItem(AnalyticsDashboard dashboard, boolean favorite) {
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
        map.put("can_write", true);
        map.put("public_uuid", publicLinkService.publicUuidFor(PublicLinkService.MODEL_DASHBOARD, dashboard.getId()).orElse(null));
        map.put("favorite", favorite);
        return map;
    }

    private Map<String, Object> toDashboardDetail(AnalyticsDashboard dashboard, List<AnalyticsDashboardCard> dashcards, boolean favorite) {
        Map<String, Object> map = toDashboardListItem(dashboard, favorite);
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
            map.put("card", card == null ? null : toCardResponse(card));
        }
        return map;
    }

    private Map<String, Object> toCardResponse(AnalyticsCard card) {
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

    private static Set<Long> collectLongIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        return stream(node).stream()
                .map(dc -> dc.path("id").canConvertToLong() ? dc.path("id").asLong() : null)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private static List<JsonNode> stream(JsonNode arrayNode) {
        List<JsonNode> nodes = new ArrayList<>();
        arrayNode.forEach(nodes::add);
        return nodes;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        if (!node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null ? null : value;
    }

    private static Long resolveCardId(JsonNode body) {
        if (body == null) {
            return null;
        }
        if (body.path("card_id").canConvertToLong()) {
            return body.path("card_id").asLong();
        }
        if (body.path("cardId").canConvertToLong()) {
            return body.path("cardId").asLong();
        }
        if (body.has("card") && body.path("card").path("id").canConvertToLong()) {
            return body.path("card").path("id").asLong();
        }
        return null;
    }
}
