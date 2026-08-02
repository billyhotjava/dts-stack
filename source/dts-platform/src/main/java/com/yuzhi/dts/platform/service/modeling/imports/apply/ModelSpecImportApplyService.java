package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CanonicalRunResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.RetryRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPreflightService.PreparedApply;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationAudit;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredApplyPlan;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates topology-ordered candidate transactions while keeping batch failures isolated. */
@Service
public class ModelSpecImportApplyService {

    static final int MAX_SELECTED_UNIQUE_IDS = 200;
    static final int MAX_UNIQUE_ID_LENGTH = 512;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 256;

    private static final Logger LOG = LoggerFactory.getLogger(ModelSpecImportApplyService.class);

    private final ModelSpecImportPreviewRepository previewRepository;
    private final ModelSpecImportApplyRepository applyRepository;
    private final ModelSpecImportApplyPreflightService preflight;
    private final ModelSpecImportCandidateTransactionWorker candidateWorker;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final ModelSpecImportApplyPayloadCodec payloadCodec;
    private final ObjectMapper objectMapper;
    private final ModelSpecImportReconciliationAudit reconciliationAudit;
    private final String tenantId;

    public ModelSpecImportApplyService(
        ModelSpecImportPreviewRepository previewRepository,
        ModelSpecImportApplyRepository applyRepository,
        ModelSpecImportApplyPreflightService preflight,
        ModelSpecImportCandidateTransactionWorker candidateWorker,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ModelSpecImportApplyPayloadCodec payloadCodec,
        ObjectMapper objectMapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this(
            previewRepository,
            applyRepository,
            preflight,
            candidateWorker,
            actorProvider,
            authorizationGuard,
            payloadCodec,
            objectMapper,
            tenantId,
            null
        );
    }

    @Autowired
    public ModelSpecImportApplyService(
        ModelSpecImportPreviewRepository previewRepository,
        ModelSpecImportApplyRepository applyRepository,
        ModelSpecImportApplyPreflightService preflight,
        ModelSpecImportCandidateTransactionWorker candidateWorker,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ModelSpecImportApplyPayloadCodec payloadCodec,
        ObjectMapper objectMapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId,
        ModelSpecImportReconciliationAudit reconciliationAudit
    ) {
        this.previewRepository = previewRepository;
        this.applyRepository = applyRepository;
        this.preflight = preflight;
        this.candidateWorker = candidateWorker;
        this.actorProvider = actorProvider;
        this.authorizationGuard = authorizationGuard;
        this.payloadCodec = payloadCodec;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
        this.reconciliationAudit = reconciliationAudit;
    }

    public ApplyResponse apply(ApplyRequest request) {
        requireApplyRequest(request);
        WarehousePlanActor actor = requireActor();
        StoredApplyPlan stored = loadAuthorized(request.runId(), actor, true);
        applyRepository.recoverExpiredRunning(tenantId, request.runId(), actor.ownerId());
        List<String> selectedUniqueIds = canonicalSelection(stored, request.selectedUniqueIds());
        Optional<ApplyResponse> replay = replayExisting(
            request.runId(),
            request.previewHash(),
            selectedUniqueIds,
            request.idempotencyKey(),
            request.conflictResolutions(),
            false
        );
        if (replay.isPresent()) {
            return replay.orElseThrow();
        }
        PreparedApply prepared;
        try {
            prepared = preflight.prepare(
                tenantId,
                stored,
                request.previewHash(),
                selectedUniqueIds,
                null,
                Map.of(),
                Map.of(),
                request.conflictResolutions(),
                Instant.now()
            );
        } catch (RuntimeException failure) {
            Optional<ApplyResponse> raced = replayExisting(
                request.runId(),
                request.previewHash(),
                selectedUniqueIds,
                request.idempotencyKey(),
                request.conflictResolutions(),
                false
            );
            if (raced.isPresent()) {
                return raced.orElseThrow();
            }
            throw failure;
        }
        TechnicalAllocation allocation = technicalAllocation(
            stored,
            prepared.selectedClosure(),
            Map.of()
        );
        prepared = withDependencies(prepared, allocation.syntheticDependencies());
        if (reconciliationAudit != null) {
            reconciliationAudit.beginConflictDecisions(request.runId(), request.conflictResolutions());
        }
        return execute(
            prepared,
            actor.ownerId(),
            request.idempotencyKey(),
            null,
            Map.of(),
            allocation,
            request.conflictResolutions()
        );
    }

