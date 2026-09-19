package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort;
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
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ModelMaterializationStatusView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.MaterializationAttemptView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.RelationEvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Preview;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.PreviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanService.ValidatedPlan;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidatePreflightService.BatchPreflightView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
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
    private final WarehousePlanOperationsReadPort planReadAccess;
    private final ReleaseDutyResolver dutyResolver;
    private final CandidatePublicationAdmissionService publicationAdmission;
    private final CandidatePublicationCoordinator publicationCoordinator;
    private final CandidateRollbackCommitService rollbackCommits;
    private final ReleaseCandidateWorkbenchEvidencePort workbenchEvidence;
    private final ModelMaterializationPlanService materializationPlans;
    private final ModelReleaseCandidatePreflightService preflight;
    private final CandidateGovernanceQualityEvidenceService governanceQuality;
    private CandidateQualityAssetRegistrationService qualityAssets;

    public ModelReleaseCandidateApplicationService(
        ModelReleaseCandidateRepository repository,
        ModelReleaseCandidateService commands,
        ModelMaterializationStartService materializationStarts,
        ModelSpecPlanWriteAccessPort planAccess,
        WarehousePlanOperationsReadPort planReadAccess,
        ReleaseDutyResolver dutyResolver,
        CandidatePublicationAdmissionService publicationAdmission,
        CandidatePublicationCoordinator publicationCoordinator,
        CandidateRollbackCommitService rollbackCommits,
        ReleaseCandidateWorkbenchEvidencePort workbenchEvidence,
        ModelMaterializationPlanService materializationPlans,
        ModelReleaseCandidatePreflightService preflight,
        CandidateGovernanceQualityEvidenceService governanceQuality
    ) {
        this.repository = repository;
        this.commands = commands;
        this.materializationStarts = materializationStarts;
        this.planAccess = planAccess;
        this.planReadAccess = planReadAccess;
        this.dutyResolver = dutyResolver;
        this.publicationAdmission = publicationAdmission;
        this.publicationCoordinator = publicationCoordinator;
        this.rollbackCommits = rollbackCommits;
        this.workbenchEvidence = workbenchEvidence;
        this.materializationPlans = materializationPlans;
        this.preflight = preflight;
        this.governanceQuality = governanceQuality;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ModelReleaseCandidateApplicationService(
        ModelReleaseCandidateRepository repository,
        ModelReleaseCandidateService commands,
        ModelMaterializationStartService materializationStarts,
        ModelSpecPlanWriteAccessPort planAccess,
        WarehousePlanOperationsReadPort planReadAccess,
        ReleaseDutyResolver dutyResolver,
        CandidatePublicationAdmissionService publicationAdmission,
        CandidatePublicationCoordinator publicationCoordinator,
        CandidateRollbackCommitService rollbackCommits,
        ReleaseCandidateWorkbenchEvidencePort workbenchEvidence,
        ModelMaterializationPlanService materializationPlans,
        ModelReleaseCandidatePreflightService preflight,
        CandidateGovernanceQualityEvidenceService governanceQuality,
        CandidateQualityAssetRegistrationService qualityAssets
    ) {
        this(repository, commands, materializationStarts, planAccess, planReadAccess, dutyResolver,
            publicationAdmission, publicationCoordinator, rollbackCommits, workbenchEvidence,
            materializationPlans, preflight, governanceQuality);
        this.qualityAssets = qualityAssets;
    }

    @Transactional(readOnly = true)
    public WorkbenchView workspace(String tenantId, String actorId, UUID planId) {
        Access access = authorizeRead(tenantId, actorId, planId);
        List<CandidateView> candidates = repository.listForWorkbench(access.tenantId(), access.planId()).stream()
            .filter(candidate -> candidate.origin() != ModelReleaseCandidateContract.CandidateOrigin.SCHEMA_ONLY_INTENT).toList();
        // Legacy plan overview shows the latest candidate; model actions use scoped reads.
        if (candidates.size() > 1 && isActive(candidates.get(1)) && ModelCandidateScopePolicy.conflicts(
            candidates.get(0), candidates.get(1).origin(), candidates.get(1).environment(),
            candidates.get(1).entries().stream().map(ModelReleaseCandidateContract.EntryView::modelSpecId).toList())) {
            throw new ModelReleaseCandidateException("MODEL_RELEASE_CANDIDATE_CURRENT_AMBIGUOUS",
                "Multiple active candidates reserve the same model scope", Kind.CONFLICT);
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
    public WorkbenchView workspaceForScope(String tenantId, String actorId, UUID planId, String environment, List<UUID> modelIds) {
        Access access = authorizeRead(tenantId, actorId, planId);
        if (environment == null || environment.isBlank() || modelIds == null || modelIds.isEmpty() ||
            modelIds.size() > ModelReleaseCandidateContract.MAX_ROOT_ENTRIES || modelIds.stream().anyMatch(Objects::isNull)) {
            throw badRequest("MODEL_RELEASE_CANDIDATE_SCOPE_INVALID", "Environment and a bounded model scope are required");
        }
        List<CandidateView> matches = repository.listForModelScope(access.tenantId(), planId, environment.trim(), modelIds);
        if (matches.size() > 1 && isActive(matches.get(0)) && isActive(matches.get(1))) {
            throw new ModelReleaseCandidateException("MODEL_RELEASE_CANDIDATE_SCOPE_SPLIT",
                "所选模型分别属于多个活动候选，请分别处理已有候选后再进行批量操作", Kind.CONFLICT);
        }
        if (matches.isEmpty()) return empty(planId, access.duties());
        CandidateView selected = matches.getFirst();
        return withPersistedEvidence(project(selected, access.actorId(), access.duties()), workbenchEvidence.findCurrent(selected));
    }

    /** Server-authorized command projection for a read-only delivery consumer. */
    @Transactional(readOnly = true)
    public List<DeliveryAction> allowedActionsForRead(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId
    ) {
        Access access = authorizeRead(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return roleAware(new CommandResult(candidate, false, List.of()), access).allowedActions();
    }

    /** Read-only eligibility for UI-only auxiliary actions that are not DeliveryAction enum values. */
    @Transactional(readOnly = true)
    public boolean canMaintainForRead(String tenantId, String actorId, UUID planId) {
        return authorizeRead(tenantId, actorId, planId).duties().contains(DeliveryActorRole.MODEL_MAINTAINER);
    }

    /** Exact candidate workbench projection for a model-bound read consumer. */
    @Transactional(readOnly = true)
    public WorkbenchView workspaceForCandidate(String tenantId, String actorId, UUID planId, UUID candidateId) {
        Access access = authorizeRead(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return withPersistedEvidence(project(candidate, access.actorId(), access.duties()), workbenchEvidence.findCurrent(candidate));
    }

    /** Read-only model-scoped candidate projection; never creates a candidate or falls back to another model. */
    @Transactional(readOnly = true)
    public WorkbenchView workspaceForCurrentModel(
        String tenantId,
        String actorId,
        UUID planId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        String environment
    ) {
        Access access = authorizeRead(tenantId, actorId, planId);
        CandidateView candidate = repository.findLatestForModelCurrentRevision(
            access.tenantId(),
            access.planId(),
            modelSpecId,
            modelRevision,
            modelChecksum,
            environment
        ).orElse(null);
        if (candidate == null) return empty(access.planId(), access.duties());
        return withPersistedEvidence(project(candidate, access.actorId(), access.duties()), workbenchEvidence.findCurrent(candidate));
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
            modelSpecIds.size() > ModelReleaseCandidateContract.MAX_ROOT_ENTRIES ||
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

    @Transactional(readOnly = true)
    public List<MaterializationAttemptView> materializationHistory(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId
    ) {
        Access access = authorizeRead(tenantId, actorId, planId);
        CandidateView candidate = candidateForPlan(access.tenantId(), access.planId(), candidateId);
        return workbenchEvidence.findHistory(candidate);
    }

    @Transactional(readOnly = true)
    public Preview previewMaterializationPlan(
        String tenantId,
        String actorId,
        UUID planId,
        PreviewCommand command
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_MATERIALIZATION_PLAN_SCOPE_MISMATCH",
                "Materialization plan must match the plan in the request path"
            );
        }
        return materializationPlans.preview(access.tenantId(), command);
    }

    @Transactional
    public CommandResult create(
        String tenantId,
        String actorId,
        UUID planId,
        CreateCandidateCommand command
    ) {
        return create(tenantId, actorId, planId, command, null, null);
    }

    @Transactional
    public CommandResult create(
        String tenantId,
        String actorId,
        UUID planId,
        CreateCandidateCommand command,
        String materializationPlanChecksum,
        Strategy strategy
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Candidate plan must match the plan in the request path"
            );
        }
        if (repository.findByIdempotencyKey(access.tenantId(), command.idempotencyKey()).isPresent()) {
            return roleAware(
                commands.createBatchWithExpandedScope(
                    access.tenantId(),
                    access.actorId(),
                    command,
                    command.entries()
                ),
                access
            );
        }
        repository.lockPlanForCandidate(access.tenantId(), access.planId());
        CandidateView current = repository
            .listActiveForPlan(access.tenantId(), access.planId())
            .stream()
            .filter(candidate -> ModelCandidateScopePolicy.conflicts(candidate,
                ModelReleaseCandidateContract.CandidateOrigin.BATCH_WORKBENCH, command.environment(),
                command.entries().stream().map(ModelReleaseCandidateContract.ScopeEntryCommand::modelSpecId).toList()))
            .findFirst()
            .orElse(null);
        if (current != null) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS",
                "所选模型在当前环境已有活动候选，请处理该模型的已有候选",
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
        List<ModelReleaseCandidateContract.ScopeEntryCommand> expandedEntries;
        if (materializationPlanChecksum == null) {
            ModelReleaseCandidatePreflightService.PreflightResult evaluated = preflight.requireEligible(
                access.tenantId(),
                command
            );
            expandedEntries = evaluated.command().entries();
        } else {
            expandedEntries = materializationPlans
                .requireCurrent(
                    access.tenantId(),
                    materializationCommand(access.planId(), command, strategy),
                    materializationPlanChecksum
                )
                .buildEntries();
        }
        return roleAware(
            commands.createBatchWithExpandedScope(
                access.tenantId(),
                access.actorId(),
                command,
                expandedEntries
            ),
            access
        );
    }

    @Transactional(readOnly = true)
    public BatchPreflightView preflight(
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
        return preflight.preview(access.tenantId(), command);
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

    /**
     * Wired optionally so the many narrow unit fixtures of this service need not provide the
     * dispatcher; without it no build can be abandoned.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBuildDispatches(ModelMaterializationDispatchService buildDispatches) {
        this.buildDispatches = buildDispatches;
    }

    private ModelMaterializationDispatchService buildDispatches;

    /** Gives a maintainer an exit from a build whose dispatch stopped making progress. */
    public CommandResult abandonBuild(
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
        if (buildDispatches == null) {
            throw new ModelReleaseCandidateException(
                "MODEL_MATERIALIZATION_ABANDON_UNAVAILABLE",
                "Build abandonment is not available",
                Kind.CONFLICT
            );
        }
        return roleAware(
            buildDispatches.abandonBuild(
                access.tenantId(),
                access.actorId(),
                candidateId,
                current.version(),
                expectedVersion,
                idempotencyKey,
                reason
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

    @Transactional
    public CommandResult createReplacement(
        String tenantId,
        String actorId,
        UUID planId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command
    ) {
        return createReplacement(
            tenantId,
            actorId,
            planId,
            sourceCandidateId,
            expectedVersion,
            command,
            null,
            null
        );
    }

    @Transactional
    public CommandResult createReplacement(
        String tenantId,
        String actorId,
        UUID planId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command,
        String materializationPlanChecksum,
        Strategy strategy
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        candidateForPlan(access.tenantId(), access.planId(), sourceCandidateId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Replacement plan must match the plan in the request path"
            );
        }
        if (materializationPlanChecksum == null) {
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
        repository.lockPlanForCandidate(access.tenantId(), access.planId());
        List<ModelReleaseCandidateContract.ScopeEntryCommand> expandedEntries = repository
            .findByIdempotencyKey(access.tenantId(), command.idempotencyKey())
            .isPresent()
            ? command.entries()
            : materializationPlans
                .requireCurrent(
                    access.tenantId(),
                    materializationCommand(access.planId(), command, strategy),
                    materializationPlanChecksum
                )
                .buildEntries();
        return roleAware(
            commands.createReplacementWithExpandedScope(
                access.tenantId(),
                access.actorId(),
                sourceCandidateId,
                expectedVersion,
                command,
                expandedEntries
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
        return rematerialize(
            tenantId,
            actorId,
            planId,
            sourceCandidateId,
            expectedVersion,
            command,
            null,
            null
        );
    }

    @Transactional
    public CommandResult rematerialize(
        String tenantId,
        String actorId,
        UUID planId,
        UUID sourceCandidateId,
        int expectedVersion,
        CreateCandidateCommand command,
        String materializationPlanChecksum,
        Strategy strategy
    ) {
        Access access = authorizeMaintainer(tenantId, actorId, planId);
        if (command == null || !access.planId().equals(command.planId())) {
            throw badRequest(
                "MODEL_RELEASE_CANDIDATE_PLAN_MISMATCH",
                "Rematerialization plan must match the plan in the request path"
            );
        }
        boolean replay = repository
            .findCommandByIdempotencyKey(access.tenantId(), command.idempotencyKey())
            .isPresent();
        if (!replay) repository.lockPlanForCandidate(access.tenantId(), access.planId());
        CandidateView current = candidateForPlan(access.tenantId(), access.planId(), sourceCandidateId);
        Set<UUID> currentScope = current
            .entries()
            .stream()
            .map(ModelReleaseCandidateContract.EntryView::modelSpecId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> requestedScope = command
            .entries()
            .stream()
            .map(ModelReleaseCandidateContract.ScopeEntryCommand::modelSpecId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> immutableRoots = candidateRoots(current);
        List<UUID> buildModelSpecIds = current.entries().stream().map(ModelReleaseCandidateContract.EntryView::modelSpecId).toList();
        if (materializationPlanChecksum != null && !replay) {
            if (!immutableRoots.equals(requestedScope)) {
                throw rematerializationScopeMismatch(current, requestedScope, immutableRoots);
            }
            ValidatedPlan validated = materializationPlans.requireCurrent(
                access.tenantId(),
                materializationCommand(access.planId(), command, strategy),
                materializationPlanChecksum
            );
            buildModelSpecIds = validated
                .buildEntries()
                .stream()
                .map(ModelReleaseCandidateContract.ScopeEntryCommand::modelSpecId)
                .toList();
            Set<UUID> validatedBuildScope = Set.copyOf(buildModelSpecIds);
            if (
                buildModelSpecIds.isEmpty() ||
                validatedBuildScope.size() != buildModelSpecIds.size() ||
                !currentScope.containsAll(validatedBuildScope) ||
                !validatedBuildScope.containsAll(immutableRoots)
            ) {
                throw rematerializationScopeMismatch(current, validatedBuildScope, immutableRoots);
            }
        }
        if (
            !current.environment().equals(command.environment()) ||
            (materializationPlanChecksum == null && !requestedScope.isEmpty() && !currentScope.equals(requestedScope))
        ) {
            throw rematerializationScopeMismatch(current, requestedScope, immutableRoots);
        }
        return roleAware(
            materializationStarts.rematerialize(
                access.tenantId(),
                access.actorId(),
                sourceCandidateId,
                expectedVersion,
                command.idempotencyKey(),
                command.reason(),
                replay ? List.of() : buildModelSpecIds
            ),
            access
        );
    }

    private static Set<UUID> candidateRoots(CandidateView candidate) {
        Set<UUID> explicit = candidate
            .entries()
            .stream()
            .filter(entry -> "MATERIALIZATION_ROOT".equals(entry.selectedReason()))
            .map(ModelReleaseCandidateContract.EntryView::modelSpecId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!explicit.isEmpty()) return explicit;
        return candidate
            .entries()
            .stream()
            .filter(entry -> !"AUTO_DEPENDENCY".equals(entry.selectedReason()))
            .map(ModelReleaseCandidateContract.EntryView::modelSpecId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static ModelReleaseCandidateException rematerializationScopeMismatch(
        CandidateView candidate,
        Set<UUID> requestedScope,
        Set<UUID> immutableRoots
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_REMATERIALIZATION_SCOPE_MISMATCH",
            "Rematerialization must retain the candidate environment and immutable root scope",
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "currentScope",
                candidate.entries().stream().map(ModelReleaseCandidateContract.EntryView::modelSpecId).toList(),
                "immutableRoots",
                immutableRoots,
                "requestedScope",
                requestedScope
            )
        );
    }

    private static PreviewCommand materializationCommand(
        UUID planId,
        CreateCandidateCommand command,
        Strategy strategy
    ) {
        return new PreviewCommand(
            planId,
            command.environment(),
            command
                .entries()
                .stream()
                .map(ModelReleaseCandidateContract.ScopeEntryCommand::modelSpecId)
                .toList(),
            strategy
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
        CandidateQualityEvidenceSnapshot qualitySnapshot = governanceQuality.requirePublishableSnapshot(candidate);
        publicationAdmission.requireAllowed(candidate);
        CommandResult publishing = commands.transitionWithQualityEvidence(
            access.tenantId(),
            access.actorId(),
            candidate.id(),
            new TransitionCommand(
                expectedVersion,
                DeliveryStatus.PUBLISHING,
                idempotencyKey,
                reason
            ),
            qualitySnapshot
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
                actions(candidate, actorId, duties)
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
        if (!canOperate(candidate, actorId)) return List.of();
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
        result = preferSelfServicePublication(result);
        if (
            candidate.status() == DeliveryStatus.PUBLISHED &&
            duties.contains(DeliveryActorRole.MODEL_MAINTAINER)
        ) {
            return java.util.stream.Stream
                .concat(
                    java.util.stream.Stream.of(WorkspaceAction.CREATE_CANDIDATE),
                    result.stream()
                )
                .distinct()
                .toList();
        }
        if (
            candidate.status() == DeliveryStatus.BUILDING &&
            duties.contains(DeliveryActorRole.MODEL_MAINTAINER) &&
            buildDispatches != null &&
            buildDispatches.canAbandonBuild(candidate.tenantId(), candidate.id(), candidate.version())
        ) {
            return java.util.stream.Stream
                .concat(result.stream(), java.util.stream.Stream.of(WorkspaceAction.ABANDON_BUILD))
                .toList();
        }
        if (
            Set.of(DeliveryStatus.BUILT, DeliveryStatus.QUALITY_RUNNING, DeliveryStatus.QUALITY_FAILED).contains(candidate.status()) &&
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
        GovernanceQualitySummaryView evaluated = governanceQuality == null
            ? null
            : governanceQuality.evaluateForRead(view.candidate());
        GovernanceQualitySummaryView governance = evaluated == null
            ? GovernanceQualitySummaryView.notEvaluated()
            : evaluated;
        BlockerView registrationBlocker = qualityAssets != null && view.candidate() != null &&
            view.candidate().status() == DeliveryStatus.QUALITY_RUNNING
            ? qualityAssets.previewBlocker(view.candidate()) : null;
        boolean publicationBlocked = governance.required() && !governance.passed() && publicationReady(view.candidate());
        List<WorkspaceAction> actions = publicationBlocked
            ? view.allowedActions().stream().filter(action -> action != WorkspaceAction.PUBLISH).toList()
            : view.allowedActions();
        return new WorkbenchView(
            view.planId(),
            publicationBlocked || registrationBlocker != null ? WorkbenchState.BLOCKED : view.state(),
            view.candidate(),
            evidence(view.candidate(), entries),
            entries,
            governance,
            registrationBlocker != null ? registrationBlocker :
                publicationBlocked ? new BlockerView(governance.code(), governance.message()) : view.primaryBlocker(),
            actions,
            view.etag()
        );
    }

    private static boolean publicationReady(CandidateView candidate) {
        return candidate != null && Set.of(
            DeliveryStatus.QUALITY_PASSED,
            DeliveryStatus.REVIEW_PENDING,
            DeliveryStatus.APPROVED
        ).contains(candidate.status());
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
        applyLifecycleEvidence(candidate, summaries);
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
                    "FAILED_STALE".equals(item.runStatus()) ||
                    "SKIPPED_DEPENDENCY_FAILED".equals(item.runStatus()) ||
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
                    "FAILED_STALE".equals(item.runStatus()) ||
                    "SKIPPED_DEPENDENCY_FAILED".equals(item.runStatus()) ||
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
        CandidateView candidate,
        Map<DeliveryEvidenceType, EvidenceSummaryView> summaries
    ) {
        DeliveryStatus status = candidate.status();
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
            case APPROVED ->
                summaries.put(
                    DeliveryEvidenceType.REVIEW,
                    passed(DeliveryEvidenceType.REVIEW)
                );
            case PUBLISHING, PARTIAL, PUBLISHED, ROLLED_BACK -> {
                if (candidate.audit().hasApproval()) {
                    summaries.put(
                        DeliveryEvidenceType.REVIEW,
                        passed(DeliveryEvidenceType.REVIEW)
                    );
                }
            }
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
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        if (planId == null || !planAccess.canReadPlan(tenant, planId) || !planReadAccess.canReadPlan(planId)) {
            throw planForbidden();
        }
        Set<DeliveryActorRole> duties = dutyResolver.currentDuties();
        boolean canMaintain = planAccess.canMaintain(tenant, planId, actor);
        return new Access(tenant, actor, planId, !canMaintain || duties == null ? Set.of() : Set.copyOf(duties));
    }

    private Access authorizeMaintainer(String tenantId, String actorId, UUID planId) {
        Access access = authorizeRead(tenantId, actorId, planId);
        if (!planAccess.canMaintain(access.tenantId(), access.planId(), access.actorId()) || !access.duties().contains(DeliveryActorRole.MODEL_MAINTAINER)) {
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
            Set.of(
                    DeliveryStatus.QUALITY_PASSED,
                    DeliveryStatus.REVIEW_PENDING,
                    DeliveryStatus.APPROVED
                )
                .contains(receipt.fromStatus()) &&
            receipt.toStatus() == DeliveryStatus.PUBLISHING &&
            access.actorId().equals(receipt.actorId());
        boolean separated = receipt.fromStatus() != DeliveryStatus.APPROVED || (
            candidate.audit().hasApproval() &&
            !access.actorId().equals(candidate.audit().submittedBy()) &&
            !access.actorId().equals(candidate.audit().approvedBy())
        );
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
        Access access = authorizeRead(tenantId, actorId, planId);
        if (access.duties().isEmpty() || !planAccess.canMaintain(access.tenantId(), access.planId(), access.actorId())) {
            throw planForbidden();
        }
        return access;
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
        return (
            candidate != null &&
            candidate.status() != DeliveryStatus.PUBLISHED &&
            !isReplacementSource(candidate.status())
        );
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

    @org.springframework.beans.factory.annotation.Autowired
    private ModelSpecAccessService modelAccess;
    private boolean canOperate(CandidateView candidate, String actor) {
        var ids = candidate.entries().stream().map(ModelReleaseCandidateContract.EntryView::modelSpecId).distinct().toList();
        var capabilities = modelAccess.capabilities(candidate.tenantId(), ids);
        return !ids.isEmpty() && capabilities.size() == ids.size() && capabilities.values().stream().allMatch(ModelSpecAccessService.Capabilities::canEdit);
    }

    private CommandResult roleAware(CommandResult result, Access access) {
        CandidateView candidate = result.candidate();
        List<DeliveryAction> allowedActions = candidate.entries().isEmpty() || !canOperate(candidate, access.actorId())
            ? List.of()
            : preferSelfServicePublication(
                candidate
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
        return new CommandResult(
            candidate,
            result.replayed(),
            result.driftReasons(),
            allowedActions
        );
    }

    private static <T extends Enum<T>> List<T> preferSelfServicePublication(List<T> actions) {
        boolean publishAvailable = actions.stream().anyMatch(action -> "PUBLISH".equals(action.name()));
        if (!publishAvailable) return actions;
        return actions
            .stream()
            .filter(action ->
                !Set.of("SUBMIT_REVIEW", "APPROVE", "REJECT").contains(action.name())
            )
            .toList();
    }

    private List<WorkspaceAction> visibleActions(
        CandidateView candidate,
        String actorId,
        Set<DeliveryActorRole> duties,
        List<WorkspaceAction> proposed
    ) {
        if (!canOperate(candidate, actorId)) return List.of();
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
