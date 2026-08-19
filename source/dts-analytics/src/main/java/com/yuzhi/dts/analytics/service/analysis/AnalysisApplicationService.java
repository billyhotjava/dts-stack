package com.yuzhi.dts.analytics.service.analysis;

import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AnalysisApplicationService {

    private static final String CARD_TYPE = "analysis";

    private final AnalyticsCardRepository cardRepository;
    private final GovernedAnalysisDatasetContractProvider contractProvider;
    private final AnalyticsDatabaseBindingResolver databaseBindingResolver;
    private final EntityIdGenerator entityIdGenerator;
    private final AnalysisQuerySpecParser parser;
    private final AnalysisQuerySpecValidator validator;

    public AnalysisApplicationService(
        AnalyticsCardRepository cardRepository,
        GovernedAnalysisDatasetContractProvider contractProvider,
        AnalyticsDatabaseBindingResolver databaseBindingResolver,
        EntityIdGenerator entityIdGenerator,
        AnalysisQuerySpecParser parser,
        AnalysisQuerySpecValidator validator
    ) {
        this.cardRepository = cardRepository;
        this.contractProvider = contractProvider;
        this.databaseBindingResolver = databaseBindingResolver;
        this.entityIdGenerator = entityIdGenerator;
        this.parser = parser;
        this.validator = validator;
    }

    public AnalysisDto create(CreateAnalysisCommand command, AnalyticsUser actor, String idempotencyKey) {
        requireActor(actor);
        String key = idempotencyKey(idempotencyKey);
        if (key != null) {
            AnalyticsCard existing = cardRepository.findByCreatorIdAndIdempotencyKey(actor.getId(), key).orElse(null);
            if (existing != null) return toDto(existing, actor);
        }
        requireName(command == null ? null : command.name());
        AnalysisQuerySpec submitted = command == null ? null : command.querySpec();
        GovernedAnalysisDatasetContract contract = contract(submitted);
        AnalysisQuerySpec spec = validator.validateAndNormalize(submitted, contract);

        AnalyticsCard card = new AnalyticsCard();
        card.setEntityId(entityIdGenerator.newEntityId());
        card.setName(command.name().trim());
        card.setDescription(trimToNull(command.description()));
        card.setCollectionId(command.collectionId());
        card.setArchived(false);
        card.setCardType(CARD_TYPE);
        card.setLifecycleStatus("DRAFT");
        card.setAnalysisVersion(0L);
        card.setPublishedRevisionId(null);
        card.setCreatorId(actor.getId());
        card.setIdempotencyKey(key);
        applySpec(card, spec, contract);
        return toDto(cardRepository.save(card), actor);
    }

    @Transactional(readOnly = true)
    public AnalysisPage list(int page, int size, AnalyticsUser actor) {
        requireActor(actor);
        if (page < 0 || size < 1 || size > 100) {
            throw new AnalysisSpecValidationException("ANALYSIS_PAGE_INVALID", "page", "page must be >= 0 and size between 1 and 100");
        }
        List<AnalyticsCard> visible = cardRepository
            .findAllByCardTypeAndArchivedFalseOrderByUpdatedAtDesc(CARD_TYPE)
            .stream()
            .filter(card -> actor.isSuperuser() || actor.getId().equals(card.getCreatorId()))
            .toList();
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + size, visible.size());
        int totalPages = visible.isEmpty() ? 0 : (visible.size() + size - 1) / size;
        return new AnalysisPage(
            visible.subList(from, to).stream().map(card -> toDto(card, actor)).toList(),
            page,
            size,
            visible.size(),
            totalPages
        );
    }

    @Transactional(readOnly = true)
    public AnalysisDto get(long id, AnalyticsUser actor) {
        AnalyticsCard card = requireAnalysis(id);
        assertReadable(card, actor);
        return toDto(card, actor);
    }

    public AnalysisDto update(long id, UpdateAnalysisCommand command, AnalyticsUser actor) {
        AnalyticsCard card = requireAnalysis(id);
        assertWritable(card, actor);
        if (!"DRAFT".equals(card.getLifecycleStatus())) {
            throw new AnalysisConflictException("ANALYSIS_PUBLISHED_IMMUTABLE", "published or archived analysis cannot be edited in place");
        }
        if (command == null || command.versionNo() == null || !command.versionNo().equals(card.getAnalysisVersion())) {
            throw new AnalysisConflictException("ANALYSIS_VERSION_CONFLICT", "analysis was changed by another editor");
        }
        requireName(command.name());
        GovernedAnalysisDatasetContract contract = contract(command.querySpec());
        AnalysisQuerySpec spec = validator.validateAndNormalize(command.querySpec(), contract);
        card.setName(command.name().trim());
        card.setDescription(trimToNull(command.description()));
        applySpec(card, spec, contract);
        return toDto(cardRepository.save(card), actor);
    }

    public AnalysisDto copy(long id, AnalyticsUser actor) {
        AnalyticsCard source = requireAnalysis(id);
        assertReadable(source, actor);
        return create(
            new CreateAnalysisCommand(source.getName() + " 副本", source.getDescription(), parser.parse(source.getDatasetQueryJson()), source.getCollectionId()),
            actor,
            null
        );
    }

    public AnalysisDto archive(long id, AnalyticsUser actor) {
        AnalyticsCard card = requireAnalysis(id);
        assertWritable(card, actor);
        card.setLifecycleStatus("ARCHIVED");
        card.setArchived(true);
        return toDto(cardRepository.save(card), actor);
    }

    public AnalysisDto restore(long id, AnalyticsUser actor) {
        AnalyticsCard card = requireAnalysis(id);
        assertWritable(card, actor);
        if (!"ARCHIVED".equals(card.getLifecycleStatus())) {
            throw new AnalysisConflictException("ANALYSIS_NOT_ARCHIVED", "only archived analysis can be restored");
        }
        card.setLifecycleStatus("DRAFT");
        card.setArchived(false);
        card.setPublishedRevisionId(null);
        return toDto(cardRepository.save(card), actor);
    }

    private GovernedAnalysisDatasetContract contract(AnalysisQuerySpec spec) {
        if (spec == null || spec.dataset() == null) {
            throw new AnalysisSpecValidationException("ANALYSIS_DATASET_REQUIRED", "dataset", "dataset reference is required");
        }
        return contractProvider.get(spec.dataset().id(), spec.dataset().version(), spec.dataset().checksum());
    }

    private void applySpec(
        AnalyticsCard card,
        AnalysisQuerySpec spec,
        GovernedAnalysisDatasetContract contract
    ) {
        card.setDatabaseId(databaseBindingResolver.requireDatabaseId(contract.sourceDatasourceId()));
        card.setDatasetQueryJson(parser.write(spec));
        card.setDisplay(spec.visualization().type());
        card.setVisualizationSettingsJson(parser.writeValue(spec.visualization().settings()));
        card.setQueryDatasetId(spec.dataset().id());
        card.setQueryDatasetVersion(spec.dataset().version());
        card.setSemanticContractVersion(spec.dataset().contractVersion());
    }

    private AnalyticsCard requireAnalysis(long id) {
        AnalyticsCard card = cardRepository
            .findById(id)
            .orElseThrow(() -> new AnalysisNotFoundException("analysis does not exist"));
        if (!CARD_TYPE.equals(card.getCardType())) {
            throw new AnalysisNotFoundException("asset is not a governed analysis");
        }
        return card;
    }

    private void assertReadable(AnalyticsCard card, AnalyticsUser actor) {
        requireActor(actor);
        if (!actor.isSuperuser() && !actor.getId().equals(card.getCreatorId())) {
            throw new AnalysisForbiddenException("analysis is outside the actor scope");
        }
    }

    private void assertWritable(AnalyticsCard card, AnalyticsUser actor) {
        assertReadable(card, actor);
    }

    private void requireActor(AnalyticsUser actor) {
        if (actor == null || actor.getId() == null || !actor.isActive()) {
            throw new AnalysisForbiddenException("authenticated active actor is required");
        }
    }

    private void requireName(String name) {
        if (!StringUtils.hasText(name) || name.trim().length() > 255) {
            throw new AnalysisSpecValidationException("ANALYSIS_NAME_INVALID", "name", "name is required and must not exceed 255 characters");
        }
    }

    private String idempotencyKey(String value) {
        String key = trimToNull(value);
        if (key != null && key.length() > 128) {
            throw new AnalysisSpecValidationException("ANALYSIS_IDEMPOTENCY_KEY_INVALID", "Idempotency-Key", "idempotency key exceeds 128 characters");
        }
        return key;
    }

    private AnalysisDto toDto(AnalyticsCard card, AnalyticsUser actor) {
        AnalysisQuerySpec spec = parser.parse(card.getDatasetQueryJson());
        boolean owner = actor != null && (actor.isSuperuser() || actor.getId().equals(card.getCreatorId()));
        return new AnalysisDto(
            card.getId(),
            card.getName(),
            card.getDescription(),
            card.getLifecycleStatus(),
            card.getAnalysisVersion() == null ? 0 : card.getAnalysisVersion(),
            card.getPublishedRevisionId(),
            card.getQueryDatasetId(),
            card.getQueryDatasetVersion(),
            card.getSemanticContractVersion(),
            spec.visualization(),
            spec,
            String.valueOf(card.getCreatorId()),
            card.getUpdatedAt(),
            Map.of(
                "read", owner,
                "write", owner && "DRAFT".equals(card.getLifecycleStatus()),
                "publish", owner,
				"export", owner && "PUBLISHED".equals(card.getLifecycleStatus())
            )
        );
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CreateAnalysisCommand(
        String name,
        String description,
        AnalysisQuerySpec querySpec,
        Long collectionId
    ) {}

    public record UpdateAnalysisCommand(
        String name,
        String description,
        AnalysisQuerySpec querySpec,
        Long versionNo
    ) {}

    public record AnalysisDto(
        Long id,
        String name,
        String description,
        String lifecycleStatus,
        long versionNo,
        Long publishedRevisionId,
        UUID queryDatasetId,
        Integer queryDatasetVersion,
        String contractVersion,
        AnalysisQuerySpec.Visualization visualization,
        AnalysisQuerySpec querySpec,
        String createdBy,
        Instant updatedAt,
        Map<String, Boolean> permissions
    ) {}

    public record AnalysisPage(
        List<AnalysisDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
