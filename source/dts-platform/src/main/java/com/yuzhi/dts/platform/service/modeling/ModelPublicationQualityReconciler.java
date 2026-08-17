package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelPublicationQualityEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelPublicationQualityEvidenceRepository.EvidenceState;
import com.yuzhi.dts.platform.repository.modeling.ModelPublicationQualityEvidenceRepository.QualityWorkItem;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reconciles RUN_QUALITY against the existing durable Airflow {@code dbt build} evidence. */
@Service
public class ModelPublicationQualityReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(
        ModelPublicationQualityReconciler.class
    );
    private static final String SERVICE_ACTOR =
        "service:dts-platform-quality";
    private static final int BATCH_SIZE = 20;

    private final ModelPublicationQualityEvidenceRepository evidence;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelReleaseCandidateService commands;
    private final CandidateGovernanceQualityEvidenceService governanceQuality;

    public ModelPublicationQualityReconciler(
        ModelPublicationQualityEvidenceRepository evidence,
        ModelReleaseCandidateRepository candidates,
        ModelReleaseCandidateService commands,
        CandidateGovernanceQualityEvidenceService governanceQuality
    ) {
        this.evidence = Objects.requireNonNull(
            evidence,
            "evidence is required"
        );
        this.candidates = Objects.requireNonNull(
            candidates,
            "candidates is required"
        );
        this.commands = Objects.requireNonNull(
            commands,
            "commands is required"
        );
        this.governanceQuality = Objects.requireNonNull(
            governanceQuality,
            "governanceQuality is required"
        );
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.publication.quality-reconcile-delay-ms:5000}"
    )
    public void reconcilePending() {
        List<QualityWorkItem> work = evidence.findQualityRunning(BATCH_SIZE);
        for (QualityWorkItem item : work) {
            QualityReconcileResult result = reconcile(item);
            if (result.outcome() == QualityReconcileOutcome.BLOCKED) {
                LOG.warn(
                    "event=model_publication_quality_reconcile_blocked candidateId={} code={}",
                    item.candidateId(),
                    result.blockerCode()
                );
            }
        }
    }

    public QualityReconcileResult reconcile(QualityWorkItem item) {
        if (item == null || item.candidateId() == null) {
            return blocked(
                null,
                "MODEL_RELEASE_QUALITY_WORK_ITEM_INVALID"
            );
        }
        CandidateView candidate = candidates
            .find(item.tenantId(), item.candidateId())
            .orElse(null);
        if (
            candidate == null ||
            candidate.status() != DeliveryStatus.QUALITY_RUNNING
        ) {
            return ignored(item.candidateId());
        }
        EvidenceState state = item.evidenceState();
        if (state == EvidenceState.PENDING) {
            return new QualityReconcileResult(
                candidate.id(),
                QualityReconcileOutcome.WAITING,
                null,
                false
            );
        }
        if (state == EvidenceState.MISSING) {
            return blocked(
                candidate.id(),
                "MODEL_RELEASE_QUALITY_EVIDENCE_MISSING"
            );
        }
        DeliveryStatus target = state == EvidenceState.PASSED
            ? DeliveryStatus.QUALITY_PASSED
            : DeliveryStatus.QUALITY_FAILED;
        String reason = state == EvidenceState.PASSED
            ? "Canonical Airflow dbt build and tests completed with verified relations"
            : failureReason(item);
        try {
            TransitionCommand transition = new TransitionCommand(
                candidate.version(),
                target,
                qualityResultKey(item.qualityCommandEventId()),
                reason
            );
            CommandResult command;
            if (state == EvidenceState.PASSED) {
                GovernanceQualitySummaryView governance = governanceQuality.evaluateLive(candidate);
                if (governance.required() && !governance.passed()) {
                    return blocked(
                        candidate.id(),
                        governance.code() == null
                            ? "MODEL_SPEC_GOVERNANCE_QUALITY_REQUIRED"
                            : governance.code()
                    );
                }
                CandidateQualityEvidenceSnapshot snapshot = CandidateQualityEvidenceSnapshot.capture(
                    candidate,
                    item.pipelineRunGroupId(),
                    item.candidateEntryCount(),
                    item.runCount(),
                    item.verifiedCount(),
                    governance
                );
                command = commands.transitionWithQualityEvidence(
                    candidate.tenantId(),
                    SERVICE_ACTOR,
                    candidate.id(),
                    transition,
                    snapshot
                );
            } else {
                command = commands.transition(
                    candidate.tenantId(),
                    SERVICE_ACTOR,
                    candidate.id(),
                    transition
                );
            }
            if (command.candidate().status() == DeliveryStatus.STALE) {
                return blocked(
                    candidate.id(),
                    ModelReleaseCandidateContract.STALE_ERROR_CODE
                );
            }
            return new QualityReconcileResult(
                candidate.id(),
                state == EvidenceState.PASSED
                    ? QualityReconcileOutcome.PASSED
                    : QualityReconcileOutcome.FAILED,
                state == EvidenceState.FAILED
                    ? "MODEL_RELEASE_QUALITY_EXECUTION_FAILED"
                    : null,
                command.replayed()
            );
        } catch (ModelReleaseCandidateException conflict) {
            CandidateView current = candidates
                .find(candidate.tenantId(), candidate.id())
                .orElse(null);
            if (
                current == null ||
                current.status() != DeliveryStatus.QUALITY_RUNNING
            ) {
                return ignored(candidate.id());
            }
            return blocked(candidate.id(), conflict.code());
        }
    }

    static String qualityResultKey(UUID qualityCommandEventId) {
        if (qualityCommandEventId == null) {
            throw new IllegalArgumentException(
                "qualityCommandEventId is required"
            );
        }
        return "candidate-quality-result:" + qualityCommandEventId;
    }

    private static String failureReason(QualityWorkItem item) {
        String code = item.lastErrorCode();
        return code == null || code.isBlank()
            ? "MODEL_RELEASE_QUALITY_EXECUTION_FAILED"
            : code.trim();
    }

    private static QualityReconcileResult blocked(
        UUID candidateId,
        String blockerCode
    ) {
        return new QualityReconcileResult(
            candidateId,
            QualityReconcileOutcome.BLOCKED,
            blockerCode,
            false
        );
    }

    private static QualityReconcileResult ignored(UUID candidateId) {
        return new QualityReconcileResult(
            candidateId,
            QualityReconcileOutcome.IGNORED,
            null,
            false
        );
    }

    public enum QualityReconcileOutcome {
        WAITING,
        PASSED,
        FAILED,
        BLOCKED,
        IGNORED,
    }

    public record QualityReconcileResult(
        UUID candidateId,
        QualityReconcileOutcome outcome,
        String blockerCode,
        boolean replayed
    ) {}
}
