package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelPublicationReconciliationRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelPublicationReconciliationRepository.PendingReviewSubmission;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Advances QUALITY_PASSED to REVIEW_PENDING only after current authority and snapshot revalidation.
 *
 * <p>No user token is stored or replayed. Candidate CAS plus the deterministic command key gives
 * exactly-once state effect when multiple scheduler instances race.
 */
@Service
public class ModelPublicationReviewReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(
        ModelPublicationReviewReconciler.class
    );
    private static final int BATCH_SIZE = 20;

    private final ModelPublicationReconciliationRepository pending;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelReleaseCandidateService commands;
    private final ReleaseDutyDirectoryPort dutyDirectory;
    private final ModelSpecPlanWriteAccessPort planAccess;

    public ModelPublicationReviewReconciler(
        ModelPublicationReconciliationRepository pending,
        ModelReleaseCandidateRepository candidates,
        ModelReleaseCandidateService commands,
        ReleaseDutyDirectoryPort dutyDirectory,
        ModelSpecPlanWriteAccessPort planAccess
    ) {
        this.pending = Objects.requireNonNull(pending, "pending is required");
        this.candidates = Objects.requireNonNull(
            candidates,
            "candidates is required"
        );
        this.commands = Objects.requireNonNull(
            commands,
            "commands is required"
        );
        this.dutyDirectory = Objects.requireNonNull(
            dutyDirectory,
            "dutyDirectory is required"
        );
        this.planAccess = Objects.requireNonNull(
            planAccess,
            "planAccess is required"
        );
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.publication.review-reconcile-delay-ms:30000}"
    )
    public void reconcilePending() {
        List<PendingReviewSubmission> work =
            pending.findPendingReviewSubmissions(BATCH_SIZE);
        for (PendingReviewSubmission item : work) {
            ReconcileResult result = reconcile(item);
            if (result.outcome() == ReconcileOutcome.BLOCKED) {
                LOG.warn(
                    "event=model_publication_review_reconcile_blocked candidateId={} code={}",
                    item.candidateId(),
                    result.blockerCode()
                );
            }
        }
    }

    public ReconcileResult reconcile(PendingReviewSubmission item) {
        if (
            item == null ||
            item.candidateId() == null ||
            item.planId() == null
        ) {
            return blocked(
                null,
                "MODEL_RELEASE_RECONCILE_WORK_ITEM_INVALID"
            );
        }
        CandidateView candidate = candidates
            .find(item.tenantId(), item.candidateId())
            .orElse(null);
        if (
            candidate == null ||
            candidate.status() != DeliveryStatus.QUALITY_PASSED
        ) {
            return ignored(item.candidateId());
        }
        if (
            item.publicationEventId() == null ||
            item.requesterActorId() == null ||
            item.requesterActorId().isBlank()
        ) {
            return blocked(
                item.candidateId(),
                "MODEL_RELEASE_PUBLICATION_REQUEST_EVIDENCE_MISSING"
            );
        }
        String requester = item.requesterActorId().trim();
        try {
            if (
                !dutyDirectory.hasDuty(
                    requester,
                    DeliveryActorRole.MODEL_MAINTAINER
                )
            ) {
                return blocked(
                    item.candidateId(),
                    "MODEL_RELEASE_MAINTAINER_DUTY_REVOKED"
                );
            }
        } catch (ReleaseDutyDirectoryUnavailableException unavailable) {
            return blocked(
                item.candidateId(),
                "MODEL_RELEASE_DUTY_DIRECTORY_UNAVAILABLE"
            );
        }
        if (
            !planAccess.canMaintain(
                candidate.tenantId(),
                candidate.planId(),
                requester
            )
        ) {
            return blocked(
                item.candidateId(),
                "MODEL_RELEASE_PLAN_ACCESS_REVOKED"
            );
        }
        String key = reviewSubmissionKey(item.publicationEventId());
        try {
            CommandResult command = commands.transition(
                candidate.tenantId(),
                requester,
                candidate.id(),
                new TransitionCommand(
                    candidate.version(),
                    DeliveryStatus.REVIEW_PENDING,
                    key,
                    "Submit review after current duty and snapshot revalidation"
                )
            );
            if (command.candidate().status() == DeliveryStatus.STALE) {
                return blocked(
                    candidate.id(),
                    ModelReleaseCandidateContract.STALE_ERROR_CODE
                );
            }
            if (
                command.candidate().status() ==
                DeliveryStatus.REVIEW_PENDING
            ) {
                return new ReconcileResult(
                    candidate.id(),
                    ReconcileOutcome.ADVANCED,
                    null,
                    command.replayed()
                );
            }
            return ignored(candidate.id());
        } catch (ModelReleaseCandidateException conflict) {
            CandidateView current = candidates
                .find(candidate.tenantId(), candidate.id())
                .orElse(null);
            if (
                current == null ||
                current.status() != DeliveryStatus.QUALITY_PASSED
            ) {
                return ignored(candidate.id());
            }
            return blocked(candidate.id(), conflict.code());
        }
    }

    static String reviewSubmissionKey(UUID publicationEventId) {
        return "publication-review:" + publicationEventId;
    }

    private static ReconcileResult blocked(
        UUID candidateId,
        String blockerCode
    ) {
        return new ReconcileResult(
            candidateId,
            ReconcileOutcome.BLOCKED,
            blockerCode,
            false
        );
    }

    private static ReconcileResult ignored(UUID candidateId) {
        return new ReconcileResult(
            candidateId,
            ReconcileOutcome.IGNORED,
            null,
            false
        );
    }

    public enum ReconcileOutcome {
        ADVANCED,
        BLOCKED,
        IGNORED,
    }

    public record ReconcileResult(
        UUID candidateId,
        ReconcileOutcome outcome,
        String blockerCode,
        boolean replayed
    ) {}
}
