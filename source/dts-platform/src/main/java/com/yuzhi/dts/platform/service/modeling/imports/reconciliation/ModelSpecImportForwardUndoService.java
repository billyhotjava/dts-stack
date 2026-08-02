package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.OperationType;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyRepository;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardUndoWorker.Execution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoPlan;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoRequest;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates idempotent forward undo over the existing apply attempt/result ledger. */
@Service
public class ModelSpecImportForwardUndoService {

    private final ModelSpecImportApplyRepository applyRepository;
    private final ModelSpecImportPreviewRepository previewRepository;
    private final ModelSpecImportForwardUndoPlanner planner;
    private final ModelSpecImportForwardUndoWorker worker;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final ModelSpecImportApplyPayloadCodec payloadCodec;
    private final ObjectMapper objectMapper;
    private final ModelSpecImportReconciliationAudit audit;
    private final String tenantId;

    public ModelSpecImportForwardUndoService(
        ModelSpecImportApplyRepository applyRepository,
        ModelSpecImportPreviewRepository previewRepository,
        ModelSpecImportForwardUndoPlanner planner,
        ModelSpecImportForwardUndoWorker worker,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ModelSpecImportApplyPayloadCodec payloadCodec,
        ObjectMapper objectMapper,
        ModelSpecImportReconciliationAudit audit,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.applyRepository = applyRepository;
        this.previewRepository = previewRepository;
        this.planner = planner;
        this.worker = worker;
        this.actorProvider = actorProvider;
        this.authorizationGuard = authorizationGuard;
        this.payloadCodec = payloadCodec;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.tenantId = tenantId;
    }

    public ApplyResponse forwardUndo(ForwardUndoRequest request) {
        if (request == null) throw badRequest("MODEL_IMPORT_UNDO_REQUEST_INVALID", "Forward undo request is required");
        WarehousePlanActor actor = requireActor();
        Attempt target = applyRepository
            .find(tenantId, request.targetAttemptId())
            .orElseThrow(() -> notFound(request.targetAttemptId()));
        requireUndoTarget(target);
        PlanSnapshot plan = previewRepository
            .findPlan(tenantId, target.planId())
            .orElseThrow(() -> notFound(target.planId()));
        authorizationGuard.requirePlanMaintenance(plan.header(), actor);

        List<UndoTargetItem> targetItems = targetItems(target);
        ForwardUndoPlan undoPlan;
        try {
            undoPlan = planner.plan(
                request.selectedItemIds(),
                targetItems,
                request.expectedCurrentRevisions()
            );
        } catch (IllegalArgumentException invalid) {
            throw conflict("MODEL_IMPORT_UNDO_SELECTION_INVALID", invalid.getMessage(), request.targetAttemptId());
        }

        applyRepository.recoverExpiredRunning(tenantId, target.runId(), actor.ownerId());
        String requestHash = requestHash(request, undoPlan);
        BeginResult begin = applyRepository.begin(
            new BeginCommand(
                UUID.randomUUID(),
                target.runId(),
                target.planId(),
                null,
                tenantId,
                target.previewHash(),
                undoPlan.selectedUniqueIds(),
                undoPlan.reverseClosure().stream().map(UndoTargetItem::dbtUniqueId).toList(),
                request.idempotencyKey(),
                requestHash,
                actor.ownerId(),
                Instant.now(),
                OperationType.FORWARD_UNDO,
                target.id()
            )
        );
        if (begin.disposition() != BeginDisposition.STARTED) {
            return response(begin.disposition(), begin.attempt());
        }

        audit.beginForwardUndo(begin.attempt().id(), target.id(), target.runId(), undoPlan.reverseClosure().size());
        String ownerToken = begin.ownerToken();
        Map<String, ResultStatus> outcomes = new HashMap<>();
        for (int sequence = 0; sequence < undoPlan.reverseClosure().size(); sequence++) {
            UndoTargetItem item = undoPlan.reverseClosure().get(sequence);
            applyRepository.renewLease(tenantId, target.runId(), begin.attempt().id(), ownerToken);
            String candidateKey = "model-import-forward-undo:" +
                ModelPackageChecksum.sha256Text(request.idempotencyKey() + "\n" + item.dbtUniqueId());
            String candidateHash = payloadCodec.checksum(objectMapper.valueToTree(item));
            CandidateResult result;
            String failedDependent = failedDependent(item, undoPlan.reverseClosure(), outcomes);
            if (failedDependent != null) {
                result = recordDependencyBlocked(
                    target,
                    begin.attempt().id(),
                    ownerToken,
                    sequence,
                    item,
                    candidateKey,
                    candidateHash,
                    failedDependent
                );
            } else {
                try {
                    result = worker.execute(
                        new Execution(
                            tenantId,
                            actor.ownerId(),
                            target.runId(),
                            begin.attempt().id(),
                            ownerToken,
                            sequence,
                            projectKey(target, item.dbtUniqueId()),
                            item,
                            candidateKey,
                            candidateHash
                        )
                    );
                } catch (RuntimeException failure) {
                    result = recordFailure(
                        target,
                        begin.attempt().id(),
                        ownerToken,
                        sequence,
                        item,
                        candidateKey,
                        candidateHash,
                        failure
                    );
                }
            }
            outcomes.put(item.dbtUniqueId(), result.status());
        }
        Attempt completed = applyRepository.finalizeAttempt(
            tenantId,
            target.runId(),
            begin.attempt().id(),
            ownerToken,
            actor.ownerId(),
            Instant.now()
        );
        return response(BeginDisposition.STARTED, completed);
    }

