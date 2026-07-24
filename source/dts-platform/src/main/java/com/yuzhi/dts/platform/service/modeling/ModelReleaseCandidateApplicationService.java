package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.BlockerView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceSummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan-scoped application boundary for the Stage 6 workbench.
 *
 * <p>The browser receives one server-owned projection and never derives candidate state, blockers or allowed actions
 * by joining lower-level endpoints.
 */
@Service
public class ModelReleaseCandidateApplicationService {

    private static final String PLAN_FORBIDDEN = "MODEL_RELEASE_CANDIDATE_PLAN_FORBIDDEN";
    private static final String CANDIDATE_NOT_FOUND = "MODEL_RELEASE_CANDIDATE_NOT_FOUND";
    private static final String EVIDENCE_UNAVAILABLE = "MODEL_RELEASE_EVIDENCE_NOT_AVAILABLE";

    private final ModelReleaseCandidateRepository repository;
    private final ModelReleaseCandidateService commands;
    private final ModelSpecPlanWriteAccessPort planAccess;

    public ModelReleaseCandidateApplicationService(
        ModelReleaseCandidateRepository repository,
        ModelReleaseCandidateService commands,
        ModelSpecPlanWriteAccessPort planAccess
    ) {
        this.repository = repository;
        this.commands = commands;
        this.planAccess = planAccess;
    }

