package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.QueuedBuildGroup;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationStartService.StartResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless single-model facade over the canonical release-candidate control plane.
 *
 * <p>It never edits an existing batch scope and owns no lifecycle state. Candidate creation and
 * START_BUILD participate in one database transaction; the durable run group remains owned by
 * {@link ModelMaterializationStartService}.
 */
@Service
public class ModelBuildIntentService {

    static final String REASON = "Build current model materialization";

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelReleaseCandidateService candidateCommands;
    private final ModelMaterializationStartService materializationStarts;
    private final ModelMaterializationBuildRepository builds;
    private final ModelLifecycleRepository implementations;

    public ModelBuildIntentService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecPlanWriteAccessPort planAccess,
        ModelReleaseCandidateRepository candidates,
        ModelReleaseCandidateService candidateCommands,
        ModelMaterializationStartService materializationStarts,
        ModelMaterializationBuildRepository builds,
        ModelLifecycleRepository implementations
    ) {
        this.modelSpecs = Objects.requireNonNull(modelSpecs, "modelSpecs is required");
        this.planAccess = Objects.requireNonNull(planAccess, "planAccess is required");
        this.candidates = Objects.requireNonNull(candidates, "candidates is required");
        this.candidateCommands = Objects.requireNonNull(
            candidateCommands,
            "candidateCommands is required"
        );
        this.materializationStarts = Objects.requireNonNull(
            materializationStarts,
            "materializationStarts is required"
        );
        this.builds = Objects.requireNonNull(builds, "builds is required");
        this.implementations = Objects.requireNonNull(implementations, "implementations is required");
    }

    @Transactional
    public BuildIntentResult start(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        BuildIntentCommand command
    ) {
        String tenant = required(tenantId, "tenantId", 128);
        String actor = required(actorId, "actorId", 128);
        if (modelSpecId == null || expected == null || command == null) {
            throw invalid("Model, If-Match and build intent are required");
        }
        if (!modelSpecId.equals(expected.modelSpecId())) {
            throw invalid("If-Match identifies another ModelSpec");
        }
        UUID planId = command.planId();
        if (!planAccess.canMaintain(tenant, planId, actor)) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_PLAN_FORBIDDEN",
                "Warehouse plan is not available for maintenance",
                Kind.FORBIDDEN
            );
        }
        candidates.lockPlanForCandidate(tenant, planId);

        ModelSpecView model = modelSpecs.get(tenant, modelSpecId);
        requireCurrentModel(model, planId, expected);
        var implementation = implementations.findImplementation(tenant, modelSpecId).orElse(null);
        if (implementation != null && !implementations.lockImplementation(tenant, modelSpecId, implementation)) {
            throw new ModelReleaseCandidateException("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation changed before materialization", Kind.CONFLICT);
        }
        boolean structure = ModelSchemaOnlySupport.isSchemaOnly(implementation);
        if (structure != "SCHEMA_ONLY".equals(command.buildMode())) {
            throw new ModelReleaseCandidateException("MODEL_BUILD_MODE_MISMATCH", "Requested build mode does not match the current implementation", Kind.CONFLICT);
        }
        if (model.status() == ModelStatus.PUBLISHED) {
            throw new ModelReleaseCandidateException(
                "MODEL_OPERATIONAL_RUN_REQUIRED",
                "The model is already published; run the active plan instead of rebuilding a release candidate",
                Kind.CONFLICT,
                Map.of(
                    "modelSpecId",
                    modelSpecId,
                    "planId",
                    planId,
                    "requiredAction",
                    "OPERATIONAL_RUN"
                )
            );
        }

        CandidateView active = activeCandidate(tenant, planId, modelSpecId, command.environment(), structure);
        if (active != null) {
            return reuseOrStartExisting(
                tenant,
                actor,
                model,
                command,
                active
            );
        }

        CreateCandidateCommand create = new CreateCandidateCommand(
            planId,
            command.environment(),
            List.of(
                new ScopeEntryCommand(
                    modelSpecId,
                    0,
                    "single-model build intent"
                )
            ),
            createCommandKey(command.executionKey()),
            REASON
        );
        CandidateOrigin expectedOrigin = structure ? CandidateOrigin.SCHEMA_ONLY_INTENT : CandidateOrigin.SINGLE_MODEL_INTENT;
        CommandResult draft = structure
            ? candidateCommands.createSchemaOnlyIntent(tenant, actor, create)
            : candidateCommands.createSingleModelIntent(tenant, actor, create);
        if (
            draft.candidate().origin() != expectedOrigin ||
            !exactScope(draft.candidate(), model, command.environment())
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_BUILD_INTENT_SCOPE_INVARIANT_BROKEN",
                "Build intent did not resolve to one exact single-model candidate",
                Kind.CONFLICT
            );
        }
        StartResult started = materializationStarts.startWithBuild(
            tenant,
            actor,
            draft.candidate().id(),
            draft.candidate().version(),
            startCommandKey(command.executionKey()),
            REASON
        );
        CandidateView current = candidates
            .find(tenant, started.command().candidate().id())
            .orElse(started.command().candidate());
        return new BuildIntentResult(
            current,
            started.build(),
            draft.replayed() || started.command().replayed()
        );
    }

    private BuildIntentResult reuseOrStartExisting(
        String tenant,
        String actor,
        ModelSpecView model,
        BuildIntentCommand command,
        CandidateView active
    ) {
        if (active.origin() == CandidateOrigin.BATCH_WORKBENCH) {
            throw new ModelReleaseCandidateException(
                "MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT",
                "The plan already has an active batch release candidate",
                Kind.CONFLICT,
                candidateConflict(active, model.id())
            );
        }
        CandidateOrigin expectedOrigin = "SCHEMA_ONLY".equals(command.buildMode())
            ? CandidateOrigin.SCHEMA_ONLY_INTENT : CandidateOrigin.SINGLE_MODEL_INTENT;
        if (active.origin() != expectedOrigin) {
            throw new ModelReleaseCandidateException("MODEL_BUILD_MODE_MISMATCH",
                "An active candidate reserves this model for a different build mode", Kind.CONFLICT,
                candidateConflict(active, model.id()));
        }
        if (!exactScope(active, model, command.environment())) {
            throw new ModelReleaseCandidateException(
                "MODEL_ACTIVE_SINGLE_CANDIDATE_CONFLICT",
                "The active single-model candidate has a different model, version or environment",
                Kind.CONFLICT,
                candidateConflict(active, model.id())
            );
        }
        if (active.status() == DeliveryStatus.DRAFT) {
            StartResult started = materializationStarts.startWithBuild(
                tenant,
                actor,
                active.id(),
                active.version(),
                startCommandKey(command.executionKey()),
                REASON
            );
            CandidateView current = candidates
                .find(tenant, active.id())
                .orElse(started.command().candidate());
            return new BuildIntentResult(
                current,
                started.build(),
                started.command().replayed()
            );
        }
        if (!builds.detectForRead(active).isEmpty()) {
            throw new ModelReleaseCandidateException("MODEL_BUILD_INTENT_SNAPSHOT_STALE", "The active build no longer matches the current implementation or target", Kind.CONFLICT);
        }
        return new BuildIntentResult(active, builds.requireQueuedBuild(active), true);
    }

    private CandidateView activeCandidate(String tenant, UUID planId, UUID modelId, String environment, boolean structure) {
        List<CandidateView> active = candidates
            .listForWorkbench(tenant, planId)
            .stream()
            .filter(candidate -> ModelCandidateScopePolicy.conflicts(candidate,
                structure ? CandidateOrigin.SCHEMA_ONLY_INTENT : CandidateOrigin.SINGLE_MODEL_INTENT,
                environment, List.of(modelId)))
            .toList();
        if (active.size() > 1) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_CURRENT_AMBIGUOUS",
                "More than one active release candidate exists for the plan",
                Kind.CONFLICT,
                Map.of("planId", planId)
            );
        }
        return active.isEmpty() ? null : active.getFirst();
    }

    private static boolean exactScope(
        CandidateView candidate,
        ModelSpecView model,
        String environment
    ) {
        if (
            candidate.entries().size() != 1 ||
            !candidate.environment().equals(environment)
        ) {
            return false;
        }
        var entry = candidate.entries().getFirst();
        return (
            entry.modelSpecId().equals(model.id()) &&
            entry.revision() == model.revision() &&
            entry.checksum().equals(model.checksum())
        );
    }

    private static boolean isActive(CandidateView candidate) {
        return (
            candidate.status() != DeliveryStatus.PUBLISHED &&
            candidate.status() != DeliveryStatus.REJECTED &&
            candidate.status() != DeliveryStatus.ROLLED_BACK &&
            candidate.status() != DeliveryStatus.CANCELLED &&
            candidate.status() != DeliveryStatus.STALE
        );
    }

    private static void requireCurrentModel(
        ModelSpecView model,
        UUID planId,
        ExpectedVersion expected
    ) {
        if (model == null || !planId.equals(model.planId())) {
            throw new ModelReleaseCandidateException(
                "MODEL_BUILD_INTENT_PLAN_MISMATCH",
                "ModelSpec does not belong to the requested warehouse plan",
                Kind.BAD_REQUEST
            );
        }
        if (
            model.revision() != expected.revision() ||
            !Objects.equals(model.checksum(), expected.checksum())
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_BUILD_INTENT_MODEL_STALE",
                "ModelSpec changed; refresh before starting a build",
                Kind.CONFLICT,
                Map.of(
                    "modelSpecId",
                    model.id(),
                    "currentRevision",
                    model.revision(),
                    "currentChecksum",
                    model.checksum()
                )
            );
        }
    }

    private static Map<String, Object> candidateConflict(
        CandidateView candidate,
        UUID requestedModelSpecId
    ) {
        return Map.of(
            "candidateId",
            candidate.id(),
            "candidateVersion",
            candidate.version(),
            "candidateStatus",
            candidate.status(),
            "candidateOrigin",
            candidate.origin(),
            "requestedModelSpecId",
            requestedModelSpecId,
            "workspaceHref",
            "/modeling/plans/" + candidate.planId()
        );
    }

    static String createCommandKey(String idempotencyKey) {
        return "build-intent-create:" + sha256(idempotencyKey);
    }

    static String startCommandKey(String idempotencyKey) {
        return "build-intent-start:" + sha256(idempotencyKey);
    }

    private static String sha256(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(
                            required(value, "idempotencyKey", 128)
                                .getBytes(StandardCharsets.UTF_8)
                        )
                );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String name, int maximum) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw invalid(name + " exceeds " + maximum + " characters");
        }
        return normalized;
    }

    private static ModelReleaseCandidateException invalid(String message) {
        return new ModelReleaseCandidateException(
            "MODEL_BUILD_INTENT_REQUEST_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    public record BuildIntentCommand(
        UUID planId,
        String environment,
        String idempotencyKey,
        String buildMode
    ) {
        public BuildIntentCommand(UUID planId, String environment, String idempotencyKey) {
            this(planId, environment, idempotencyKey, "DATA_BUILD");
        }
        String executionKey() {
            return "SCHEMA_ONLY".equals(buildMode) ? sha256(idempotencyKey) + ":schema" : idempotencyKey;
        }
        public BuildIntentCommand {
            if (planId == null) throw invalid("planId is required");
            environment = required(environment, "environment", 64);
            idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
            if (buildMode == null) buildMode = "DATA_BUILD";
            if (!Set.of("SCHEMA_ONLY", "DATA_BUILD").contains(buildMode)) throw invalid("buildMode is invalid");
        }
    }

    public record BuildIntentResult(
        CandidateView candidate,
        QueuedBuildGroup build,
        boolean replayed
    ) {
        public BuildIntentResult {
            Objects.requireNonNull(candidate, "candidate is required");
            if (candidate.status() == DeliveryStatus.BUILDING && build == null) {
                throw new IllegalArgumentException(
                    "BUILDING candidate requires a durable build group"
                );
            }
        }
    }
}
