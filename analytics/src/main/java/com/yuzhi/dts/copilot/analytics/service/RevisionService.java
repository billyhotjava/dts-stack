package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsRevisionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RevisionService {

    public static final String MODEL_CARD = "card";
    public static final String MODEL_DASHBOARD = "dashboard";

    private final AnalyticsRevisionRepository revisionRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardRepository dashboardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final ObjectMapper objectMapper;

    public RevisionService(
            AnalyticsRevisionRepository revisionRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsDashboardRepository dashboardRepository,
            AnalyticsDashboardCardRepository dashboardCardRepository,
            ObjectMapper objectMapper) {
        this.revisionRepository = revisionRepository;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.objectMapper = objectMapper;
    }

    public AnalyticsRevision recordCardRevision(AnalyticsCard card, Long userId, boolean isReversion) {
        AnalyticsRevision revision = new AnalyticsRevision();
        revision.setModel(MODEL_CARD);
        revision.setModelId(card.getId());
        revision.setUserId(userId);
        revision.setReversion(isReversion);
        revision.setObjectJson(toCardObject(card));
        return revisionRepository.save(revision);
    }

    public AnalyticsRevision recordDashboardRevision(AnalyticsDashboard dashboard, List<AnalyticsDashboardCard> dashcards, Long userId, boolean isReversion) {
        AnalyticsRevision revision = new AnalyticsRevision();
        revision.setModel(MODEL_DASHBOARD);
        revision.setModelId(dashboard.getId());
        revision.setUserId(userId);
        revision.setReversion(isReversion);
        revision.setObjectJson(toDashboardObject(dashboard, dashcards));
        return revisionRepository.save(revision);
    }

    public Optional<AnalyticsRevision> find(long revisionId) {
        return revisionRepository.findById(revisionId);
    }

    public List<AnalyticsRevision> list(String model, long modelId) {
        if (model == null || model.isBlank() || modelId <= 0) {
            return List.of();
        }
        return revisionRepository.findAllByModelAndModelIdOrderByIdDesc(model, modelId);
    }

    public Optional<Map<String, Object>> revert(long revisionId, Long actorUserId) {
        AnalyticsRevision revision = revisionRepository.findById(revisionId).orElse(null);
        if (revision == null) {
            return Optional.empty();
        }

        String model = revision.getModel();
        if (MODEL_CARD.equals(model)) {
            AnalyticsCard card = cardRepository.findById(revision.getModelId()).orElse(null);
            if (card == null) {
                return Optional.empty();
            }
            applyCardRevision(card, revision);
            cardRepository.save(card);
            recordCardRevision(card, actorUserId, true);
            return Optional.of(Map.of("status", "ok"));
        }
        if (MODEL_DASHBOARD.equals(model)) {
            AnalyticsDashboard dashboard = dashboardRepository.findById(revision.getModelId()).orElse(null);
            if (dashboard == null) {
                return Optional.empty();
            }
            applyDashboardRevision(dashboard, revision);
            dashboardRepository.save(dashboard);

            List<AnalyticsDashboardCard> currentDashcards = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboard.getId());
            recordDashboardRevision(dashboard, currentDashcards, actorUserId, true);
            return Optional.of(Map.of("status", "ok"));
        }
        return Optional.of(Map.of("status", "ignored"));
    }

    private String toCardObject(AnalyticsCard card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", card.getId());
        map.put("name", card.getName());
        map.put("description", card.getDescription());
        map.put("archived", card.isArchived());
        map.put("collection_id", card.getCollectionId());
        map.put("database_id", card.getDatabaseId());
        map.put("display", card.getDisplay());
        map.put("dataset_query_json", card.getDatasetQueryJson());
        map.put("visualization_settings_json", card.getVisualizationSettingsJson());
        map.put("creator_id", card.getCreatorId());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toDashboardObject(AnalyticsDashboard dashboard, List<AnalyticsDashboardCard> dashcards) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dashboard.getId());
        map.put("name", dashboard.getName());
        map.put("description", dashboard.getDescription());
        map.put("archived", dashboard.isArchived());
        map.put("collection_id", dashboard.getCollectionId());
        map.put("parameters_json", dashboard.getParametersJson());
        map.put("creator_id", dashboard.getCreatorId());
        map.put(
                "dashcards",
                dashcards.stream()
                        .map(dc -> Map.of(
                                "id", dc.getId(),
                                "card_id", dc.getCardId(),
                                "row", dc.getRow(),
                                "col", dc.getCol(),
                                "size_x", dc.getSizeX(),
                                "size_y", dc.getSizeY(),
                                "parameter_mappings_json", dc.getParameterMappingsJson(),
                                "visualization_settings_json", dc.getVisualizationSettingsJson()))
                        .toList());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void applyCardRevision(AnalyticsCard card, AnalyticsRevision revision) {
        JsonNode node;
        try {
            node = objectMapper.readTree(revision.getObjectJson());
        } catch (Exception e) {
            return;
        }
        if (node == null || node.isNull()) {
            return;
        }
        if (node.has("name") && !node.path("name").isNull()) {
            card.setName(node.path("name").asText(card.getName()));
        }
        if (node.has("description")) {
            card.setDescription(node.path("description").isNull() ? null : node.path("description").asText(null));
        }
        if (node.has("archived")) {
            card.setArchived(node.path("archived").asBoolean(card.isArchived()));
        }
        if (node.has("collection_id")) {
            card.setCollectionId(node.path("collection_id").isNull() ? null : node.path("collection_id").asLong());
        }
        if (node.has("database_id")) {
            card.setDatabaseId(node.path("database_id").isNull() ? null : node.path("database_id").asLong());
        }
        if (node.has("display") && !node.path("display").isNull()) {
            card.setDisplay(node.path("display").asText(card.getDisplay()));
        }
        if (node.has("dataset_query_json") && !node.path("dataset_query_json").isNull()) {
            card.setDatasetQueryJson(node.path("dataset_query_json").asText(card.getDatasetQueryJson()));
        }
        if (node.has("visualization_settings_json") && !node.path("visualization_settings_json").isNull()) {
            card.setVisualizationSettingsJson(node.path("visualization_settings_json").asText(card.getVisualizationSettingsJson()));
        }
    }

    private void applyDashboardRevision(AnalyticsDashboard dashboard, AnalyticsRevision revision) {
        JsonNode node;
        try {
            node = objectMapper.readTree(revision.getObjectJson());
        } catch (Exception e) {
            return;
        }
        if (node == null || node.isNull()) {
            return;
        }
        if (node.has("name") && !node.path("name").isNull()) {
            dashboard.setName(node.path("name").asText(dashboard.getName()));
        }
        if (node.has("description")) {
            dashboard.setDescription(node.path("description").isNull() ? null : node.path("description").asText(null));
        }
        if (node.has("archived")) {
            dashboard.setArchived(node.path("archived").asBoolean(dashboard.isArchived()));
        }
        if (node.has("collection_id")) {
            dashboard.setCollectionId(node.path("collection_id").isNull() ? null : node.path("collection_id").asLong());
        }
        if (node.has("parameters_json")) {
            dashboard.setParametersJson(node.path("parameters_json").isNull() ? null : node.path("parameters_json").asText(null));
        }

        if (node.has("dashcards") && node.path("dashcards").isArray()) {
            List<AnalyticsDashboardCard> existing = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboard.getId());
            Set<Long> incomingIds = streamDashcardIds(node.path("dashcards"));
            for (AnalyticsDashboardCard dc : existing) {
                if (dc.getId() != null && !incomingIds.contains(dc.getId())) {
                    dashboardCardRepository.delete(dc);
                }
            }

            for (JsonNode dcNode : node.path("dashcards")) {
                Long dashcardId = dcNode.path("id").canConvertToLong() ? dcNode.path("id").asLong() : null;
                AnalyticsDashboardCard dashcard =
                        dashcardId == null || dashcardId <= 0 ? null : dashboardCardRepository.findById(dashcardId).orElse(null);

                Long cardId = dcNode.path("card_id").canConvertToLong() ? dcNode.path("card_id").asLong() : null;
                if (dashcard == null) {
                    if (cardId == null || cardId <= 0) {
                        continue;
                    }
                    dashcard = new AnalyticsDashboardCard();
                    dashcard.setDashboardId(dashboard.getId());
                    dashcard.setCardId(cardId);
                } else if (dashcard.getDashboardId() == null || dashcard.getDashboardId() != dashboard.getId()) {
                    continue;
                }

                if (cardId != null && cardId > 0) {
                    dashcard.setCardId(cardId);
                }
                dashcard.setRow(dcNode.has("row") && !dcNode.path("row").isNull() ? dcNode.path("row").asInt() : null);
                dashcard.setCol(dcNode.has("col") && !dcNode.path("col").isNull() ? dcNode.path("col").asInt() : null);
                dashcard.setSizeX(dcNode.has("size_x") && !dcNode.path("size_x").isNull() ? dcNode.path("size_x").asInt() : null);
                dashcard.setSizeY(dcNode.has("size_y") && !dcNode.path("size_y").isNull() ? dcNode.path("size_y").asInt() : null);
                dashcard.setParameterMappingsJson(dcNode.path("parameter_mappings_json").isNull() ? null : dcNode.path("parameter_mappings_json").asText(null));
                dashcard.setVisualizationSettingsJson(
                        dcNode.path("visualization_settings_json").isNull() ? null : dcNode.path("visualization_settings_json").asText(null));
                dashboardCardRepository.save(dashcard);
            }
        }
    }

    private static Set<Long> streamDashcardIds(JsonNode dashcards) {
        if (dashcards == null || !dashcards.isArray()) {
            return Set.of();
        }
        return toList(dashcards).stream()
                .map(n -> n.path("id").canConvertToLong() ? n.path("id").asLong() : null)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false).toList();
    }
}

