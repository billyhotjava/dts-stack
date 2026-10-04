package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCollection;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsBookmark;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCollectionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.ActivityService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CollectionService;
import com.yuzhi.dts.copilot.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collection")
@Transactional
public class CollectionResource {

    private static final Map<String, Object> ROOT_COLLECTION;

    static {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("authority_level", null);
        root.put("name", "Our analytics");
        root.put("id", "root");
        root.put("parent_id", null);
        root.put("effective_location", null);
        root.put("effective_ancestors", List.of());
        root.put("can_write", true);
        ROOT_COLLECTION = Collections.unmodifiableMap(root);
    }

    private final AnalyticsSessionService sessionService;
    private final AnalyticsCollectionRepository collectionRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsBookmarkRepository bookmarkRepository;
    private final CollectionService collectionService;
    private final EntityIdGenerator entityIdGenerator;
    private final ActivityService activityService;

    public CollectionResource(
            AnalyticsSessionService sessionService,
            AnalyticsCollectionRepository collectionRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsBookmarkRepository bookmarkRepository,
            CollectionService collectionService,
            EntityIdGenerator entityIdGenerator,
            ActivityService activityService) {
        this.sessionService = sessionService;
        this.collectionRepository = collectionRepository;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.collectionService = collectionService;
        this.entityIdGenerator = entityIdGenerator;
        this.activityService = activityService;
    }

    @GetMapping(path = "/root", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> root(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(ROOT_COLLECTION);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        AnalyticsCollection personal = collectionService.ensurePersonalCollection(user.get());
        List<Map<String, Object>> result = List.of(ROOT_COLLECTION, toListItem(personal, true));
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> tree(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        AnalyticsCollection personal = collectionService.ensurePersonalCollection(user.get());
        Map<String, Object> item = toTreeItem(personal);
        return ResponseEntity.ok(List.of(item));
    }

    @GetMapping(path = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> graph(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(Map.of("revision", 0, "groups", Map.of("1", Map.of("root", "write"), "2", Map.of("root", "write"))));
    }

    @GetMapping(path = "/{collectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("collectionId") String collectionId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if ("root".equals(collectionId)) {
            return ResponseEntity.ok(ROOT_COLLECTION);
        }
        Long id = parseLong(collectionId);
        if (id == null) {
            return ResponseEntity.notFound().build();
        }
        return collectionRepository.findById(id)
                .map(collection -> {
                    activityService.recordView(user.get().getId(), "collection", id);
                    return ResponseEntity.ok(toListItem(collection, true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{collectionId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> items(@PathVariable("collectionId") String collectionId, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        Long id = "root".equals(collectionId) ? null : parseLong(collectionId);
        if (!"root".equals(collectionId) && id == null) {
            return ResponseEntity.notFound().build();
        }

        Set<Long> favoriteDashboardIds = new HashSet<>();
        for (AnalyticsBookmark b : bookmarkRepository.findAllByUserIdAndModel(user.get().getId(), "dashboard")) {
            if (b.getModelId() != null) {
                favoriteDashboardIds.add(b.getModelId());
            }
        }
        Set<Long> favoriteCardIds = new HashSet<>();
        for (AnalyticsBookmark b : bookmarkRepository.findAllByUserIdAndModel(user.get().getId(), "card")) {
            if (b.getModelId() != null) {
                favoriteCardIds.add(b.getModelId());
            }
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        result.addAll((id == null
                        ? dashboardRepository.findAllByArchivedFalseAndCollectionIdIsNullOrderByIdAsc()
                        : dashboardRepository.findAllByArchivedFalseAndCollectionIdOrderByIdAsc(id))
                .stream()
                .map(dashboard -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", dashboard.getId());
                    map.put("entity_id", dashboard.getEntityId());
                    map.put("model", "dashboard");
                    map.put("name", dashboard.getName());
                    map.put("description", dashboard.getDescription());
                    map.put("archived", dashboard.isArchived());
                    map.put("collection_id", dashboard.getCollectionId());
                    map.put("creator_id", dashboard.getCreatorId());
                    map.put("created_at", dashboard.getCreatedAt());
                    map.put("updated_at", dashboard.getUpdatedAt());
                    map.put("can_write", true);
                    map.put("favorite", favoriteDashboardIds.contains(dashboard.getId()));
                    return map;
                })
                .toList());

        result.addAll((id == null ? cardRepository.findAllByArchivedFalseAndCollectionIdIsNullOrderByIdAsc() : cardRepository.findAllByArchivedFalseAndCollectionIdOrderByIdAsc(id))
                .stream()
                .map(card -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", card.getId());
                    map.put("entity_id", card.getEntityId());
                    map.put("model", "card");
                    map.put("name", card.getName());
                    map.put("description", card.getDescription());
                    map.put("archived", card.isArchived());
                    map.put("collection_id", card.getCollectionId());
                    map.put("database_id", card.getDatabaseId());
                    map.put("display", card.getDisplay());
                    map.put("creator_id", card.getCreatorId());
                    map.put("created_at", card.getCreatedAt());
                    map.put("updated_at", card.getUpdatedAt());
                    map.put("can_write", true);
                    map.put("favorite", favoriteCardIds.contains(card.getId()));
                    return map;
                })
                .toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/{collectionId}/timelines", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> timelines(@PathVariable("collectionId") String collectionId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody CollectionRequest requestBody, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        String name = requestBody == null ? null : trimToNull(requestBody.name());
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("name", "value must be a non-blank string.")));
        }

        AnalyticsCollection collection = new AnalyticsCollection();
        collection.setEntityId(entityIdGenerator.newEntityId());
        collection.setName(name);
        collection.setDescription(requestBody.description());
        collection.setColor(Optional.ofNullable(trimToNull(requestBody.color())).orElse("#31698A"));
        collection.setNamespace(requestBody.namespace());
        collection.setLocation("/");
        collection.setParentId(requestBody.parentId());
        collection.setArchived(false);
        collection.setSlug(buildSlug(name));
        collection = collectionRepository.save(collection);

        return ResponseEntity.ok(toListItem(collection, true));
    }

    private static Map<String, Object> toListItem(AnalyticsCollection collection, boolean canWrite) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("authority_level", null);
        map.put("description", collection.getDescription());
        map.put("archived", collection.isArchived());
        map.put("slug", collection.getSlug());
        map.put("color", collection.getColor());
        map.put("can_write", canWrite);
        map.put("name", collection.getName());
        map.put("personal_owner_id", collection.getPersonalOwnerId());
        map.put("id", collection.getId());
        map.put("entity_id", collection.getEntityId());
        map.put("location", collection.getLocation());
        map.put("namespace", collection.getNamespace());
        map.put("created_at", collection.getCreatedAt());
        return map;
    }

    private static Map<String, Object> toTreeItem(AnalyticsCollection collection) {
        Map<String, Object> map = toListItem(collection, true);
        map.remove("can_write");
        map.put("children", List.of());
        return map;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String buildSlug(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        return slug.isBlank() ? null : slug;
    }

    public record CollectionRequest(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("color") String color,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("parent_id") Long parentId) {}
}