    private List<UndoTargetItem> targetItems(Attempt target) {
        ArrayList<UndoTargetItem> result = new ArrayList<>();
        for (CandidateResult item : target.results()) {
            if (!item.successful()) continue;
            // CANCEL is an explicit no-write conflict decision and does not advance a checkpoint.
            if ("CANCEL".equals(item.appliedAction())) continue;
            if (
                item.mergeCheckpoint() == null ||
                item.appliedAction() == null ||
                item.projectKey() == null ||
                item.modelSpecId() == null ||
                item.revision() == null ||
                item.modelChecksum() == null ||
                item.implementationRevision() == null ||
                item.implementationChecksum() == null
            ) {
                throw conflict(
                    "MODEL_IMPORT_UNDO_LEGACY_CHECKPOINT_MISSING",
                    "The target attempt predates accepted merge checkpoints and cannot be undone automatically",
                    target.id()
                );
            }
            RevisionPins post = new RevisionPins(
                item.revision(),
                item.modelChecksum(),
                item.implementationRevision(),
                item.implementationChecksum()
            );
            result.add(
                new UndoTargetItem(
                    item.modelSpecId(),
                    item.dbtUniqueId(),
                    item.appliedAction(),
                    item.dependencyUniqueIds(),
                    post,
                    item.preAttemptPins()
                )
            );
        }
        return List.copyOf(result);
    }

