package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsActivity;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCollection;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsActivityRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCollectionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
@Transactional(readOnly = true)
public class ActivityResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsActivityRepository activityRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsCollectionRepository collectionRepository;

    public ActivityResource(
            AnalyticsSessionService sessionService,
            AnalyticsActivityRepository activityRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsCollectionRepository collectionRepository) {
        this.sessionService = sessionService;
        this.activityRepository = activityRepository;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.collectionRepository = collectionRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        int size = clamp(limit, 100, 100);
        List<AnalyticsActivity> items = activityRepository.findAllByUserIdOrderByCreatedAtDesc(user.get().getId(), PageRequest.of(0, size));
        return ResponseEntity.ok(items.stream().map(this::toActivityResponse).toList());
    }

    @GetMapping(path = "/recent_views", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> recentViews(@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        int size = clamp(limit, 20, 100);
        List<AnalyticsActivity> recent = activityRepository.findAllByUserIdOrderByCreatedAtDesc(user.get().getId(), PageRequest.of(0, 200));
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnalyticsActivity activity : recent) {
            String key = activity.getModel() + ":" + activity.getModelId();
            if (!seen.add(key)) {
                continue;
            }
            result.add(toActivityResponse(activity));
            if (result.size() >= size) {
                break;
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/popular_items", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> popularItems(@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        int size = clamp(limit, 20, 100);
        List<AnalyticsActivityRepository.PopularItemProjection> rows = activityRepository.findPopularViewedItems(PageRequest.of(0, size));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnalyticsActivityRepository.PopularItemProjection row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("model", row.getModel());
            map.put("model_id", row.getModelId());
            map.put("views", row.getViews());
            map.put("last_viewed_at", row.getLastViewedAt());
            map.put("model_object", loadModelObject(row.getModel(), row.getModelId()));
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toActivityResponse(AnalyticsActivity activity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", activity.getId());
        map.put("model", activity.getModel());
        map.put("model_id", activity.getModelId());
        map.put("action", activity.getAction());
        map.put("timestamp", activity.getCreatedAt());
        map.put("model_object", loadModelObject(activity.getModel(), activity.getModelId()));
        return map;
    }

    private Object loadModelObject(String model, Long id) {
        if (model == null || id == null || id <= 0) {
            return null;
        }
        return switch (model) {
            case "card" -> cardRepository.findById(id).map(this::toCardObject).orElse(null);
            case "dashboard" -> dashboardRepository.findById(id).map(this::toDashboardObject).orElse(null);
            case "collection" -> collectionRepository.findById(id).map(this::toCollectionObject).orElse(null);
            default -> null;
        };
    }

    private Map<String, Object> toCardObject(AnalyticsCard card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", card.getId());
        map.put("entity_id", card.getEntityId());
        map.put("name", card.getName());
        map.put("display_name", card.getName());
        map.put("model", "card");
        map.put("archived", card.isArchived());
        map.put("collection_id", card.getCollectionId());
        map.put("database_id", card.getDatabaseId());
        return map;
    }

    private Map<String, Object> toDashboardObject(AnalyticsDashboard dashboard) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dashboard.getId());
        map.put("entity_id", dashboard.getEntityId());
        map.put("name", dashboard.getName());
        map.put("display_name", dashboard.getName());
        map.put("model", "dashboard");
        map.put("archived", dashboard.isArchived());
        map.put("collection_id", dashboard.getCollectionId());
        return map;
    }

    private Map<String, Object> toCollectionObject(AnalyticsCollection collection) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", collection.getId());
        map.put("entity_id", collection.getEntityId());
        map.put("name", collection.getName());
        map.put("display_name", collection.getName());
        map.put("model", "collection");
        map.put("archived", collection.isArchived());
        map.put("location", collection.getLocation());
        map.put("namespace", collection.getNamespace());
        return map;
    }

    private static int clamp(Integer requested, int defaultValue, int max) {
        if (requested == null) {
            return defaultValue;
        }
        if (requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, max);
    }
}
