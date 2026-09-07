package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository.IdempotencyCollisionException;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CurrentModelReference;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.VersionConflictView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate-scoped command service.
 *
 * <p>All state writes use expectedVersion, update header and entry status together, and append one immutable command
 * receipt. Tenant-scoped idempotency returns the original response for the same payload and fails closed for a
 * different payload or candidate.
 */
@Service
public class ModelReleaseCandidateService {

    private static final String NOT_FOUND = "MODEL_RELEASE_CANDIDATE_NOT_FOUND";
    private static final String SCOPE_STALE = "MODEL_RELEASE_CANDIDATE_SCOPE_STALE";
    private static final String WRITE_CONFLICT = "MODEL_RELEASE_CANDIDATE_WRITE_CONFLICT";
    private static final String REPLACEMENT_NOT_ALLOWED = "MODEL_RELEASE_CANDIDATE_REPLACEMENT_NOT_ALLOWED";
    private static final String AUDIT_CREATE = "MODEL_RELEASE_CANDIDATE_CREATE";
    private static final String AUDIT_REPLACEMENT_CREATE = "MODEL_RELEASE_CANDIDATE_REPLACEMENT_CREATE";
    private static final String AUDIT_SCOPE_REPLACE = "MODEL_RELEASE_CANDIDATE_SCOPE_REPLACE";
    private static final String AUDIT_STATUS_CHANGE = "MODEL_RELEASE_CANDIDATE_STATUS_CHANGE";

    private final ModelReleaseCandidateRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;
    private final ModelClassificationPublishGate classificationGate;
    private final ModelReleaseCandidateRetryDriftGate retryDriftGate;
    private final AuditService auditService;

