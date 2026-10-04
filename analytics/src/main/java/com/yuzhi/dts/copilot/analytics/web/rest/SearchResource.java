package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCollection;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCollectionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@Transactional(readOnly = true)
public class SearchResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsCollectionRepository collectionRepository;

    public SearchResource(
            AnalyticsSessionService sessionService,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsCollectionRepository collectionRepository) {
        this.sessionService = sessionService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.collectionRepository = collectionRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@RequestParam Map<String, String> params, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        String q = trimToNull(firstNonBlank(params.get("q"), params.get("query"), params.get("search")));
        boolean includeArchived = "true".equalsIgnoreCase(params.get("archived"));
        int limit = parseIntOrDefault(params.get("limit"), 25);
        int offset = Math.max(0, parseIntOrDefault(params.get("offset"), 0));

        Set<String> models = parseModels(params.get("models"));
        if (models.isEmpty()) {
            models = Set.of("card", "dashboard", "collection");
        }

        List<Map<String, Object>> all = new ArrayList<>();
        if (q != null) {
            String qLower = q.toLowerCase(Locale.ROOT);
            if (models.contains("dashboard")) {
                for (AnalyticsDashboard d : dashboardRepository.findAll()) {
                    if (!includeArchived && d.isArchived()) {
                        continue;
                    }
                    if (!containsIgnoreCase(d.getName(), qLower) && !containsIgnoreCase(d.getDescription(), qLower)) {
                        continue;
                    }
                    all.add(toDashboardResult(d));
                }
            }
            if (models.contains("card")) {
                for (AnalyticsCard c : cardRepository.findAll()) {
                    if (!includeArchived && c.isArchived()) {
                        continue;
                    }
                    if (!containsIgnoreCase(c.getName(), qLower) && !containsIgnoreCase(c.getDescription(), qLower)) {
                        continue;
                    }
                    all.add(toCardResult(c));
                }
            }
            if (models.contains("collection")) {
                for (AnalyticsCollection c : collectionRepository.findAll()) {
                    if (!includeArchived && c.isArchived()) {
                        continue;
                    }
                    if (c.getPersonalOwnerId() != null) {
                        // Hide personal collections from global search for now; UI has dedicated navigation.
                        continue;
                    }
                    if (!containsIgnoreCase(c.getName(), qLower) && !containsIgnoreCase(c.getDescription(), qLower)) {
                        continue;
                    }
                    all.add(toCollectionResult(c));
                }
            }
        }

        String sortColumn = trimToNull(params.get("sort_column"));
        String sortDirection = trimToNull(params.get("sort_direction"));
        Comparator<Map<String, Object>> comparator =
                "updated_at".equalsIgnoreCase(sortColumn) ? Comparator.comparing(m -> (Comparable) m.get("updated_at"), Comparator.nullsLast(Comparator.naturalOrder())) : Comparator.comparing(m -> (String) m.get("name"), Comparator.nullsLast(String::compareToIgnoreCase));
        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        all.sort(comparator);

        int total = all.size();
        List<Map<String, Object>> page =
                all.stream().skip(offset).limit(Math.max(0, limit)).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", page, "total", total));
    }

    private static Map<String, Object> toDashboardResult(AnalyticsDashboard dashboard) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "dashboard");
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
        return map;
    }

    private static Map<String, Object> toCardResult(AnalyticsCard card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "card");
        map.put("id", card.getId());
        map.put("entity_id", card.getEntityId());
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
        return map;
    }

    private static Map<String, Object> toCollectionResult(AnalyticsCollection collection) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "collection");
        map.put("id", collection.getId());
        map.put("entity_id", collection.getEntityId());
        map.put("name", collection.getName());
        map.put("description", collection.getDescription());
        map.put("archived", collection.isArchived());
        map.put("color", collection.getColor());
        map.put("namespace", collection.getNamespace());
        map.put("location", collection.getLocation());
        map.put("created_at", collection.getCreatedAt());
        map.put("updated_at", collection.getUpdatedAt());
        map.put("can_write", true);
        return map;
    }

    private static Set<String> parseModels(String models) {
        String raw = trimToNull(models);
        if (raw == null) {
            return Set.of();
        }
        return Set.of(raw.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    private static boolean containsIgnoreCase(String value, String qLower) {
        if (qLower == null || qLower.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(qLower);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            String t = trimToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

