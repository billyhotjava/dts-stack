package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trash")
@Transactional(readOnly = true)
public class TrashResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;

    public TrashResource(
            AnalyticsSessionService sessionService,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository) {
        this.sessionService = sessionService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        List<Map<String, Object>> dashboards = dashboardRepository.findAllByArchivedTrueOrderByIdAsc().stream()
                .map(this::dashboardItem)
                .toList();
        List<Map<String, Object>> cards = cardRepository.findAllByArchivedTrueOrderByIdAsc().stream()
                .map(this::cardItem)
                .toList();

        return ResponseEntity.ok(Map.of("dashboards", dashboards, "cards", cards));
    }

    private Map<String, Object> dashboardItem(AnalyticsDashboard dashboard) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "dashboard");
        map.put("id", dashboard.getId());
        map.put("name", dashboard.getName());
        map.put("description", dashboard.getDescription());
        map.put("collection_id", dashboard.getCollectionId());
        map.put("updated_at", dashboard.getUpdatedAt());
        map.put("created_at", dashboard.getCreatedAt());
        return map;
    }

    private Map<String, Object> cardItem(AnalyticsCard card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "card");
        map.put("id", card.getId());
        map.put("name", card.getName());
        map.put("description", card.getDescription());
        map.put("collection_id", card.getCollectionId());
        map.put("database_id", card.getDatabaseId());
        map.put("display", card.getDisplay());
        map.put("updated_at", card.getUpdatedAt());
        map.put("created_at", card.getCreatedAt());
        return map;
    }
}

