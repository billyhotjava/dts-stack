package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically withdraws Candidate-owned ModelSpec and Catalog publication facts. */
@Service
public class CandidateRollbackCommitService {

    private final CandidatePublicationEvidenceRepository evidence;
    private final ModelSpecRepository modelSpecs;
    private final ModelSpecSnapshotCodec codec;
    private final ModelLifecyclePublicationService lifecyclePublication;
    private final CandidatePublicationRepository publications;
    private final PlatformEventOutboxService outbox;
    private final ModelReleaseCandidateService candidateCommands;
    private final Clock clock;
    private final AuditService auditService;

    @Autowired
    public CandidateRollbackCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        AuditService auditService
    ) {
        this(
            evidence,
            modelSpecs,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            Clock.systemUTC(),
            auditService
        );
    }

    CandidateRollbackCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        Clock clock
    ) {
        this(
            evidence,
            modelSpecs,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            clock,
            null
        );
    }

    CandidateRollbackCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        Clock clock,
        AuditService auditService
    ) {
        this.evidence = evidence;
        this.modelSpecs = modelSpecs;
        this.codec = codec;
        this.lifecyclePublication = lifecyclePublication;
        this.publications = publications;
        this.outbox = outbox;
        this.candidateCommands = candidateCommands;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional
    public CommandResult rollback(
        String tenantId,
        String actorId,
        CandidateView candidate,
        String rollbackRequestKey,
        String reason
    ) {
        if (candidate == null || candidate.status() != DeliveryStatus.PUBLISHED) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_PUBLISHED_REQUIRED",
                "Candidate must be PUBLISHED before rollback",
                Kind.CONFLICT
            );
        }
        if (!Objects.equals(tenantId, candidate.tenantId())) {
            throw new IllegalArgumentException("tenantId must match Candidate tenant");
        }
        Instant now = clock.instant();
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, true);
        for (PublicationEntryEvidence observation : observations) {
            ModelSpecView model = requirePublished(candidate, observation);
            var rollback = lifecyclePublication.rollback(
                tenantId,
                actorId,
                model,
                new RollbackCommand(
                    reason,
                    "candidate-rollback:" +
                    candidate.id() +
                    ":model:" +
                    model.id() +
                    ":r" +
                    model.revision()
                ),
                now
            );
            publications.rollbackModel(candidate, observation, model, rollback, actorId, now);
        }
        publications.rebuildManualBindingAfterRollback(candidate, actorId, now);
        outbox.publishInternal(
            new PlatformEventRequest(
                "model-release-candidate-rolled-back:" + candidate.id() + ":v" + candidate.version(),
                "MODEL_RELEASE_CANDIDATE_ROLLED_BACK",
                "modeling",
                "dts-platform",
                "MODEL_RELEASE_CANDIDATE",
                candidate.id().toString(),
                candidate.planId().toString(),
                "ROLLBACK",
                "WARN",
                "SUCCESS",
                now,
                actorId,
                rollbackRequestKey,
                null,
                "MODEL_RELEASE_CANDIDATE_ROLLBACK",
                "SPRINT_36_F3_ASSET_ACTION",
                Map.of(
                    "tenantId",
                    tenantId,
                    "planId",
                    candidate.planId(),
                    "environment",
                    candidate.environment(),
                    "entryCount",
                    observations.size()
                )
            )
        );
        CommandResult result = candidateCommands.transitionWithinAuditedCommit(
            tenantId,
            actorId,
            candidate.id(),
            new TransitionCommand(
                candidate.version(),
                DeliveryStatus.ROLLED_BACK,
                rollbackRequestKey,
                reason
            )
        );
        if (!result.replayed()) {
            auditSuccess(
                actorId,
                candidate,
                Map.of(
                    "tenantId",
                    tenantId,
                    "planId",
                    candidate.planId(),
                    "environment",
                    candidate.environment(),
                    "fromStatus",
                    candidate.status().name(),
                    "toStatus",
                    result.candidate().status().name(),
                    "version",
                    result.candidate().version(),
                    "entryCount",
                    observations.size()
                )
            );
        }
        return result;
    }

    private void auditSuccess(
        String actorId,
        CandidateView candidate,
        Map<String, Object> payload
    ) {
        if (auditService == null) return;
        String actionCode = "MODEL_RELEASE_CANDIDATE_ROLLBACK";
        Map<String, Object> auditPayload = new LinkedHashMap<>(payload);
        auditPayload.put("actor", actorId);
        auditService.auditAction(
            actionCode,
            AuditStage.SUCCESS,
            candidate.id().toString(),
            auditPayload
        );
    }

    private ModelSpecView requirePublished(
        CandidateView candidate,
        PublicationEntryEvidence observation
    ) {
        StoredModelSpec stored = modelSpecs
            .findCurrent(candidate.tenantId(), observation.modelSpecId())
            .orElseThrow(() -> conflict(candidate, observation.modelSpecId()));
        if (!stored.currentHead() || stored.currentSnapshot() == null || stored.currentSnapshot().isBlank()) {
            throw conflict(candidate, observation.modelSpecId());
        }
        ModelSpecView model = codec.readView(stored.currentSnapshot());
        if (
            model.status() != ModelStatus.PUBLISHED ||
            !candidate.planId().equals(model.planId()) ||
            !observation.modelSpecId().equals(model.id()) ||
            model.revision() != observation.modelRevision() ||
            !observation.modelChecksum().equals(model.checksum())
        ) {
            throw conflict(candidate, observation.modelSpecId());
        }
        return model;
    }

    private static ModelReleaseCandidateException conflict(
        CandidateView candidate,
        UUID modelSpecId
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_ROLLBACK_SNAPSHOT_CONFLICT",
            "Published ModelSpec changed before Candidate rollback",
            Kind.CONFLICT,
            Map.of("candidateId", candidate.id(), "modelSpecId", modelSpecId)
        );
    }
}