    public ApplyResponse retry(UUID runId, RetryRequest request) {
        if (runId == null || request == null) {
            throw badRequest("MODEL_IMPORT_RETRY_REQUEST_INVALID", "Retry request is required");
        }
        requirePreviewHash(request.previewHash());
        requireIdempotencyKey(request.idempotencyKey());
        WarehousePlanActor actor = requireActor();
        StoredApplyPlan stored = loadAuthorized(runId, actor, true);
        applyRepository.recoverExpiredRunning(tenantId, runId, actor.ownerId());
        Optional<ApplyResponse> replay = replayExisting(
            runId,
            request.previewHash(),
            List.of(),
            request.idempotencyKey(),
            Map.of(),
            true
        );
        if (replay.isPresent()) {
            return replay.orElseThrow();
        }
        Attempt source = applyRepository
            .findRetrySource(tenantId, runId)
            .orElseThrow(() -> conflict("MODEL_IMPORT_RETRY_NOT_AVAILABLE", "The latest apply attempt has no retryable failures"));
        RetryHistory history = retryHistory(source);
        TechnicalAllocation allocation = technicalAllocation(
            stored,
            history.rootSelectedClosure(),
            history.successfulResults()
        );
        PreparedApply prepared;
        try {
            prepared = preflight.prepare(
                tenantId,
                stored,
                request.previewHash(),
                List.of(),
                source,
                history.successfulResults(),
                allocation.syntheticDependencies(),
                Instant.now()
            );
        } catch (RuntimeException failure) {
            Optional<ApplyResponse> raced = replayExisting(
                runId,
                request.previewHash(),
                List.of(),
                request.idempotencyKey(),
                Map.of(),
                true
            );
            if (raced.isPresent()) {
                return raced.orElseThrow();
            }
            throw failure;
        }
        Map<String, String> candidateKeys = new HashMap<>();
        source.results().forEach(item -> candidateKeys.put(item.dbtUniqueId(), item.candidateIdempotencyKey()));
        return execute(
            prepared,
            actor.ownerId(),
            request.idempotencyKey(),
            source,
            candidateKeys,
            allocation,
            Map.of()
        );
    }

    public ApplyResponse latest(UUID runId) {
        WarehousePlanActor actor = requireActor();
        authorizeRun(runId, actor, false, false);
        applyRepository.recoverExpiredRunning(tenantId, runId, actor.ownerId());
        Attempt attempt = applyRepository
            .findLatest(tenantId, runId)
            .orElseThrow(() ->
                new ModelSpecImportApplyException(
                    "MODEL_IMPORT_APPLY_ATTEMPT_NOT_FOUND",
                    "No apply attempt exists for this preview",
                    Kind.NOT_FOUND,
                    runId
                )
            );
        return response(BeginDisposition.REPLAY, attempt);
    }

