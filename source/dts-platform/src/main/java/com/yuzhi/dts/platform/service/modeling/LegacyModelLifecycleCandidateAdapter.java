package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReleaseView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility surface for old per-model lifecycle URLs.
 *
 * <p>Only a one-entry current Candidate may be addressed. The adapter never performs a ModelSpec lifecycle
 * transition directly; Candidate application commands remain the mutation owner.
 */
@Service
public class LegacyModelLifecycleCandidateAdapter {

    private final ModelSpecRepository modelSpecs;
    private final ModelSpecSnapshotCodec codec;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelReleaseCandidateApplicationService candidateApplication;
    private final ModelLifecycleRepository lifecycle;
    private final Clock clock;

    @Autowired
    public LegacyModelLifecycleCandidateAdapter(
        ModelSpecRepository modelSpecs,
        ModelSpecSnapshotCodec codec,
        ModelReleaseCandidateRepository candidates,
        ModelReleaseCandidateApplicationService candidateApplication,
        ModelLifecycleRepository lifecycle
    ) {
        this(modelSpecs, codec, candidates, candidateApplication, lifecycle, Clock.systemUTC());
    }

    LegacyModelLifecycleCandidateAdapter(
        ModelSpecRepository modelSpecs,
        ModelSpecSnapshotCodec codec,
        ModelReleaseCandidateRepository candidates,
        ModelReleaseCandidateApplicationService candidateApplication,
        ModelLifecycleRepository lifecycle,
        Clock clock
    ) {
        this.modelSpecs = modelSpecs;
        this.codec = codec;
        this.candidates = candidates;
        this.candidateApplication = candidateApplication;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public LifecycleEventView submitReview(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion ignoredImplementation,
        ReviewCommand command
    ) {
        requireReviewCommand(command);
        Scope scope = scope(tenantId, modelSpecId, expected);
        LifecycleEventView replay = lifecycle
            .findEvent(tenantId, modelSpecId, EventType.REVIEW_SUBMITTED, command.idempotencyKey().trim())
            .orElse(null);
        if (replay != null) return replay;
        var result = candidateApplication.submitReview(
            tenantId,
            actorId,
            scope.model().planId(),
            scope.candidate().id(),
            scope.candidate().version(),
            command.idempotencyKey(),
            command.comment()
        );
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            scope.model(),
            EventType.REVIEW_SUBMITTED,
            "SUBMITTED",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of(
                "candidateId",
                result.candidate().id(),
                "candidateVersion",
                result.candidate().version()
            ),
            clock.instant()
        );
    }