    private CandidateResult recordFailure(
        Attempt target,
        UUID attemptId,
        String ownerToken,
        int sequence,
        UndoTargetItem item,
        String candidateKey,
        String candidateHash,
        RuntimeException failure
    ) {
        String correlationId = UUID.randomUUID().toString();
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            sequence,
            item.dbtUniqueId(),
            candidateKey,
            candidateHash,
            ResultStatus.FAILED,
            item.modelSpecId(),
            item.postPins().modelRevision(),
            item.postPins().modelChecksum(),
            item.postPins().implementationRevision(),
            item.postPins().implementationChecksum(),
            0,
            List.of(
                new ApplyIssue(
                    "MODEL_IMPORT_UNDO_CAS_OR_PERSISTENCE_FAILED",
                    Severity.ERROR,
                    "UNDO",
                    "STALE",
                    false,
                    "$.expectedCurrentRevisions",
                    item.dbtUniqueId(),
                    null,
                    "Forward undo failed because the pinned model state could not be restored",
                    "OPEN_MODEL",
                    correlationId
                )
            ),
            Instant.now(),
            item.appliedAction(),
            projectKey(target, item.dbtUniqueId()),
            null,
            item.postPins(),
            item.dependencies()
        );
        return applyRepository.recordFailure(tenantId, target.runId(), attemptId, ownerToken, result);
    }

    private CandidateResult recordDependencyBlocked(
        Attempt target,
        UUID attemptId,
        String ownerToken,
        int sequence,
        UndoTargetItem item,
        String candidateKey,
        String candidateHash,
        String failedDependent
    ) {
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            sequence,
            item.dbtUniqueId(),
            candidateKey,
            candidateHash,
            ResultStatus.BLOCKED,
            item.modelSpecId(),
            item.postPins().modelRevision(),
            item.postPins().modelChecksum(),
            item.postPins().implementationRevision(),
            item.postPins().implementationChecksum(),
            0,
            List.of(
                new ApplyIssue(
                    "MODEL_IMPORT_UNDO_DEPENDENT_FAILED",
                    Severity.ERROR,
                    "UNDO",
                    "DEPENDENCY",
                    false,
                    "$.selectedItemIds",
                    item.dbtUniqueId(),
                    failedDependent,
                    "A downstream item failed before this dependency could be safely restored",
                    "OPEN_MODEL",
                    UUID.randomUUID().toString()
                )
            ),
            Instant.now(),
            item.appliedAction(),
            projectKey(target, item.dbtUniqueId()),
            null,
            item.postPins(),
            item.dependencies()
        );
        return applyRepository.recordBlocked(tenantId, target.runId(), attemptId, ownerToken, result);
    }

    static String failedDependent(
        UndoTargetItem item,
        List<UndoTargetItem> reverseClosure,
        Map<String, ResultStatus> outcomes
    ) {
        Map<String, UndoTargetItem> byId = new LinkedHashMap<>();
        reverseClosure.forEach(value -> byId.put(value.dbtUniqueId(), value));
        return outcomes.entrySet().stream()
            .filter(entry -> entry.getValue() == ResultStatus.FAILED || entry.getValue() == ResultStatus.BLOCKED)
            .map(Map.Entry::getKey)
            .filter(failed -> dependsTransitively(byId.get(failed), item.dbtUniqueId(), byId, new java.util.HashSet<>()))
            .sorted()
            .findFirst()
            .orElse(null);
    }

    private static boolean dependsTransitively(
        UndoTargetItem candidate,
        String dependency,
        Map<String, UndoTargetItem> byId,
        java.util.Set<String> visited
    ) {
        if (candidate == null || !visited.add(candidate.dbtUniqueId())) return false;
        if (candidate.dependencies().contains(dependency)) return true;
        return candidate.dependencies().stream().anyMatch(value -> dependsTransitively(byId.get(value), dependency, byId, visited));
    }

    private String projectKey(Attempt target, String dbtUniqueId) {
        return target.results().stream()
            .filter(item -> item.dbtUniqueId().equals(dbtUniqueId))
            .map(CandidateResult::projectKey)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> conflict("MODEL_IMPORT_UNDO_PROJECT_MISSING", "Target project identity is unavailable", target.id()));
    }

    private String requestHash(ForwardUndoRequest request, ForwardUndoPlan plan) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("operationType", OperationType.FORWARD_UNDO.name());
        projection.put("targetAttemptId", request.targetAttemptId());
        projection.put("selectedUniqueIds", plan.selectedUniqueIds());
        projection.put("selectedClosure", plan.reverseClosure().stream().map(UndoTargetItem::dbtUniqueId).toList());
        projection.put("expectedCurrentRevisions", new java.util.TreeMap<>(request.expectedCurrentRevisions()));
        return payloadCodec.checksum(objectMapper.valueToTree(projection));
    }

    private static void requireUndoTarget(Attempt target) {
        if (target.operationType() != OperationType.APPLY || target.status() == AttemptStatus.RUNNING) {
            throw conflict(
                "MODEL_IMPORT_UNDO_TARGET_INVALID",
                "Forward undo requires a terminal APPLY attempt",
                target.id()
            );
        }
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

    private static ApplyResponse response(BeginDisposition disposition, Attempt attempt) {
        return new ApplyResponse(
            attempt.id(), attempt.runId(), disposition, attempt.status(), attempt.summary(), attempt.results()
        );
    }

    private static ModelSpecImportApplyException badRequest(String code, String message) {
        return new ModelSpecImportApplyException(code, message, Kind.BAD_REQUEST, null);
    }

    private static ModelSpecImportApplyException notFound(Object details) {
        return new ModelSpecImportApplyException(
            "MODEL_IMPORT_UNDO_TARGET_NOT_FOUND",
            "Forward undo target was not found",
            Kind.NOT_FOUND,
            details
        );
    }

    private static ModelSpecImportApplyException conflict(String code, String message, Object details) {
        return new ModelSpecImportApplyException(code, message, Kind.CONFLICT, details);
    }
}
