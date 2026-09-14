package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalyticsConsumerClassificationService {

    public static final String CARD = "CARD";
    public static final String REPORT = "REPORT";
    public static final String SCREEN = "SCREEN";

    private final AnalyticsClassificationClient client;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsDashboardCardRepository dashboardCardRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsSemanticModelRepository semanticModelRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsConsumerClassificationService(
        AnalyticsClassificationClient client,
        AnalyticsCardRepository cardRepository,
        AnalyticsDashboardCardRepository dashboardCardRepository,
        AnalyticsMetricRepository metricRepository,
        AnalyticsScreenRepository screenRepository,
        AnalyticsTableRepository tableRepository,
        AnalyticsDatabaseRepository databaseRepository,
        AnalyticsSemanticModelRepository semanticModelRepository,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.cardRepository = cardRepository;
        this.dashboardCardRepository = dashboardCardRepository;
        this.metricRepository = metricRepository;
        this.screenRepository = screenRepository;
        this.tableRepository = tableRepository;
        this.databaseRepository = databaseRepository;
        this.semanticModelRepository = semanticModelRepository;
        this.objectMapper = objectMapper;
    }

    public AnalyticsClassificationClient.ClassificationResult deriveCard(AnalyticsCard card) {
        if (card == null || card.getId() == null) {
            throw new IllegalArgumentException("Saved card is required for classification derivation");
        }
        return client.derive(
            CARD,
            cardKey(card.getId()),
            null,
            cardUpstreams(card),
            "dts-analytics:card:" + card.getId()
        );
    }

    public AnalyticsClassificationClient.ClassificationResult deriveMetric(AnalyticsMetric metric) {
        if (metric == null || metric.getId() == null || metric.getBaseTableId() == null) {
            throw new IllegalArgumentException("Metric base table is required for classification derivation");
        }
        return client.derive(
            "METRIC",
            metricKey(metric.getId()),
            null,
            List.of(tableSubject(metric.getBaseTableId())),
            "dts-analytics:metric:" + metric.getId()
        );
    }

    public AnalyticsClassificationClient.ClassificationResult deriveDashboard(long dashboardId) {
        List<AnalyticsDashboardCard> bindings = dashboardCardRepository.findAllByDashboardIdOrderByIdAsc(dashboardId);
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("Dashboard must contain at least one classified card");
        }
        LinkedHashSet<AnalyticsClassificationClient.SubjectRef> upstreams = new LinkedHashSet<>();
        for (AnalyticsDashboardCard binding : bindings) {
            AnalyticsCard card = cardRepository
                .findById(binding.getCardId())
                .orElseThrow(() -> new IllegalArgumentException("Dashboard card not found: " + binding.getCardId()));
            deriveCard(card);
            upstreams.add(new AnalyticsClassificationClient.SubjectRef("ASSET", cardKey(card.getId())));
        }
        return client.derive(
            REPORT,
            dashboardKey(dashboardId),
            null,
            List.copyOf(upstreams),
            "dts-analytics:dashboard:" + dashboardId
        );
    }

    public AnalyticsClassificationClient.ClassificationResult deriveScreen(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null) {
            throw new IllegalArgumentException("Saved screen is required for classification derivation");
        }
        ScreenSources sources = screenSources(screen);
        if (!sources.unresolved().isEmpty()) {
            throw new IllegalArgumentException(
                "Screen contains data sources without classification identity: " +
                String.join(", ", sources.unresolved())
            );
        }
        LinkedHashSet<AnalyticsClassificationClient.SubjectRef> upstreams = new LinkedHashSet<>(
            sources.explicitSubjects()
        );
        for (Long cardId : sources.cardIds()) {
            AnalyticsCard card = cardRepository
                .findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Screen card not found: " + cardId));
            deriveCard(card);
            upstreams.add(new AnalyticsClassificationClient.SubjectRef("ASSET", cardKey(cardId)));
        }
        for (Long metricId : sources.metricIds()) {
            AnalyticsMetric metric = metricRepository
                .findById(metricId)
                .orElseThrow(() -> new IllegalArgumentException("Screen metric not found: " + metricId));
            deriveMetric(metric);
            upstreams.add(new AnalyticsClassificationClient.SubjectRef("ASSET", metricKey(metricId)));
        }
        for (Long tableId : sources.tableIds()) {
            upstreams.add(tableSubject(tableId));
        }
        if (!sources.sqlSources().isEmpty()) {
            upstreams.addAll(client.resolveSqlSources(sources.sqlSources().stream()
                .map(source -> new AnalyticsClassificationClient.SqlSource(platformSourceId(source.databaseId()), source.sql()))
                .toList()));
        }
        for (Long databaseId : sources.databaseIds()) {
            upstreams.add(databaseSubject(databaseId));
        }
        String manualFloor = firstText(
            screen.getManualClassificationFloor(),
            screen.getClassification()
        );
        AnalyticsClassificationClient.ClassificationResult result = client.derive(
            SCREEN,
            screenKey(screen.getId()),
            manualFloor,
            List.copyOf(upstreams),
            "dts-analytics:screen:" + screen.getId()
        );
        screen.setClassification(result.effectiveLevel());
        screen.setClassificationSnapshotId(result.snapshotId());
        screen.setClassificationSnapshotVersion(result.snapshotVersion());
        screen.setClassificationDerivedAt(Instant.now());
        try {
            screen.setClassificationEvidenceJson(
                objectMapper.writeValueAsString(
                    java.util.Map.of(
                        "consumerKey",
                        result.consumerKey(),
                        "effectiveLevel",
                        result.effectiveLevel(),
                        "manualFloor",
                        result.manualFloor() == null ? "" : result.manualFloor(),
                        "upstreams",
                        result.upstreams() == null ? List.of() : result.upstreams()
                    )
                )
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Screen classification evidence cannot be serialized", ex);
        }
        screenRepository.save(screen);
        return result;
    }

    public void prepareScreenDraft(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null) {
            throw new IllegalArgumentException("Saved screen is required for classification derivation");
        }
        ScreenSources sources = screenSources(screen);
        if (sources.unresolved().isEmpty()) {
            try {
                deriveScreen(screen);
                return;
            } catch (AnalyticsClassificationClient.ClassificationContractException ex) {
                if (!ex.isSourceClassificationMissing()) {
                    throw ex;
                }
                saveBlockedScreenDraft(
                    screen,
                    "BLOCKED_UPSTREAM",
                    Map.of(
                        "blockers",
                        List.of(AnalyticsClassificationClient.SOURCE_CLASSIFICATION_MISSING)
                    )
                );
                return;
            }
        }

        saveBlockedScreenDraft(
            screen,
            "BLOCKED_UNRESOLVED",
            Map.of("unresolvedSources", sources.unresolved())
        );
    }

    private void saveBlockedScreenDraft(
        AnalyticsScreen screen,
        String status,
        Map<String, Object> blockerEvidence
    ) {
        String manualFloor = firstText(
            screen.getManualClassificationFloor(),
            screen.getClassification()
        );
        screen.setClassification(
            SecurityLevelCatalog.maxDataCode(screen.getClassification(), manualFloor)
        );
        screen.setClassificationSnapshotId(null);
        screen.setClassificationSnapshotVersion(null);
        screen.setClassificationDerivedAt(null);
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("status", status);
            evidence.put("manualFloor", manualFloor == null ? "" : manualFloor);
            evidence.putAll(blockerEvidence);
            screen.setClassificationEvidenceJson(
                objectMapper.writeValueAsString(evidence)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Screen draft classification evidence cannot be serialized", ex);
        }
        screenRepository.save(screen);
    }

    public boolean hasUnresolvedScreenSources(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null) {
            throw new IllegalArgumentException("Saved screen is required for classification inspection");
        }
        if (!screenSources(screen).unresolved().isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(screen.getClassificationEvidenceJson())) {
            return false;
        }
        try {
            return "BLOCKED_UPSTREAM".equals(
                objectMapper.readTree(screen.getClassificationEvidenceJson()).path("status").asText()
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Screen classification evidence cannot be parsed", ex);
        }
    }

    public AnalyticsClassificationClient.ClassificationResult deriveScreen(long screenId) {
        return deriveScreen(
            screenRepository
                .findById(screenId)
                .orElseThrow(() -> new IllegalArgumentException("Screen not found: " + screenId))
        );
    }

    public AnalyticsClassificationClient.ClassificationResult requireCurrentScreen(long screenId) {
        AnalyticsScreen screen = screenRepository
            .findById(screenId)
            .orElseThrow(() -> new IllegalArgumentException("Screen not found: " + screenId));
        if (hasUnresolvedScreenSources(screen)) {
            throw new IllegalStateException(
                "Screen classification is governance-blocked by unresolved upstream sources"
            );
        }
        AnalyticsClassificationClient.ClassificationResult current =
            client.requireCurrent(SCREEN, screenKey(screenId));
        if (
            !StringUtils.hasText(screen.getClassificationSnapshotId()) ||
            !screen.getClassificationSnapshotId().equals(current.snapshotId()) ||
            screen.getClassificationSnapshotVersion() == null ||
            !screen.getClassificationSnapshotVersion().equals(current.snapshotVersion())
        ) {
            screen.setClassification(current.effectiveLevel());
            screen.setClassificationSnapshotId(current.snapshotId());
            screen.setClassificationSnapshotVersion(current.snapshotVersion());
            screen.setClassificationDerivedAt(Instant.now());
            screenRepository.save(screen);
        }
        return current;
    }

    public AnalyticsClassificationClient.ExportSeal sealScreenExport(long screenId, String fileSubjectKey) {
        requireCurrentScreen(screenId);
        return client.sealExport(
            fileSubjectKey,
            List.of(new AnalyticsClassificationClient.SubjectRef("ASSET", screenKey(screenId))),
            "dts-analytics:screen-export:" + screenId
        );
    }

    public AnalyticsClassificationClient.ClassificationResult ensurePublicConsumer(String model, long modelId) {
        return switch (normalizedModel(model)) {
            case PublicLinkService.MODEL_CARD -> deriveCard(
                cardRepository.findById(modelId).orElseThrow(() -> new IllegalArgumentException("Card not found"))
            );
            case PublicLinkService.MODEL_DASHBOARD -> deriveDashboard(modelId);
            case PublicLinkService.MODEL_SCREEN -> deriveScreen(modelId);
            default -> throw new IllegalArgumentException("Unsupported public link model: " + model);
        };
    }

    public AnalyticsClassificationClient.ClassificationResult requireCurrentCard(long cardId) {
        return client.requireCurrent(CARD, cardKey(cardId));
    }

    public AnalyticsClassificationClient.ClassificationResult requireCardPersonnelClearance(
        long cardId,
        String callerClassification
    ) {
        return requirePersonnelClearance(
            requireCurrentCard(cardId),
            callerClassification,
            "card " + cardId
        );
    }

    public AnalyticsClassificationClient.ClassificationResult requireDashboardPersonnelClearance(
        long dashboardId,
        String callerClassification
    ) {
        return requirePersonnelClearance(
            client.requireCurrent(REPORT, dashboardKey(dashboardId)),
            callerClassification,
            "dashboard " + dashboardId
        );
    }

    private AnalyticsClassificationClient.ClassificationResult requirePersonnelClearance(
        AnalyticsClassificationClient.ClassificationResult current,
        String callerClassification,
        String resourceLabel
    ) {
        SecurityLevelCatalog.DataSecurityLevel required =
            SecurityLevelCatalog.DataSecurityLevel.parse(current.effectiveLevel());
        if (required == null) {
            throw new IllegalStateException("Classification is missing or unknown for " + resourceLabel);
        }
        if (required == SecurityLevelCatalog.DataSecurityLevel.PUBLIC) {
            return current;
        }
        SecurityLevelCatalog.DataSecurityLevel caller =
            SecurityLevelCatalog.parseMaxDataLevel(callerClassification);
        if (caller == null || caller.number() < required.number()) {
            throw new PersonnelClassificationDeniedException(
                "Personnel classification does not allow access to " + resourceLabel
            );
        }
        return current;
    }

    public AnalyticsClassificationClient.ExportSeal sealCardExport(long cardId, String fileSubjectKey) {
        requireCurrentCard(cardId);
        return client.sealExport(
            fileSubjectKey,
            List.of(new AnalyticsClassificationClient.SubjectRef("ASSET", cardKey(cardId))),
            "dts-analytics:card-export:" + cardId
        );
    }

    public AnalyticsClassificationClient.ExportSeal sealCardExport(
        long cardId,
        String fileSubjectKey,
        String callerClassification
    ) {
        requireCardPersonnelClearance(cardId, callerClassification);
        return client.sealExport(
            fileSubjectKey,
            List.of(new AnalyticsClassificationClient.SubjectRef("ASSET", cardKey(cardId))),
            "dts-analytics:card-export:" + cardId
        );
    }

    public AnalyticsClassificationClient.AccessBinding bindPublicLink(
        String publicUuid,
        String model,
        long modelId,
        java.time.Instant validTo
    ) {
        ConsumerRef consumer = consumer(model, modelId);
        return client.bind("PUBLIC_LINK", publicUuid, consumer.type(), consumer.key(), validTo);
    }

    public void requireCurrentPublicLink(String publicUuid) {
        client.requireCurrentBinding("PUBLIC_LINK", publicUuid);
    }

    private List<AnalyticsClassificationClient.SubjectRef> cardUpstreams(AnalyticsCard card) {
        if (card.getQueryDatasetId() != null) {
            return List.of(new AnalyticsClassificationClient.SubjectRef(
                "ASSET",
                "bi-dataset:" + card.getQueryDatasetId()
            ));
        }
        Set<Long> tableIds = new LinkedHashSet<>();
        Set<Long> metricIds = new LinkedHashSet<>();
        JsonNode query;
        try {
            query = objectMapper.readTree(card.getDatasetQueryJson());
            collectIds(query, tableIds, metricIds);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Card dataset query cannot be parsed", ex);
        }
        LinkedHashSet<AnalyticsClassificationClient.SubjectRef> upstreams = new LinkedHashSet<>();
        for (Long tableId : tableIds) {
            upstreams.add(tableSubject(tableId));
        }
        for (Long metricId : metricIds) {
            AnalyticsMetric metric = metricRepository
                .findById(metricId)
                .orElseThrow(() -> new IllegalArgumentException("Metric not found: " + metricId));
            deriveMetric(metric);
            upstreams.add(new AnalyticsClassificationClient.SubjectRef("ASSET", metricKey(metricId)));
        }
        addSemanticModelUpstreams(query, upstreams);
        if (upstreams.isEmpty()) {
            upstreams.add(databaseSubject(card.getDatabaseId()));
        }
        return List.copyOf(upstreams);
    }

    private void addSemanticModelUpstreams(
        JsonNode datasetQuery,
        LinkedHashSet<AnalyticsClassificationClient.SubjectRef> upstreams
    ) {
        if (datasetQuery == null || !"semantic".equalsIgnoreCase(datasetQuery.path("type").asText())) {
            return;
        }
        JsonNode semanticQuery = datasetQuery.path("semantic_query");
        LinkedHashSet<String> modelNames = new LinkedHashSet<>();
        addSemanticModelName(modelNames, semanticQuery.path("base"));
        JsonNode joins = semanticQuery.path("joins");
        if (joins.isArray()) {
            joins.forEach(join -> addSemanticModelName(modelNames, join.path("to")));
        }
        if (modelNames.isEmpty()) {
            throw new IllegalArgumentException("Semantic card requires a classified base model");
        }
        for (String modelName : modelNames) {
            AnalyticsSemanticModel model = semanticModelRepository
                .findByModelNameIgnoreCase(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Semantic model not found: " + modelName));
            if (model.getTableId() == null) {
                throw new IllegalArgumentException("Semantic model has no physical table binding: " + modelName);
            }
            upstreams.add(tableSubject(model.getTableId()));
        }
    }

    private static void addSemanticModelName(Set<String> names, JsonNode node) {
        if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
            names.add(node.asText().trim());
        }
    }

    private ScreenSources screenSources(AnalyticsScreen screen) {
        ScreenSourceCollector collector = new ScreenSourceCollector();
        collectScreenJson(collector, screen.getComponentsJson(), "components");
        collectScreenJson(collector, screen.getPagesJson(), "pages");
        collectScreenJson(collector, screen.getV2SpecJson(), "v2Spec");
        collectScreenJson(collector, screen.getVariablesJson(), "globalVariables");
        collectScreenJson(collector, screen.getCarouselJson(), "carouselConfig");
        return collector.snapshot();
    }

    private void collectScreenJson(ScreenSourceCollector collector, String json, String path) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            collector.collect(objectMapper.readTree(json), path);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Screen " + path + " cannot be parsed", ex);
        }
    }

    private AnalyticsClassificationClient.SubjectRef tableSubject(Long tableId) {
        AnalyticsTable table = tableRepository
            .findById(tableId)
            .orElseThrow(() -> new IllegalArgumentException("Analytics table not found: " + tableId));
        UUID sourceId = platformSourceId(table.getDatabaseId());
        String key =
            "source:" +
            sourceId +
            "/schema:" +
            segment(firstText(table.getSchemaName(), "default")) +
            "/table:" +
            segment(table.getName());
        return new AnalyticsClassificationClient.SubjectRef("ASSET", key);
    }

    private AnalyticsClassificationClient.SubjectRef databaseSubject(Long databaseId) {
        return new AnalyticsClassificationClient.SubjectRef("ASSET", "data-source:" + platformSourceId(databaseId));
    }

    private UUID platformSourceId(Long databaseId) {
        AnalyticsDatabase database = databaseRepository
            .findById(databaseId)
            .orElseThrow(() -> new IllegalArgumentException("Analytics database not found: " + databaseId));
        try {
            JsonNode details = objectMapper.readTree(database.getDetailsJson());
            String raw = firstText(
                details.path("platformDataSourceId").asText(null),
                details.path("platform_data_source_id").asText(null),
                details.path("platform").path("dataSourceId").asText(null),
                details.path("platform").path("id").asText(null)
            );
            if (!StringUtils.hasText(raw)) {
                throw new IllegalArgumentException("Analytics database is not linked to a platform data source");
            }
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Analytics database platform source is invalid", ex);
        }
    }

    private static void collectIds(JsonNode node, Set<Long> tableIds, Set<Long> metricIds) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (
                    ("source-table".equals(field) || "source_table".equals(field) || "tableid".equals(field) || "table_id".equals(field)) &&
                    value.canConvertToLong() &&
                    value.asLong() > 0
                ) {
                    tableIds.add(value.asLong());
                }
                if (
                    ("metricid".equals(field) || "metric_id".equals(field)) &&
                    value.canConvertToLong() &&
                    value.asLong() > 0
                ) {
                    metricIds.add(value.asLong());
                }
                collectIds(value, tableIds, metricIds);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectIds(child, tableIds, metricIds));
        }
    }

    private static final class ScreenSourceCollector {

        private final LinkedHashSet<Long> cardIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> metricIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> tableIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> databaseIds = new LinkedHashSet<>();
        private final LinkedHashSet<ScreenSqlSource> sqlSources = new LinkedHashSet<>();
        private final LinkedHashSet<AnalyticsClassificationClient.SubjectRef> explicitSubjects =
            new LinkedHashSet<>();
        private final LinkedHashSet<String> unresolved = new LinkedHashSet<>();

        private void collect(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return;
            }
            if (node.isArray()) {
                for (int i = 0; i < node.size(); i++) {
                    collect(node.get(i), path + "[" + i + "]");
                }
                return;
            }
            if (!node.isObject()) {
                return;
            }
            String subjectKey = firstText(
                node.path("classificationSubjectKey").asText(null),
                node.path("classification_subject_key").asText(null),
                node.path("canonicalAssetKey").asText(null),
                node.path("canonical_asset_key").asText(null)
            );
            if (StringUtils.hasText(subjectKey)) {
                String subjectType = firstText(
                    node.path("classificationSubjectType").asText(null),
                    node.path("classification_subject_type").asText(null),
                    "ASSET"
                );
                explicitSubjects.add(
                    new AnalyticsClassificationClient.SubjectRef(
                        subjectType.toUpperCase(Locale.ROOT),
                        subjectKey
                    )
                );
            }
            addPositive(node, cardIds, "cardId", "card_id", "sourceCardId", "source_card_id");
            addPositive(node, metricIds, "metricId", "metric_id");
            addPositive(node, tableIds, "source-table", "source_table", "tableId", "table_id");
            long sqlDatabase = node.path("databaseId").asLong(node.path("database_id").asLong(0));
            String sqlQuery = node.path("query").isTextual() ? node.path("query").asText() : null;
            if (sqlDatabase > 0 && StringUtils.hasText(sqlQuery)) {
                sqlSources.add(new ScreenSqlSource(sqlDatabase, sqlQuery));
            } else {
                addPositive(node, databaseIds, "databaseId", "database_id");
            }

            String sourceType = firstText(
                node.path("sourceType").asText(null),
                node.path("source_type").asText(null),
                node.path("type").asText(null)
            );
            if ("api".equalsIgnoreCase(sourceType) && !StringUtils.hasText(subjectKey)) {
                JsonNode config = node.path("apiConfig");
                String nestedSubjectKey = config.isObject()
                    ? firstText(
                        config.path("classificationSubjectKey").asText(null),
                        config.path("classification_subject_key").asText(null),
                        config.path("canonicalAssetKey").asText(null),
                        config.path("canonical_asset_key").asText(null)
                    )
                    : null;
                if (StringUtils.hasText(nestedSubjectKey)) {
                    String nestedSubjectType = firstText(
                        config.path("classificationSubjectType").asText(null),
                        config.path("classification_subject_type").asText(null),
                        "ASSET"
                    );
                    explicitSubjects.add(
                        new AnalyticsClassificationClient.SubjectRef(
                            nestedSubjectType.toUpperCase(Locale.ROOT),
                            nestedSubjectKey
                        )
                    );
                    subjectKey = nestedSubjectKey;
                }
                String url = config.isObject() ? config.path("url").asText(null) : null;
                if (!StringUtils.hasText(subjectKey)) {
                    unresolved.add(path + ":api:" + firstText(url, "unknown"));
                }
            }
            node.fields().forEachRemaining(entry ->
                collect(entry.getValue(), path + "." + entry.getKey())
            );
        }

        private static void addPositive(
            JsonNode node,
            Set<Long> target,
            String... fields
        ) {
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value != null && value.canConvertToLong() && value.asLong() > 0) {
                    target.add(value.asLong());
                }
            }
        }

        private ScreenSources snapshot() {
            return new ScreenSources(
                List.copyOf(cardIds),
                List.copyOf(metricIds),
                List.copyOf(tableIds),
                List.copyOf(databaseIds),
                List.copyOf(sqlSources),
                List.copyOf(explicitSubjects),
                List.copyOf(unresolved)
            );
        }
    }

    private static ConsumerRef consumer(String model, long modelId) {
        return switch (normalizedModel(model)) {
            case PublicLinkService.MODEL_CARD -> new ConsumerRef(CARD, cardKey(modelId));
            case PublicLinkService.MODEL_DASHBOARD -> new ConsumerRef(REPORT, dashboardKey(modelId));
            case PublicLinkService.MODEL_SCREEN -> new ConsumerRef(SCREEN, screenKey(modelId));
            default -> throw new IllegalArgumentException("Unsupported public link model: " + model);
        };
    }

    private static String normalizedModel(String model) {
        return model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
    }

    public static String cardKey(long id) {
        return "analytics-card:" + id;
    }

    public static String dashboardKey(long id) {
        return "analytics-dashboard:" + id;
    }

    public static String screenKey(long id) {
        return "screen:" + id;
    }

    public static String metricKey(long id) {
        return "analytics-metric:" + id;
    }

    private static String segment(String value) {
        String normalized = value == null
            ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.:-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Classification asset key segment is required");
        }
        return normalized;
    }

    private static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private record ConsumerRef(String type, String key) {}

    public static class PersonnelClassificationDeniedException extends RuntimeException {

        public PersonnelClassificationDeniedException(String message) {
            super(message);
        }
    }

    private record ScreenSqlSource(long databaseId, String sql) {}

    private record ScreenSources(
        List<Long> cardIds,
        List<Long> metricIds,
        List<Long> tableIds,
        List<Long> databaseIds,
        List<ScreenSqlSource> sqlSources,
        List<AnalyticsClassificationClient.SubjectRef> explicitSubjects,
        List<String> unresolved
    ) {}
}