    @Transactional(readOnly = true)
    public WorkbenchView workspace(String tenantId, String actorId, UUID planId) {
        Access access = authorize(tenantId, actorId, planId);
        List<CandidateView> candidates = repository.listForWorkbench(access.tenantId(), access.planId());
        if (candidates.size() > 1 && isActive(candidates.get(0)) && isActive(candidates.get(1))) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_CURRENT_AMBIGUOUS",
                "More than one active release candidate exists for the plan",
                Kind.CONFLICT,
                Map.of("planId", access.planId())
            );
        }
        CandidateView candidate = candidates.isEmpty() ? null : candidates.get(0);
        return candidate == null ? empty(access.planId()) : project(candidate, access.actorId());
    }

    @Transactional(readOnly = true)
    public CandidateView get(String tenantId, String actorId, UUID planId, UUID candidateId) {
        Access access = authorize(tenantId, actorId, planId);
        return candidateForPlan(access.tenantId(), access.planId(), candidateId);
    }

    public CommandResult create(
        String tenantId,
        String actorId,
        UUID planId,
        CreateCandidateCommand command
    ) {
        Access access = authorize(tenantId, actorId, planId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Candidate plan must match the plan in the request path"
            );
        }
        if (
            repository
                .findByIdempotencyKey(access.tenantId(), command.idempotencyKey())
                .filter(existing -> access.planId().equals(existing.planId()))
                .isPresent()
        ) {
            return commands.create(access.tenantId(), access.actorId(), command);
        }
        CandidateView current = repository
            .listForWorkbench(access.tenantId(), access.planId())
            .stream()
            .filter(ModelReleaseCandidateApplicationService::isActive)
            .findFirst()
            .orElse(null);
        if (current != null) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS",
                "The plan already has an active release candidate",
                Kind.CONFLICT,
                Map.of(
                    "planId",
                    access.planId(),
                    "candidateId",
                    current.id(),
                    "currentStatus",
                    current.status(),
                    "currentVersion",
                    current.version()
                )
            );
        }
        return commands.create(access.tenantId(), access.actorId(), command);
    }

    public CommandResult replaceScope(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        ReplaceScopeCommand command
    ) {
        Access access = authorize(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return commands.replaceScope(access.tenantId(), access.actorId(), candidateId, command);
    }

    public CommandResult lock(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = authorize(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return commands.transition(
            access.tenantId(),
            access.actorId(),
            candidateId,
            new TransitionCommand(expectedVersion, DeliveryStatus.BUILDING, idempotencyKey, reason)
        );
    }

    public CommandResult retry(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = authorize(tenantId, actorId, planId);
        CandidateView current = candidateForPlan(access.tenantId(), access.planId(), candidateId);
        CommandEventView existing = repository.findCommandByIdempotencyKey(access.tenantId(), idempotencyKey).orElse(null);
        DeliveryStatus target = existing == null
            ? retryTarget(current)
            : replayRetryTarget(candidateId, existing);
        return commands.transition(
            access.tenantId(),
            access.actorId(),
            candidateId,
            new TransitionCommand(expectedVersion, target, idempotencyKey, reason)
        );
    }

    public CommandResult refreshDrift(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = authorize(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return commands.transition(
            access.tenantId(),
            access.actorId(),
            candidateId,
            new TransitionCommand(expectedVersion, DeliveryStatus.STALE, idempotencyKey, reason)
        );
    }

    public CommandResult createReplacement(
        String tenantId,
        String actorId,
        UUID planId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command
    ) {
        Access access = authorize(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), sourceCandidateId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Replacement plan must match the plan in the request path"
            );
        }
        return commands.createReplacement(
            access.tenantId(),
            access.actorId(),
            sourceCandidateId,
            expectedVersion,
            command
        );
    }

    public static String etag(CandidateView candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        return "\"release-candidate:" + candidate.id() + ":" + candidate.version() + "\"";
    }

    private WorkbenchView empty(UUID planId) {
        return new WorkbenchView(
            planId,
            WorkbenchState.EMPTY,
            null,
            unavailableEvidence(),
            new BlockerView(
                "MODEL_RELEASE_CANDIDATE_REQUIRED",
                "Create a release candidate before starting build and quality work"
            ),
            List.of(WorkspaceAction.CREATE_CANDIDATE),
            null
        );
    }

    private WorkbenchView project(CandidateView candidate, String actorId) {
        if (candidate.entries().isEmpty()) {
            return new WorkbenchView(
                candidate.planId(),
                candidate.status() == DeliveryStatus.DRAFT ? WorkbenchState.EMPTY : WorkbenchState.BLOCKED,
                candidate,
                unavailableEvidence(),
                new BlockerView(
                    ModelReleaseCandidateContract.SCOPE_EMPTY_ERROR_CODE,
                    "Select at least one current model before starting delivery"
                ),
                candidate.status() == DeliveryStatus.DRAFT
                    ? List.of(WorkspaceAction.UPDATE_SCOPE)
                    : List.of(),
                etag(candidate)
            );
        }
        if (candidate.status() == DeliveryStatus.STALE) {
            return blocked(
                candidate,
                WorkbenchState.STALE,
                ModelReleaseCandidateContract.STALE_ERROR_CODE,
                "The candidate no longer matches the current model revisions",
                List.of(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE)
            );
        }
        if (!isReplacementSource(candidate.status())) {
            List<DriftReasonView> drift = commands.detectDrift(candidate.tenantId(), candidate);
            if (!drift.isEmpty()) {
                return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                ModelReleaseCandidateContract.STALE_ERROR_CODE,
                "The candidate no longer matches the current model revisions and must be refreshed",
                List.of(WorkspaceAction.REFRESH_CANDIDATE)
            );
        }
        }
        if (candidate.status() == DeliveryStatus.BUILD_FAILED) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_BUILD_FAILED",
                "The latest build failed and must be retried",
                List.of(WorkspaceAction.RETRY_BUILD)
            );
        }
        if (candidate.status() == DeliveryStatus.QUALITY_FAILED) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_QUALITY_FAILED",
                "The latest quality run failed and must be rerun",
                List.of(WorkspaceAction.RUN_QUALITY)
            );
        }
        if (candidate.status() == DeliveryStatus.REJECTED || candidate.status() == DeliveryStatus.ROLLED_BACK) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_REPLACEMENT_REQUIRED",
                "Create a replacement candidate to continue delivery",
                List.of(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE)
            );
        }
        return new WorkbenchView(
            candidate.planId(),
            WorkbenchState.READY,
            candidate,
            unavailableEvidence(),
            null,
            actions(candidate, actorId),
            etag(candidate)
        );
    }

    private WorkbenchView blocked(
        CandidateView candidate,
        WorkbenchState state,
        String code,
        String message,
        List<WorkspaceAction> actions
    ) {
        return new WorkbenchView(
            candidate.planId(),
            state,
            candidate,
            unavailableEvidence(),
            new BlockerView(code, message),
            actions,
            etag(candidate)
        );
    }

    private List<WorkspaceAction> actions(CandidateView candidate, String actorId) {
        if (candidate.status() == DeliveryStatus.DRAFT) {
            return List.of(WorkspaceAction.UPDATE_SCOPE, WorkspaceAction.START_BUILD);
        }
        return candidate
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
            .map(action -> WorkspaceAction.valueOf(action.name()))
            .toList();
    }

    private List<EvidenceSummaryView> unavailableEvidence() {
        return Arrays
            .stream(DeliveryEvidenceType.values())
            .map(type ->
                new EvidenceSummaryView(
                    type,
                    EvidenceState.UNAVAILABLE,
                    EVIDENCE_UNAVAILABLE,
                    "No current evidence is available"
                )
            )
            .toList();
    }

    private Access authorize(String tenantId, String actorId, UUID planId) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (planId == null || !planAccess.canMaintain(tenant, planId, actor)) {
            throw new ModelReleaseCandidateException(
                PLAN_FORBIDDEN,
                "Warehouse plan is not available for maintenance",
                Kind.FORBIDDEN
            );
        }
        return new Access(tenant, actor, planId);
    }

    private CandidateView candidateForPlan(String tenantId, UUID planId, UUID candidateId) {
        CandidateView candidate = candidateId == null
            ? null
            : repository.find(tenantId, candidateId).orElse(null);
        if (candidate == null || !planId.equals(candidate.planId())) {
            throw new ModelReleaseCandidateException(
                CANDIDATE_NOT_FOUND,
                "Release candidate was not found",
                Kind.NOT_FOUND
            );
        }
        return candidate;
    }

    private static ModelReleaseCandidateException badRequest(String code, String message) {
        return new ModelReleaseCandidateException(code, message, Kind.BAD_REQUEST);
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_REQUEST_INVALID",
                name + " is required",
                Kind.BAD_REQUEST
            );
        }
        return value.trim();
    }

    private static DeliveryStatus retryTarget(CandidateView current) {
        return switch (current.status()) {
            case BUILD_FAILED -> DeliveryStatus.BUILDING;
            case QUALITY_FAILED -> DeliveryStatus.QUALITY_RUNNING;
            default -> throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.INVALID_TRANSITION_ERROR_CODE,
                "Only failed build or quality candidates can be retried",
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
        };
    }

    private static DeliveryStatus replayRetryTarget(UUID candidateId, CommandEventView event) {
        boolean buildRetry =
            event.fromStatus() == DeliveryStatus.BUILD_FAILED && event.toStatus() == DeliveryStatus.BUILDING;
        boolean qualityRetry =
            event.fromStatus() == DeliveryStatus.QUALITY_FAILED && event.toStatus() == DeliveryStatus.QUALITY_RUNNING;
        if (!candidateId.equals(event.candidateId()) || (!buildRetry && !qualityRetry)) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE,
                "Idempotency key already belongs to another command",
                Kind.CONFLICT,
                Map.of("idempotencyKey", event.idempotencyKey(), "candidateId", event.candidateId())
            );
        }
        return event.toStatus();
    }

    private static boolean isActive(CandidateView candidate) {
        return candidate != null && !isReplacementSource(candidate.status());
    }

    private static boolean isReplacementSource(DeliveryStatus status) {
        return status == DeliveryStatus.REJECTED || status == DeliveryStatus.ROLLED_BACK || status == DeliveryStatus.STALE;
    }

    private record Access(String tenantId, String actorId, UUID planId) {}
}