    private ApplyResponse execute(
        PreparedApply prepared,
        String actorId,
        String idempotencyKey,
        Attempt retrySource,
        Map<String, String> replayKeys,
        TechnicalAllocation allocation,
        Map<String, ConflictResolution> conflictResolutions
    ) {
        String requestHash = requestHash(
            prepared.stored().runId(),
            prepared.stored().previewHash(),
            prepared.selectedUniqueIds(),
            prepared.selectedClosure(),
            retrySource == null ? null : retrySource.id(),
            conflictResolutions
        );
        BeginResult begin = applyRepository.begin(
            new BeginCommand(
                UUID.randomUUID(),
                prepared.stored().runId(),
                prepared.stored().planId(),
                retrySource == null ? null : retrySource.id(),
                tenantId,
                prepared.stored().previewHash(),
                prepared.selectedUniqueIds(),
                prepared.selectedClosure(),
                idempotencyKey,
                requestHash,
                actorId,
                Instant.now()
            )
        );
        if (begin.disposition() != BeginDisposition.STARTED) {
            return response(begin.disposition(), begin.attempt());
        }
        String ownerToken = begin.ownerToken();
        String diagnosticStage = diagnosticStage(retrySource);

        Map<String, ResultStatus> outcomes = new HashMap<>();
        int sequence = 0;
        for (Candidate candidate : prepared.candidates()) {
            applyRepository.renewLease(
                tenantId,
                prepared.stored().runId(),
                begin.attempt().id(),
                ownerToken
            );
            String candidateKey = replayKeys.getOrDefault(
                candidate.dbtUniqueId(),
                "model-import-candidate:" +
                ModelPackageChecksum.sha256Text(idempotencyKey + "\n" + candidate.dbtUniqueId())
            );
            String candidateHash = payloadCodec.checksum(objectMapper.valueToTree(candidate));
            String failedDependencyUniqueId = failedDependencyUniqueId(
                candidate.dbtUniqueId(),
                prepared.dependencies(),
                outcomes
            );
            CandidateResult result;
            if (failedDependencyUniqueId != null) {
                result = blocked(
                    prepared.stored().runId(),
                    begin.attempt().id(),
                    ownerToken,
                    sequence,
                    candidate,
                    candidateKey,
                    candidateHash,
                    failedDependencyUniqueId,
                    diagnosticStage
                );
            } else {
                try {
                    result = candidateWorker.execute(
                        new ModelSpecImportCandidateTransactionWorker.Execution(
                            tenantId,
                            actorId,
                            prepared.stored().runId(),
                            begin.attempt().id(),
                            sequence,
                            candidate,
                            prepared.stored().applyPayloadJson(),
                            candidateKey,
                            candidateHash,
                            ownerToken,
                            allocation.ownedTechnicalNodeIds().getOrDefault(
                                candidate.dbtUniqueId(),
                                Set.of()
                            )
                        )
                    );
                } catch (RuntimeException failure) {
                    result = failed(
                        prepared.stored().runId(),
                        begin.attempt().id(),
                        ownerToken,
                        sequence,
                        candidate,
                        candidateKey,
                        candidateHash,
                        failure,
                        diagnosticStage
                    );
                }
            }
            outcomes.put(candidate.dbtUniqueId(), result.status());
            applyRepository.renewLease(
                tenantId,
                prepared.stored().runId(),
                begin.attempt().id(),
                ownerToken
            );
            sequence++;
        }
        Attempt finalized = applyRepository.finalizeAttempt(
            tenantId,
            prepared.stored().runId(),
            begin.attempt().id(),
            ownerToken,
            actorId,
            Instant.now(),
            conflictResolutions
        );
        return response(BeginDisposition.STARTED, finalized);
    }

