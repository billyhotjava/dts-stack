package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.AttributeSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchySemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ScopeType;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical application boundary for reusable business dimensions. */
@Service
public class DimensionDefinitionApplicationService {

    public static final int DEFAULT_LIST_LIMIT = 50;
    public static final int MAX_LIST_LIMIT = 100;

    private final DimensionDefinitionRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;
    private final ModelSpecDomainWriteAccessPort domainWriteAccess;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final AuditService auditService;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    @Autowired
    public DimensionDefinitionApplicationService(
        DimensionDefinitionRepository repository,
        ObjectMapper objectMapper,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        AuditService auditService
    ) {
        this(
            repository,
            objectMapper,
            domainWriteAccess,
            domainReadAccess,
            auditService,
            Clock.systemUTC(),
            UUID::randomUUID
        );
    }

    DimensionDefinitionApplicationService(
        DimensionDefinitionRepository repository,
        ObjectMapper objectMapper,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        AuditService auditService,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
        this.domainWriteAccess = domainWriteAccess;
        this.domainReadAccess = domainReadAccess;
        this.auditService = auditService;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreateResult create(String tenantId, String actorId, CreateCommand command) {
        requireServerContext(tenantId, actorId);
        rejectIssues(DimensionDefinitionContract.validateCreate(command));
        String requestHash = hash(command);
        StoredDimensionDefinition existing = repository
            .findByIdempotencyKey(tenantId, command.idempotencyKey())
            .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        UUID id = idGenerator.get();
        return persistDraft(tenantId, actorId, command, requestHash, id, systemCode(id));
    }

    @Transactional
    public View importCurrent(
        String tenantId,
        String actorId,
        UUID definitionId,
        String definitionSystemCode,
        CreateCommand command
    ) {
        requireServerContext(tenantId, actorId);
        rejectIssues(DimensionDefinitionContract.validateCreate(command));
        if (definitionId == null || !Objects.equals(systemCode(definitionId), definitionSystemCode)) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_IMPORT_IDENTITY_INVALID",
                "Imported dimension definition id and system code do not match",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        StoredDimensionDefinition stored = repository.findCurrent(tenantId, definitionId).orElse(null);
        View draftOrCurrent;
        if (stored != null) {
            requireWriteAccess(stored.domainId());
            draftOrCurrent = currentView(tenantId, stored);
        } else {
            String requestHash = hash(command);
            StoredDimensionDefinition replay = repository
                .findByIdempotencyKey(tenantId, command.idempotencyKey())
                .orElse(null);
            draftOrCurrent = replay == null
                ? persistDraft(tenantId, actorId, command, requestHash, definitionId, definitionSystemCode).dimensionDefinition()
                : replay(replay, requestHash).dimensionDefinition();
        }
        requireCompatibleImport(draftOrCurrent, definitionId, definitionSystemCode, command);
        if (draftOrCurrent.status() == Status.CURRENT) {
            if (draftOrCurrent.revision() != 2) {
                throw importConflict();
            }
            return draftOrCurrent;
        }
        if (draftOrCurrent.status() != Status.DRAFT || draftOrCurrent.revision() != 1) {
            throw importConflict();
        }
        View confirmed = checksum(
            new View(
                draftOrCurrent.id(),
                draftOrCurrent.systemCode(),
                draftOrCurrent.domainId(),
                draftOrCurrent.name(),
                draftOrCurrent.abbreviation(),
                draftOrCurrent.definition(),
                draftOrCurrent.ownerId(),
                draftOrCurrent.reuseScope(),
                draftOrCurrent.hierarchies(),
                Status.CURRENT,
                2,
                "",
                draftOrCurrent.usageCount(),
                draftOrCurrent.createdAt(),
                databaseInstant(),
                draftOrCurrent.scopeType(),
                draftOrCurrent.dataMartId(),
                draftOrCurrent.attributes()
            )
        );
        View persisted = compareAndSet(tenantId, actorId, draftOrCurrent, confirmed);
        audit("MODELING_DIMENSION_DEFINITION_CONFIRM", tenantId, actorId, persisted);
        return persisted;
    }

    private CreateResult persistDraft(
        String tenantId,
        String actorId,
        CreateCommand command,
        String requestHash,
        UUID id,
        String definitionSystemCode
    ) {

        requireWriteAccess(command.domainId());
        ScopeType scopeType = effectiveScope(command.scopeType());
        validateDataMartScope(tenantId, command.domainId(), scopeType, command.dataMartId());
        requireUniqueName(tenantId, command.domainId(), command.name(), null);
        Instant now = databaseInstant();
        View created = checksum(
            new View(
                id,
                definitionSystemCode,
                command.domainId(),
                command.name(),
                command.abbreviation(),
                command.definition(),
                command.ownerId(),
                command.reuseScope(),
                copy(command.hierarchies()),
                Status.DRAFT,
                1,
                "",
                0,
                now,
                now,
                scopeType,
                command.dataMartId(),
                copyAttributes(command.attributes())
            )
        );
        int inserted;
        try {
            inserted = repository.insert(tenantId, actorId, command, created, requestHash);
        } catch (DataIntegrityViolationException exception) {
            throw persistenceConflict(exception, command.domainId(), command.name());
        }
        if (inserted == 0) {
            StoredDimensionDefinition concurrent = repository
                .findByIdempotencyKey(tenantId, command.idempotencyKey())
                .orElseThrow(() ->
                    new ModelSpecException(
                        "DIMENSION_DEFINITION_CREATE_CONCURRENCY_CONFLICT",
                        "Concurrent dimension definition creation did not converge",
                        ModelSpecException.Kind.CONFLICT
                    )
                );
            return replay(concurrent, requestHash);
        }
        audit("MODELING_DIMENSION_DEFINITION_CREATE", tenantId, actorId, created);
        return new CreateResult(created, false);
    }

    private void requireCompatibleImport(
        View view,
        UUID definitionId,
        String definitionSystemCode,
        CreateCommand command
    ) {
        if (
            !Objects.equals(view.id(), definitionId) ||
            !Objects.equals(view.systemCode(), definitionSystemCode) ||
            !Objects.equals(view.domainId(), command.domainId()) ||
            !Objects.equals(view.name(), command.name()) ||
            !Objects.equals(view.abbreviation(), command.abbreviation()) ||
            !Objects.equals(view.definition(), command.definition()) ||
            view.reuseScope() != command.reuseScope() ||
            !Objects.equals(view.hierarchies(), copy(command.hierarchies())) ||
            view.scopeType() != effectiveScope(command.scopeType()) ||
            !Objects.equals(view.dataMartId(), command.dataMartId()) ||
            !Objects.equals(view.attributes(), copyAttributes(command.attributes()))
        ) {
            throw importConflict();
        }
    }

    private static ModelSpecException importConflict() {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_IMPORT_CONFLICT",
            "The target dimension definition already exists with different content or lifecycle pins",
            ModelSpecException.Kind.CONFLICT
        );
    }

