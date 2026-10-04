package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsBookmark;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCollectionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
@RequestMapping("/api/bookmark")
@Transactional
public class BookmarkResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsBookmarkRepository bookmarkRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsCollectionRepository collectionRepository;

    public BookmarkResource(
            AnalyticsSessionService sessionService,
            AnalyticsBookmarkRepository bookmarkRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsCollectionRepository collectionRepository) {
        this.sessionService = sessionService;
        this.bookmarkRepository = bookmarkRepository;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.collectionRepository = collectionRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(bookmarkRepository.findAllByUserIdOrderByOrderingAscCreatedAtDesc(user.get().getId()).stream()
                .map(this::toBookmarkResponse)
                .toList());
    }

    @PostMapping(path = "/{model}/{id}")
    public ResponseEntity<?> create(@PathVariable("model") String model, @PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        String normalizedModel = normalizeModel(model);
        if (normalizedModel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bookmark model: " + model));
        }
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "id must be a positive integer"));
        }
        if (!targetExists(normalizedModel, id)) {
            return ResponseEntity.notFound().build();
        }

        Long userId = user.get().getId();
        Optional<AnalyticsBookmark> existing = bookmarkRepository.findByUserIdAndModelAndModelId(userId, normalizedModel, id);
        if (existing.isPresent()) {
            return ResponseEntity.noContent().build();
        }

        int maxOrdering = bookmarkRepository.findMaxOrderingByUserId(userId);
        AnalyticsBookmark bookmark = new AnalyticsBookmark();
        bookmark.setUserId(userId);
        bookmark.setModel(normalizedModel);
        bookmark.setModelId(id);
        bookmark.setOrdering(maxOrdering + 1);
        bookmarkRepository.save(bookmark);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{model}/{id}")
    public ResponseEntity<?> delete(@PathVariable("model") String model, @PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        String normalizedModel = normalizeModel(model);
        if (normalizedModel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bookmark model: " + model));
        }
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "id must be a positive integer"));
        }

        bookmarkRepository.deleteByUserIdAndModelAndModelId(user.get().getId(), normalizedModel, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/ordering", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reorder(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        JsonNode orderingNode = body;
        if (orderingNode != null && orderingNode.isObject() && orderingNode.has("ordering")) {
            orderingNode = orderingNode.get("ordering");
        }
        if (orderingNode == null || !orderingNode.isArray()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Expected JSON array payload (or {\"ordering\": [...]})"));
        }

        Long userId = user.get().getId();
        List<AnalyticsBookmark> all = bookmarkRepository.findAllByUserIdOrderByOrderingAscCreatedAtDesc(userId);
        Map<String, AnalyticsBookmark> index = new HashMap<>();
        for (AnalyticsBookmark b : all) {
            index.put(key(b.getModel(), b.getModelId()), b);
        }

        int position = 0;
        for (JsonNode item : orderingNode) {
            String model = normalizeModel(textOrNull(item, "model"));
            if (model == null) {
                model = normalizeModel(textOrNull(item, "type"));
            }
            Long modelId = item != null && item.path("model_id").canConvertToLong() ? item.path("model_id").asLong() : null;
            if (modelId == null || modelId <= 0) {
                modelId = item != null && item.path("id").canConvertToLong() ? item.path("id").asLong() : null;
            }
            if (model == null || modelId == null || modelId <= 0) {
                continue;
            }

            AnalyticsBookmark bookmark = index.get(key(model, modelId));
            if (bookmark == null) {
                continue;
            }
            bookmark.setOrdering(position++);
            bookmarkRepository.save(bookmark);
        }

        return ResponseEntity.ok(bookmarkRepository.findAllByUserIdOrderByOrderingAscCreatedAtDesc(userId).stream()
                .map(this::toBookmarkResponse)
                .toList());
    }

    private boolean targetExists(String model, long id) {
        return switch (model) {
            case "card" -> cardRepository.existsById(id);
            case "dashboard" -> dashboardRepository.existsById(id);
            case "collection" -> collectionRepository.existsById(id);
            default -> false;
        };
    }

    private Map<String, Object> toBookmarkResponse(AnalyticsBookmark bookmark) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", bookmark.getId());
        map.put("model", bookmark.getModel());
        map.put("model_id", bookmark.getModelId());
        map.put("created_at", bookmark.getCreatedAt());
        map.put("updated_at", bookmark.getUpdatedAt());
        map.put("ordering", bookmark.getOrdering());
        return map;
    }

    private static String normalizeModel(String model) {
        if (model == null) {
            return null;
        }
        String m = model.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "card", "dashboard", "collection" -> m;
            default -> null;
        };
    }

    private static String key(String model, Long id) {
        return model + ":" + id;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isTextual()) {
            String s = v.asText();
            return s == null || s.isBlank() ? null : s;
        }
        return v.asText(null);
    }
}
