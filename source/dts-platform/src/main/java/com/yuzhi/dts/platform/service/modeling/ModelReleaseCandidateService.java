package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository.IdempotencyCollisionException;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final ModelReleaseCandidateRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;
    private final ModelClassificationPublishGate classificationGate;
    private final ModelReleaseCandidateRetryDriftGate retryDriftGate;

    @Autowired
    public ModelReleaseCandidateService(
        ModelReleaseCandidateRepository repository,
        ObjectMapper objectMapper,
        ModelClassificationPublishGate classificationGate,
        ModelReleaseCandidateRetryDriftGate retryDriftGate
    ) {
        this(
            repository,
            objectMapper,
            Clock.systemUTC(),
            UUID::randomUUID,
            classificationGate,
            retryDriftGate
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
            candidate -> List.of()
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
            candidate -> List.of()
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
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator is required");
        this.classificationGate = classificationGate;
        this.retryDriftGate = Objects.requireNonNull(
            retryDriftGate,
            "retryDriftGate is required"
        );
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
            CandidateOrigin.BATCH_WORKBENCH
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
            CandidateOrigin.SINGLE_MODEL_INTENT
        );
    }

    private CommandResult createWithOrigin(
        String tenantId,
        String actorId,
        CreateCandidateCommand command,
        CandidateOrigin origin
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (command == null) throw invalid("create command is required");
        if (origin == null) throw invalid("candidate origin is required");
        String requestHash = origin == CandidateOrigin.BATCH_WORKBENCH
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
        String tenant = requiredText(tenantId, "tenantId");
        if (sourceCandidateId == null) throw notFound(null);
        if (expectedVersion < 1) throw invalid("expectedVersion must be positive");
        if (command == null) throw invalid("create command is required");
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
        if (!source.planId().equals(command.planId())) {
            throw new ModelReleaseCandidateException(
                REPLACEMENT_NOT_ALLOWED,
                "Replacement candidate must belong to the same plan",
                Kind.CONFLICT,
                Map.of("candidateId", source.id(), "planId", source.planId())
            );
        }
        CreateCandidateCommand replacement = new CreateCandidateCommand(
            command.planId(),
            command.environment(),
            command.entries(),
            command.idempotencyKey(),
            command.reason() + " [replaces " + source.id() + "]"
        );
        CommandResult result = createWithOrigin(
            tenant,
            actorId,
            replacement,
            source.origin()
        );
        if (!result.replayed()) {
            repository.transferActiveClaims(source, result.candidate());
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
            CommandEventType.STATUS_CHANGED
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
            CommandEventType.PUBLICATION_REQUESTED
        );
    }

    private CommandResult transition(
        String tenantId,
        String actorId,
        UUID candidateId,
        TransitionCommand command,
        CommandEventType requestedEventType
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

        List<DriftReasonView> driftReasons = cancellation
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
        if (command.targetStatus() == DeliveryStatus.STALE && driftReasons.isEmpty()) {
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
            result
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
        return result;
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
            CommandResult original = objectMapper.readValue(existing.responseSnapshot(), CommandResult.class);
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
            write(result)
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
        if (
            (target == DeliveryStatus.APPROVED ||
                target == DeliveryStatus.REJECTED ||
                target == DeliveryStatus.PUBLISHING ||
                target == DeliveryStatus.PARTIAL ||
                target == DeliveryStatus.PUBLISHED) &&
            submittedBy != null &&
            submittedBy.equals(actorId)
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED",
                "Submitter cannot approve, reject or publish the same candidate",
                Kind.FORBIDDEN
            );
        }
        if (target == DeliveryStatus.PUBLISHING && approvedAt == null) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_APPROVAL_REQUIRED",
                "Candidate must be approved before publishing",
                Kind.UNPROCESSABLE
            );
        }
        if (
            (target == DeliveryStatus.PUBLISHING ||
                target == DeliveryStatus.PARTIAL ||
                target == DeliveryStatus.PUBLISHED) &&
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
        try {
            return canonicalWriter.writeValueAsString(result);
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