    @Transactional(readOnly = true)
    public View get(String tenantId, UUID id) {
        requireTenant(tenantId);
        StoredDimensionDefinition stored = repository.findCurrent(tenantId, id).orElseThrow(() -> notFound(id));
        requireReadAccess(stored, id);
        return currentView(tenantId, stored);
    }

    @Transactional(readOnly = true)
    public View revision(String tenantId, UUID id, int revision) {
        requireTenant(tenantId);
        if (revision < 1) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_REVISION_INVALID",
                "Dimension definition revision must be positive",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        StoredDimensionDefinition stored = repository
            .findRevision(tenantId, id, revision)
            .orElseThrow(() -> notFound(id));
        requireReadAccess(stored, id);
        return stored.toView(repository.usageCount(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<View> list(String tenantId, UUID domainId, Status status, int offset, int limit) {
        return list(tenantId, domainId, null, status, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<View> list(
        String tenantId,
        UUID domainId,
        UUID dataMartId,
        Status status,
        int offset,
        int limit
    ) {
        requireTenant(tenantId);
        requireListWindow(offset, limit);
        Set<UUID> visibleDomainIds;
        if (domainId == null) {
            visibleDomainIds = domainReadAccess.visibleDomainIds();
        } else {
            visibleDomainIds = domainReadAccess.canRead(domainId) ? Set.of(domainId) : Set.of();
        }
        if (visibleDomainIds.isEmpty()) {
            return List.of();
        }
        List<StoredDimensionDefinition> visible = repository
            .listCurrent(tenantId, domainId, dataMartId, status, visibleDomainIds, offset, limit)
            .stream()
            .filter(stored -> visibleDomainIds.contains(stored.domainId()))
            .toList();
        Map<UUID, Long> usageCounts = visible.isEmpty()
            ? Map.of()
            : repository.usageCounts(tenantId, visible.stream().map(StoredDimensionDefinition::id).toList());
        return visible
            .stream()
            .map(stored -> stored.toView(usageCounts.getOrDefault(stored.id(), 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<View> revisionsForRelationshipGraph(
        String tenantId,
        List<DimensionDefinitionRef> references,
        int limit
    ) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 500) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_RELATIONSHIP_GRAPH_WINDOW_INVALID",
                "Relationship graph dimension limit must be between 1 and 500",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        Set<UUID> visibleDomainIds = domainReadAccess.visibleDomainIds();
        if (visibleDomainIds == null || visibleDomainIds.isEmpty() || references == null || references.isEmpty()) {
            return List.of();
        }
        Map<String, DimensionDefinitionRef> unique = new LinkedHashMap<>();
        for (DimensionDefinitionRef reference : references) {
            if (reference == null || reference.dimensionDefinitionId() == null || reference.revision() < 1) {
                continue;
            }
            unique.putIfAbsent(reference.dimensionDefinitionId() + "@" + reference.revision(), reference);
            if (unique.size() >= limit) {
                break;
            }
        }
        List<DimensionDefinitionRef> bounded = unique
            .values()
            .stream()
            .sorted(
                java.util.Comparator
                    .comparing(DimensionDefinitionRef::dimensionDefinitionId)
                    .thenComparingInt(DimensionDefinitionRef::revision)
            )
            .toList();
        return repository
            .listRevisionsForRelationshipGraph(tenantId, bounded, visibleDomainIds, limit)
            .stream()
            .filter(stored -> visibleDomainIds.contains(stored.domainId()))
            .map(stored -> stored.toView(0))
            .toList();
    }

    @Transactional
    public View update(
        String tenantId,
        String actorId,
        UUID id,
        ExpectedVersion expected,
        UpdateCommand command
    ) {
        requireServerContext(tenantId, actorId);
        View current = currentForMutation(tenantId, id);
        requireExpected(id, current, expected);
        if (current.status() == Status.RETIRED) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_RETIRED",
                "Retired dimension definitions are read-only",
                ModelSpecException.Kind.CONFLICT
            );
        }
        rejectIssues(DimensionDefinitionContract.validateUpdate(command));
        ScopeType scopeType = command.scopeType() == null ? current.scopeType() : command.scopeType();
        UUID dataMartId = scopeType == ScopeType.DATA_MART
            ? (command.dataMartId() == null ? current.dataMartId() : command.dataMartId())
            : null;
        List<AttributeSemantic> attributes = command.attributes() == null
            ? current.attributes()
            : copyAttributes(command.attributes());
        validateDataMartScope(tenantId, current.domainId(), scopeType, dataMartId);
        if (sameBusinessContent(current, command, scopeType, dataMartId, attributes)) {
            return current;
        }
        requireUniqueName(tenantId, current.domainId(), command.name(), current.id());
        View replacement = checksum(
            new View(
                current.id(),
                current.systemCode(),
                current.domainId(),
                command.name(),
                command.abbreviation(),
                command.definition(),
                command.ownerId(),
                command.reuseScope(),
                copy(command.hierarchies()),
                current.status(),
                current.revision() + 1,
                "",
                current.usageCount(),
                current.createdAt(),
                databaseInstant(),
                scopeType,
                dataMartId,
                attributes
            )
        );
        View updated = compareAndSet(tenantId, actorId, current, replacement);
        audit("MODELING_DIMENSION_DEFINITION_UPDATE", tenantId, actorId, updated);
        return updated;
    }

    @Transactional
    public View confirm(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        View confirmed = transition(tenantId, actorId, id, expected, Status.DRAFT, Status.CURRENT);
        audit("MODELING_DIMENSION_DEFINITION_CONFIRM", tenantId, actorId, confirmed);
        return confirmed;
    }

    @Transactional
    public View retire(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        View retired = transition(tenantId, actorId, id, expected, Status.CURRENT, Status.RETIRED);
        audit("MODELING_DIMENSION_DEFINITION_RETIRE", tenantId, actorId, retired);
        return retired;
    }

    @Transactional
    public void delete(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        requireServerContext(tenantId, actorId);
        View current = currentForMutation(tenantId, id);
        requireExpected(id, current, expected);
        if (current.status() != Status.DRAFT) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_DELETE_INVALID",
                "Only DRAFT dimension definitions can be deleted; confirmed definitions must be retired",
                ModelSpecException.Kind.CONFLICT,
                Map.of("dimensionDefinitionId", id, "currentStatus", current.status())
            );
        }
        boolean deleted;
        try {
            deleted = repository.deleteDraft(tenantId, id, current.checksum());
        } catch (DataIntegrityViolationException exception) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_DELETE_IN_USE",
                "The DRAFT dimension definition is referenced by a model and cannot be deleted",
                ModelSpecException.Kind.CONFLICT,
                Map.of("dimensionDefinitionId", id)
            );
        }
        if (!deleted) {
            throw revisionConflict(current);
        }
        audit("MODELING_DIMENSION_DEFINITION_DELETE", tenantId, actorId, current);
    }

    private View transition(
        String tenantId,
        String actorId,
        UUID id,
        ExpectedVersion expected,
        Status required,
        Status target
    ) {
        requireServerContext(tenantId, actorId);
        View current = currentForMutation(tenantId, id);
        requireExpected(id, current, expected);
        if (current.status() != required) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_TRANSITION_INVALID",
                "Dimension definition lifecycle does not allow this transition",
                ModelSpecException.Kind.CONFLICT,
                Map.of("currentStatus", current.status(), "requiredStatus", required, "targetStatus", target)
            );
        }
        View replacement = checksum(
            new View(
                current.id(),
                current.systemCode(),
                current.domainId(),
                current.name(),
                current.abbreviation(),
                current.definition(),
                current.ownerId(),
                current.reuseScope(),
                current.hierarchies(),
                target,
                current.revision() + 1,
                "",
                current.usageCount(),
                current.createdAt(),
                databaseInstant(),
                current.scopeType(),
                current.dataMartId(),
                current.attributes()
            )
        );
        return compareAndSet(tenantId, actorId, current, replacement);
    }

    private View currentForMutation(String tenantId, UUID id) {
        StoredDimensionDefinition stored = repository.findCurrent(tenantId, id).orElseThrow(() -> notFound(id));
        requireWriteAccess(stored.domainId());
        return currentView(tenantId, stored);
    }

    private View compareAndSet(String tenantId, String actorId, View current, View replacement) {
        int updated;
        try {
            updated = repository.compareAndSet(
                tenantId,
                actorId,
                new DimensionDefinitionRepository.ExpectedVersion(
                    current.id(),
                    current.revision(),
                    current.checksum()
                ),
                replacement
            );
        } catch (DataIntegrityViolationException exception) {
            throw persistenceConflict(exception, replacement.domainId(), replacement.name());
        }
        if (updated == 0) {
            throw revisionConflict(latestVisible(tenantId, current));
        }
        return replacement;
    }

    private void audit(String actionCode, String tenantId, String actorId, View view) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        payload.put("domainId", view.domainId());
        payload.put("systemCode", view.systemCode());
        payload.put("name", view.name());
        payload.put("revision", view.revision());
        payload.put("status", view.status().name());
        auditService.auditAction(actionCode, AuditStage.SUCCESS, view.id().toString(), payload);
    }

    private CreateResult replay(StoredDimensionDefinition stored, String requestHash) {
        if (!domainReadAccess.canRead(stored.domainId())) {
            throw notFound(null);
        }
        if (!Objects.equals(stored.idempotencyRequestHash(), requestHash)) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_IDEMPOTENCY_CONFLICT",
                "The idempotency key was already used for different dimension definition content",
                ModelSpecException.Kind.CONFLICT
            );
        }
        String snapshot = stored.idempotencyResponseSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            throw replayInvalid();
        }
        StoredDimensionDefinition revisionOne = repository
            .findRevision(stored.tenantId(), stored.id(), 1)
            .orElseThrow(DimensionDefinitionApplicationService::replayInvalid);
        try {
            View response = objectMapper.readValue(snapshot, View.class);
            if (
                response.status() != Status.DRAFT ||
                response.revision() != 1 ||
                response.usageCount() != 0 ||
                !Objects.equals(response.id(), stored.id()) ||
                !Objects.equals(response.systemCode(), stored.systemCode()) ||
                !Objects.equals(response.domainId(), stored.domainId()) ||
                !matchesRevision(response, revisionOne) ||
                !Objects.equals(response.checksum(), contentChecksum(response))
            ) {
                throw replayInvalid();
            }
            return new CreateResult(response, true);
        } catch (JsonProcessingException exception) {
            throw replayInvalid();
        }
    }

    private View currentView(String tenantId, StoredDimensionDefinition stored) {
        return stored.toView(repository.usageCount(tenantId, stored.id()));
    }

    private View latestVisible(String tenantId, View fallback) {
        StoredDimensionDefinition latest = repository.findCurrent(tenantId, fallback.id()).orElse(null);
        if (latest == null) {
            return fallback;
        }
        requireWriteAccess(latest.domainId());
        return currentView(tenantId, latest);
    }

    private void requireReadAccess(StoredDimensionDefinition stored, UUID id) {
        if (!domainReadAccess.canRead(stored.domainId())) {
            throw notFound(id);
        }
    }

    private void requireWriteAccess(UUID domainId) {
        if (!domainWriteAccess.canMaintain(domainId)) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_DOMAIN_FORBIDDEN",
                "Business category is not available for dimension maintenance",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private void requireUniqueName(String tenantId, UUID domainId, String name, UUID excludingDefinitionId) {
        if (repository.existsByDomainAndName(tenantId, domainId, name, excludingDefinitionId)) {
            throw duplicateName(domainId, name);
        }
    }

    private static void requireListWindow(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_LIST_WINDOW_INVALID",
                "Dimension definition list offset or limit is outside the supported range",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("offset", offset, "limit", limit, "maxLimit", MAX_LIST_LIMIT)
            );
        }
    }

    private View checksum(View view) {
        String checksum = contentChecksum(view);
        return new View(
            view.id(),
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.abbreviation(),
            view.definition(),
            view.ownerId(),
            view.reuseScope(),
            view.hierarchies(),
            view.status(),
            view.revision(),
            checksum,
            view.usageCount(),
            view.createdAt(),
            view.updatedAt(),
            view.scopeType(),
            view.dataMartId(),
            view.attributes()
        );
    }

    private String contentChecksum(View view) {
        return hash(
            new Content(
                view.id(),
                view.systemCode(),
                view.domainId(),
                view.name(),
                view.abbreviation(),
                view.definition(),
                view.ownerId(),
                view.reuseScope(),
                view.hierarchies(),
                view.status(),
                view.revision(),
                view.createdAt(),
                view.updatedAt(),
                view.scopeType(),
                view.dataMartId(),
                view.attributes()
            )
        );
    }

    private String hash(Object value) {
        try {
            byte[] json = canonicalWriter.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Dimension definition content cannot be hashed", exception);
        }
    }

    private static boolean sameBusinessContent(
        View current,
        UpdateCommand command,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {
        return (
            Objects.equals(current.abbreviation(), command.abbreviation()) &&
            Objects.equals(current.name(), command.name()) &&
            Objects.equals(current.definition(), command.definition()) &&
            Objects.equals(current.ownerId(), command.ownerId()) &&
            current.reuseScope() == command.reuseScope() &&
            Objects.equals(current.hierarchies(), copy(command.hierarchies())) &&
            current.scopeType() == scopeType &&
            Objects.equals(current.dataMartId(), dataMartId) &&
            Objects.equals(current.attributes(), attributes)
        );
    }

    private static boolean matchesRevision(View response, StoredDimensionDefinition revision) {
        return (
            revision.revision() == 1 &&
            revision.status() == Status.DRAFT &&
            Objects.equals(response.id(), revision.id()) &&
            Objects.equals(response.systemCode(), revision.systemCode()) &&
            Objects.equals(response.domainId(), revision.domainId()) &&
            Objects.equals(response.name(), revision.name()) &&
            Objects.equals(response.abbreviation(), revision.abbreviation()) &&
            Objects.equals(response.definition(), revision.definition()) &&
            Objects.equals(response.ownerId(), revision.ownerId()) &&
            response.reuseScope() == revision.reuseScope() &&
            Objects.equals(response.hierarchies(), revision.hierarchies()) &&
            response.scopeType() == revision.scopeType() &&
            Objects.equals(response.dataMartId(), revision.dataMartId()) &&
            Objects.equals(response.attributes(), revision.attributes()) &&
            response.status() == revision.status() &&
            response.revision() == revision.revision() &&
            Objects.equals(response.checksum(), revision.checksum()) &&
            Objects.equals(response.createdAt(), revision.createdAt()) &&
            Objects.equals(response.updatedAt(), revision.updatedAt())
        );
    }

    private static List<HierarchySemantic> copy(List<HierarchySemantic> hierarchies) {
        return hierarchies == null ? List.of() : List.copyOf(hierarchies);
    }

    private static List<AttributeSemantic> copyAttributes(List<AttributeSemantic> attributes) {
        return attributes == null ? List.of() : List.copyOf(attributes);
    }

    private static ScopeType effectiveScope(ScopeType scopeType) {
        return scopeType == null ? ScopeType.DOMAIN : scopeType;
    }

    private void validateDataMartScope(String tenantId, UUID domainId, ScopeType scopeType, UUID dataMartId) {
        if (scopeType == ScopeType.DOMAIN) {
            return;
        }
        if (dataMartId == null || !repository.dataMartContainsDomain(tenantId, dataMartId, domainId)) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_DATA_MART_SCOPE_INVALID",
                "The selected data mart is not active or does not include this business category",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("domainId", domainId, "dataMartId", dataMartId == null ? "" : dataMartId)
            );
        }
    }

    private static void requireExpected(UUID id, View current, ExpectedVersion expected) {
        if (expected == null) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (!Objects.equals(id, expected.id())) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_IF_MATCH_INVALID",
                "If-Match identifies a different dimension definition",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        if (
            current.revision() != expected.revision() ||
            !Objects.equals(current.checksum(), expected.checksum())
        ) {
            throw revisionConflict(current);
        }
    }

    private static void rejectIssues(List<FieldIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        throw new ModelSpecException(
            "DIMENSION_DEFINITION_VALIDATION_FAILED",
            "Dimension definition request contains validation errors",
            ModelSpecException.Kind.UNPROCESSABLE,
            issues
        );
    }

    private static void requireServerContext(String tenantId, String actorId) {
        requireTenant(tenantId);
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_ACTOR_REQUIRED",
                "Authenticated actor is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_SERVER_TENANT_REQUIRED",
                "Server tenant context is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_NOT_FOUND",
            "Dimension definition was not found",
            ModelSpecException.Kind.NOT_FOUND,
            id == null ? Map.of() : Map.of("dimensionDefinitionId", id)
        );
    }

    private static ModelSpecException revisionConflict(View current) {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_REVISION_CONFLICT",
            "Dimension definition was changed by another operation",
            ModelSpecException.Kind.CONFLICT,
            Map.of(
                "currentRevision",
                current.revision(),
                "currentChecksum",
                current.checksum(),
                "currentEtag",
                etag(current)
            )
        );
    }

    private static ModelSpecException replayInvalid() {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_IDEMPOTENCY_SNAPSHOT_INVALID",
            "The original dimension definition create response is inconsistent",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private static ModelSpecException persistenceConflict(
        DataIntegrityViolationException exception,
        UUID domainId,
        String name
    ) {
        if (rootMessage(exception).contains("idempotency key")) {
            return new ModelSpecException(
                "DIMENSION_DEFINITION_IDEMPOTENCY_CONFLICT",
                "The idempotency key was already used for different dimension definition content",
                ModelSpecException.Kind.CONFLICT
            );
        }
        if (rootMessage(exception).contains("uk_dimension_definition_tenant_domain_name_ci")) {
            return duplicateName(domainId, name);
        }
        return new ModelSpecException(
            "DIMENSION_DEFINITION_PERSISTENCE_CONFLICT",
            "Dimension definition could not be persisted because its references or uniqueness changed",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private static ModelSpecException duplicateName(UUID domainId, String name) {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_NAME_CONFLICT",
            "A dimension definition with the same name already exists in this business category",
            ModelSpecException.Kind.CONFLICT,
            Map.of("domainId", domainId, "name", name)
        );
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private Instant databaseInstant() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String systemCode(UUID id) {
        return "dim_" + id.toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static String etag(View view) {
        return (
            "\"dimension-definition:" +
            view.id() +
            ":" +
            view.revision() +
            ":" +
            view.checksum() +
            "\""
        );
    }

    public record CreateResult(View dimensionDefinition, boolean replayed) {}

    public record ExpectedVersion(UUID id, int revision, String checksum) {}

    private record Content(
        UUID id,
        String systemCode,
        UUID domainId,
        String name,
        String abbreviation,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        Status status,
        int revision,
        Instant createdAt,
        Instant updatedAt,
        ScopeType scopeType,
        UUID dataMartId,
        List<AttributeSemantic> attributes
    ) {}
}
