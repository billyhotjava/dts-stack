package com.yuzhi.dts.platform.service.rollback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.CompletionEvent;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.DispatchRecord;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.Receipt;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.Target;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.RollbackCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class RollbackInvalidationService {

    public static final String PREPARED = "PREPARED";
    public static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
    public static final String APPLIED = "APPLIED";
    public static final String ABORTED = "ABORTED";
    public static final String RESTORED = "RESTORED";

    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_DOWNSTREAM_REFERENCE_LENGTH = 256;
    private static final int MAX_TARGET_TABLES = 500;
    private static final int MAX_DISPATCH_ATTEMPTS = 20;
    // The rollback HTTP client may wait for up to 180 seconds. Keep the lease longer so a
    // healthy in-flight delivery is not reclaimed by another platform instance.
    private static final Duration DISPATCH_CLAIM_TTL = Duration.ofMinutes(5);
    private static final Duration DISPATCH_RETRY_DELAY = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RollbackInvalidationRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RollbackInvalidationService(
        RollbackInvalidationRepository repository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this(repository, auditService, objectMapper, Clock.systemUTC());
    }

    RollbackInvalidationService(
        RollbackInvalidationRepository repository,
        AuditService auditService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PreparedInvalidation prepare(PrepareCommand command) {
        requirePrepareCommand(command);
        String actor = normalizeActor(command.actor());
        String idempotencyKey = sha256("rollback-confirmation\u0000" + command.idempotencyToken().trim());
        String payloadHash = sha256(
            lengthPrefixedEvidence(
                "rollback-prepare-v1",
                command.plan().canonicalFingerprintSource(),
                command.sourceDataSourceId().toString(),
                actor
            )
        );
        OptionalReceipt existing = existingByIdempotency(idempotencyKey, payloadHash);
        if (existing.value() != null) {
            Receipt current = requireReceipt(existing.value().id());
            requireSameHash(current.payloadHash(), payloadHash, "Rollback prepare idempotency key");
            int targetCount = repository.findTargets(current.id()).size();
            ensureDispatch(current, command.plan(), targetCount, clock.instant());
            return toPrepared(current, targetCount, true);
        }

        List<Target> targets = repository.discoverTargets(command.sourceDataSourceId(), command.plan().tables());
        long previousSequence = targets
            .stream()
            .mapToLong(target -> target.previousAvailability().sourceSequence())
            .max()
            .orElse(0L);
        long sourceSequence = nextSourceSequenceAfter(previousSequence);
        UUID receiptId = UUID.randomUUID();
        Receipt receipt = new Receipt(
            receiptId,
            idempotencyKey,
            payloadHash,
            PREPARED,
            command.plan().level(),
            command.plan().scope(),
            command.plan().taskId(),
            command.sourceDataSourceId(),
            sourceSequence,
            actor,
            null,
            null,
            null
        );
        Instant now = clock.instant();
        if (!repository.insertReceipt(receipt, now)) {
            Receipt concurrent = repository
                .findReceiptByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Concurrent rollback receipt disappeared"));
            Receipt current = requireReceipt(concurrent.id());
            requireSameHash(current.payloadHash(), payloadHash, "Rollback prepare idempotency key");
            int targetCount = repository.findTargets(current.id()).size();
            ensureDispatch(current, command.plan(), targetCount, now);
            return toPrepared(current, targetCount, true);
        }

        String eventId = eventId(receiptId, "prepare");
        String eventHash = sha256(payloadHash + "\u0000" + sourceSequence + "\u0000PREPARE");
        repository.insertTargets(receiptId, targets, now);
        repository.fenceTargets(receiptId, sourceSequence, eventId, eventHash, targets, now);
        ensureDispatch(receipt, command.plan(), targets.size(), now);
        Map<String, Object> payload = eventPayload(receipt, PREPARED, sourceSequence, targets.size(), "ROLLBACK_PREPARED");
        auditService.auditActionStrict(
            "ROLLBACK_INVALIDATION_PREPARE",
            AuditStage.SUCCESS,
            receiptId.toString(),
            payload
        );
        repository.insertPlatformEvent(
            eventId,
            eventHash,
            "RollbackInvalidationPrepared",
            receiptId.toString(),
            "PREPARE",
            PREPARED,
            actor,
            "ROLLBACK_INVALIDATION_PREPARE",
            writeJson(payload),
            now
        );
        return toPrepared(receipt, targets.size(), false);
    }

    public CompletionView complete(CompletionCommand command) {
        requireCompletionCommand(command);
        return completeLocked(requireReceipt(command.receiptId()), command);
    }

    public CompletionView markReconciliationRequired(UUID receiptId, Object downstreamSummary) {
        Receipt receipt = requireReceipt(receiptId);
        if (RECONCILIATION_REQUIRED.equals(receipt.state())) {
            return toCompletion(receipt, true);
        }
        if (!PREPARED.equals(receipt.state())) {
            throw RollbackInvalidationException.conflict(
                "Only a prepared rollback invalidation can enter reconciliation: " + receipt.state()
            );
        }
        Instant now = clock.instant();
        String summaryJson = writeJson(downstreamSummary == null ? Map.of() : downstreamSummary);
        repository.updateReceiptState(
            receipt.id(),
            RECONCILIATION_REQUIRED,
            null,
            null,
            null,
            summaryJson,
            now
        );
        Map<String, Object> payload = eventPayload(
            receipt,
            RECONCILIATION_REQUIRED,
            receipt.sourceSequence(),
            repository.findTargets(receipt.id()).size(),
            "DOWNSTREAM_OUTCOME_AMBIGUOUS"
        );
        String platformEventId = eventId(receipt.id(), "reconciliation");
        String platformEventHash = sha256(summaryJson + "\u0000" + RECONCILIATION_REQUIRED);
        auditService.auditActionStrict(
            "ROLLBACK_INVALIDATION_APPLY",
            AuditStage.FAIL,
            receipt.id().toString(),
            payload
        );
        repository.insertPlatformEvent(
            platformEventId,
            platformEventHash,
            "RollbackInvalidationReconciliationRequired",
            receipt.id().toString(),
            "RECONCILE",
            RECONCILIATION_REQUIRED,
            receipt.actor(),
            "ROLLBACK_INVALIDATION_APPLY",
            writeJson(payload),
            now
        );
        return new CompletionView(
            receipt.id(),
            RECONCILIATION_REQUIRED,
            receipt.sourceSequence(),
            repository.findTargets(receipt.id()).size(),
            false
        );
    }

    private CompletionView completeLocked(Receipt receipt, CompletionCommand command) {
        requireCompletionCommand(command);
        String normalizedEventId = normalizeEventId(command.eventId());
        String reason = normalizeReason(command.reason());
        String completionHash = completionHash(command, normalizedEventId, reason);

        CompletionEvent historical = repository
            .findCompletionEvent(receipt.id(), normalizedEventId)
            .orElse(null);
        if (historical != null) {
            requireSameHash(historical.payloadHash(), completionHash, "Rollback completion event");
            repository.markDispatchCompletedByCallback(receipt.id(), clock.instant());
            return toCompletion(receipt, true);
        }
        if (normalizedEventId.equals(receipt.completionEventId())) {
            requireSameHash(receipt.completionPayloadHash(), completionHash, "Rollback completion event");
            repository.markDispatchCompletedByCallback(receipt.id(), clock.instant());
            return toCompletion(receipt, true);
        }
        validateTransition(receipt, command);
        long expectedSequence;
        try {
            expectedSequence = Math.incrementExact(currentSequence(receipt));
        } catch (ArithmeticException exhausted) {
            throw sequenceExhausted(exhausted);
        }
        if (command.sourceSequence() != expectedSequence) {
            throw RollbackInvalidationException.stale(
                "Rollback completion source sequence must be exactly " + expectedSequence
            );
        }
        repository.advanceSourceSequence(command.sourceSequence());

        List<Target> targets = repository.findTargets(receipt.id());
        Instant now = clock.instant();
        String nextState;
        String actionCode;
        String eventType;
        String action;
        switch (command.outcome()) {
            case APPLY -> {
                repository.applyTargets(
                    receipt,
                    targets,
                    normalizedEventId,
                    completionHash,
                    command.sourceSequence(),
                    reason,
                    now
                );
                nextState = APPLIED;
                actionCode = "ROLLBACK_INVALIDATION_APPLY";
                eventType = "RollbackInvalidationApplied";
                action = "APPLY";
            }
            case ABORT_NO_SIDE_EFFECT -> {
                if (!command.zeroSideEffectsConfirmed()) {
                    throw new RollbackInvalidationException(
                        "ROLLBACK_INVALIDATION_ABORT_NOT_PROVEN",
                        HttpStatus.CONFLICT,
                        "Abort requires an explicit zero-side-effect confirmation"
                    );
                }
                repository.abortTargets(
                    receipt,
                    targets,
                    normalizedEventId,
                    completionHash,
                    command.sourceSequence(),
                    reason,
                    now
                );
                nextState = ABORTED;
                actionCode = "ROLLBACK_INVALIDATION_ABORT";
                eventType = "RollbackInvalidationAborted";
                action = "ABORT";
            }
            case RESTORE -> {
                repository.restoreTargets(
                    receipt,
                    targets,
                    normalizedEventId,
                    completionHash,
                    command.sourceSequence(),
                    reason,
                    now
                );
                nextState = RESTORED;
                actionCode = "SOURCE_AVAILABILITY_RESTORE";
                eventType = "SourceAvailabilityRestored";
                action = "RESTORE";
            }
            default -> throw new IllegalArgumentException("Unsupported rollback invalidation outcome");
        }

        String summaryJson = writeJson(
            Map.of(
                "downstreamReference",
                normalizeOptional(command.downstreamReference()),
                "reason",
                reason
            )
        );
        repository.updateReceiptState(
            receipt.id(),
            nextState,
            normalizedEventId,
            completionHash,
            command.sourceSequence(),
            summaryJson,
            now
        );
        repository.insertCompletionEvent(
            new CompletionEvent(
                receipt.id(),
                normalizedEventId,
                completionHash,
                command.outcome().name(),
                nextState,
                command.sourceSequence(),
                reason,
                command.zeroSideEffectsConfirmed(),
                normalizeOptional(command.downstreamReference()),
                now
            )
        );
        repository.markDispatchCompletedByCallback(receipt.id(), now);
        Receipt completed = new Receipt(
            receipt.id(),
            receipt.idempotencyKey(),
            receipt.payloadHash(),
            nextState,
            receipt.level(),
            receipt.scope(),
            receipt.taskId(),
            receipt.sourceDataSourceId(),
            receipt.sourceSequence(),
            receipt.actor(),
            normalizedEventId,
            completionHash,
            command.sourceSequence()
        );
        Map<String, Object> payload = eventPayload(
            completed,
            nextState,
            command.sourceSequence(),
            targets.size(),
            reason
        );
        auditService.auditActionStrict(actionCode, AuditStage.SUCCESS, receipt.id().toString(), payload);
        repository.insertPlatformEvent(
            eventId(receipt.id(), action.toLowerCase(Locale.ROOT)),
            completionHash,
            eventType,
            receipt.id().toString(),
            action,
            nextState,
            receipt.actor(),
            actionCode,
            writeJson(payload),
            now
        );
        return new CompletionView(receipt.id(), nextState, command.sourceSequence(), targets.size(), false);
    }

    public Optional<DispatchEnvelope> claimDispatch(UUID receiptId) {
        if (receiptId == null) {
            throw new IllegalArgumentException("receiptId is required");
        }
        return decodeClaim(repository.claimDispatch(receiptId, clock.instant(), DISPATCH_CLAIM_TTL));
    }

    public Optional<DispatchEnvelope> claimNextDispatch() {
        return decodeClaim(repository.claimNextDispatch(clock.instant(), DISPATCH_CLAIM_TTL));
    }

    public DispatchView markDispatchSent(UUID receiptId, int claimAttempt) {
        if (receiptId == null || claimAttempt < 1) {
            throw new IllegalArgumentException("Rollback dispatch claim identity is invalid");
        }
        Receipt receipt = requireReceipt(receiptId);
        Instant now = clock.instant();
        if (APPLIED.equals(receipt.state()) || ABORTED.equals(receipt.state()) || RESTORED.equals(receipt.state())) {
            repository.markDispatchCompletedByCallback(receiptId, now);
            return dispatchView(receiptId);
        }
        DispatchRecord dispatch = requireActiveDispatchClaim(receiptId, claimAttempt);
        if (dispatch.generationAttempts() >= MAX_DISPATCH_ATTEMPTS) {
            markReconciliationRequired(
                receiptId,
                Map.of(
                    "completionCallbackMissingAfterGenerationAttempts",
                    dispatch.generationAttempts(),
                    "cumulativeDispatchAttempts",
                    dispatch.attempts()
                )
            );
            repository.markDispatchDead(
                receiptId,
                claimAttempt,
                "ROLLBACK_COMPLETION_CALLBACK_NOT_OBSERVED",
                now
            );
            return dispatchView(receiptId);
        }
        repository.markDispatchSent(
            receiptId,
            claimAttempt,
            now.plus(DISPATCH_RETRY_DELAY),
            now
        );
        return dispatchView(receiptId);
    }

    public DispatchView recordDispatchFailure(
        UUID receiptId,
        int claimAttempt,
        String error,
        Object downstreamSummary
    ) {
        if (receiptId == null || claimAttempt < 1) {
            throw new IllegalArgumentException("Rollback dispatch claim identity is invalid");
        }
        Receipt receipt = requireReceipt(receiptId);
        if (APPLIED.equals(receipt.state()) || ABORTED.equals(receipt.state()) || RESTORED.equals(receipt.state())) {
            repository.markDispatchCompletedByCallback(receiptId, clock.instant());
            return dispatchView(receiptId);
        }
        DispatchRecord dispatch = requireActiveDispatchClaim(receiptId, claimAttempt);
        markReconciliationRequired(receiptId, downstreamSummary);
        Instant now = clock.instant();
        if (dispatch.generationAttempts() >= MAX_DISPATCH_ATTEMPTS) {
            repository.markDispatchDead(receiptId, claimAttempt, normalizeDispatchError(error), now);
        } else {
            repository.markDispatchRetry(
                receiptId,
                claimAttempt,
                normalizeDispatchError(error),
                now.plus(dispatchRetryDelay(dispatch.generationAttempts())),
                now
            );
        }
        return dispatchView(receiptId);
    }

    public ReceiptView receipt(UUID receiptId) {
        Receipt receipt = requireReceipt(receiptId);
        DispatchView dispatch = dispatchView(receiptId);
        return new ReceiptView(
            receipt.id(),
            receipt.state(),
            receipt.sourceDataSourceId(),
            currentSequence(receipt),
            repository.findTargets(receipt.id()).size(),
            dispatch
        );
    }

    public ReceiptView replayDispatch(UUID receiptId) {
        Receipt receipt = requireReceipt(receiptId);
        if (!PREPARED.equals(receipt.state()) && !RECONCILIATION_REQUIRED.equals(receipt.state())) {
            throw RollbackInvalidationException.conflict(
                "Terminal rollback invalidation receipts cannot replay dispatch: " + receipt.state()
            );
        }
        repository.replayDispatch(receiptId, clock.instant());
        return receipt(receiptId);
    }

    private Optional<DispatchEnvelope> decodeClaim(Optional<DispatchRecord> claimed) {
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        DispatchRecord dispatch = claimed.orElseThrow();
        Receipt receipt = repository
            .findReceipt(dispatch.receiptId())
            .orElseThrow(() -> new IllegalStateException("Rollback dispatch receipt is missing"));
        try {
            if (!dispatch.commandHash().equals(sha256(dispatch.commandJson()))) {
                throw new IllegalArgumentException("command hash mismatch");
            }
            Map<String, Object> command = objectMapper.readValue(dispatch.commandJson(), MAP_TYPE);
            requireDispatchIdentity(command, receipt);
            return Optional.of(
                new DispatchEnvelope(
                    dispatch.receiptId(),
                    dispatch.attempts(),
                    dispatch.commandHash(),
                    Map.copyOf(command)
                )
            );
        } catch (Exception malformed) {
            markReconciliationRequired(
                receipt.id(),
                Map.of("dispatchIntegrityFailure", malformed.getMessage() == null ? "unknown" : malformed.getMessage())
            );
            repository.markDispatchDead(
                receipt.id(),
                dispatch.attempts(),
                "ROLLBACK_DISPATCH_INTEGRITY_FAILED",
                clock.instant()
            );
            return Optional.empty();
        }
    }

    private void requireDispatchIdentity(Map<String, Object> command, Receipt receipt) {
        if (
            !receipt.id().toString().equals(String.valueOf(command.get("rollbackId"))) ||
            !("platform:" + receipt.id()).equals(String.valueOf(command.get("idempotencyKey"))) ||
            !receipt.payloadHash().equals(String.valueOf(command.get("requestHash")))
        ) {
            throw new IllegalArgumentException("command identity mismatch");
        }
        if (!(command.get("availabilityFence") instanceof Map<?, ?> fence)) {
            throw new IllegalArgumentException("availability fence is missing");
        }
        Object sequence = fence.get("sourceSequence");
        if (
            !receipt.id().toString().equals(String.valueOf(fence.get("receiptId"))) ||
            !receipt.sourceDataSourceId().toString().equals(String.valueOf(fence.get("sourceDataSourceId"))) ||
            !(sequence instanceof Number number) ||
            number.longValue() != receipt.sourceSequence()
        ) {
            throw new IllegalArgumentException("availability fence identity mismatch");
        }
    }

    private DispatchView dispatchView(UUID receiptId) {
        DispatchRecord dispatch = repository
            .findDispatch(receiptId)
            .orElseThrow(() -> new IllegalStateException("Rollback receipt has no durable dispatch"));
        return new DispatchView(
            dispatch.receiptId(),
            dispatch.status(),
            dispatch.attempts(),
            dispatch.nextAttemptAt(),
            dispatch.sentAt(),
            dispatch.completedAt(),
            dispatch.lastError()
        );
    }

    private DispatchRecord requireActiveDispatchClaim(UUID receiptId, int claimAttempt) {
        DispatchRecord dispatch = repository
            .findDispatch(receiptId)
            .orElseThrow(() -> new IllegalStateException("Rollback receipt has no durable dispatch"));
        if (!"CLAIMED".equals(dispatch.status()) || dispatch.attempts() != claimAttempt) {
            throw RollbackInvalidationException.stale("Rollback dispatch claim is stale");
        }
        return dispatch;
    }

    private Duration dispatchRetryDelay(int generationAttempts) {
        int exponent = Math.max(0, Math.min(generationAttempts - 1, 4));
        return DISPATCH_RETRY_DELAY.multipliedBy(1L << exponent);
    }

    private String normalizeDispatchError(String error) {
        String value = normalizeOptional(error);
        return value.isEmpty() ? "ROLLBACK_DISPATCH_FAILED" : value;
    }

    private void validateTransition(Receipt receipt, CompletionCommand command) {
        boolean prepared = PREPARED.equals(receipt.state()) || RECONCILIATION_REQUIRED.equals(receipt.state());
        boolean allowed = switch (command.outcome()) {
            case APPLY, ABORT_NO_SIDE_EFFECT -> prepared;
            case RESTORE -> APPLIED.equals(receipt.state());
        };
        if (!allowed) {
            throw RollbackInvalidationException.conflict(
                "Rollback invalidation transition is not allowed: " + receipt.state() + " -> " + command.outcome()
            );
        }
    }

    private OptionalReceipt existingByIdempotency(String idempotencyKey, String payloadHash) {
        Receipt existing = repository.findReceiptByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            requireSameHash(existing.payloadHash(), payloadHash, "Rollback prepare idempotency key");
        }
        return new OptionalReceipt(existing);
    }

    private Receipt requireReceipt(UUID receiptId) {
        if (receiptId == null) {
            throw new IllegalArgumentException("receiptId is required");
        }
        return repository
            .findReceiptForUpdate(receiptId)
            .orElseThrow(
                () ->
                    new RollbackInvalidationException(
                        "ROLLBACK_INVALIDATION_RECEIPT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Rollback invalidation receipt was not found"
                    )
            );
    }

    private void requirePrepareCommand(PrepareCommand command) {
        if (
            command == null ||
            command.plan() == null ||
            command.sourceDataSourceId() == null ||
            !StringUtils.hasText(command.idempotencyToken())
        ) {
            throw new IllegalArgumentException("Rollback invalidation prepare command is incomplete");
        }
        if (command.plan().level() < 1 || command.plan().level() > 3) {
            throw new IllegalArgumentException("Rollback invalidation supports levels 1 through 3");
        }
        RollbackCommand plan = command.plan();
        if (plan.dryRun()) {
            throw new IllegalArgumentException("Rollback invalidation prepare does not accept dry-run plans");
        }
        if (
            plan.tables() == null ||
            plan.tables().size() > MAX_TARGET_TABLES ||
            plan.tables().stream().anyMatch(table -> !StringUtils.hasText(table))
        ) {
            throw new IllegalArgumentException("Rollback invalidation target tables are invalid");
        }
        if (plan.level() > 1 && !plan.tables().isEmpty()) {
            throw new IllegalArgumentException("Only level-one rollback supports table subsets");
        }
        boolean taskScope = "task".equals(plan.scope());
        boolean dataSourceScope = "datasource".equals(plan.scope());
        if (!taskScope && !dataSourceScope) {
            throw new IllegalArgumentException("Rollback invalidation scope is invalid");
        }
        if (
            taskScope &&
            (plan.taskId() == null || plan.taskId() < 1L || plan.dataSourceId() != null)
        ) {
            throw new IllegalArgumentException("Task rollback invalidation identity is invalid");
        }
        if (
            dataSourceScope &&
            (plan.dataSourceId() == null ||
                plan.taskId() != null ||
                !plan.dataSourceId().equals(command.sourceDataSourceId()))
        ) {
            throw new IllegalArgumentException("Data-source rollback invalidation identity is invalid");
        }
    }

    private void requireCompletionCommand(CompletionCommand command) {
        if (command == null || command.receiptId() == null || command.outcome() == null) {
            throw new IllegalArgumentException("Rollback invalidation completion command is incomplete");
        }
        normalizeEventId(command.eventId());
        if (command.sourceSequence() < 1L || command.sourceSequence() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("sourceSequence must be positive and below the maximum bigint value");
        }
        if (command.outcome() != CompletionOutcome.ABORT_NO_SIDE_EFFECT && command.zeroSideEffectsConfirmed()) {
            throw new IllegalArgumentException("zeroSideEffectsConfirmed is only valid for abort completions");
        }
        if (normalizeOptional(command.downstreamReference()).length() > MAX_DOWNSTREAM_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("downstreamReference is too long");
        }
    }

    private long nextSourceSequenceAfter(long previousSequence) {
        long nextAfterPrevious;
        try {
            nextAfterPrevious = Math.incrementExact(previousSequence);
        } catch (ArithmeticException exhausted) {
            throw sequenceExhausted(exhausted);
        }
        long allocated = repository.nextSourceSequence();
        long next = Math.max(allocated, nextAfterPrevious);
        if (next == Long.MAX_VALUE) {
            throw sequenceExhausted(null);
        }
        if (next > allocated) {
            repository.advanceSourceSequence(next);
        }
        return next;
    }

    private RollbackInvalidationException sequenceExhausted(Throwable cause) {
        RollbackInvalidationException error = new RollbackInvalidationException(
            "ROLLBACK_INVALIDATION_SEQUENCE_EXHAUSTED",
            HttpStatus.CONFLICT,
            "Rollback invalidation source sequence is exhausted"
        );
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private String completionHash(CompletionCommand command, String eventId, String reason) {
        return sha256(
            lengthPrefixedEvidence(
                "rollback-completion-v1",
                command.receiptId().toString(),
                eventId,
                String.valueOf(command.sourceSequence()),
                command.outcome().name(),
                reason,
                String.valueOf(command.zeroSideEffectsConfirmed()),
                normalizeOptional(command.downstreamReference())
            )
        );
    }

    private String lengthPrefixedEvidence(String domain, String... values) {
        StringBuilder canonical = new StringBuilder(domain).append(';');
        for (String value : values) {
            String safeValue = value == null ? "" : value;
            canonical.append(safeValue.length()).append(':').append(safeValue).append(';');
        }
        return canonical.toString();
    }

    private Map<String, Object> eventPayload(
        Receipt receipt,
        String state,
        long sourceSequence,
        int targetCount,
        String reason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptId", receipt.id().toString());
        payload.put("state", state);
        payload.put("rollbackLevel", receipt.level());
        payload.put("rollbackScope", receipt.scope());
        payload.put("sourceDataSourceId", receipt.sourceDataSourceId().toString());
        payload.put("sourceSequence", sourceSequence);
        payload.put("targetCount", targetCount);
        payload.put("reason", reason);
        return Map.copyOf(payload);
    }

    private PreparedInvalidation toPrepared(Receipt receipt, int targetCount, boolean idempotentReplay) {
        return new PreparedInvalidation(
            receipt.id(),
            receipt.state(),
            receipt.sourceDataSourceId(),
            receipt.sourceSequence(),
            targetCount,
            receipt.payloadHash(),
            idempotentReplay
        );
    }

    private void ensureDispatch(Receipt receipt, RollbackCommand plan, int targetCount, Instant now) {
        if (
            !PREPARED.equals(receipt.state()) &&
            !RECONCILIATION_REQUIRED.equals(receipt.state()) &&
            repository.findDispatch(receipt.id()).isEmpty()
        ) {
            throw new IllegalStateException("Terminal rollback receipt is missing its durable dispatch evidence");
        }
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("level", plan.level());
        command.put("scope", plan.scope());
        if (plan.taskId() != null) {
            command.put("taskId", plan.taskId());
        }
        if (plan.dataSourceId() != null) {
            command.put("dataSourceId", plan.dataSourceId().toString());
        }
        if (!plan.tables().isEmpty()) {
            command.put("tables", plan.tables());
        }
        command.put("dryRun", plan.dryRun());
        command.put("rollbackId", receipt.id().toString());
        command.put("idempotencyKey", "platform:" + receipt.id());
        command.put("requestHash", receipt.payloadHash());
        Map<String, Object> fence = new LinkedHashMap<>();
        fence.put("receiptId", receipt.id().toString());
        fence.put("sourceDataSourceId", receipt.sourceDataSourceId().toString());
        fence.put("sourceSequence", receipt.sourceSequence());
        fence.put("state", PREPARED);
        fence.put("targetCount", targetCount);
        command.put("availabilityFence", fence);
        String commandJson = writeJson(command);
        repository.insertDispatch(receipt.id(), sha256(commandJson), commandJson, now);
    }

    private CompletionView toCompletion(Receipt receipt, boolean idempotentReplay) {
        return new CompletionView(
            receipt.id(),
            receipt.state(),
            currentSequence(receipt),
            repository.findTargets(receipt.id()).size(),
            idempotentReplay
        );
    }

    private long currentSequence(Receipt receipt) {
        return receipt.completionSourceSequence() == null
            ? receipt.sourceSequence()
            : Math.max(receipt.sourceSequence(), receipt.completionSourceSequence());
    }

    private String eventId(UUID receiptId, String phase) {
        return "rollback-invalidation:" + receiptId + ":" + phase;
    }

    private String normalizeEventId(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("eventId is required");
        }
        String value = eventId.trim();
        if (value.length() > MAX_EVENT_ID_LENGTH || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/@+-]*")) {
            throw new IllegalArgumentException("eventId is invalid");
        }
        return value;
    }

    private String normalizeActor(String actor) {
        String value = normalizeOptional(actor);
        return value.isEmpty() ? "system" : value.substring(0, Math.min(value.length(), 128));
    }

    private String normalizeReason(String reason) {
        String value = normalizeOptional(reason);
        if (value.isEmpty()) {
            return "UNSPECIFIED";
        }
        return value.substring(0, Math.min(value.length(), MAX_REASON_LENGTH));
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireSameHash(String existingHash, String requestedHash, String identity) {
        if (!requestedHash.equals(existingHash)) {
            throw RollbackInvalidationException.conflict(identity + " already exists with a different payload");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Rollback invalidation payload serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record PrepareCommand(
        RollbackCommand plan,
        UUID sourceDataSourceId,
        String actor,
        String idempotencyToken
    ) {}

    public record PreparedInvalidation(
        UUID receiptId,
        String state,
        UUID sourceDataSourceId,
        long sourceSequence,
        int targetCount,
        String requestHash,
        boolean idempotentReplay
    ) {}

    public enum CompletionOutcome {
        APPLY,
        ABORT_NO_SIDE_EFFECT,
        RESTORE,
    }

    public record CompletionCommand(
        UUID receiptId,
        String eventId,
        long sourceSequence,
        CompletionOutcome outcome,
        String reason,
        boolean zeroSideEffectsConfirmed,
        String downstreamReference
    ) {}

    public record CompletionView(
        UUID receiptId,
        String state,
        long sourceSequence,
        int targetCount,
        boolean idempotentReplay
    ) {}

    public record DispatchEnvelope(
        UUID receiptId,
        int claimAttempt,
        String commandHash,
        Map<String, Object> command
    ) {}

    public record DispatchView(
        UUID receiptId,
        String status,
        int attempts,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant completedAt,
        String lastError
    ) {}

    public record ReceiptView(
        UUID receiptId,
        String state,
        UUID sourceDataSourceId,
        long sourceSequence,
        int targetCount,
        DispatchView dispatch
    ) {}

    private record OptionalReceipt(Receipt value) {}
}
