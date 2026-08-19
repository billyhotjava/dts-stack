package com.yuzhi.dts.analytics.service.publication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisDto;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.CreateAnalysisCommand;
import com.yuzhi.dts.analytics.service.analysis.AnalysisConflictException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisForbiddenException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisNotFoundException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpec;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpecParser;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpecValidator;
import com.yuzhi.dts.analytics.service.analysis.AnalysisSpecValidationException;
import com.yuzhi.dts.analytics.service.analysis.GovernedAnalysisDatasetContract;
import com.yuzhi.dts.analytics.service.analysis.GovernedAnalysisDatasetContractProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;

@Service
@Transactional
public class AnalysisPublicationService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisPublicationService.class);

    public static final String MODEL = "analysis";

    private final AnalyticsCardRepository cards;
    private final AnalyticsRevisionRepository revisions;
    private final GovernedAnalysisDatasetContractProvider contracts;
    private final AnalysisQuerySpecParser parser;
    private final AnalysisQuerySpecValidator validator;
    private final AnalysisApplicationService applicationService;
    private final AnalyticsConsumerClassificationService classificationService;
    private final PublicationEntityLock entityLock;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AnalysisPublicationService(
        AnalyticsCardRepository cards,
        AnalyticsRevisionRepository revisions,
        GovernedAnalysisDatasetContractProvider contracts,
        AnalysisQuerySpecParser parser,
        AnalysisQuerySpecValidator validator,
        AnalysisApplicationService applicationService,
        AnalyticsConsumerClassificationService classificationService,
        PublicationEntityLock entityLock,
        ObjectMapper objectMapper
    ) {
        this(
            cards,
            revisions,
            contracts,
            parser,
            validator,
            applicationService,
            classificationService,
            entityLock,
            objectMapper,
            Clock.systemUTC()
        );
    }

    AnalysisPublicationService(
        AnalyticsCardRepository cards,
        AnalyticsRevisionRepository revisions,
        GovernedAnalysisDatasetContractProvider contracts,
        AnalysisQuerySpecParser parser,
        AnalysisQuerySpecValidator validator,
        AnalysisApplicationService applicationService,
        AnalyticsConsumerClassificationService classificationService,
        PublicationEntityLock entityLock,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.cards = cards;
        this.revisions = revisions;
        this.contracts = contracts;
        this.parser = parser;
        this.validator = validator;
        this.applicationService = applicationService;
        this.classificationService = classificationService;
        this.entityLock = entityLock;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(long id, AnalyticsUser actor, PublicationCommand command) {
        AnalyticsCard card = requireAnalysis(id);
        assertReadable(card, actor);
        return validateCard(card, command);
    }

    public PublicationResult publish(long id, AnalyticsUser actor, PublicationCommand command) {
        AnalyticsCard card = entityLock.analysis(id);
        assertWritable(card, actor);
        if (!"DRAFT".equals(card.getLifecycleStatus())) {
            throw new AnalysisConflictException("ANALYSIS_PUBLISH_STATE_INVALID", "only a draft analysis can be published");
        }
        ValidationResult validation = validateCard(card, command);
        if (!validation.valid()) {
            PublicationIssue first = validation.blockers().getFirst();
            throw new AnalysisSpecValidationException(first.code(), first.path(), first.message());
        }

        revisions.findCurrentPublished(MODEL, id).ifPresent(current -> {
            current.setStatus("SUPERSEDED");
            revisions.saveAndFlush(current);
        });
        int version = revisions.findMaxVersionNo(MODEL, id) + 1;
        String objectJson = snapshot(card, normalized(command));
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

        card.setLifecycleStatus("PUBLISHED");
        card.setPublishedRevisionId(revision.getId());
        cards.save(card);
        classificationService.deriveCard(card);
        LOG.info(
            "Analysis published correlationId={} actor={} assetKey=analysis:{} revisionId={} datasetVersion={} checksum={} outcome=SUCCESS",
            RequestContextUtils.resolveRequestId(), actor.getId(), id, revision.getId(),
            validation.dependencySnapshot().getOrDefault("datasetVersion", "unknown"), checksum
        );
        return new PublicationResult(
            id,
            revision.getId(),
            version,
            "PUBLISHED",
            checksum,
            validation.dependencySnapshot(),
            revision.getPublishedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<VersionItem> versions(long id, AnalyticsUser actor) {
        AnalyticsCard card = requireAnalysis(id);
        assertReadable(card, actor);
        return revisions.findAllByModelAndModelIdOrderByVersionNoDesc(MODEL, id).stream()
            .map(this::toVersionItem)
            .toList();
    }

    public AnalysisDto createDraftFromVersion(long id, long revisionId, AnalyticsUser actor) {
        AnalyticsCard source = requireAnalysis(id);
        assertReadable(source, actor);
        AnalyticsRevision revision = revisions.findById(revisionId)
            .filter(value -> MODEL.equals(value.getModel()) && Long.valueOf(id).equals(value.getModelId()))
            .orElseThrow(() -> new AnalysisNotFoundException("analysis revision does not exist"));
        try {
            JsonNode snapshot = objectMapper.readTree(revision.getObjectJson());
            AnalysisQuerySpec spec = parser.parse(snapshot.path("querySpec").toString());
            return applicationService.create(
                new CreateAnalysisCommand(
                    snapshot.path("name").asText(source.getName()) + "（基于 v" + revision.getVersionNo() + "）",
                    snapshot.path("description").isNull() ? null : snapshot.path("description").asText(null),
                    spec,
                    snapshot.path("collectionId").isNumber() ? snapshot.path("collectionId").asLong() : null
                ),
                actor,
                null
            );
        } catch (AnalysisSpecValidationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new AnalysisConflictException("ANALYSIS_REVISION_SNAPSHOT_INVALID", "revision snapshot cannot create a draft");
        }
    }

    private ValidationResult validateCard(AnalyticsCard card, PublicationCommand command) {
        List<PublicationIssue> blockers = new ArrayList<>();
        List<PublicationIssue> warnings = new ArrayList<>();
        PublicationCommand audience = normalized(command);
        if (audience.deptCodes().isEmpty() && audience.roleCodes().isEmpty()) {
            blockers.add(issue("ANALYSIS_AUDIENCE_REQUIRED", "audience", "at least one department or role is required"));
        }
        if (audience.expiresAt() != null && !audience.expiresAt().isAfter(clock.instant())) {
            blockers.add(issue("ANALYSIS_EXPIRY_INVALID", "expiresAt", "expiry must be in the future"));
        }

        Map<String, Object> dependency = new LinkedHashMap<>();
        try {
            AnalysisQuerySpec submitted = parser.parse(card.getDatasetQueryJson());
            GovernedAnalysisDatasetContract contract = contracts.get(
                submitted.dataset().id(), submitted.dataset().version(), submitted.dataset().checksum()
            );
            AnalysisQuerySpec normalized = validator.validateAndNormalize(submitted, contract);
            String classification = normalizeClassification(audience.classification());
            if (classificationRank(classification) < classificationRank(contract.classification())) {
                blockers.add(issue(
                    "ANALYSIS_CLASSIFICATION_DOWNGRADE",
                    "classification",
                    "publication classification cannot be lower than the dataset classification"
                ));
            }
            dependency.put("datasetId", contract.datasetId());
            dependency.put("datasetVersion", contract.version());
            dependency.put("contractVersion", contract.contractVersion());
            dependency.put("contractChecksum", contract.contractChecksum());
            dependency.put("classification", normalizeClassification(contract.classification()));
            dependency.put("policyRefs", contract.policyRefs() == null ? List.of() : List.copyOf(contract.policyRefs()));
            dependency.put("queryBudgetVersion", "analysis-query-budget/v1");
            dependency.put("resultLimit", normalized.limit());
        } catch (RuntimeException failure) {
            blockers.add(issue(errorCode(failure), "querySpec.dataset", safeMessage(failure)));
        }
        dependency.put("audience", audienceMap(audience));
        return new ValidationResult(blockers.isEmpty(), List.copyOf(blockers), List.copyOf(warnings), Map.copyOf(dependency));
    }

    private AnalyticsCard requireAnalysis(long id) {
        return cards.findById(id)
            .filter(card -> "analysis".equals(card.getCardType()))
            .orElseThrow(() -> new AnalysisNotFoundException("analysis does not exist"));
    }

    private void assertReadable(AnalyticsCard card, AnalyticsUser actor) {
        requireActor(actor);
        if (!actor.isSuperuser() && !actor.getId().equals(card.getCreatorId())) {
            throw new AnalysisForbiddenException("analysis is outside the actor scope");
        }
    }

    private void assertWritable(AnalyticsCard card, AnalyticsUser actor) {
        assertReadable(card, actor);
        if (card.isArchived()) {
            throw new AnalysisConflictException("ANALYSIS_ARCHIVED", "archived analysis cannot be published");
        }
    }

    private void requireActor(AnalyticsUser actor) {
        if (actor == null || actor.getId() == null || !actor.isActive()) {
            throw new AnalysisForbiddenException("authenticated active actor is required");
        }
    }

    private VersionItem toVersionItem(AnalyticsRevision revision) {
        return new VersionItem(
            revision.getId(),
            revision.getVersionNo(),
            revision.getStatus(),
            revision.getContractChecksum(),
            readMap(revision.getDependencySnapshotJson()),
            revision.getUserId(),
            revision.getPublishedAt(),
            revision.getCreatedAt()
        );
    }

    private String snapshot(AnalyticsCard card, PublicationCommand audience) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", card.getId());
        value.put("entityId", card.getEntityId());
        value.put("name", card.getName());
        value.put("description", card.getDescription());
        value.put("collectionId", card.getCollectionId());
        value.put("querySpec", parser.parse(card.getDatasetQueryJson()));
        value.put("visualizationSettings", readMap(card.getVisualizationSettingsJson()));
        value.put("audience", audienceMap(audience));
        return write(value);
    }

    private Map<String, Object> audienceMap(PublicationCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("deptCodes", command.deptCodes());
        value.put("roleCodes", command.roleCodes());
        value.put("classification", normalizeClassification(command.classification()));
        value.put("expiresAt", command.expiresAt());
        return value;
    }

    private PublicationCommand normalized(PublicationCommand command) {
        PublicationCommand value = command == null
            ? new PublicationCommand(List.of(), List.of(), null, null)
            : command;
        return new PublicationCommand(
            normalizeAudience(value.deptCodes()),
            normalizeAudience(value.roleCodes()),
            normalizeClassification(value.classification()),
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

    static String normalizeClassification(String value) {
        if (!StringUtils.hasText(value)) return "DATA_INTERNAL";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLIC", "S0", "DATA_PUBLIC" -> "DATA_PUBLIC";
            case "INTERNAL", "S1", "DATA_INTERNAL" -> "DATA_INTERNAL";
            case "CONFIDENTIAL", "S2", "DATA_CONFIDENTIAL" -> "DATA_CONFIDENTIAL";
            case "SENSITIVE", "S3", "DATA_SENSITIVE" -> "DATA_SENSITIVE";
            case "SECRET", "S4", "DATA_SECRET" -> "DATA_SECRET";
            default -> normalized;
        };
    }

    static int classificationRank(String value) {
        return switch (normalizeClassification(value)) {
            case "DATA_PUBLIC" -> 0;
            case "DATA_INTERNAL" -> 1;
            case "DATA_CONFIDENTIAL" -> 2;
            case "DATA_SENSITIVE" -> 3;
            case "DATA_SECRET" -> 4;
            default -> -1;
        };
    }

    private PublicationIssue issue(String code, String path, String message) {
        return new PublicationIssue(code, path, message);
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof AnalysisSpecValidationException validation) return validation.getErrorCode();
        if (failure instanceof AnalysisConflictException conflict) return conflict.getErrorCode();
        return "ANALYSIS_DEPENDENCY_INVALID";
    }

    private String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (!StringUtils.hasText(message)) return "analysis dependency validation failed";
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new AnalysisConflictException("ANALYSIS_REVISION_SERIALIZATION_FAILED", "publication snapshot cannot be serialized");
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

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record PublicationCommand(
        List<String> deptCodes,
        List<String> roleCodes,
        String classification,
        Instant expiresAt
    ) {}

    public record PublicationIssue(String code, String path, String message) {}

    public record ValidationResult(
        boolean valid,
        List<PublicationIssue> blockers,
        List<PublicationIssue> warnings,
        Map<String, Object> dependencySnapshot
    ) {}

    public record PublicationResult(
        Long analysisId,
        Long revisionId,
        int versionNo,
        String status,
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
}
