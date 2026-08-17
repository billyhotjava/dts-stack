package com.yuzhi.dts.analytics.service.publication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.analysis.AnalysisConflictException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisForbiddenException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisNotFoundException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisSpecValidationException;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService.PublicationCommand;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService.PublicationIssue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;

@Service
@Transactional
public class DashboardPublicationService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardPublicationService.class);

    public static final String MODEL = "dashboard";
    private static final int MAX_COMPONENTS = 50;
    private static final int MAX_PARAMETERS = 20;

    private final AnalyticsDashboardRepository dashboards;
    private final AnalyticsDashboardCardRepository dashboardCards;
    private final AnalyticsCardRepository cards;
    private final AnalyticsRevisionRepository revisions;
    private final EntityIdGenerator entityIdGenerator;
    private final PublicationEntityLock entityLock;
    private final ReportRegistrationOutboxService outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DashboardPublicationService(
        AnalyticsDashboardRepository dashboards,
        AnalyticsDashboardCardRepository dashboardCards,
        AnalyticsCardRepository cards,
        AnalyticsRevisionRepository revisions,
        EntityIdGenerator entityIdGenerator,
        PublicationEntityLock entityLock,
        ReportRegistrationOutboxService outbox,
        ObjectMapper objectMapper
    ) {
        this(dashboards, dashboardCards, cards, revisions, entityIdGenerator, entityLock, outbox, objectMapper, Clock.systemUTC());
    }

    DashboardPublicationService(
        AnalyticsDashboardRepository dashboards,
        AnalyticsDashboardCardRepository dashboardCards,
        AnalyticsCardRepository cards,
        AnalyticsRevisionRepository revisions,
        EntityIdGenerator entityIdGenerator,
        PublicationEntityLock entityLock,
        ReportRegistrationOutboxService outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.dashboards = dashboards;
        this.dashboardCards = dashboardCards;
        this.cards = cards;
        this.revisions = revisions;
        this.entityIdGenerator = entityIdGenerator;
        this.entityLock = entityLock;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(long id, AnalyticsUser actor, PublicationCommand command) {
        AnalyticsDashboard dashboard = requireDashboard(id);
        assertReadable(dashboard, actor);
        return validateDashboard(dashboard, command);
    }

    public PublicationResult publish(long id, AnalyticsUser actor, PublicationCommand command) {
        AnalyticsDashboard dashboard = entityLock.dashboard(id);
        assertWritable(dashboard, actor);
        if (!"DRAFT".equals(dashboard.getLifecycleStatus())) {
            throw new AnalysisConflictException("DASHBOARD_PUBLISH_STATE_INVALID", "only a draft dashboard can be published");
        }
        ValidationResult validation = validateDashboard(dashboard, command);
        if (!validation.valid()) {
            PublicationIssue first = validation.blockers().getFirst();
            throw new AnalysisSpecValidationException(first.code(), first.path(), first.message());
        }

        revisions.findCurrentPublished(MODEL, id).ifPresent(current -> {
            current.setStatus("SUPERSEDED");
            revisions.saveAndFlush(current);
        });
        int version = revisions.findMaxVersionNo(MODEL, id) + 1;
        PublicationCommand audience = normalize(command);
        String objectJson = snapshot(dashboard, validation.dashcards(), audience);
        String dependencyJson = write(validation.dependencySnapshot());
        String checksum = sha256(objectJson + "\n" + dependencyJson);

        AnalyticsRevision revision = new AnalyticsRevision();
        revision.setModel(MODEL);
        revision.setModelId(id);
        revision.setUserId(actor.getId());
        revision.setReversion(false);
        revision.setObjectJson(objectJson);
        revision.setVersionNo(version);
        revision.setStatus("PUBLISHED");
        revision.setPublishedAt(clock.instant());
        revision.setDependencySnapshotJson(dependencyJson);
        revision.setContractChecksum(checksum);
        revision = revisions.save(revision);

        dashboard.setLifecycleStatus("PUBLISHED");
        dashboard.setPublishedRevisionId(revision.getId());
        dashboard.setRegistrationStatus("PENDING_REGISTRATION");
        dashboards.save(dashboard);

        DatasetBinding binding = datasetBinding(validation.dependencySnapshot());
        ReportRegistrationCommand registration = new ReportRegistrationCommand(
            "DTS_BI",
            "DASHBOARD",
            dashboard.getEntityId(),
            version,
            dashboard.getName(),
            "DASHBOARD",
            "/bi/dashboards/" + dashboard.getId(),
            binding.datasetId(),
            binding.version(),
            audience.deptCodes(),
            audience.roleCodes(),
            AnalysisPublicationService.normalizeClassification(audience.classification()),
            audience.expiresAt(),
            true
        );
        outbox.enqueueDashboard(id, version, "UPSERT", registration);
        LOG.info(
            "Dashboard published correlationId={} actor={} assetKey=dashboard:{} revisionId={} version={} checksum={} outcome=PENDING_REGISTRATION",
            RequestContextUtils.resolveRequestId(), actor.getId(), id, revision.getId(), version, checksum
        );
        return new PublicationResult(
            id,
            revision.getId(),
            version,
            "PUBLISHED",
            "PENDING_REGISTRATION",
            checksum,
            validation.dependencySnapshot(),
            revision.getPublishedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<VersionItem> versions(long id, AnalyticsUser actor) {
        AnalyticsDashboard dashboard = requireDashboard(id);
        assertReadable(dashboard, actor);
        return revisions.findAllByModelAndModelIdOrderByVersionNoDesc(MODEL, id).stream()
            .map(revision -> new VersionItem(
                revision.getId(), revision.getVersionNo(), revision.getStatus(), revision.getContractChecksum(),
                readMap(revision.getDependencySnapshotJson()), revision.getUserId(),
                revision.getPublishedAt(), revision.getCreatedAt()
            ))
            .toList();
    }

    public DraftResult createDraftFromVersion(long id, long revisionId, AnalyticsUser actor) {
        AnalyticsDashboard source = requireDashboard(id);
        assertReadable(source, actor);
        AnalyticsRevision sourceRevision = revisions.findById(revisionId)
            .filter(revision -> MODEL.equals(revision.getModel()) && Long.valueOf(id).equals(revision.getModelId()))
            .orElseThrow(() -> new AnalysisNotFoundException("dashboard revision does not exist"));

        JsonNode snapshot = readTree(sourceRevision.getObjectJson());
        if (snapshot == null || !snapshot.isObject()) {
            throw new AnalysisConflictException("DASHBOARD_REVISION_INVALID", "dashboard revision snapshot is unavailable");
        }

        AnalyticsDashboard draft = new AnalyticsDashboard();
        draft.setEntityId(entityIdGenerator.newEntityId());
        draft.setName(snapshot.path("name").asText(source.getName()) + "（草稿）");
        draft.setDescription(textOrNull(snapshot, "description"));
        draft.setCollectionId(positiveLongOrNull(snapshot.get("collectionId")));
        draft.setCreatorId(actor.getId());
        JsonNode parameters = snapshot.get("parameters");
        draft.setParametersJson(parameters == null || parameters.isNull() ? "[]" : parameters.toString());
        draft.setLifecycleStatus("DRAFT");
        draft.setRegistrationStatus("NOT_REGISTERED");
        draft.setArchived(false);
        draft = dashboards.save(draft);

        List<AnalyticsDashboardCard> copied = new ArrayList<>();
        JsonNode components = snapshot.get("components");
        if (components != null && components.isArray()) {
            for (JsonNode item : components) {
                Long analysisId = positiveLongOrNull(item.get("analysisId"));
                if (analysisId == null) continue;
                AnalyticsDashboardCard component = new AnalyticsDashboardCard();
                component.setDashboardId(draft.getId());
                component.setCardId(analysisId);
                component.setRow(integerOrDefault(item.get("row"), 0));
                component.setCol(integerOrDefault(item.get("col"), 0));
                component.setSizeX(integerOrDefault(item.get("sizeX"), 6));
                component.setSizeY(integerOrDefault(item.get("sizeY"), 4));
                JsonNode mappings = item.get("parameterMappings");
                JsonNode visualization = item.get("visualizationSettings");
                component.setParameterMappingsJson(mappings == null || mappings.isNull() ? "[]" : mappings.toString());
                component.setVisualizationSettingsJson(visualization == null || visualization.isNull() ? "{}" : visualization.toString());
                copied.add(component);
            }
        }
        copied = dashboardCards.saveAll(copied);

        AnalyticsRevision draftRevision = new AnalyticsRevision();
        draftRevision.setModel(MODEL);
        draftRevision.setModelId(draft.getId());
        draftRevision.setUserId(actor.getId());
        draftRevision.setReversion(true);
        draftRevision.setObjectJson(snapshot(draft, copied, normalize(null)));
        draftRevision.setVersionNo(1);
        draftRevision.setStatus("DRAFT");
        revisions.save(draftRevision);
        return new DraftResult(draft, List.copyOf(copied), sourceRevision.getId(), sourceRevision.getVersionNo());
    }

    public boolean retryRegistration(long id, AnalyticsUser actor) {
        AnalyticsDashboard dashboard = requireDashboard(id);
        assertWritable(dashboard, actor);
        if (!"PUBLISHED".equals(dashboard.getLifecycleStatus())) {
            throw new AnalysisConflictException("DASHBOARD_NOT_PUBLISHED", "only a published dashboard can retry registration");
        }
        return outbox.retryDashboard(id);
    }

    public void archiveRegistration(AnalyticsDashboard dashboard, AnalyticsUser actor) {
        assertWritable(dashboard, actor);
        dashboard.setLifecycleStatus("ARCHIVED");
        dashboard.setArchived(true);
        if (dashboard.getPublishedRevisionId() == null) {
            dashboard.setRegistrationStatus("NOT_REGISTERED");
            dashboards.save(dashboard);
            return;
        }
        AnalyticsRevision revision = revisions.findById(dashboard.getPublishedRevisionId()).orElse(null);
        long assetVersion = revision == null || revision.getVersionNo() == null ? 1L : revision.getVersionNo();
        PublicationCommand audience = audienceFrom(revision);
        ReportRegistrationCommand disable = new ReportRegistrationCommand(
            "DTS_BI", "DASHBOARD", dashboard.getEntityId(), assetVersion, dashboard.getName(), "DASHBOARD",
            "/bi/dashboards/" + dashboard.getId(), null, null,
            audience.deptCodes(), audience.roleCodes(), audience.classification(), audience.expiresAt(), false
        );
        dashboard.setRegistrationStatus("PENDING_REGISTRATION");
        dashboards.save(dashboard);
        outbox.enqueueDashboard(dashboard.getId(), assetVersion, "DISABLE", disable);
    }

    private ValidationResult validateDashboard(AnalyticsDashboard dashboard, PublicationCommand command) {
        PublicationCommand audience = normalize(command);
        List<PublicationIssue> blockers = new ArrayList<>();
        List<PublicationIssue> warnings = new ArrayList<>();
        if (audience.deptCodes().isEmpty() && audience.roleCodes().isEmpty()) {
            blockers.add(issue("DASHBOARD_AUDIENCE_REQUIRED", "audience", "at least one department or role is required"));
        }
        if (audience.expiresAt() != null && !audience.expiresAt().isAfter(clock.instant())) {
            blockers.add(issue("DASHBOARD_EXPIRY_INVALID", "expiresAt", "expiry must be in the future"));
        }

        List<AnalyticsDashboardCard> components = dashboardCards.findAllByDashboardIdOrderByIdAsc(dashboard.getId());
        if (components.isEmpty()) {
            blockers.add(issue("DASHBOARD_COMPONENT_REQUIRED", "components", "at least one published analysis is required"));
        }
        if (components.size() > MAX_COMPONENTS) {
            blockers.add(issue("DASHBOARD_COMPONENT_LIMIT_EXCEEDED", "components", "dashboard cannot exceed 50 components"));
        }
        JsonNode parameters = readTree(dashboard.getParametersJson());
        int parameterCount = parameters != null && parameters.isArray() ? parameters.size() : 0;
        if (parameterCount > MAX_PARAMETERS) {
            blockers.add(issue("DASHBOARD_PARAMETER_LIMIT_EXCEEDED", "parameters", "dashboard cannot exceed 20 parameters"));
        }

        Set<Long> cardIds = components.stream()
            .map(AnalyticsDashboardCard::getCardId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AnalyticsCard> cardById = cards.findAllById(cardIds).stream()
            .collect(Collectors.toMap(AnalyticsCard::getId, Function.identity()));
        Set<Long> revisionIds = cardById.values().stream()
            .map(AnalyticsCard::getPublishedRevisionId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, AnalyticsRevision> revisionById = revisions.findAllById(revisionIds).stream()
            .collect(Collectors.toMap(AnalyticsRevision::getId, Function.identity()));

        List<Map<String, Object>> dependencies = new ArrayList<>();
        int dependencyClassification = 0;
        for (int index = 0; index < components.size(); index++) {
            AnalyticsDashboardCard component = components.get(index);
            AnalyticsCard analysis = cardById.get(component.getCardId());
            String path = "components[" + index + "]";
            if (analysis == null || !"analysis".equals(analysis.getCardType())) {
                blockers.add(issue("DASHBOARD_ANALYSIS_REQUIRED", path, "component must reference a governed analysis"));
                continue;
            }
            if (!"PUBLISHED".equals(analysis.getLifecycleStatus()) || analysis.getPublishedRevisionId() == null) {
                blockers.add(issue("DASHBOARD_ANALYSIS_NOT_PUBLISHED", path, "analysis must have a published revision"));
                continue;
            }
            AnalyticsRevision revision = revisionById.get(analysis.getPublishedRevisionId());
            if (revision == null || !"PUBLISHED".equals(revision.getStatus())) {
                blockers.add(issue("DASHBOARD_ANALYSIS_REVISION_INVALID", path, "published analysis revision is unavailable"));
                continue;
            }
            Map<String, Object> analysisDependency = readMap(revision.getDependencySnapshotJson());
            int rank = AnalysisPublicationService.classificationRank(String.valueOf(
                analysisDependency.getOrDefault("classification", "DATA_INTERNAL")
            ));
            dependencyClassification = Math.max(dependencyClassification, rank);
            Map<String, Object> dependency = new LinkedHashMap<>();
            dependency.put("analysisId", analysis.getId());
            dependency.put("analysisRevisionId", revision.getId());
            dependency.put("analysisVersion", revision.getVersionNo());
            dependency.put("analysisChecksum", revision.getContractChecksum());
            dependency.put("dataset", analysisDependency);
            dependency.put("parameterMappings", readJsonValue(component.getParameterMappingsJson()));
            dependencies.add(dependency);
        }
        int publishedClassification = AnalysisPublicationService.classificationRank(audience.classification());
        if (publishedClassification < dependencyClassification) {
            blockers.add(issue(
                "DASHBOARD_CLASSIFICATION_DOWNGRADE",
                "classification",
                "dashboard classification cannot be lower than its highest dependency"
            ));
        }

        Map<String, Object> dependencySnapshot = new LinkedHashMap<>();
        dependencySnapshot.put("dashboardId", dashboard.getId());
        dependencySnapshot.put("analyses", dependencies);
        dependencySnapshot.put("parameters", parameters != null && parameters.isArray() ? parameters : List.of());
        dependencySnapshot.put("classification", audience.classification());
        dependencySnapshot.put("queryBudgetVersion", "analysis-query-budget/v1");
        dependencySnapshot.put("audience", audienceMap(audience));
        return new ValidationResult(
            blockers.isEmpty(), List.copyOf(blockers), List.copyOf(warnings),
            Map.copyOf(dependencySnapshot), List.copyOf(components)
        );
    }

    private AnalyticsDashboard requireDashboard(long id) {
        return dashboards.findById(id).orElseThrow(() -> new AnalysisNotFoundException("dashboard does not exist"));
    }

    private void assertReadable(AnalyticsDashboard dashboard, AnalyticsUser actor) {
        requireActor(actor);
        if (!actor.isSuperuser() && !actor.getId().equals(dashboard.getCreatorId())) {
            throw new AnalysisForbiddenException("dashboard is outside the actor scope");
        }
    }

    private void assertWritable(AnalyticsDashboard dashboard, AnalyticsUser actor) {
        assertReadable(dashboard, actor);
        if (dashboard.isArchived()) {
            throw new AnalysisConflictException("DASHBOARD_ARCHIVED", "archived dashboard cannot be changed");
        }
    }

    private void requireActor(AnalyticsUser actor) {
        if (actor == null || actor.getId() == null || !actor.isActive()) {
            throw new AnalysisForbiddenException("authenticated active actor is required");
        }
    }

    private String snapshot(
        AnalyticsDashboard dashboard,
        List<AnalyticsDashboardCard> components,
        PublicationCommand audience
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", dashboard.getId());
        value.put("entityId", dashboard.getEntityId());
        value.put("name", dashboard.getName());
        value.put("description", dashboard.getDescription());
        value.put("collectionId", dashboard.getCollectionId());
        value.put("parameters", readJsonValue(dashboard.getParametersJson()));
        value.put("components", components.stream().map(component -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", component.getId());
            item.put("analysisId", component.getCardId());
            item.put("row", component.getRow());
            item.put("col", component.getCol());
            item.put("sizeX", component.getSizeX());
            item.put("sizeY", component.getSizeY());
            item.put("parameterMappings", readJsonValue(component.getParameterMappingsJson()));
            item.put("visualizationSettings", readJsonValue(component.getVisualizationSettingsJson()));
            return item;
        }).toList());
        value.put("audience", audienceMap(audience));
        return write(value);
    }

    private PublicationCommand audienceFrom(AnalyticsRevision revision) {
        if (revision == null) return normalize(null);
        try {
            JsonNode audience = objectMapper.readTree(revision.getObjectJson()).path("audience");
            return normalize(objectMapper.treeToValue(audience, PublicationCommand.class));
        } catch (Exception ignored) {
            return normalize(null);
        }
    }

    private DatasetBinding datasetBinding(Map<String, Object> dependencySnapshot) {
        Object raw = dependencySnapshot.get("analyses");
        if (!(raw instanceof List<?> analyses) || analyses.isEmpty()) return new DatasetBinding(null, null);
        Set<DatasetBinding> bindings = new LinkedHashSet<>();
        for (Object value : analyses) {
            if (!(value instanceof Map<?, ?> analysis)) continue;
            Object datasetValue = analysis.get("dataset");
            if (!(datasetValue instanceof Map<?, ?> dataset)) continue;
            try {
                UUID id = UUID.fromString(String.valueOf(dataset.get("datasetId")));
                Integer version = Integer.valueOf(String.valueOf(dataset.get("datasetVersion")));
                bindings.add(new DatasetBinding(id, version));
            } catch (Exception ignored) {
                // Multi-source dashboard registration may legitimately omit the convenience binding.
            }
        }
        return bindings.size() == 1 ? bindings.iterator().next() : new DatasetBinding(null, null);
    }

    private PublicationCommand normalize(PublicationCommand command) {
        PublicationCommand value = command == null
            ? new PublicationCommand(List.of(), List.of(), "DATA_INTERNAL", null)
            : command;
        return new PublicationCommand(
            normalizeAudience(value.deptCodes()),
            normalizeAudience(value.roleCodes()),
            AnalysisPublicationService.normalizeClassification(value.classification()),
            value.expiresAt()
        );
    }

    private List<String> normalizeAudience(List<String> values) {
        if (values == null) return List.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private Map<String, Object> audienceMap(PublicationCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("deptCodes", command.deptCodes());
        value.put("roleCodes", command.roleCodes());
        value.put("classification", command.classification());
        value.put("expiresAt", command.expiresAt());
        return value;
    }

    private PublicationIssue issue(String code, String path, String message) {
        return new PublicationIssue(code, path, message);
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) return objectMapper.createArrayNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createArrayNode();
        }
    }

    private Object readJsonValue(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new AnalysisConflictException("DASHBOARD_REVISION_SERIALIZATION_FAILED", "dashboard publication snapshot cannot be serialized");
        }
    }

    private String textOrNull(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Long positiveLongOrNull(JsonNode value) {
        if (value == null || !value.canConvertToLong()) return null;
        long number = value.asLong();
        return number > 0 ? number : null;
    }

    private Integer integerOrDefault(JsonNode value, int fallback) {
        return value != null && value.canConvertToInt() ? value.asInt() : fallback;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record ValidationResult(
        boolean valid,
        List<PublicationIssue> blockers,
        List<PublicationIssue> warnings,
        Map<String, Object> dependencySnapshot,
        List<AnalyticsDashboardCard> dashcards
    ) {}

    public record PublicationResult(
        Long dashboardId,
        Long revisionId,
        int versionNo,
        String lifecycleStatus,
        String registrationStatus,
        String contractChecksum,
        Map<String, Object> dependencySnapshot,
        Instant publishedAt
    ) {}

    public record VersionItem(
        Long revisionId,
        Integer versionNo,
        String status,
        String contractChecksum,
        Map<String, Object> dependencySnapshot,
        Long publishedBy,
        Instant publishedAt,
        Instant createdAt
    ) {}

    public record DraftResult(
        AnalyticsDashboard dashboard,
        List<AnalyticsDashboardCard> dashcards,
        Long sourceRevisionId,
        Integer sourceVersionNo
    ) {}

    private record DatasetBinding(UUID datasetId, Integer version) {}
}