    private CandidateResult blocked(
        UUID runId,
        UUID attemptId,
        String ownerToken,
        int sequence,
        Candidate candidate,
        String candidateKey,
        String candidateHash,
        String dependencyUniqueId,
        String diagnosticStage
    ) {
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            sequence,
            candidate.dbtUniqueId(),
            candidateKey,
            candidateHash,
            ResultStatus.BLOCKED,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(
                new ApplyIssue(
                    "MODEL_IMPORT_DEPENDENCY_FAILED",
                    Severity.ERROR,
                    diagnosticStage,
                    "DEPENDENCY",
                    false,
                    "$.dependencies",
                    candidate.dbtUniqueId(),
                    dependencyUniqueId,
                    "An upstream candidate failed in this apply attempt",
                    "RETRY",
                    UUID.randomUUID().toString()
                )
            ),
            Instant.now()
        );
        return applyRepository.recordBlocked(tenantId, runId, attemptId, ownerToken, result);
    }

    private CandidateResult failed(
        UUID runId,
        UUID attemptId,
        String ownerToken,
        int sequence,
        Candidate candidate,
        String candidateKey,
        String candidateHash,
        RuntimeException failure,
        String diagnosticStage
    ) {
        SanitizedFailure sanitized = sanitizeCandidateFailure(failure);
        String correlationId = UUID.randomUUID().toString();
        if (!sanitized.allowlisted()) {
            LOG.error(
                "Model import candidate failed; correlationId={}, attemptId={}, candidate={}",
                correlationId,
                attemptId,
                candidate.dbtUniqueId()
            );
        }
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            sequence,
            candidate.dbtUniqueId(),
            candidateKey,
            candidateHash,
            ResultStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(
                new ApplyIssue(
                    sanitized.code(),
                    Severity.ERROR,
                    diagnosticStage,
                    sanitized.category(),
                    sanitized.retryable(),
                    "$.items[" + sequence + "]",
                    candidate.dbtUniqueId(),
                    null,
                    sanitized.message(),
                    sanitized.recoveryAction(),
                    correlationId
                )
            ),
            Instant.now()
        );
        return applyRepository.recordFailure(tenantId, runId, attemptId, ownerToken, result);
    }

    private StoredApplyPlan loadAuthorized(UUID runId, WarehousePlanActor actor, boolean maintenance) {
        authorizeRun(runId, actor, maintenance, true);
        return previewRepository
            .findApplyPlan(tenantId, runId)
            .orElseThrow(() -> conflict("MODEL_IMPORT_PREVIEW_STALE", "Stored apply plan checksum or payload is invalid"));
    }

    private ModelSpecImportPreviewRepository.StoredRun authorizeRun(
        UUID runId,
        WarehousePlanActor actor,
        boolean maintenance,
        boolean requireFresh
    ) {
        if (runId == null) {
            throw badRequest("MODEL_IMPORT_RUN_REQUIRED", "runId is required");
        }
        ModelSpecImportPreviewRepository.StoredRun run = previewRepository
            .findRun(tenantId, runId)
            .orElseThrow(() ->
                new ModelSpecImportApplyException(
                    "MODEL_IMPORT_RUN_NOT_FOUND",
                    "Model import preview was not found",
                    Kind.NOT_FOUND,
                    runId
                )
            );
        PlanSnapshot plan = previewRepository
            .findPlan(tenantId, run.planId())
            .orElseThrow(() ->
                new ModelSpecImportApplyException(
                    "MODEL_IMPORT_RUN_NOT_FOUND",
                    "Warehouse plan was not found",
                    Kind.NOT_FOUND,
                    run.planId()
                )
            );
        if (maintenance) {
            authorizationGuard.requirePlanMaintenance(plan.header(), actor);
        } else {
            authorizationGuard.requirePlanRead(plan.header(), actor);
        }
        if (requireFresh && (run.expiresAt() == null || !run.expiresAt().isAfter(Instant.now()))) {
            throw new ModelSpecImportApplyException(
                "MODEL_IMPORT_PREVIEW_STALE",
                "Model import preview has expired",
                Kind.GONE,
                runId
            );
        }
        return run;
    }

    private Optional<ApplyResponse> replayExisting(
        UUID runId,
        String previewHash,
        List<String> selectedUniqueIds,
        String idempotencyKey,
        Map<String, ConflictResolution> conflictResolutions,
        boolean retry
    ) {
        return applyRepository.findByIdempotencyKey(tenantId, idempotencyKey).map(existing -> {
            boolean storedRetry = existing.retrySourceAttemptId() != null;
            if (
                !Objects.equals(existing.runId(), runId) ||
                !Objects.equals(existing.previewHash(), previewHash) ||
                storedRetry != retry ||
                (!retry && !Objects.equals(existing.selectedUniqueIds(), selectedUniqueIds)) ||
                !Objects.equals(
                    existing.requestHash(),
                    requestHash(
                        runId,
                        previewHash,
                        selectedUniqueIds,
                        existing.selectedClosure(),
                        retry ? existing.retrySourceAttemptId() : null,
                        conflictResolutions
                    )
                )
            ) {
                throw conflict(
                    "MODEL_IMPORT_IDEMPOTENCY_CONFLICT",
                    "Apply idempotency key belongs to a different raw request"
                );
            }
            BeginDisposition disposition = existing.status() == ModelSpecImportApplyContract.AttemptStatus.RUNNING
                ? BeginDisposition.RUNNING
                : BeginDisposition.REPLAY;
            return response(disposition, existing);
        });
    }

    private static List<String> canonicalSelection(StoredApplyPlan stored, List<String> requestedUniqueIds) {
        Set<String> requested = new LinkedHashSet<>(requestedUniqueIds);
        List<String> canonical = stored.applyPlan().topology().stream().filter(requested::contains).toList();
        if (canonical.size() != requested.size()) {
            throw conflict(
                "MODEL_IMPORT_DEPENDENCY_MISSING",
                "Selected candidate is not present in the frozen preview"
            );
        }
        return canonical;
    }

    RetryHistory retryHistory(Attempt source) {
        List<Attempt> chain = retryChain(source);
        Map<String, CandidateResult> successful = new LinkedHashMap<>();
        for (Attempt attempt : chain) {
            attempt
                .results()
                .stream()
                .filter(CandidateResult::successful)
                .forEach(result -> successful.putIfAbsent(result.dbtUniqueId(), result));
        }
        return new RetryHistory(
            chain.getLast().selectedClosure(),
            Map.copyOf(successful)
        );
    }

    private List<Attempt> retryChain(Attempt source) {
        List<Attempt> chain = new ArrayList<>();
        Set<UUID> visited = new LinkedHashSet<>();
        Attempt current = source;
        while (current != null && visited.add(current.id())) {
            chain.add(current);
            if (current.retrySourceAttemptId() == null) {
                break;
            }
            current = applyRepository
                .find(tenantId, current.retrySourceAttemptId())
                .orElseThrow(() ->
                    conflict(
                        "MODEL_IMPORT_RETRY_SOURCE_STALE",
                        "Retry ancestor attempt is unavailable"
                    )
                );
        }
        if (current != null && current.retrySourceAttemptId() != null && !visited.add(current.retrySourceAttemptId())) {
            throw conflict("MODEL_IMPORT_RETRY_SOURCE_STALE", "Retry ancestor chain contains a cycle");
        }
        return List.copyOf(chain);
    }

    TechnicalAllocation technicalAllocation(
        StoredApplyPlan stored,
        List<String> rootSelectedClosure,
        Map<String, CandidateResult> successfulResults
    ) {
        Set<String> root = Set.copyOf(rootSelectedClosure);
        Map<String, Candidate> candidates = new HashMap<>();
        stored.applyPlan().candidates().forEach(candidate -> candidates.put(candidate.dbtUniqueId(), candidate));
        Map<String, String> ownerByNode = new LinkedHashMap<>();
        for (String uniqueId : stored.applyPlan().topology()) {
            Candidate candidate = candidates.get(uniqueId);
            if (!root.contains(uniqueId) || !claimsTechnicalNodes(candidate)) {
                continue;
            }
            technicalPathNodeIds(candidate).forEach(node -> ownerByNode.putIfAbsent(node, uniqueId));
        }
        Map<String, Set<String>> owned = new HashMap<>();
        Map<String, Set<String>> synthetic = new HashMap<>();
        for (String uniqueId : stored.applyPlan().topology()) {
            Candidate candidate = candidates.get(uniqueId);
            if (!root.contains(uniqueId) || !claimsTechnicalNodes(candidate)) {
                continue;
            }
            for (String node : technicalPathNodeIds(candidate)) {
                String owner = ownerByNode.get(node);
                if (Objects.equals(owner, uniqueId)) {
                    if (!successfulResults.containsKey(owner)) {
                        owned.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(node);
                    }
                } else if (owner != null) {
                    synthetic.computeIfAbsent(uniqueId, ignored -> new LinkedHashSet<>()).add(owner);
                }
            }
        }
        return new TechnicalAllocation(immutableSets(owned), immutableSets(synthetic));
    }

    private List<String> technicalPathNodeIds(Candidate candidate) {
        if (candidate == null || candidate.dependencyPinsJson() == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(candidate.dependencyPinsJson());
            List<String> result = new ArrayList<>();
            for (JsonNode node : root.path("technicalPathNodes")) {
                String value = node.asText(null);
                if (value != null && !value.isBlank()) {
                    result.add(value);
                }
            }
            return result.stream().distinct().sorted().toList();
        } catch (JsonProcessingException exception) {
            throw conflict("MODEL_IMPORT_PREVIEW_STALE", "Stored technical dependency pins are invalid");
        }
    }

    private boolean claimsTechnicalNodes(Candidate candidate) {
        if (candidate == null || "SKIP".equals(candidate.action())) {
            return false;
        }
        try {
            JsonNode wrapper = objectMapper.readTree(candidate.implementationJson());
            JsonNode command = wrapper.has("command") ? wrapper.path("command") : wrapper;
            String ownership = command.path("ownership").asText(null);
            if (ownership != null && !ownership.isBlank()) {
                return "DBT_MANAGED".equals(ownership);
            }
            // Compatibility for frozen previews created before ownership was persisted separately.
            return "DBT_BACKED".equals(candidate.conversionMode());
        } catch (JsonProcessingException exception) {
            throw conflict(
                "MODEL_IMPORT_PREVIEW_STALE",
                "Stored model import implementation ownership is invalid"
            );
        }
    }

    private static PreparedApply withDependencies(
        PreparedApply prepared,
        Map<String, Set<String>> syntheticDependencies
    ) {
        Map<String, Set<String>> merged = new HashMap<>();
        prepared.dependencies().forEach((uniqueId, values) -> merged.put(uniqueId, new LinkedHashSet<>(values)));
        syntheticDependencies.forEach((uniqueId, values) ->
            merged.computeIfAbsent(uniqueId, ignored -> new LinkedHashSet<>()).addAll(values)
        );
        return new PreparedApply(
            prepared.plan(),
            prepared.stored(),
            prepared.selectedUniqueIds(),
            prepared.selectedClosure(),
            prepared.candidates(),
            immutableSets(merged)
        );
    }

    private static Map<String, Set<String>> immutableSets(Map<String, Set<String>> values) {
        Map<String, Set<String>> result = new HashMap<>();
        values.forEach((key, members) -> result.put(key, Set.copyOf(members)));
        return Map.copyOf(result);
    }

    static boolean hasFailedDependency(
        String dbtUniqueId,
        Map<String, Set<String>> dependencies,
        Map<String, ResultStatus> outcomes
    ) {
        return failedDependencyUniqueId(dbtUniqueId, dependencies, outcomes) != null;
    }

    static String failedDependencyUniqueId(
        String dbtUniqueId,
        Map<String, Set<String>> dependencies,
        Map<String, ResultStatus> outcomes
    ) {
        return dependencies
            .getOrDefault(dbtUniqueId, Set.of())
            .stream()
            .sorted()
            .filter(dependency -> {
                ResultStatus status = outcomes.get(dependency);
                return status == ResultStatus.FAILED || status == ResultStatus.BLOCKED;
            })
            .findFirst()
            .orElse(null);
    }

    private String requestHash(
        UUID runId,
        String previewHash,
        List<String> selected,
        List<String> closure,
        UUID retrySource,
        Map<String, ConflictResolution> conflictResolutions
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("runId", runId.toString());
        node.put("previewHash", previewHash);
        node.set("selectedUniqueIds", objectMapper.valueToTree(selected));
        node.set("selectedClosure", objectMapper.valueToTree(closure));
        if (retrySource != null) {
            node.put("retrySourceAttemptId", retrySource.toString());
        }
        if (conflictResolutions != null && !conflictResolutions.isEmpty()) {
            TreeMap<String, String> normalized = new TreeMap<>();
            conflictResolutions.forEach((uniqueId, resolution) -> normalized.put(uniqueId, resolution.name()));
            node.set("conflictResolutions", objectMapper.valueToTree(normalized));
        }
        return payloadCodec.checksum(node);
    }

    private WarehousePlanActor requireActor() {
        WarehousePlanActor actor = actorProvider.currentActor();
        if (actor == null || actor.ownerId() == null || actor.ownerId().isBlank()) {
            throw new ModelSpecImportApplyException(
                "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED",
                "Authenticated actor is required",
                Kind.FORBIDDEN,
                null
            );
        }
        return actor;
    }

    private static void requireApplyRequest(ApplyRequest request) {
        if (request == null || request.runId() == null) {
            throw badRequest("MODEL_IMPORT_APPLY_REQUEST_INVALID", "Apply request and runId are required");
        }
        requirePreviewHash(request.previewHash());
        requireIdempotencyKey(request.idempotencyKey());
        if (request.selectedUniqueIds().isEmpty()) {
            throw badRequest("MODEL_IMPORT_SELECTION_REQUIRED", "selectedUniqueIds must contain at least one candidate");
        }
        if (request.selectedUniqueIds().size() > MAX_SELECTED_UNIQUE_IDS) {
            throw badRequest("MODEL_IMPORT_REQUEST_INVALID", "selectedUniqueIds cannot contain more than 200 candidates");
        }
        if (request.selectedUniqueIds().stream().anyMatch(uniqueId -> uniqueId.length() > MAX_UNIQUE_ID_LENGTH)) {
            throw badRequest("MODEL_IMPORT_REQUEST_INVALID", "selectedUniqueIds item cannot exceed 512 characters");
        }
    }

    private static String requirePreviewHash(String value) {
        String previewHash = requireText(
            value,
            "MODEL_IMPORT_PREVIEW_HASH_REQUIRED",
            "previewHash is required"
        );
        if (!previewHash.matches("(?i)[0-9a-f]{64}")) {
            throw badRequest(
                "MODEL_IMPORT_REQUEST_INVALID",
                "previewHash must be a 64-character hexadecimal checksum"
            );
        }
        return previewHash;
    }

    private static String requireIdempotencyKey(String value) {
        String idempotencyKey = requireText(
            value,
            "MODEL_IMPORT_IDEMPOTENCY_REQUIRED",
            "idempotencyKey is required"
        );
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest(
                "MODEL_IMPORT_REQUEST_INVALID",
                "idempotencyKey cannot exceed 256 characters"
            );
        }
        return idempotencyKey;
    }

    private static String requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(code, message);
        }
        return value.trim();
    }

    private ApplyResponse response(BeginDisposition disposition, Attempt attempt) {
        return new ApplyResponse(
            attempt.id(),
            attempt.runId(),
            disposition,
            attempt.status(),
            attempt.summary(),
            attempt.results(),
            canonicalRun(attempt)
        );
    }

    CanonicalRunResult canonicalRun(Attempt latestAttempt) {
        List<Attempt> chain = retryChain(latestAttempt);
        Attempt root = chain.getLast();
        Map<String, CandidateResult> latestResults = new LinkedHashMap<>();
        chain.forEach(attempt ->
            attempt.results().forEach(result -> latestResults.putIfAbsent(result.dbtUniqueId(), result))
        );
        List<CandidateResult> items = root
            .selectedClosure()
            .stream()
            .map(latestResults::get)
            .filter(Objects::nonNull)
            .toList();
        int selected = root.selectedClosure().size();
        ApplySummary summary = new ApplySummary(
            selected,
            selected - items.size(),
            count(items, ResultStatus.CREATED) + count(items, ResultStatus.UPDATED),
            count(items, ResultStatus.CREATED),
            count(items, ResultStatus.UPDATED),
            count(items, ResultStatus.SKIPPED),
            count(items, ResultStatus.FAILED),
            count(items, ResultStatus.BLOCKED)
        );
        ModelSpecImportApplyContract.AttemptStatus status = summary.pending() > 0
            ? ModelSpecImportApplyContract.AttemptStatus.RUNNING
            : ModelSpecImportApplyRepository.terminalStatus(summary);
        return new CanonicalRunResult(root.id(), status, summary, items);
    }

    private static int count(List<CandidateResult> items, ResultStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }

    static String diagnosticStage(Attempt retrySource) {
        return retrySource == null ? "APPLY" : "RETRY";
    }

    private static ModelSpecImportApplyException badRequest(String code, String message) {
        return new ModelSpecImportApplyException(code, message, Kind.BAD_REQUEST, null);
    }

    private static ModelSpecImportApplyException conflict(String code, String message) {
        return new ModelSpecImportApplyException(code, message, Kind.CONFLICT, null);
    }

    static SanitizedFailure sanitizeCandidateFailure(RuntimeException failure) {
        if (failure instanceof ModelSpecImportApplyException applyFailure) {
            return switch (applyFailure.code()) {
                case "MODEL_IMPORT_IDEMPOTENCY_CONFLICT" ->
                    new SanitizedFailure(
                        applyFailure.code(),
                        "CONFLICT",
                        true,
                        "Candidate request conflicts with a previously persisted apply result",
                        "RETRY",
                        true
                    );
                case "MODEL_IMPORT_APPLY_CANDIDATE_NOT_SELECTED" ->
                    new SanitizedFailure(
                        applyFailure.code(),
                        "VALIDATION",
                        false,
                        "Candidate is outside the frozen selection closure",
                        "REFRESH_PREVIEW",
                        true
                    );
                default -> SanitizedFailure.GENERIC;
            };
        }
        return SanitizedFailure.GENERIC;
    }

    record RetryHistory(
        List<String> rootSelectedClosure,
        Map<String, CandidateResult> successfulResults
    ) {}

    record TechnicalAllocation(
        Map<String, Set<String>> ownedTechnicalNodeIds,
        Map<String, Set<String>> syntheticDependencies
    ) {}

    record SanitizedFailure(
        String code,
        String category,
        boolean retryable,
        String message,
        String recoveryAction,
        boolean allowlisted
    ) {
        private static final SanitizedFailure GENERIC = new SanitizedFailure(
            "MODEL_IMPORT_CANDIDATE_FAILED",
            "INTERNAL",
            true,
            "Candidate apply failed; internal details were withheld",
            "RETRY",
            false
        );
    }
}
