package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.BlockerView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceSummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ModelMaterializationStatusView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.RelationEvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ModelMaterializationStartService materializationStarts;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver dutyResolver;
    private final CandidatePublicationAdmissionService publicationAdmission;
    private final CandidatePublicationCoordinator publicationCoordinator;
    private final CandidateRollbackCommitService rollbackCommits;
    private final ReleaseCandidateWorkbenchEvidencePort workbenchEvidence;

    public ModelReleaseCandidateApplicationService(
        ModelReleaseCandidateRepository repository,
        ModelReleaseCandidateService commands,
        ModelMaterializationStartService materializationStarts,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver dutyResolver,
        CandidatePublicationAdmissionService publicationAdmission,
        CandidatePublicationCoordinator publicationCoordinator,
        CandidateRollbackCommitService rollbackCommits,
        ReleaseCandidateWorkbenchEvidencePort workbenchEvidence
    ) {
        this.repository = repository;
        this.commands = commands;
        this.materializationStarts = materializationStarts;
        this.planAccess = planAccess;
        this.dutyResolver = dutyResolver;
        this.publicationAdmission = publicationAdmission;
        this.publicationCoordinator = publicationCoordinator;
        this.rollbackCommits = rollbackCommits;
        this.workbenchEvidence = workbenchEvidence;
    }

    @Transactional(readOnly = true)
    public WorkbenchView workspace(String tenantId, String actorId, UUID planId) {
        Access access = authorizeRead(tenantId, actorId, planId);
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
        WorkbenchView view = candidate == null
            ? empty(access.planId(), access.duties())
            : project(candidate, access.actorId(), access.duties());
        return candidate == null
            ? view
            : withPersistedEvidence(
                view,
                workbenchEvidence.findCurrent(candidate)
            );
    }

    @Transactional(readOnly = true)
    public CandidateView get(String tenantId, String actorId, UUID planId, UUID candidateId) {
        Access access = authorizeRead(tenantId, actorId, planId);
        return candidateForPlan(access.tenantId(), access.planId(), candidateId);
    }

    @Transactional(readOnly = true)
    public List<ModelMaterializationStatusView> materializationStatuses(
        String tenantId,
        String actorId,
        UUID planId,
        List<UUID> modelSpecIds
    ) {
        Access access = authorizeRead(tenantId, actorId, planId);
        if (
            modelSpecIds == null ||
            modelSpecIds.isEmpty() ||
            modelSpecIds.size() > ModelReleaseCandidateContract.MAX_SCOPE_ENTRIES ||
            modelSpecIds.stream().anyMatch(id -> id == null) ||
            modelSpecIds.stream().distinct().count() != modelSpecIds.size()
        ) {
            throw badRequest(
                "MODEL_MATERIALIZATION_STATUS_SCOPE_INVALID",
                "modelSpecIds must contain between 1 and 100 unique model identifiers"
            );
        }
        List<UUID> ids = List.copyOf(modelSpecIds);
        return workbenchEvidence.findLatest(access.tenantId(), access.planId(), ids);
    }

    public CommandResult create(
        String tenantId,
        String actorId,
        UUID planId,
        CreateCandidateCommand command
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
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
            return roleAware(commands.create(access.tenantId(), access.actorId(), command), access);
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
        return roleAware(commands.create(access.tenantId(), access.actorId(), command), access);
    }

    public CommandResult replaceScope(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        ReplaceScopeCommand command
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return roleAware(
            commands.replaceScope(access.tenantId(), access.actorId(), candidateId, command),
            access
        );
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
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return roleAware(
            materializationStarts.start(
                access.tenantId(),
                access.actorId(),
                candidateId,
                expectedVersion,
                idempotencyKey,
                reason
            ),
            access
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
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        CandidateView current = candidateForPlan(access.tenantId(), access.planId(), candidateId);
        CommandEventView existing = repository.findCommandByIdempotencyKey(access.tenantId(), idempotencyKey).orElse(null);
        DeliveryStatus target = existing == null
            ? retryTarget(current)
            : replayRetryTarget(candidateId, existing);
        if (target == DeliveryStatus.BUILDING) {
            return roleAware(
                materializationStarts.retry(
                    access.tenantId(),
                    access.actorId(),
                    candidateId,
                    expectedVersion,
                    idempotencyKey,
                    reason
                ),
                access
            );
        }
        return roleAware(
            commands.transition(
                access.tenantId(),
                access.actorId(),
                candidateId,
                new TransitionCommand(expectedVersion, target, idempotencyKey, reason)
            ),
            access
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
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return roleAware(
            commands.transition(
                access.tenantId(),
                access.actorId(),
                candidateId,
                new TransitionCommand(expectedVersion, DeliveryStatus.STALE, idempotencyKey, reason)
            ),
            access
        );
    }

    public CommandResult cancel(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return roleAware(
            commands.transition(
                access.tenantId(),
                access.actorId(),
                candidateId,
                new TransitionCommand(
                    expectedVersion,
                    DeliveryStatus.CANCELLED,
                    idempotencyKey,
                    reason
                )
            ),
            access
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
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), sourceCandidateId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Replacement plan must match the plan in the request path"
            );
        }
        return roleAware(
            commands.createReplacement(
                access.tenantId(),
                access.actorId(),
                sourceCandidateId,
                expectedVersion,
                command
            ),
            access
        );
    }

    @Transactional
    public CommandResult rematerialize(
        String tenantId,
        String actorId,
        UUID planId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        CandidateView current = candidateForPlan(access.tenantId(), access.planId(), sourceCandidateId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Rematerialization plan must match the plan in the request path"
            );
        }
        if (command.entries().isEmpty()) {
            throw badRequest(ModelReleaseCandidateContract.SCOPE_EMPTY_ERROR_CODE, "Select at least one model to materialize");
        }

        CandidateView replacementSource;
        if (
            current.status() == DeliveryStatus.BUILT ||
            (current.status() == DeliveryStatus.STALE && expectedVersion < current.version())
        ) {
            replacementSource = commands
                .supersedeForRematerialization(
                    access.tenantId(),
                    access.actorId(),
                    sourceCandidateId,
                    new TransitionCommand(
                        expectedVersion,
                        DeliveryStatus.STALE,
                        phaseKey(command.idempotencyKey(), sourceCandidateId, "archive"),
                        command.reason()
                    )
                )
                .candidate();
        } else if (isReplacementSource(current.status())) {
            replacementSource = current;
        } else {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_REMATERIALIZATION_NOT_ALLOWED",
                "Only a built or replaceable candidate can start rematerialization",
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

        CommandResult replacement = commands.createReplacement(
            access.tenantId(),
            access.actorId(),
            sourceCandidateId,
            replacementSource.version(),
            new CreateCandidateCommand(
                command.planId(),
                command.environment(),
                command.entries(),
                phaseKey(command.idempotencyKey(), sourceCandidateId, "replacement"),
                command.reason()
            )
        );
        CandidateView candidate = replacement.candidate();
        return roleAware(
            materializationStarts.start(
                access.tenantId(),
                access.actorId(),
                candidate.id(),
                candidate.version(),
                phaseKey(command.idempotencyKey(), sourceCandidateId, "start"),
                command.reason()
            ),
            access
        );
    }

    public CommandResult runQuality(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        return transition(
            tenantId,
            actorId,
            planId,
            candidateId,
            DeliveryAction.RUN_QUALITY,
            DeliveryStatus.QUALITY_RUNNING,
            expectedVersion,
            idempotencyKey,
            reason
        );
    }

    public CommandResult submitReview(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        return transition(
            tenantId,
            actorId,
            planId,
            candidateId,
            DeliveryAction.SUBMIT_REVIEW,
            DeliveryStatus.REVIEW_PENDING,
            expectedVersion,
            idempotencyKey,
            reason
        );
    }

    public CommandResult approve(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        return transition(
            tenantId,
            actorId,
            planId,
            candidateId,
            DeliveryAction.APPROVE,
            DeliveryStatus.APPROVED,
            expectedVersion,
            idempotencyKey,
            reason
        );
    }

    public CommandResult reject(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        return transition(
            tenantId,
            actorId,
            planId,
            candidateId,
            DeliveryAction.REJECT,
            DeliveryStatus.REJECTED,
            expectedVersion,
            idempotencyKey,
            reason
        );
    }

    public CommandResult publish(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = releaseAccess(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(
            access.tenantId(),
            access.planId(),
            candidateId
        );
        CommandEventView receipt = repository
            .findCommandByIdempotencyKey(access.tenantId(), idempotencyKey)
            .orElse(null);
        if (receipt != null) {
            requirePublishReplayAccess(access, candidate, receipt);
            commands.transition(
                access.tenantId(),
                access.actorId(),
                candidate.id(),
                new TransitionCommand(
                    expectedVersion,
                    DeliveryStatus.PUBLISHING,
                    idempotencyKey,
                    reason
                )
            );
            if (
                candidate.status() == DeliveryStatus.PUBLISHED ||
                candidate.status() == DeliveryStatus.PARTIAL
            ) {
                return roleAware(new CommandResult(candidate, true, List.of()), access);
            }
            if (candidate.status() != DeliveryStatus.PUBLISHING) {
                throw publishReplayConflict(candidate, idempotencyKey);
            }
            publicationAdmission.requireAllowed(candidate);
            CommandResult replayed = publicationCoordinator.publish(
                access.tenantId(),
                access.actorId(),
                candidate,
                idempotencyKey,
                reason
            );
            return roleAware(
                new CommandResult(
                    replayed.candidate(),
                    true,
                    replayed.driftReasons()
                ),
                access
            );
        }
        boolean allowed = access
            .duties()
            .stream()
            .anyMatch(role ->
                DeliveryAction.PUBLISH.isAllowedFor(
                    candidate.status(),
                    role,
                    access.actorId(),
                    candidate.audit()
                )
            );
        if (!allowed) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_DUTY_FORBIDDEN",
                "Current release duty cannot execute this candidate command",
                Kind.FORBIDDEN,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "currentStatus",
                    candidate.status(),
                    "requiredDuty",
                    DeliveryAction.PUBLISH.requiredRole()
                )
            );
        }
        publicationAdmission.requireAllowed(candidate);
        CommandResult publishing = commands.transition(
            access.tenantId(),
            access.actorId(),
            candidate.id(),
            new TransitionCommand(
                expectedVersion,
                DeliveryStatus.PUBLISHING,
                idempotencyKey,
                reason
            )
        );
        return roleAware(
            publicationCoordinator.publish(
                access.tenantId(),
                access.actorId(),
                publishing.candidate(),
                idempotencyKey,
                reason
            ),
            access
        );
    }

    public CommandResult retryPublication(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = releaseAccess(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(
            access.tenantId(),
            access.planId(),
            candidateId
        );
        String commitKey;
        if (candidate.status() == DeliveryStatus.PUBLISHED) {
            commitKey = CandidatePublicationKeys.publicationRetry(idempotencyKey);
            CommandEventView receipt = repository
                .findCommandByIdempotencyKey(access.tenantId(), commitKey)
                .orElse(null);
            if (
                receipt != null &&
                receipt.candidateId().equals(candidate.id()) &&
                receipt.fromStatus() == DeliveryStatus.PARTIAL &&
                receipt.toStatus() == DeliveryStatus.PUBLISHED
            ) {
                requirePublicationRetryReplayAccess(access, candidate, receipt);
                CommandResult replay = commands.transition(
                    access.tenantId(),
                    access.actorId(),
                    candidate.id(),
                    new TransitionCommand(
                        expectedVersion,
                        DeliveryStatus.PUBLISHED,
                        commitKey,
                        reason
                    )
                );
                return roleAware(replay, access);
            }
        }
        boolean allowed = access
            .duties()
            .stream()
            .anyMatch(role ->
                DeliveryAction.RETRY_PUBLICATION.isAllowedFor(
                    candidate.status(),
                    role,
                    access.actorId(),
                    candidate.audit()
                )
            );
        if (!allowed) {
            throw dutyForbidden(candidate, DeliveryAction.RETRY_PUBLICATION);
        }
        publicationAdmission.requireAllowed(candidate);
        return roleAware(
            publicationCoordinator.publish(
                access.tenantId(),
                access.actorId(),
                candidate,
                idempotencyKey,
                reason
            ),
            access
        );
    }

    public CommandResult rollback(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = releaseAccess(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(
            access.tenantId(),
            access.planId(),
            candidateId
        );
        CommandEventView receipt = repository
            .findCommandByIdempotencyKey(access.tenantId(), idempotencyKey)
            .orElse(null);
        if (receipt != null) {
            if (
                !candidate.id().equals(receipt.candidateId()) ||
                receipt.fromStatus() != DeliveryStatus.PUBLISHED ||
                receipt.toStatus() != DeliveryStatus.ROLLED_BACK ||
                !access.actorId().equals(receipt.actorId()) ||
                !access.duties().contains(DeliveryActorRole.RELEASE_OPERATOR)
            ) {
                throw publishReplayConflict(candidate, idempotencyKey);
            }
            return roleAware(
                commands.transition(
                    access.tenantId(),
                    access.actorId(),
                    candidate.id(),
                    new TransitionCommand(
                        expectedVersion,
                        DeliveryStatus.ROLLED_BACK,
                        idempotencyKey,
                        reason
                    )
                ),
                access
            );
        }
        boolean allowed = access
            .duties()
            .stream()
            .anyMatch(role ->
                DeliveryAction.ROLLBACK.isAllowedFor(
                    candidate.status(),
                    role,
                    access.actorId(),
                    candidate.audit()
                )
            );
        if (!allowed) {
            throw dutyForbidden(candidate, DeliveryAction.ROLLBACK);
        }
        publicationAdmission.requireArchiveAllowed(candidate);
        return roleAware(
            rollbackCommits.rollback(
                access.tenantId(),
                access.actorId(),
                candidate,
                idempotencyKey,
                reason
            ),
            access
        );
    }

    public static String etag(CandidateView candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        return "\"release-candidate:" + candidate.id() + ":" + candidate.version() + "\"";
    }

    private WorkbenchView empty(UUID planId, Set<DeliveryActorRole> duties) {
        return new WorkbenchView(
            planId,
            WorkbenchState.EMPTY,
            null,
            unavailableEvidence(),
            new BlockerView(
                "MODEL_RELEASE_CANDIDATE_REQUIRED",
                "Create a release candidate before starting build and quality work"
            ),
            duties.contains(DeliveryActorRole.MODEL_MAINTAINER)
                ? List.of(WorkspaceAction.CREATE_CANDIDATE)
                : List.of(),
            null
        );
    }

    private WorkbenchView project(
        CandidateView candidate,
        String actorId,
        Set<DeliveryActorRole> duties
    ) {
        if (candidate.status() == DeliveryStatus.CANCELLED) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_REPLACEMENT_REQUIRED",
                "Create a replacement candidate to continue delivery",
                visibleActions(
                    candidate,
                    actorId,
                    duties,
                    List.of(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE)
                )
            );
        }
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
                    ? visibleActions(
                        candidate,
                        actorId,
                        duties,
                        List.of(WorkspaceAction.UPDATE_SCOPE, WorkspaceAction.CANCEL_CANDIDATE)
                    )
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
                visibleActions(
                    candidate,
                    actorId,
                    duties,
                    List.of(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE)
                )
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
                    visibleActions(
                        candidate,
                        actorId,
                        duties,
                        List.of(WorkspaceAction.REFRESH_CANDIDATE)
                    )
            );
        }
        }
        if (candidate.status() == DeliveryStatus.BUILD_FAILED) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_BUILD_FAILED",
                "The latest build failed and must be retried",
                visibleActions(
                    candidate,
                    actorId,
                    duties,
                    List.of(WorkspaceAction.RETRY_BUILD, WorkspaceAction.CANCEL_CANDIDATE)
                )
            );
        }
        if (candidate.status() == DeliveryStatus.QUALITY_FAILED) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_QUALITY_FAILED",
                "The latest quality run failed and must be rerun",
                visibleActions(
                    candidate,
                    actorId,
                    duties,
                    List.of(WorkspaceAction.RUN_QUALITY, WorkspaceAction.CANCEL_CANDIDATE)
                )
            );
        }
        if (
            candidate.status() == DeliveryStatus.REJECTED ||
            candidate.status() == DeliveryStatus.ROLLED_BACK
        ) {
            return blocked(
                candidate,
                WorkbenchState.BLOCKED,
                "MODEL_RELEASE_CANDIDATE_REPLACEMENT_REQUIRED",
                "Create a replacement candidate to continue delivery",
                visibleActions(
                    candidate,
                    actorId,
                    duties,
                    List.of(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE)
                )
            );
        }
        return new WorkbenchView(
            candidate.planId(),
            WorkbenchState.READY,
            candidate,
            unavailableEvidence(),
            null,
            actions(candidate, actorId, duties),
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

    private List<WorkspaceAction> actions(
        CandidateView candidate,
        String actorId,
        Set<DeliveryActorRole> duties
    ) {
        if (candidate.status() == DeliveryStatus.DRAFT) {
            return visibleActions(
                candidate,
                actorId,
                duties,
                List.of(
                    WorkspaceAction.UPDATE_SCOPE,
                    WorkspaceAction.START_BUILD,
                    WorkspaceAction.CANCEL_CANDIDATE
                )
            );
        }
        List<WorkspaceAction> result = candidate
            .status()
            .allowedActions()
            .stream()
            .filter(action ->
                duties
                    .stream()
                    .anyMatch(role ->
                        action.isAllowedFor(
                            candidate.status(),
                            role,
                            actorId,
                            candidate.audit()
                        )
                    )
            )
            .map(action -> WorkspaceAction.valueOf(action.name()))
            .toList();
        if (
            candidate.status() == DeliveryStatus.BUILT &&
            duties.contains(DeliveryActorRole.MODEL_MAINTAINER)
        ) {
            return java.util.stream.Stream
                .concat(result.stream(), java.util.stream.Stream.of(WorkspaceAction.REMATERIALIZE))
                .toList();
        }
        return result;
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

    private WorkbenchView withPersistedEvidence(
        WorkbenchView view,
        List<EntryEvidenceView> entryEvidence
    ) {
        List<EntryEvidenceView> entries = entryEvidence == null
            ? List.of()
            : List.copyOf(entryEvidence);
        return new WorkbenchView(
            view.planId(),
            view.state(),
            view.candidate(),
            evidence(view.candidate(), entries),
            entries,
            view.primaryBlocker(),
            view.allowedActions(),
            view.etag()
        );
    }

    private List<EvidenceSummaryView> evidence(
        CandidateView candidate,
        List<EntryEvidenceView> entries
    ) {
        Map<DeliveryEvidenceType, EvidenceSummaryView> summaries =
            new EnumMap<>(DeliveryEvidenceType.class);
        unavailableEvidence().forEach(item ->
            summaries.put(item.type(), item)
        );
        if (
            !entries.isEmpty() &&
            entries
                .stream()
                .allMatch(item -> item.pipelineRunGroupId() != null)
        ) {
            summaries.put(
                DeliveryEvidenceType.ARTIFACT,
                artifactEvidence(entries)
            );
            summaries.put(
                DeliveryEvidenceType.BUILD_RUN,
                buildEvidence(entries)
            );
        }
        applyLifecycleEvidence(candidate.status(), summaries);
        return Arrays
            .stream(DeliveryEvidenceType.values())
            .map(summaries::get)
            .toList();
    }

    private static EvidenceSummaryView artifactEvidence(
        List<EntryEvidenceView> entries
    ) {
        if (
            entries
                .stream()
                .anyMatch(item ->
                    "FAILED".equals(item.runStatus()) ||
                    "BLOCKED".equals(item.runStatus())
                )
        ) {
            return failedEvidence(
                DeliveryEvidenceType.ARTIFACT,
                entries,
                "MODEL_MATERIALIZATION_ARTIFACT_FAILED",
                "Current dbt artifact synchronization failed"
            );
        }
        boolean passed = entries
            .stream()
            .allMatch(item ->
                "DBT_SUCCEEDED".equals(item.runStatus()) ||
                "BUILT".equals(item.runStatus())
            );
        return new EvidenceSummaryView(
            DeliveryEvidenceType.ARTIFACT,
            passed ? EvidenceState.PASSED : EvidenceState.RUNNING,
            null,
            null
        );
    }

    private static EvidenceSummaryView buildEvidence(
        List<EntryEvidenceView> entries
    ) {
        if (
            entries
                .stream()
                .anyMatch(item ->
                    item.relationState() ==
                        RelationEvidenceState.FAILED ||
                    "FAILED".equals(item.runStatus()) ||
                    "BLOCKED".equals(item.runStatus())
                )
        ) {
            return failedEvidence(
                DeliveryEvidenceType.BUILD_RUN,
                entries,
                "MODEL_PHYSICAL_RELATION_VERIFICATION_FAILED",
                "Current build or physical relation verification failed"
            );
        }
        if (
            entries
                .stream()
                .anyMatch(item ->
                    item.relationState() ==
                    RelationEvidenceState.UNKNOWN
                )
        ) {
            return new EvidenceSummaryView(
                DeliveryEvidenceType.BUILD_RUN,
                EvidenceState.STALE,
                "MODEL_MATERIALIZATION_DISPATCH_UNKNOWN",
                "Airflow dispatch outcome requires reconciliation"
            );
        }
        boolean passed = entries
            .stream()
            .allMatch(item ->
                "BUILT".equals(item.runStatus()) &&
                item.relationState() ==
                RelationEvidenceState.VERIFIED
            );
        return new EvidenceSummaryView(
            DeliveryEvidenceType.BUILD_RUN,
            passed ? EvidenceState.PASSED : EvidenceState.RUNNING,
            null,
            null
        );
    }

    private static EvidenceSummaryView failedEvidence(
        DeliveryEvidenceType type,
        List<EntryEvidenceView> entries,
        String fallbackCode,
        String message
    ) {
        String code = entries
            .stream()
            .map(EntryEvidenceView::repairCode)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(fallbackCode);
        return new EvidenceSummaryView(
            type,
            EvidenceState.FAILED,
            code,
            message
        );
    }

    private static void applyLifecycleEvidence(
        DeliveryStatus status,
        Map<DeliveryEvidenceType, EvidenceSummaryView> summaries
    ) {
        switch (status) {
            case QUALITY_RUNNING ->
                summaries.put(
                    DeliveryEvidenceType.QUALITY_RUN,
                    running(DeliveryEvidenceType.QUALITY_RUN)
                );
            case QUALITY_FAILED ->
                summaries.put(
                    DeliveryEvidenceType.QUALITY_RUN,
                    failed(
                        DeliveryEvidenceType.QUALITY_RUN,
                        "MODEL_RELEASE_CANDIDATE_QUALITY_FAILED",
                        "Current quality run failed"
                    )
                );
            case QUALITY_PASSED, REVIEW_PENDING, REJECTED, APPROVED,
                PUBLISHING, PARTIAL, PUBLISHED, ROLLED_BACK ->
                summaries.put(
                    DeliveryEvidenceType.QUALITY_RUN,
                    passed(DeliveryEvidenceType.QUALITY_RUN)
                );
            default -> {}
        }
        switch (status) {
            case REVIEW_PENDING ->
                summaries.put(
                    DeliveryEvidenceType.REVIEW,
                    running(DeliveryEvidenceType.REVIEW)
                );
            case REJECTED ->
                summaries.put(
                    DeliveryEvidenceType.REVIEW,
                    failed(
                        DeliveryEvidenceType.REVIEW,
                        "MODEL_RELEASE_CANDIDATE_REJECTED",
                        "Current review rejected the candidate"
                    )
                );
            case APPROVED, PUBLISHING, PARTIAL, PUBLISHED, ROLLED_BACK ->
                summaries.put(
                    DeliveryEvidenceType.REVIEW,
                    passed(DeliveryEvidenceType.REVIEW)
                );
            default -> {}
        }
        switch (status) {
            case PUBLISHING -> {
                summaries.put(
                    DeliveryEvidenceType.PUBLICATION,
                    running(DeliveryEvidenceType.PUBLICATION)
                );
                summaries.put(
                    DeliveryEvidenceType.REGISTRATION,
                    running(DeliveryEvidenceType.REGISTRATION)
                );
            }
            case PARTIAL -> {
                summaries.put(
                    DeliveryEvidenceType.PUBLICATION,
                    failed(
                        DeliveryEvidenceType.PUBLICATION,
                        "MODEL_RELEASE_PUBLICATION_PARTIAL",
                        "Publication did not complete atomically"
                    )
                );
                summaries.put(
                    DeliveryEvidenceType.REGISTRATION,
                    failed(
                        DeliveryEvidenceType.REGISTRATION,
                        "MODEL_RELEASE_REGISTRATION_FAILED",
                        "Physical asset registration requires repair"
                    )
                );
            }
            case PUBLISHED, ROLLED_BACK -> {
                summaries.put(
                    DeliveryEvidenceType.PUBLICATION,
                    passed(DeliveryEvidenceType.PUBLICATION)
                );
                summaries.put(
                    DeliveryEvidenceType.REGISTRATION,
                    passed(DeliveryEvidenceType.REGISTRATION)
                );
            }
            default -> {}
        }
        if (status == DeliveryStatus.ROLLED_BACK) {
            summaries.put(
                DeliveryEvidenceType.ROLLBACK,
                passed(DeliveryEvidenceType.ROLLBACK)
            );
        }
    }

    private static EvidenceSummaryView running(
        DeliveryEvidenceType type
    ) {
        return new EvidenceSummaryView(
            type,
            EvidenceState.RUNNING,
            null,
            null
        );
    }

    private static EvidenceSummaryView passed(
        DeliveryEvidenceType type
    ) {
        return new EvidenceSummaryView(
            type,
            EvidenceState.PASSED,
            null,
            null
        );
    }

    private static EvidenceSummaryView failed(
        DeliveryEvidenceType type,
        String code,
        String message
    ) {
        return new EvidenceSummaryView(
            type,
            EvidenceState.FAILED,
            code,
            message
        );
    }

    private Access authorizeRead(String tenantId, String actorId, UUID planId) {
        Access access = releaseAccess(tenantId, actorId, planId);
        if (
            access.duties().equals(Set.of(DeliveryActorRole.MODEL_MAINTAINER)) &&
            !planAccess.canMaintain(access.tenantId(), access.planId(), access.actorId())
        ) {
            throw planForbidden();
        }
        return access;
    }

    private Access authorizeMaintainer(String tenantId, String actorId, UUID planId) {
        Access access = releaseAccess(tenantId, actorId, planId);
        if (
            !access.duties().contains(DeliveryActorRole.MODEL_MAINTAINER) ||
            !planAccess.canMaintain(access.tenantId(), access.planId(), access.actorId())
        ) {
            throw planForbidden();
        }
        return access;
    }

    private void requirePublishReplayAccess(
        Access access,
        CandidateView candidate,
        CommandEventView receipt
    ) {
        boolean receiptMatches =
            candidate.id().equals(receipt.candidateId()) &&
            receipt.eventType() == ModelReleaseCandidateContract.CommandEventType.STATUS_CHANGED &&
            receipt.fromStatus() == DeliveryStatus.APPROVED &&
            receipt.toStatus() == DeliveryStatus.PUBLISHING &&
            access.actorId().equals(receipt.actorId());
        boolean separated =
            candidate.audit().hasApproval() &&
            !access.actorId().equals(candidate.audit().submittedBy()) &&
            !access.actorId().equals(candidate.audit().approvedBy());
        if (
            !receiptMatches ||
            !separated ||
            !access.duties().contains(DeliveryActorRole.RELEASE_OPERATOR)
        ) {
            throw publishReplayConflict(candidate, receipt.idempotencyKey());
        }
    }

    private void requirePublicationRetryReplayAccess(
        Access access,
        CandidateView candidate,
        CommandEventView receipt
    ) {
        if (
            !candidate.id().equals(receipt.candidateId()) ||
            !access.actorId().equals(receipt.actorId()) ||
            !access.duties().contains(DeliveryActorRole.RELEASE_OPERATOR)
        ) {
            throw publishReplayConflict(candidate, receipt.idempotencyKey());
        }
    }

    private static ModelReleaseCandidateException publishReplayConflict(
        CandidateView candidate,
        String idempotencyKey
    ) {
        return new ModelReleaseCandidateException(
            ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE,
            "Publication idempotency key belongs to another command",
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "idempotencyKey",
                idempotencyKey
            )
        );
    }

    private static ModelReleaseCandidateException dutyForbidden(
        CandidateView candidate,
        DeliveryAction action
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CANDIDATE_DUTY_FORBIDDEN",
            "Current release duty cannot execute this candidate command",
            Kind.FORBIDDEN,
            Map.of(
                "candidateId",
                candidate.id(),
                "currentStatus",
                candidate.status(),
                "requiredDuty",
                action.requiredRole()
            )
        );
    }

    private Access releaseAccess(String tenantId, String actorId, UUID planId) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        Set<DeliveryActorRole> duties = dutyResolver.currentDuties();
        if (planId == null || duties == null || duties.isEmpty()) {
            throw planForbidden();
        }
        return new Access(tenant, actor, planId, Set.copyOf(duties));
    }

    private ModelReleaseCandidateException planForbidden() {
        return new ModelReleaseCandidateException(
            PLAN_FORBIDDEN,
            "Warehouse plan is not available for this release duty",
            Kind.FORBIDDEN
        );
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
            event.fromStatus() == DeliveryStatus.BUILD_FAILED &&
            (
                event.toStatus() == DeliveryStatus.BUILDING ||
                event.toStatus() == DeliveryStatus.STALE
            );
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
        return buildRetry
            ? DeliveryStatus.BUILDING
            : DeliveryStatus.QUALITY_RUNNING;
    }

    private static boolean isActive(CandidateView candidate) {
        return candidate != null && !isReplacementSource(candidate.status());
    }

    private static boolean isReplacementSource(DeliveryStatus status) {
        return (
            status == DeliveryStatus.REJECTED ||
            status == DeliveryStatus.ROLLED_BACK ||
            status == DeliveryStatus.CANCELLED ||
            status == DeliveryStatus.STALE
        );
    }

    private static String phaseKey(String idempotencyKey, UUID candidateId, String phase) {
        return UUID
            .nameUUIDFromBytes((idempotencyKey + "|" + candidateId + "|" + phase).getBytes(StandardCharsets.UTF_8))
            .toString();
    }

    private CommandResult transition(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        DeliveryAction action,
        DeliveryStatus target,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Access access = action.requiredRole() == DeliveryActorRole.MODEL_MAINTAINER
            ? authorizeMaintainer(tenantId, actorId, planId)
            : releaseAccess(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(
            access.tenantId(),
            access.planId(),
            candidateId
        );
        boolean allowed = access
            .duties()
            .stream()
            .anyMatch(role ->
                action.isAllowedFor(
                    candidate.status(),
                    role,
                    access.actorId(),
                    candidate.audit()
                )
            );
        if (!allowed) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_DUTY_FORBIDDEN",
                "Current release duty cannot execute this candidate command",
                Kind.FORBIDDEN,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "currentStatus",
                    candidate.status(),
                    "requiredDuty",
                    action.requiredRole()
                )
            );
        }
        return roleAware(
            commands.transition(
                access.tenantId(),
                access.actorId(),
                candidate.id(),
                new TransitionCommand(
                    expectedVersion,
                    target,
                    idempotencyKey,
                    reason
                )
            ),
            access
        );
    }

    private CommandResult roleAware(CommandResult result, Access access) {
        CandidateView candidate = result.candidate();
        return new CommandResult(
            candidate,
            result.replayed(),
            result.driftReasons(),
            candidate.entries().isEmpty()
                ? List.of()
                : candidate
                    .status()
                    .allowedActions()
                    .stream()
                    .filter(action ->
                        access
                            .duties()
                            .stream()
                            .anyMatch(role ->
                                action.isAllowedFor(
                                    candidate.status(),
                                    role,
                                    access.actorId(),
                                    candidate.audit()
                                )
                            )
                    )
                    .toList()
        );
    }

    private List<WorkspaceAction> visibleActions(
        CandidateView candidate,
        String actorId,
        Set<DeliveryActorRole> duties,
        List<WorkspaceAction> proposed
    ) {
        return proposed
            .stream()
            .filter(action -> {
                if (
                    action == WorkspaceAction.UPDATE_SCOPE ||
                    action == WorkspaceAction.REFRESH_CANDIDATE
                ) {
                    return duties.contains(DeliveryActorRole.MODEL_MAINTAINER);
                }
                DeliveryAction deliveryAction = DeliveryAction.valueOf(action.name());
                return duties
                    .stream()
                    .anyMatch(role ->
                        deliveryAction.isAllowedFor(
                            candidate.status(),
                            role,
                            actorId,
                            candidate.audit()
                        )
                    );
            })
            .toList();
    }

    private record Access(
        String tenantId,
        String actorId,
        UUID planId,
        Set<DeliveryActorRole> duties
    ) {}
}
