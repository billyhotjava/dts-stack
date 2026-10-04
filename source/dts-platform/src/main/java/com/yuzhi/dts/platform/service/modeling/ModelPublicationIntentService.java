package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Strict single-model publish facade over the canonical release-candidate control plane. */
@Service
public class ModelPublicationIntentService {

    private final ModelReleaseCandidateRepository repository;
    private final ModelReleaseCandidateService candidateCommands;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver dutyResolver;

    public ModelPublicationIntentService(
        ModelReleaseCandidateRepository repository,
        ModelReleaseCandidateService candidateCommands,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver dutyResolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.candidateCommands = Objects.requireNonNull(
            candidateCommands,
            "candidateCommands is required"
        );
        this.planAccess = Objects.requireNonNull(planAccess, "planAccess is required");
        this.dutyResolver = Objects.requireNonNull(dutyResolver, "dutyResolver is required");
    }

    @Transactional
    public PublicationIntentResult start(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        String tenant = required(tenantId, "tenantId", 128);
        String actor = required(actorId, "actorId", 128);
        String key = required(idempotencyKey, "idempotencyKey", 128);
        String selectedReason = required(
            reason,
            "reason",
            ModelReleaseCandidateContract.MAX_COMMAND_REASON_BYTES
        );
        if (modelSpecId == null || candidateId == null || expectedVersion < 1) {
            throw invalid("ModelSpec, candidate and positive Candidate ETag version are required");
        }
        Set<DeliveryActorRole> duties = dutyResolver.currentDuties();
        if (
            duties == null ||
            !duties.contains(DeliveryActorRole.MODEL_MAINTAINER)
        ) {
            throw forbidden();
        }

        CandidateView candidate = repository
            .find(tenant, candidateId)
            .orElseThrow(() -> notFound(candidateId));
        if (
            !planAccess.canMaintain(
                tenant,
                candidate.planId(),
                actor
            )
        ) {
            throw forbidden();
        }
        planAccess.requireOperation(tenant, candidate.entries().stream().map(entry -> entry.modelSpecId()).toList(), actor);
        requireSingleModelScope(candidate, modelSpecId);

        CommandEventView receipt = repository
            .findCommandByIdempotencyKey(tenant, key)
            .orElse(null);
        if (receipt != null) {
            if (
                !candidate.id().equals(receipt.candidateId()) ||
                receipt.eventType() != CommandEventType.PUBLICATION_REQUESTED
            ) {
                throw new ModelReleaseCandidateException(
                    ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE,
                    "Idempotency key already belongs to another candidate command",
                    Kind.CONFLICT
                );
            }
            candidateCommands.publicationRequested(
                tenant,
                actor,
                candidate.id(),
                new TransitionCommand(
                    expectedVersion,
                    DeliveryStatus.QUALITY_RUNNING,
                    key,
                    selectedReason
                )
            );
            CandidateView current = repository
                .find(tenant, candidate.id())
                .orElseThrow(() -> notFound(candidate.id()));
            requireSingleModelScope(current, modelSpecId);
            return project(current, true);
        }

        if (candidate.version() != expectedVersion) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.VERSION_CONFLICT_ERROR_CODE,
                "Candidate version changed; refresh before submitting publication",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "expectedVersion",
                    expectedVersion,
                    "currentVersion",
                    candidate.version(),
                    "currentStatus",
                    candidate.status()
                )
            );
        }
        if (!candidateCommands.detectDrift(tenant, candidate).isEmpty()) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.STALE_ERROR_CODE,
                "Candidate no longer matches the current ModelSpec snapshot",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "workspaceHref",
                    workbench(candidate)
                )
            );
        }

        if (candidate.status() == DeliveryStatus.BUILT) {
            return result(
                candidateCommands.publicationRequested(
                    tenant,
                    actor,
                    candidate.id(),
                    new TransitionCommand(
                        expectedVersion,
                        DeliveryStatus.QUALITY_RUNNING,
                        key,
                        selectedReason
                    )
                )
            );
        }
        return project(candidate, false);
    }

    private void requireSingleModelScope(
        CandidateView candidate,
        UUID modelSpecId
    ) {
        if (candidate.origin() == CandidateOrigin.BATCH_WORKBENCH) {
            throw new ModelReleaseCandidateException(
                "MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT",
                "The active release candidate is owned by the batch workbench",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "candidateStatus",
                    candidate.status(),
                    "workspaceHref",
                    workbench(candidate)
                )
            );
        }
        if (
            candidate.entries().size() != 1 ||
            !candidate.entries().getFirst().modelSpecId().equals(modelSpecId)
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_PUBLICATION_INTENT_SCOPE_MISMATCH",
                "Publish intent must reference the candidate for the path ModelSpec only",
                Kind.CONFLICT
            );
        }
    }

    private PublicationIntentResult result(CommandResult command) {
        return project(command.candidate(), command.replayed());
    }

    private PublicationIntentResult project(
        CandidateView candidate,
        boolean replayed
    ) {
        Set<DeliveryActorRole> duties = dutyResolver.currentDuties();
        boolean canSelfPublish = duties != null && duties.contains(DeliveryActorRole.RELEASE_OPERATOR);
        Projection projection = switch (candidate.status()) {
            case QUALITY_RUNNING -> new Projection(
                PublicationOutcome.QUALITY_RUNNING,
                NextHumanAction.NONE,
                OnlineReadiness.PROCESSING,
                null
            );
            case QUALITY_FAILED -> new Projection(
                PublicationOutcome.QUALITY_FAILED,
                NextHumanAction.RETRY_QUALITY,
                OnlineReadiness.NOT_READY,
                new PublicationBlocker(
                    "MODEL_RELEASE_CANDIDATE_QUALITY_FAILED",
                    "Quality failed; repair the model and retry quality explicitly"
                )
            );
            case QUALITY_PASSED -> canSelfPublish
                ? new Projection(
                    PublicationOutcome.PUBLICATION_READY,
                    NextHumanAction.PUBLISH,
                    OnlineReadiness.PROCESSING,
                    null
                )
                : new Projection(
                    PublicationOutcome.REVIEW_SUBMISSION_PENDING,
                    NextHumanAction.NONE,
                    OnlineReadiness.PROCESSING,
                    null
                );
            case REVIEW_PENDING -> canSelfPublish
                ? new Projection(
                    PublicationOutcome.PUBLICATION_READY,
                    NextHumanAction.PUBLISH,
                    OnlineReadiness.PROCESSING,
                    null
                )
                : new Projection(
                    PublicationOutcome.REVIEW_PENDING,
                    NextHumanAction.REVIEW,
                    OnlineReadiness.PROCESSING,
                    null
                );
            case APPROVED -> new Projection(
                PublicationOutcome.APPROVED,
                NextHumanAction.PUBLISH,
                OnlineReadiness.PROCESSING,
                null
            );
            case PUBLISHING -> new Projection(
                PublicationOutcome.PUBLISHING,
                NextHumanAction.NONE,
                OnlineReadiness.PROCESSING,
                null
            );
            case PARTIAL -> new Projection(
                PublicationOutcome.PARTIAL,
                NextHumanAction.REPAIR_REGISTRATION,
                OnlineReadiness.DEGRADED,
                new PublicationBlocker(
                    "MODEL_RELEASE_REGISTRATION_PARTIAL",
                    "Publication completed partially; repair registration from the workbench"
                )
            );
            case PUBLISHED -> new Projection(
                PublicationOutcome.PUBLISHED,
                NextHumanAction.NONE,
                OnlineReadiness.PROCESSING,
                new PublicationBlocker(
                    "MODEL_RELEASE_BINDING_STATUS_PENDING",
                    "Published candidate is waiting for an ACTIVE operational binding"
                )
            );
            case BUILT -> throw invalid(
                "BUILT candidate must enter quality through the publication command"
            );
            case DRAFT, BUILDING, BUILD_FAILED -> throw new ModelReleaseCandidateException(
                "MODEL_PUBLICATION_BUILD_REQUIRED",
                "Complete the canonical materialization build before submitting publication",
                Kind.CONFLICT
            );
            case REJECTED, ROLLED_BACK, CANCELLED, STALE -> throw new ModelReleaseCandidateException(
                "MODEL_PUBLICATION_REPLACEMENT_REQUIRED",
                "This candidate cannot continue; create a replacement candidate",
                Kind.CONFLICT,
                Map.of("workspaceHref", workbench(candidate))
            );
        };
        return new PublicationIntentResult(
            candidate.id(),
            candidate.version(),
            candidate.status(),
            projection.outcome(),
            projection.nextHumanAction(),
            projection.blocker(),
            projection.onlineReadiness(),
            workbench(candidate),
            replayed
        );
    }

    private static String workbench(CandidateView candidate) {
        return "/modeling/plans/" + candidate.planId();
    }

    private static ModelReleaseCandidateException forbidden() {
        return new ModelReleaseCandidateException(
            "MODEL_PUBLICATION_INTENT_FORBIDDEN",
            "A current model-maintainer duty is required",
            Kind.FORBIDDEN
        );
    }

    private static ModelReleaseCandidateException notFound(UUID candidateId) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CANDIDATE_NOT_FOUND",
            "Release candidate was not found",
            Kind.NOT_FOUND,
            candidateId == null ? Map.of() : Map.of("candidateId", candidateId)
        );
    }

    private static ModelReleaseCandidateException invalid(String message) {
        return new ModelReleaseCandidateException(
            "MODEL_PUBLICATION_INTENT_REQUEST_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
        String text = value.trim();
        if (text.length() > maxLength) throw invalid(name + " exceeds " + maxLength + " characters");
        return text;
    }

    public enum PublicationOutcome {
        QUALITY_RUNNING,
        QUALITY_FAILED,
        PUBLICATION_READY,
        REVIEW_SUBMISSION_PENDING,
        REVIEW_PENDING,
        APPROVED,
        PUBLISHING,
        PARTIAL,
        PUBLISHED,
    }

    public enum NextHumanAction {
        NONE,
        RETRY_QUALITY,
        REVIEW,
        PUBLISH,
        REPAIR_REGISTRATION,
    }

    public enum OnlineReadiness {
        NOT_READY,
        PROCESSING,
        DEGRADED,
        READY,
    }

    public record PublicationBlocker(String code, String message) {
        public PublicationBlocker {
            code = required(code, "code", 128);
            message = required(message, "message", 512);
        }
    }

    public record PublicationIntentResult(
        UUID candidateId,
        int candidateVersion,
        DeliveryStatus candidateStatus,
        PublicationOutcome outcome,
        NextHumanAction nextHumanAction,
        PublicationBlocker blocker,
        OnlineReadiness onlineReadiness,
        String workbenchUrl,
        boolean replayed
    ) {
        public PublicationIntentResult {
            if (candidateId == null) throw new IllegalArgumentException("candidateId is required");
            if (candidateVersion < 1) throw new IllegalArgumentException("candidateVersion must be positive");
            Objects.requireNonNull(candidateStatus, "candidateStatus is required");
            Objects.requireNonNull(outcome, "outcome is required");
            Objects.requireNonNull(nextHumanAction, "nextHumanAction is required");
            Objects.requireNonNull(onlineReadiness, "onlineReadiness is required");
            workbenchUrl = required(workbenchUrl, "workbenchUrl", 512);
        }
    }

    private record Projection(
        PublicationOutcome outcome,
        NextHumanAction nextHumanAction,
        OnlineReadiness onlineReadiness,
        PublicationBlocker blocker
    ) {}
}