    @Transactional
    public LifecycleEventView approveReview(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion ignoredImplementation,
        ReviewCommand command
    ) {
        requireReviewCommand(command);
        Scope scope = scope(tenantId, modelSpecId, expected);
        LifecycleEventView replay = lifecycle
            .findEvent(tenantId, modelSpecId, EventType.REVIEW_APPROVED, command.idempotencyKey().trim())
            .orElse(null);
        if (replay != null) return replay;
        var result = candidateApplication.approve(
            tenantId,
            actorId,
            scope.model().planId(),
            scope.candidate().id(),
            scope.candidate().version(),
            command.idempotencyKey(),
            command.comment()
        );
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            scope.model(),
            EventType.REVIEW_APPROVED,
            "APPROVED",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of(
                "candidateId",
                result.candidate().id(),
                "candidateVersion",
                result.candidate().version()
            ),
            clock.instant()
        );
    }

    public ReleaseView publish(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ExpectedImplementationVersion ignoredImplementation,
        PublishCommand command
    ) {
        requirePublishCommand(command);
        Scope scope = scope(tenantId, modelSpecId, expected);
        var result = candidateApplication.publish(
            tenantId,
            actorId,
            scope.model().planId(),
            scope.candidate().id(),
            scope.candidate().version(),
            command.idempotencyKey(),
            command.comment()
        );
        if (result.candidate().status() != DeliveryStatus.PUBLISHED) {
            throw failure(
                "MODEL_RELEASE_CANDIDATE_PARTIAL",
                "Candidate publication did not complete; use the Candidate registration retry command"
            );
        }
        return releaseView(tenantId, modelSpecId, scope.model().revision(), "PUBLISHED");
    }

    public ReleaseView retryRegistration(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        UUID legacyReleaseId
    ) {
        if (legacyReleaseId == null) throw failure("MODEL_RELEASE_EVENT_REQUIRED", "releaseId is required");
        Scope scope = scope(tenantId, modelSpecId, null);
        String requestKey = "legacy-registration-retry:" + legacyReleaseId;
        int expectedVersion = scope.candidate().version();
        if (scope.candidate().status() == DeliveryStatus.PUBLISHED) {
            String commitKey = CandidatePublicationKeys.registrationRetry(requestKey);
            CommandEventView receipt = candidates
                .findCommandByIdempotencyKey(tenantId, commitKey)
                .orElse(null);
            if (receipt != null && receipt.toStatus() == DeliveryStatus.PUBLISHED) {
                expectedVersion = receipt.candidateVersion() - 1;
            }
        }
        var result = candidateApplication.retryRegistration(
            tenantId,
            actorId,
            scope.model().planId(),
            scope.candidate().id(),
            expectedVersion,
            requestKey,
            "Retry Candidate publication through legacy lifecycle route"
        );
        if (result.candidate().status() != DeliveryStatus.PUBLISHED) {
            throw failure("MODEL_RELEASE_CANDIDATE_PARTIAL", "Candidate publication retry did not complete");
        }
        return releaseView(tenantId, modelSpecId, scope.model().revision(), "PUBLISHED");
    }

    public LifecycleEventView rollback(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        RollbackCommand command
    ) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw failure("MODEL_ROLLBACK_IDEMPOTENCY_REQUIRED", "Rollback idempotency key is required");
        }
        Scope scope = scope(tenantId, modelSpecId, expected);
        candidateApplication.rollback(
            tenantId,
            actorId,
            scope.model().planId(),
            scope.candidate().id(),
            scope.candidate().version(),
            command.idempotencyKey(),
            command.comment()
        );
        return lifecycle
            .findEvent(
                tenantId,
                modelSpecId,
                EventType.ROLLBACK,
                "candidate-rollback:" +
                scope.candidate().id() +
                ":model:" +
                modelSpecId +
                ":r" +
                scope.model().revision()
            )
            .orElseThrow(() ->
                failure(
                    "MODEL_ROLLBACK_EVIDENCE_REQUIRED",
                    "Candidate rollback completed without its lifecycle evidence"
                )
            );
    }

    private Scope scope(String tenantId, UUID modelSpecId, ExpectedVersion expected) {
        StoredModelSpec stored = modelSpecs
            .findCurrent(tenantId, modelSpecId)
            .orElseThrow(() -> failure("MODEL_SPEC_NOT_FOUND", "ModelSpec was not found"));
        if (
            !stored.currentHead() ||
            stored.currentSnapshot() == null ||
            stored.currentSnapshot().isBlank()
        ) {
            throw failure("MODEL_SPEC_CURRENT_REQUIRED", "Current ModelSpec snapshot is required");
        }
        ModelSpecView model = codec.readView(stored.currentSnapshot());
        if (
            expected != null &&
            (
                !modelSpecId.equals(expected.modelSpecId()) ||
                model.revision() != expected.revision() ||
                !Objects.equals(model.checksum(), expected.checksum())
            )
        ) {
            throw failure("MODEL_SPEC_VERSION_CONFLICT", "ModelSpec changed before the compatibility command");
        }
        List<CandidateView> matching = candidates
            .listForWorkbench(tenantId, model.planId())
            .stream()
            .filter(candidate ->
                candidate.entries().stream().anyMatch(entry -> modelSpecId.equals(entry.modelSpecId()))
            )
            .toList();
        List<CandidateView> active = matching
            .stream()
            .filter(candidate -> !isReplacementSource(candidate.status()))
            .toList();
        if (matching.isEmpty() || active.size() > 1) {
            throw failure(
                "MODEL_RELEASE_CURRENT_CANDIDATE_REQUIRED",
                "Exactly one current release Candidate must own this ModelSpec"
            );
        }
        CandidateView candidate = active.isEmpty() ? matching.getFirst() : active.getFirst();
        if (
            candidate.entries().size() != 1 ||
            !modelSpecId.equals(candidate.entries().getFirst().modelSpecId())
        ) {
            throw failure(
                "MODEL_RELEASE_LEGACY_ROUTE_BATCH_FORBIDDEN",
                "Batch Candidates must be operated from the plan release workbench"
            );
        }
        return new Scope(model, candidate);
    }

    private static boolean isReplacementSource(DeliveryStatus status) {
        return (
            status == DeliveryStatus.REJECTED ||
            status == DeliveryStatus.ROLLED_BACK ||
            status == DeliveryStatus.CANCELLED ||
            status == DeliveryStatus.STALE
        );
    }

    private ReleaseView releaseView(
        String tenantId,
        UUID modelSpecId,
        int revision,
        String status
    ) {
        LifecycleEventView release = lifecycle
            .findLatestEvent(tenantId, modelSpecId, revision, EventType.RELEASE, status)
            .orElseThrow(() ->
                failure(
                    "MODEL_RELEASE_EVIDENCE_REQUIRED",
                    "Candidate publication completed without release evidence"
                )
            );
        return new ReleaseView(
            release,
            release.status(),
            release.revision(),
            release.modelChecksum(),
            lifecycle.listRegistrations(release.id())
        );
    }

    private static void requireReviewCommand(ReviewCommand command) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw failure("MODEL_REVIEW_IDEMPOTENCY_REQUIRED", "Review idempotency key is required");
        }
    }

    private static void requirePublishCommand(PublishCommand command) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw failure("MODEL_RELEASE_IDEMPOTENCY_REQUIRED", "Publish idempotency key is required");
        }
    }

    private static ModelSpecException failure(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT);
    }

    private record Scope(ModelSpecView model, CandidateView candidate) {}
}