    @Autowired
    public ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        ModelClassificationPublishGate classificationGate,
        ModelReleaseCandidateRetryDriftGate retryDriftGate,
        AuditService auditService
    ) {
        this(
            repository,
            objectMapper,
            Clock.systemUTC(),
            UUID::randomUUID,
            classificationGate,
            retryDriftGate,
            auditService
        );
    }

    ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this(
            repository,
            objectMapper,
            clock,
            idGenerator,
            null,
            candidate -> List.of(),
            null
        );
    }

    ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        Supplier<UUID> idGenerator,
        ModelClassificationPublishGate classificationGate
    ) {
        this(
            repository,
            objectMapper,
            clock,
            idGenerator,
            classificationGate,
            candidate -> List.of(),
            null
        );
    }

    ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        Supplier<UUID> idGenerator,
        ModelClassificationPublishGate classificationGate,
        ModelReleaseCandidateRetryDriftGate retryDriftGate
    ) {
        this(
            repository,
            objectMapper,
            clock,
            idGenerator,
            classificationGate,
            retryDriftGate,
            null
        );
    }

    ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        Supplier<UUID> idGenerator,
        ModelClassificationPublishGate classificationGate,
        ModelReleaseCandidateRetryDriftGate retryDriftGate,
        AuditService auditService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator is required");
        this.classificationGate = classificationGate;
        this.retryDriftGate = Objects.requireNonNull(
            retryDriftGate,
            "retryDriftGate is required"
        );
        this.auditService = auditService;
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
    }

    @Transactional
    public CommandResult create(String tenantId, String actorId, CreateCandidateCommand command) {
        return createWithOrigin(
            tenantId,
            actorId,
            command,
            CandidateOrigin.BATCH_WORKBENCH,
            AUDIT_CREATE
        );
    }

    /**
     * Persists a server-expanded dependency closure while retaining the user root request as the
     * idempotency identity. A replay therefore returns the original candidate even if the live DAG
     * has changed since the first response.
     */
    @Transactional
    public CommandResult createBatchWithExpandedScope(
        String tenantId,
        String actorId,
        CreateCandidateCommand rootCommand,
        List<ScopeEntryCommand> expandedEntries
    ) {
        if (rootCommand == null) throw invalid("create command is required");
        if (rootCommand.entries().size() > ModelReleaseCandidateContract.MAX_ROOT_ENTRIES) {
            throw invalid("batch root scope exceeds maximum");
        }
        CreateCandidateCommand expanded = new CreateCandidateCommand(
            rootCommand.planId(),
            rootCommand.environment(),
            expandedEntries,
            rootCommand.idempotencyKey(),
            rootCommand.reason()
        );
        return createWithOrigin(
            tenantId,
            actorId,
            expanded,
            CandidateOrigin.BATCH_WORKBENCH,
            AUDIT_CREATE,
            hash(rootCommand)
        );
    }

    @Transactional
    public CommandResult createSingleModelIntent(
        String tenantId,
        String actorId,
        CreateCandidateCommand command
    ) {
        return createWithOrigin(
            tenantId,
            actorId,
            command,
            CandidateOrigin.SINGLE_MODEL_INTENT,
            AUDIT_CREATE
        );
    }

    @Transactional
    public CommandResult createSchemaOnlyIntent(String tenantId, String actorId, CreateCandidateCommand command) {
        if (command == null || command.entries().size() != 1) throw invalid("Structure candidate requires one model");
        return createWithOrigin(tenantId, actorId, command, CandidateOrigin.SCHEMA_ONLY_INTENT, AUDIT_CREATE);
    }

    private CommandResult createWithOrigin(
        String tenantId,
        String actorId,
        CreateCandidateCommand command,
        CandidateOrigin origin,
        String auditActionCode
    ) {
        return createWithOrigin(tenantId, actorId, command, origin, auditActionCode, null);
    }

    private CommandResult createWithOrigin(
        String tenantId,
        String actorId,
        CreateCandidateCommand command,
        CandidateOrigin origin,
        String auditActionCode,
        String requestHashOverride
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (command == null) throw invalid("create command is required");
        if (origin == null) throw invalid("candidate origin is required");
        if (origin == CandidateOrigin.SCHEMA_ONLY_INTENT && command.entries().size() != 1) {
            throw invalid("Structure candidate requires one model");
        }
        String requestHash = requestHashOverride != null
            ? requestHashOverride
            : origin == CandidateOrigin.BATCH_WORKBENCH
                ? hash(command)
                : hash(Map.of("command", command, "origin", origin.name()));
        CommandResult eventReplay = replayCommand(tenant, null, command.idempotencyKey(), requestHash, actor);
        if (eventReplay != null) return requireOrigin(eventReplay, origin, command.idempotencyKey());

        CandidateView headerReplay = repository.findByIdempotencyKey(tenant, command.idempotencyKey()).orElse(null);
        if (headerReplay != null) {
            return requireOrigin(
                replayHeader(headerReplay, requestHash, actor),
                origin,
                command.idempotencyKey()
            );
        }
        repository.lockPlanForCandidate(tenant, command.planId());
        repository.listActiveForPlan(tenant, command.planId()).stream()
            .filter(existing -> ModelCandidateScopePolicy.conflicts(existing, origin, command.environment(),
                command.entries().stream().map(ScopeEntryCommand::modelSpecId).toList()))
            .findFirst().ifPresent(existing -> {
                throw new ModelReleaseCandidateException("MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS",
                    "An active candidate already reserves the requested scope", Kind.CONFLICT,
                    Map.of("candidateId", existing.id(), "planId", command.planId()));
            });
        Map<UUID, CurrentModelReference> currentReferences = resolveCurrentScope(
            tenant,
            command.planId(),
            command.entries()
        );

        UUID candidateId = idGenerator.get();
        Instant now = clock.instant();
        DeliveryAuditView audit = new DeliveryAuditView(actor, now, null, null, null, null, null, null);
        List<EntryView> entries = entries(
            tenant,
            candidateId,
            command.planId(),
            command.entries(),
            currentReferences,
            DeliveryStatus.DRAFT
        );
        CandidateView created = new CandidateView(
            candidateId,
            tenant,
            command.planId(),
            command.environment(),
            DeliveryStatus.DRAFT,
            1,
            command.idempotencyKey(),
            requestHash,
            audit,
            actor,
            now,
            entries,
            origin
        );
        CommandResult result = result(created, false, List.of(), actor);
        CommandEventView event = event(
            tenant,
            created,
            CommandEventType.CREATED,
            null,
            actor,
            now,
            command.reason(),
            command.idempotencyKey(),
            requestHash,
            result
        );

        if (repository.insert(created) == 0) {
            CommandResult concurrentReplay = replayCommand(
                tenant,
                null,
                command.idempotencyKey(),
                requestHash,
                actor
            );
            if (concurrentReplay != null) {
                return requireOrigin(
                    concurrentReplay,
                    origin,
                    command.idempotencyKey()
                );
            }
            CandidateView concurrent = repository
                .findByIdempotencyKey(tenant, command.idempotencyKey())
                .orElseThrow(() -> conflict("Concurrent candidate creation did not converge", null));
            return requireOrigin(
                replayHeader(concurrent, requestHash, actor),
                origin,
                command.idempotencyKey()
            );
        }
        if (repository.appendCommand(event) == 0) {
            throw idempotencyConflict(command.idempotencyKey(), null);
        }
        auditSuccess(
            actor,
            auditActionCode,
            created,
            Map.of(
                "tenantId",
                tenant,
                "planId",
                created.planId(),
                "environment",
                created.environment(),
                "origin",
                created.origin().name(),
                "version",
                created.version(),
                "entryCount",
                created.entries().size()
            )
        );
        return result;
    }

    @Transactional
    public CommandResult createReplacement(
        String tenantId,
        String actorId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command
    ) {
        return createReplacement(
            tenantId,
            actorId,
            sourceCandidateId,
            expectedVersion,
            command,
            command == null ? List.of() : command.entries(),
            null
        );
    }

    /**
     * Creates a replacement from the server-owned dependency expansion while retaining the user
     * root request as the idempotency identity.
     */
    @Transactional
    public CommandResult createReplacementWithExpandedScope(
        String tenantId,
        String actorId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand rootCommand,
        List<ScopeEntryCommand> expandedEntries
    ) {
        if (rootCommand == null) throw invalid("create command is required");
        if (rootCommand.entries().size() > ModelReleaseCandidateContract.MAX_ROOT_ENTRIES) {
            throw invalid("batch root scope exceeds maximum");
        }
        return createReplacement(
            tenantId,
            actorId,
            sourceCandidateId,
            expectedVersion,
            rootCommand,
            expandedEntries,
            hash(rootCommand)
        );
    }

    private CommandResult createReplacement(
        String tenantId,
        String actorId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand rootCommand,
        List<ScopeEntryCommand> expandedEntries,
        String requestHashOverride
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (sourceCandidateId == null) throw notFound(null);
        if (expectedVersion < 1) throw invalid("expectedVersion must be positive");
        if (rootCommand == null) throw invalid("create command is required");
        CandidateView source = repository.find(tenant, sourceCandidateId).orElseThrow(() -> notFound(sourceCandidateId));
        requireExpectedVersion(source, expectedVersion);
        if (!isReplacementSource(source.status())) {
            throw new ModelReleaseCandidateException(
                REPLACEMENT_NOT_ALLOWED,
                "Only rejected, stale, cancelled or rolled-back candidates can be replaced",
                Kind.CONFLICT,
                Map.of("candidateId", source.id(), "status", source.status())
            );
        }
        if (!source.planId().equals(rootCommand.planId())) {
            throw new ModelReleaseCandidateException(
                REPLACEMENT_NOT_ALLOWED,
                "Replacement candidate must belong to the same plan",
                Kind.CONFLICT,
                Map.of("candidateId", source.id(), "planId", source.planId())
            );
        }
        CreateCandidateCommand replacement = new CreateCandidateCommand(
            rootCommand.planId(),
            rootCommand.environment(),
            expandedEntries,
            rootCommand.idempotencyKey(),
            rootCommand.reason() + " [replaces " + source.id() + "]"
        );
        CommandResult result = createWithOrigin(
            tenant,
            actor,
            replacement,
            source.origin(),
            null,
            requestHashOverride
        );
        if (!result.replayed()) {
            repository.transferActiveClaims(source, result.candidate());
            auditSuccess(
                actor,
                AUDIT_REPLACEMENT_CREATE,
                result.candidate(),
                Map.of(
                    "tenantId",
                    tenant,
                    "planId",
                    result.candidate().planId(),
                    "sourceCandidateId",
                    source.id(),
                    "sourceStatus",
                    source.status().name(),
                    "origin",
                    result.candidate().origin().name(),
                    "version",
                    result.candidate().version(),
                    "entryCount",
                    result.candidate().entries().size()
                )
            );
        }
        return result;
    }

    @Transactional
    public CommandResult replaceScope(
        String tenantId,
        String actorId,
        UUID candidateId,
        ReplaceScopeCommand command
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (candidateId == null) throw notFound(null);
        if (command == null) throw invalid("replace-scope command is required");
        String requestHash = hash(command);
        CommandResult replay = replayCommand(
            tenant,
            candidateId,
            command.idempotencyKey(),
            requestHash,
            actor
        );
        if (replay != null) return replay;
        rejectCreateKeyReuse(tenant, command.idempotencyKey());

        CandidateView current = repository.find(tenant, candidateId).orElseThrow(() -> notFound(candidateId));
        requireExpectedVersion(current, command.expectedVersion());
        if (current.status() != DeliveryStatus.DRAFT) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.SCOPE_LOCKED_ERROR_CODE,
                "Candidate scope is locked after leaving DRAFT",
                Kind.CONFLICT,
                Map.of("candidateId", current.id(), "status", current.status(), "currentVersion", current.version())
            );
        }
        Map<UUID, CurrentModelReference> currentReferences = resolveCurrentScope(
            tenant,
            current.planId(),
            command.entries()
        );

        Instant now = clock.instant();
        List<EntryView> replacementEntries = entries(
            tenant,
            candidateId,
            current.planId(),
            command.entries(),
            currentReferences,
            DeliveryStatus.DRAFT
        );
        CandidateView replacement = candidate(
            current,
            DeliveryStatus.DRAFT,
            current.version() + 1,
            current.audit(),
            actor,
            now,
            replacementEntries
        );
        CommandResult result = result(replacement, false, List.of(), actor);
        CommandEventView event = event(
            tenant,
            replacement,
            CommandEventType.SCOPE_REPLACED,
            DeliveryStatus.DRAFT,
            actor,
            now,
            command.reason(),
            command.idempotencyKey(),
            requestHash,
            result
        );
        int updated;
        try {
            updated = repository.replaceDraftScope(
                current,
                command.expectedVersion(),
                replacementEntries,
                actor,
                now,
                event
            );
        } catch (IdempotencyCollisionException exception) {
            throw idempotencyConflict(command.idempotencyKey(), null);
        }
        if (updated == 0) {
            return convergeAfterCas(
                tenant,
                actor,
                candidateId,
                command.idempotencyKey(),
                requestHash,
                command.expectedVersion()
            );
        }
        auditSuccess(
            actor,
            AUDIT_SCOPE_REPLACE,
            replacement,
            Map.of(
                "tenantId",
                tenant,
                "planId",
                replacement.planId(),
                "version",
                replacement.version(),
                "entryCount",
                replacement.entries().size()
            )
        );
        return result;
    }

    @Transactional
    public CommandResult transition(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command
    ) {
        return transition(
            tenantId,
            actorId,
            candidateId,
            command,
            CommandEventType.STATUS_CHANGED,
            true,
            false,
            null
        );
    }

    /** Commits one quality/release transition together with its immutable evidence references. */
    @Transactional
    public CommandResult transitionWithQualityEvidence(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command,
        CandidateQualityEvidenceSnapshot evidenceSnapshot
    ) {
        if (
            command == null ||
            !Set.of(DeliveryStatus.QUALITY_PASSED, DeliveryStatus.PUBLISHING).contains(command.targetStatus())
        ) {
            throw invalid("quality evidence can only accompany QUALITY_PASSED or PUBLISHING transitions");
        }
        if (evidenceSnapshot == null) throw invalid("quality evidence snapshot is required");
        return transition(
            tenantId,
            actorId,
            candidateId,
            command,
            CommandEventType.STATUS_CHANGED,
            true,
            false,
            evidenceSnapshot
        );
    }

    /** Explicitly supersedes successful physical evidence before creating a replacement build candidate. */
    @Transactional
    public CommandResult supersedeForRematerialization(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command
    ) {
        if (command == null || command.targetStatus() != DeliveryStatus.STALE) {
            throw invalid("rematerialization supersede command must target STALE");
        }
        return transition(
            tenantId,
            actorId,
            candidateId,
            command,
            CommandEventType.STATUS_CHANGED,
            true,
            true,
            null
        );
    }

    /**
     * Performs the final Candidate CAS for a transaction whose orchestrator records the single specialized audit.
     * Callers must emit that audit only after this method returns a non-replayed result.
     */
    @Transactional
    CommandResult transitionWithinAuditedCommit(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command
    ) {
        return transition(
            tenantId,
            actorId,
            candidateId,
            command,
            CommandEventType.STATUS_CHANGED,
            false,
            false,
            null
        );
    }

    @Transactional
    public CommandResult publicationRequested(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command
    ) {
        if (
            command == null ||
            command.targetStatus() != DeliveryStatus.QUALITY_RUNNING
        ) {
            throw invalid(
                "publication request must start the canonical quality transition"
            );
        }
        return transition(
            tenantId,
            actorId,
            candidateId,
            command,
            CommandEventType.PUBLICATION_REQUESTED,
            true,
            false,
            null
        );
    }

    private CommandResult transition(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command,
        CommandEventType requestedEventType,
        boolean auditStatusChange,
        boolean explicitRematerialization,
        CandidateQualityEvidenceSnapshot evidenceSnapshot
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (candidateId == null) throw notFound(null);
        if (command == null) throw invalid("transition command is required");
        String requestHash = hash(command);
        CommandResult replay = replayCommand(
            tenant,
            candidateId,
            command.idempotencyKey(),
            requestHash,
            actor
        );
        if (replay != null) return replay;
        rejectCreateKeyReuse(tenant, command.idempotencyKey());

        CandidateView current = repository.find(tenant, candidateId).orElseThrow(() -> notFound(candidateId));
        requireExpectedVersion(current, command.expectedVersion());
        if (evidenceSnapshot != null && !current.id().equals(evidenceSnapshot.candidateId())) {
            throw invalid("quality evidence snapshot belongs to another candidate");
        }
        if (explicitRematerialization && current.status() != DeliveryStatus.BUILT) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_REMATERIALIZATION_NOT_ALLOWED",
                "Only a successfully built candidate can be explicitly rematerialized",
                Kind.CONFLICT,
                Map.of("candidateId", current.id(), "currentStatus", current.status(), "currentVersion", current.version())
            );
        }
        if (isReplacementSource(current.status())) {
            throw invalidTransition(current, command.targetStatus());
        }
        boolean cancellation = command.targetStatus() == DeliveryStatus.CANCELLED;
        if (current.entries().isEmpty() && !cancellation) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.SCOPE_EMPTY_ERROR_CODE,
                "Select at least one current ModelSpec before locking the candidate scope",
                Kind.UNPROCESSABLE,
                Map.of("candidateId", current.id(), "currentVersion", current.version())
            );
        }

        List<DriftReasonView> driftReasons = cancellation || explicitRematerialization
            ? List.of()
            : scopeDriftEntries(tenant, current.planId(), current.entries(), true);
        if (
            driftReasons.isEmpty() &&
            requiresMaterializationSnapshotValidation(
                current,
                command.targetStatus()
            )
        ) {
            List<DriftReasonView> snapshotDrift = retryDriftGate.detect(current);
            driftReasons = snapshotDrift == null
                ? List.of()
                : List.copyOf(snapshotDrift);
        }
        if (command.targetStatus() == DeliveryStatus.STALE && driftReasons.isEmpty() && !explicitRematerialization) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_DRIFT_REQUIRED",
                "The candidate can be refreshed only after canonical reference drift is confirmed",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    current.id(),
                    "currentStatus",
                    current.status(),
                    "currentVersion",
                    current.version()
                )
            );
        }
        DeliveryStatus target = driftReasons.isEmpty() ? command.targetStatus() : DeliveryStatus.STALE;
        if (!DeliveryStatus.canTransition(current.status(), target)) {
            throw invalidTransition(current, target);
        }
        if (target == DeliveryStatus.PUBLISHING && classificationGate != null) {
            requireClassificationAdmission(tenant, current, command.idempotencyKey());
        }

        Instant now = clock.instant();
        DeliveryAuditView audit = auditForTransition(current.audit(), target, actor, now);
        List<EntryView> transitionedEntries = current
            .entries()
            .stream()
            .map(entry ->
                new EntryView(
                    entry.id(),
                    entry.tenantId(),
                    entry.candidateId(),
                    entry.planId(),
                    entry.modelSpecId(),
                    entry.revision(),
                    entry.checksum(),
                    entry.implementationId(),
                    entry.implementationMode(),
                    target,
                    entry.sortOrder(),
                    entry.selectedReason()
                )
            )
            .toList();
        CandidateView transitioned = candidate(
            current,
            target,
            current.version() + 1,
            audit,
            actor,
            now,
            transitionedEntries
        );
        CommandResult result = result(transitioned, false, driftReasons, actor);
        String reason = driftReasons.isEmpty() ? command.reason() : driftReason(driftReasons);
        CommandEventView event = event(
            tenant,
            transitioned,
            driftReasons.isEmpty()
                ? requestedEventType
                : CommandEventType.STALE_DETECTED,
            current.status(),
            actor,
            now,
            reason,
            command.idempotencyKey(),
            requestHash,
            result,
            target == command.targetStatus() ? evidenceSnapshot : null
        );
        int updated;
        try {
            updated = repository.transitionAndAppend(
                current,
                command.expectedVersion(),
                target,
                audit,
                actor,
                now,
                event
            );
        } catch (IdempotencyCollisionException exception) {
            throw idempotencyConflict(command.idempotencyKey(), null);
        }
        if (updated == 0) {
            return convergeAfterCas(
                tenant,
                actor,
                candidateId,
                command.idempotencyKey(),
                requestHash,
                command.expectedVersion()
            );
        }
        if (auditStatusChange) {
            auditSuccess(
                actor,
                AUDIT_STATUS_CHANGE,
                transitioned,
                Map.of(
                    "tenantId",
                    tenant,
                    "planId",
                    transitioned.planId(),
                    "fromStatus",
                    current.status().name(),
                    "toStatus",
                    transitioned.status().name(),
                    "version",
                    transitioned.version(),
                    "eventType",
                    event.eventType().name(),
                    "driftCount",
                    driftReasons.size()
                )
            );
        }
        return result;
    }

    private void auditSuccess(
        String actor,
        String actionCode,
        CandidateView candidate,
        Map<String, Object> payload
    ) {
        if (auditService == null || actionCode == null) return;
        Map<String, Object> auditPayload = new LinkedHashMap<>(payload);
        auditPayload.put("actor", actor);
        auditService.auditAction(
            actionCode,
            AuditStage.SUCCESS,
            candidate.id().toString(),
            auditPayload
        );
    }

    private void requireClassificationAdmission(String tenantId, CandidateView candidate, String triggerRef) {
        List<Map<String, Object>> blocked = candidate
            .entries()
            .stream()
            .map(entry ->
                classificationGate.admitAndSeal(
                    tenantId,
                    entry.modelSpecId(),
                    entry.revision(),
                    entry.checksum(),
                    triggerRef
                )
            )
            .filter(decision -> !decision.ready())
            .map(decision ->
                Map.<String, Object>of(
                    "modelSpecId",
                    decision.modelSpecId(),
                    "revision",
                    decision.revision(),
                    "blockers",
                    decision.blockers()
                )
            )
            .toList();
        if (!blocked.isEmpty()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CLASSIFICATION_BLOCKED",
                "Release candidate classification evidence is incomplete",
                Kind.UNPROCESSABLE,
                Map.of("candidateId", candidate.id(), "models", blocked)
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DriftReasonView> detectDrift(String tenantId, CandidateView candidate) {
        String tenant = requiredText(tenantId, "tenantId");
        if (candidate == null || !tenant.equals(candidate.tenantId())) {
            throw invalid("candidate must belong to tenant");
        }
        List<DriftReasonView> scopeDrift = scopeDriftEntries(
            tenant,
            candidate.planId(),
            candidate.entries(),
            false
        );
        if (
            !scopeDrift.isEmpty() ||
            !requiresMaterializationSnapshotValidation(candidate, null)
        ) {
            return scopeDrift;
        }
        List<DriftReasonView> snapshotDrift = retryDriftGate.detectForRead(
            candidate
        );
        return snapshotDrift == null
            ? List.of()
            : List.copyOf(snapshotDrift);
    }

    private static boolean requiresMaterializationSnapshotValidation(
        CandidateView candidate,
        DeliveryStatus target
    ) {
        if (
            candidate == null ||
            target == DeliveryStatus.CANCELLED ||
            target == DeliveryStatus.ROLLED_BACK
        ) {
            return false;
        }
        if (candidate.executionTargetKey() != null) {
            return true;
        }
        return switch (candidate.status()) {
            case BUILD_FAILED,
                BUILT,
                QUALITY_RUNNING,
                QUALITY_FAILED,
                QUALITY_PASSED,
                REVIEW_PENDING,
                APPROVED,
                PUBLISHING,
                PARTIAL,
                PUBLISHED -> true;
            default -> false;
        };
    }

    private CommandResult convergeAfterCas(
        String tenantId,
        String actorId,
        UUID candidateId,
        String idempotencyKey,
        String requestHash,
        int expectedVersion
    ) {
        CommandResult replay = replayCommand(
            tenantId,
            candidateId,
            idempotencyKey,
            requestHash,
            actorId
        );
        if (replay != null) return replay;
        CandidateView latest = repository.find(tenantId, candidateId).orElseThrow(() -> notFound(candidateId));
        if (latest.version() != expectedVersion) {
            throw versionConflict(latest, expectedVersion);
        }
        throw new ModelReleaseCandidateException(
            WRITE_CONFLICT,
            "Candidate write did not satisfy the current state and audit preconditions",
            Kind.CONFLICT,
            Map.of("candidateId", candidateId, "currentVersion", latest.version(), "currentStatus", latest.status())
        );
    }

    private CommandResult replayCommand(
        String tenantId,
        UUID expectedCandidateId,
        String idempotencyKey,
        String requestHash,
        String actorId
    ) {
        CommandEventView existing = repository.findCommandByIdempotencyKey(tenantId, idempotencyKey).orElse(null);
        if (existing == null) return null;
        if (
            !existing.requestHash().equals(requestHash) ||
            (expectedCandidateId != null && !existing.candidateId().equals(expectedCandidateId))
        ) {
            throw idempotencyConflict(idempotencyKey, existing.candidateId());
        }
        try {
            CommandResult original = objectMapper
                .readerFor(CommandResult.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(existing.responseSnapshot());
            CandidateView candidate = original.candidate();
            if (
                !existing.tenantId().equals(candidate.tenantId()) ||
                !existing.candidateId().equals(candidate.id()) ||
                !existing.planId().equals(candidate.planId()) ||
                existing.candidateVersion() != candidate.version() ||
                existing.toStatus() != candidate.status()
            ) {
                throw new IllegalStateException(
                    "Stored release-candidate response snapshot does not match its command receipt"
                );
            }
            return result(candidate, true, original.driftReasons(), actorId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored release-candidate response snapshot is invalid", exception);
        }
    }

    private CommandResult replayHeader(CandidateView existing, String requestHash, String actorId) {
        if (!Objects.equals(existing.requestHash(), requestHash)) {
            throw idempotencyConflict(existing.idempotencyKey(), existing.id());
        }
        return result(existing, true, List.of(), actorId);
    }

    private static CommandResult requireOrigin(
        CommandResult result,
        CandidateOrigin expectedOrigin,
        String idempotencyKey
    ) {
        if (result.candidate().origin() != expectedOrigin) {
            throw idempotencyConflict(idempotencyKey, result.candidate().id());
        }
        return result;
    }

    private static CommandResult result(
        CandidateView candidate,
        boolean replayed,
        List<DriftReasonView> driftReasons,
        String actorId
    ) {
        List<DeliveryAction> allowedActions = candidate.entries().isEmpty()
            ? List.of()
            : candidate
                .status()
                .allowedActions()
                .stream()
                .filter(action ->
                    action.isAllowedFor(
                        candidate.status(),
                        DeliveryActorRole.MODEL_MAINTAINER,
                        actorId,
                        candidate.audit()
                    )
                )
                .toList();
        return new CommandResult(candidate, replayed, driftReasons, allowedActions);
    }

    private void rejectCreateKeyReuse(String tenantId, String idempotencyKey) {
        CandidateView existing = repository.findByIdempotencyKey(tenantId, idempotencyKey).orElse(null);
        if (existing != null) throw idempotencyConflict(idempotencyKey, existing.id());
    }

    private Map<UUID, CurrentModelReference> resolveCurrentScope(
        String tenantId,
        UUID planId,
        List<ScopeEntryCommand> entries
    ) {
        if (entries.isEmpty()) return Map.of();
        Map<UUID, CurrentModelReference> current = repository.findCurrentModelReferences(
            tenantId,
            planId,
            entries.stream().map(ScopeEntryCommand::modelSpecId).toList()
        );
        List<Map<String, Object>> missing = entries
            .stream()
            .filter(entry -> !current.containsKey(entry.modelSpecId()))
            .map(entry ->
                Map.<String, Object>of(
                    "modelSpecId",
                    entry.modelSpecId(),
                    "code",
                    "MODEL_RELEASE_CANDIDATE_MODEL_MISSING"
                )
            )
            .toList();
        if (!missing.isEmpty()) {
            throw new ModelReleaseCandidateException(
                SCOPE_STALE,
                "Candidate scope must reference current ModelSpec revisions in the same plan",
                Kind.UNPROCESSABLE,
                missing
            );
        }
        return current;
    }

    private List<DriftReasonView> scopeDriftEntries(
        String tenantId,
        UUID planId,
        List<EntryView> entries,
        boolean lockRows
    ) {
        if (entries.isEmpty()) return List.of();
        List<UUID> modelSpecIds = entries.stream().map(EntryView::modelSpecId).toList();
        Map<UUID, CurrentModelReference> current = lockRows
            ? repository.findCurrentModelReferences(tenantId, planId, modelSpecIds)
            : repository.findCurrentModelReferencesForRead(tenantId, planId, modelSpecIds);
        return entries
            .stream()
            .map(entry ->
                driftReason(
                    entry.modelSpecId(),
                    entry.revision(),
                    entry.checksum(),
                    entry.implementationMode(),
                    current.get(entry.modelSpecId())
                )
            )
            .filter(Objects::nonNull)
            .toList();
    }

    private static DriftReasonView driftReason(
        UUID modelSpecId,
        int lockedRevision,
        String lockedChecksum,
        ModelSpecContract.ImplementationMode lockedMode,
        CurrentModelReference current
    ) {
        if (current == null) {
            return new DriftReasonView(
                modelSpecId,
                lockedRevision,
                lockedChecksum,
                null,
                null,
                "MODEL_RELEASE_CANDIDATE_MODEL_MISSING",
                "ModelSpec is missing, archived or outside the candidate plan"
            );
        }
        if (
            lockedRevision == current.revision() &&
            lockedChecksum.equals(current.checksum()) &&
            lockedMode == current.implementationMode()
        ) {
            return null;
        }
        return new DriftReasonView(
            modelSpecId,
            lockedRevision,
            lockedChecksum,
            current.revision(),
            current.checksum(),
            ModelReleaseCandidateContract.STALE_ERROR_CODE,
            "ModelSpec revision, checksum or implementation mode changed after the candidate scope was selected"
        );
    }

    private List<EntryView> entries(
        String tenantId,
        UUID candidateId,
        UUID planId,
        List<ScopeEntryCommand> commands,
        Map<UUID, CurrentModelReference> currentReferences,
        DeliveryStatus status
    ) {
        return commands
            .stream()
            .map(command -> {
                CurrentModelReference current = currentReferences.get(command.modelSpecId());
                return new EntryView(
                    idGenerator.get(),
                    tenantId,
                    candidateId,
                    planId,
                    command.modelSpecId(),
                    current.revision(),
                    current.checksum(),
                    null,
                    current.implementationMode(),
                    status,
                    command.sortOrder(),
                    command.selectedReason()
                );
            })
            .toList();
    }

    private CommandEventView event(
        String tenantId,
        CandidateView candidate,
        CommandEventType eventType,
        DeliveryStatus fromStatus,
        String actorId,
        Instant occurredAt,
        String reason,
        String idempotencyKey,
        String requestHash,
        CommandResult result
    ) {
        return event(
            tenantId,
            candidate,
            eventType,
            fromStatus,
            actorId,
            occurredAt,
            reason,
            idempotencyKey,
            requestHash,
            result,
            null
        );
    }

    private CommandEventView event(
        String tenantId,
        CandidateView candidate,
        CommandEventType eventType,
        DeliveryStatus fromStatus,
        String actorId,
        Instant occurredAt,
        String reason,
        String idempotencyKey,
        String requestHash,
        CommandResult result,
        CandidateQualityEvidenceSnapshot evidenceSnapshot
    ) {
        return new CommandEventView(
            idGenerator.get(),
            tenantId,
            candidate.id(),
            candidate.planId(),
            candidate.version(),
            eventType,
            fromStatus,
            candidate.status(),
            actorId,
            occurredAt,
            reason,
            idempotencyKey,
            requestHash,
            write(result, evidenceSnapshot)
        );
    }

    private static CandidateView candidate(
        CandidateView current,
        DeliveryStatus status,
        int version,
        DeliveryAuditView audit,
        String actorId,
        Instant occurredAt,
        List<EntryView> entries
    ) {
        return new CandidateView(
            current.id(),
            current.tenantId(),
            current.planId(),
            current.environment(),
            status,
            version,
            current.idempotencyKey(),
            current.requestHash(),
            audit,
            actorId,
            occurredAt,
            entries,
            current.origin(),
            current.executionTargetKey(),
            current.adapter(),
            current.profileKey(),
            current.targetName()
        );
    }

    private static DeliveryAuditView auditForTransition(
        DeliveryAuditView current,
        DeliveryStatus target,
        String actorId,
        Instant occurredAt
    ) {
        String submittedBy = current.submittedBy();
        Instant submittedAt = current.submittedAt();
        String approvedBy = current.approvedBy();
        Instant approvedAt = current.approvedAt();
        String publishedBy = current.publishedBy();
        Instant publishedAt = current.publishedAt();
        boolean reviewedRelease = approvedAt != null;
        if (
            (target == DeliveryStatus.APPROVED ||
                target == DeliveryStatus.REJECTED ||
                (reviewedRelease &&
                    (target == DeliveryStatus.PUBLISHING ||
                        target == DeliveryStatus.PARTIAL ||
                        target == DeliveryStatus.PUBLISHED))) &&
            submittedBy != null &&
            submittedBy.equals(actorId)
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED",
                "Submitter cannot approve, reject or publish the same candidate",
                Kind.FORBIDDEN
            );
        }
        if (
            (target == DeliveryStatus.PUBLISHING ||
                target == DeliveryStatus.PARTIAL ||
                target == DeliveryStatus.PUBLISHED) &&
            reviewedRelease &&
            approvedBy != null &&
            approvedBy.equals(actorId)
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED",
                "Reviewer cannot publish the same candidate",
                Kind.FORBIDDEN
            );
        }
        if (target == DeliveryStatus.REVIEW_PENDING && submittedAt == null) {
            submittedBy = actorId;
            submittedAt = occurredAt;
        }
        if (target == DeliveryStatus.APPROVED && approvedAt == null) {
            approvedBy = actorId;
            approvedAt = occurredAt;
        }
        if ((target == DeliveryStatus.PARTIAL || target == DeliveryStatus.PUBLISHED) && publishedAt == null) {
            publishedBy = actorId;
            publishedAt = occurredAt;
        }
        try {
            return new DeliveryAuditView(
                current.createdBy(),
                current.createdAt(),
                submittedBy,
                submittedAt,
                approvedBy,
                approvedAt,
                publishedBy,
                publishedAt
            );
        } catch (IllegalArgumentException exception) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED",
                exception.getMessage(),
                Kind.FORBIDDEN
            );
        }
    }

    private void requireExpectedVersion(CandidateView current, int expectedVersion) {
        if (current.version() != expectedVersion) throw versionConflict(current, expectedVersion);
    }

    private static ModelReleaseCandidateException versionConflict(CandidateView current, int expectedVersion) {
        return new ModelReleaseCandidateException(
            ModelReleaseCandidateContract.VERSION_CONFLICT_ERROR_CODE,
            "Candidate version changed; refresh or replay the local selection",
            Kind.CONFLICT,
            VersionConflictView.of(current.id(), expectedVersion, current.version(), current.status())
        );
    }

    private static ModelReleaseCandidateException invalidTransition(CandidateView current, DeliveryStatus target) {
        return new ModelReleaseCandidateException(
            ModelReleaseCandidateContract.INVALID_TRANSITION_ERROR_CODE,
            "Candidate status transition is not allowed",
            Kind.UNPROCESSABLE,
            Map.of("candidateId", current.id(), "fromStatus", current.status(), "toStatus", target)
        );
    }

    private static ModelReleaseCandidateException idempotencyConflict(String key, UUID existingCandidateId) {
        return new ModelReleaseCandidateException(
            ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE,
            "Idempotency key already belongs to another request payload",
            Kind.CONFLICT,
            Map.of("idempotencyKey", key)
        );
    }

    private static ModelReleaseCandidateException conflict(String message, Object details) {
        return new ModelReleaseCandidateException(WRITE_CONFLICT, message, Kind.CONFLICT, details);
    }

    private static ModelReleaseCandidateException invalid(String message) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CANDIDATE_COMMAND_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    private static ModelReleaseCandidateException notFound(UUID candidateId) {
        return new ModelReleaseCandidateException(
            NOT_FOUND,
            "Release candidate was not found",
            Kind.NOT_FOUND,
            candidateId == null ? null : Map.of("candidateId", candidateId)
        );
    }

    private static boolean isReplacementSource(DeliveryStatus status) {
        return (
            status == DeliveryStatus.REJECTED ||
            status == DeliveryStatus.STALE ||
            status == DeliveryStatus.ROLLED_BACK ||
            status == DeliveryStatus.CANCELLED
        );
    }

    private static String driftReason(List<DriftReasonView> reasons) {
        List<String> visible = reasons
            .stream()
            .limit(20)
            .map(reason -> reason.modelSpecId() + ":" + reason.code())
            .toList();
        String remaining = reasons.size() > visible.size()
            ? " and " + (reasons.size() - visible.size()) + " more"
            : "";
        return "Candidate became STALE: " + visible + remaining;
    }

    private String write(CommandResult result) {
        return write(result, null);
    }

    private String write(CommandResult result, CandidateQualityEvidenceSnapshot evidenceSnapshot) {
        try {
            if (evidenceSnapshot == null) return canonicalWriter.writeValueAsString(result);
            ObjectNode response = objectMapper.valueToTree(result);
            evidenceSnapshot.appendTo(response, objectMapper);
            return canonicalWriter.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize release-candidate command result", exception);
        }
    }

    private String hash(Object command) {
        try {
            byte[] bytes = canonicalWriter.writeValueAsString(command).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Release-candidate command cannot be serialized", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
        String normalized = value.trim();
        int maximumLength = "tenantId".equals(name) || "actorId".equals(name) ? 128 : Integer.MAX_VALUE;
        if (normalized.length() > maximumLength) throw invalid(name + " exceeds " + maximumLength + " characters");
        return normalized;
    }
}
