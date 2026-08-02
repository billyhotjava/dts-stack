package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingService;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ProjectionMutation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits ModelSpec, Catalog, lineage, binding, outbox and Candidate publication as one local transaction. */
@Service
public class CandidatePublicationCommitService {

    private final CandidatePublicationEvidenceRepository evidence;
    private final ModelSpecRepository modelSpecs;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecSnapshotCodec codec;
    private final ModelLifecyclePublicationService lifecyclePublication;
    private final CandidatePublicationRepository publications;
    private final PlatformEventOutboxService outbox;
    private final ModelReleaseCandidateService candidateCommands;
    private final ModelExecutionTargetCatalogResolver targetResolver;
    private final Clock clock;
    private final AuditService auditService;
    private final CatalogModelServingService catalogServing;

    @Autowired
    public CandidatePublicationCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelLifecycleRepository lifecycle,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        ModelExecutionTargetCatalogResolver targetResolver,
        AuditService auditService,
        CatalogModelServingService catalogServing
    ) {
        this(
            evidence,
            modelSpecs,
            lifecycle,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            targetResolver,
            Clock.systemUTC(),
            auditService,
            catalogServing
        );
    }

    public CandidatePublicationCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelLifecycleRepository lifecycle,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        ModelExecutionTargetCatalogResolver targetResolver,
        Clock clock
    ) {
        this(
            evidence,
            modelSpecs,
            lifecycle,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            targetResolver,
            clock,
            null,
            null
        );
    }

    public CandidatePublicationCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelLifecycleRepository lifecycle,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        ModelExecutionTargetCatalogResolver targetResolver,
        Clock clock,
        AuditService auditService
    ) {
        this(
            evidence,
            modelSpecs,
            lifecycle,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            targetResolver,
            clock,
            auditService,
            null
        );
    }

    public CandidatePublicationCommitService(
        CandidatePublicationEvidenceRepository evidence,
        ModelSpecRepository modelSpecs,
        ModelLifecycleRepository lifecycle,
        ModelSpecSnapshotCodec codec,
        ModelLifecyclePublicationService lifecyclePublication,
        CandidatePublicationRepository publications,
        PlatformEventOutboxService outbox,
        ModelReleaseCandidateService candidateCommands,
        ModelExecutionTargetCatalogResolver targetResolver,
        Clock clock,
        AuditService auditService,
        CatalogModelServingService catalogServing
    ) {
        this.evidence = evidence;
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.codec = codec;
        this.lifecyclePublication = lifecyclePublication;
        this.publications = publications;
        this.outbox = outbox;
        this.candidateCommands = candidateCommands;
        this.targetResolver = targetResolver;
        this.clock = clock;
        this.auditService = auditService;
        this.catalogServing = catalogServing;
    }

    @Transactional
    public CommandResult commit(
        String tenantId,
        String actorId,
        CandidateView candidate,
        String publishRequestKey,
        String reason
    ) {
        if (
            candidate == null ||
            (
                candidate.status() != DeliveryStatus.PUBLISHING &&
                candidate.status() != DeliveryStatus.PARTIAL
            )
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_PUBLISHING_REQUIRED",
                "Candidate must be PUBLISHING or PARTIAL before the local publication commit",
                Kind.CONFLICT
            );
        }
        if (!Objects.equals(tenantId, candidate.tenantId())) {
            throw new IllegalArgumentException("tenantId must match Candidate tenant");
        }
        Instant now = clock.instant();
        ResolvedCatalogTarget target = targetResolver.resolve(candidate);
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, true);
        List<PublicationUnit> units = new ArrayList<>(observations.size());
        for (PublicationEntryEvidence observation : observations) {
            ModelSpecView model = requireDraft(candidate, observation);
            units.add(
                new PublicationUnit(
                    observation,
                    model,
                    requireImplementation(candidate, observation, model)
                )
            );
        }
        List<PublishedModelBinding> bindings = new ArrayList<>(observations.size());
        int servingNotReadyCount = 0;
        for (PublicationUnit unit : dependencyOrder(candidate, units)) {
            PublicationEntryEvidence observation = unit.observation();
            ModelSpecView model = unit.model();
            ImplementationView implementation = unit.implementation();
            String lifecycleKey =
                "candidate-publication:" +
                candidate.id() +
                ":model:" +
                model.id() +
                ":r" +
                model.revision();
            var published = lifecyclePublication.publish(
                tenantId,
                actorId,
                model,
                implementation,
                new PublishCommand(reason, lifecycleKey),
                now
            );
            if (catalogServing != null) {
                catalogServing.projectLatestPublication(
                    candidate,
                    published.model(),
                    implementation,
                    target,
                    null
                );
            }
            PublishedModelBinding binding = publications.registerModel(
                candidate,
                target,
                observation,
                published.model(),
                published.release(),
                actorId,
                now
            );
            bindings.add(binding);
            if (catalogServing != null) {
                ProjectionMutation serving = catalogServing.promoteSuccessfulServing(
                    candidate,
                    published.model(),
                    implementation,
                    target,
                    null
                );
                if (serving.servingNotReady()) servingNotReadyCount++;
            }
        }
        publications.rebuildManualBinding(candidate, List.copyOf(bindings), actorId, now);
        boolean publicationRetry = candidate.status() == DeliveryStatus.PARTIAL;
        outbox.publishInternal(
            new PlatformEventRequest(
                (
                    publicationRetry
                        ? "model-release-candidate-publication-retried:"
                        : "model-release-candidate-published:"
                ) +
                candidate.id() +
                ":v" +
                candidate.version(),
                publicationRetry
                    ? "MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRIED"
                    : "MODEL_RELEASE_CANDIDATE_PUBLISHED",
                "modeling",
                "dts-platform",
                "MODEL_RELEASE_CANDIDATE",
                candidate.id().toString(),
                candidate.planId().toString(),
                publicationRetry ? "RETRY" : "PUBLISH",
                "INFO",
                "SUCCESS",
                now,
                actorId,
                publishRequestKey,
                null,
                publicationRetry
                    ? "MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRY"
                    : "MODEL_RELEASE_CANDIDATE_PUBLISH",
                "SPRINT_36_F3_ASSET_ACTION",
                Map.of(
                    "tenantId",
                    tenantId,
                    "planId",
                    candidate.planId(),
                    "environment",
                    candidate.environment(),
                    "executionTargetKey",
                    candidate.executionTargetKey(),
                    "scheduleMode",
                    "MANUAL_ONLY",
                    "entryCount",
                    bindings.size(),
                    "servingNotReadyCount",
                    servingNotReadyCount
                )
            )
        );
        CommandResult result = candidateCommands.transitionWithinAuditedCommit(
            tenantId,
            actorId,
            candidate.id(),
            new TransitionCommand(
                candidate.version(),
                DeliveryStatus.PUBLISHED,
                CandidatePublicationKeys.finalCommit(candidate, publishRequestKey),
                reason
            )
        );
        if (!result.replayed()) {
            String actionCode = publicationRetry
                ? "MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRY"
                : "MODEL_RELEASE_CANDIDATE_PUBLISH";
            auditSuccess(
                actorId,
                actionCode,
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
                    bindings.size(),
                    "servingNotReadyCount",
                    servingNotReadyCount
                )
            );
        }
        return result;
    }

    private void auditSuccess(
        String actorId,
        String actionCode,
        CandidateView candidate,
        Map<String, Object> payload
    ) {
        if (auditService == null) return;
        Map<String, Object> auditPayload = new LinkedHashMap<>(payload);
        auditPayload.put("actor", actorId);
        auditService.auditActionStrict(
            actionCode,
            AuditStage.SUCCESS,
            candidate.id().toString(),
            auditPayload
        );
    }

    private static List<PublicationUnit> dependencyOrder(
        CandidateView candidate,
        List<PublicationUnit> units
    ) {
        Map<UUID, PublicationUnit> remaining = new LinkedHashMap<>();
        units
            .stream()
            .sorted(Comparator.comparing(unit -> unit.model().id()))
            .forEach(unit -> remaining.put(unit.model().id(), unit));
        Set<UUID> inScope = Set.copyOf(remaining.keySet());
        Set<UUID> published = new LinkedHashSet<>();
        List<PublicationUnit> ordered = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            List<PublicationUnit> ready = remaining
                .values()
                .stream()
                .filter(unit ->
                    unit
                        .model()
                        .dependsOn()
                        .stream()
                        .map(dependency -> dependency.modelSpecId())
                        .filter(inScope::contains)
                        .allMatch(published::contains)
                )
                .sorted(Comparator.comparing(unit -> unit.model().id()))
                .toList();
            if (ready.isEmpty()) {
                throw new ModelReleaseCandidateException(
                    "MODEL_RELEASE_PUBLICATION_DEPENDENCY_CYCLE",
                    "Candidate publication scope contains a dependency cycle",
                    Kind.CONFLICT,
                    Map.of("candidateId", candidate.id(), "remainingModels", remaining.keySet())
                );
            }
            for (PublicationUnit unit : ready) {
                ordered.add(unit);
                published.add(unit.model().id());
                remaining.remove(unit.model().id());
            }
        }
        return List.copyOf(ordered);
    }

    private ModelSpecView requireDraft(
        CandidateView candidate,
        PublicationEntryEvidence observation
    ) {
        StoredModelSpec stored = modelSpecs
            .findCurrent(candidate.tenantId(), observation.modelSpecId())
            .orElseThrow(() -> modelConflict(candidate, observation.modelSpecId()));
        if (!stored.currentHead() || stored.currentSnapshot() == null || stored.currentSnapshot().isBlank()) {
            throw modelConflict(candidate, observation.modelSpecId());
        }
        ModelSpecView model = codec.readView(stored.currentSnapshot());
        if (
            model.status() != ModelStatus.DRAFT ||
            !candidate.planId().equals(model.planId()) ||
            !observation.modelSpecId().equals(model.id()) ||
            model.revision() != observation.modelRevision() ||
            !observation.modelChecksum().equals(model.checksum())
        ) {
            throw modelConflict(candidate, observation.modelSpecId());
        }
        return model;
    }

    private ImplementationView requireImplementation(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        ModelSpecView model
    ) {
        ImplementationView implementation = lifecycle
            .findImplementation(candidate.tenantId(), observation.modelSpecId())
            .orElseThrow(() -> modelConflict(candidate, observation.modelSpecId()));
        if (
            !"ACTIVE".equals(implementation.status()) ||
            !model.id().equals(implementation.modelSpecId()) ||
            !model.planId().equals(implementation.planId()) ||
            model.revision() != implementation.revision() ||
            !model.checksum().equals(implementation.modelChecksum()) ||
            model.implementationMode() != implementation.ownership() ||
            observation.implementationRevision() != implementation.implementationRevision() ||
            !observation.implementationChecksum().equals(implementation.implementationChecksum()) ||
            !observation.dbtUniqueId().equals(implementation.dbtUniqueId())
        ) {
            throw modelConflict(candidate, observation.modelSpecId());
        }
        return implementation;
    }

    private static ModelReleaseCandidateException modelConflict(
        CandidateView candidate,
        UUID modelSpecId
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_PUBLICATION_SNAPSHOT_CONFLICT",
            "ModelSpec or implementation changed before publication could be committed",
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "candidateVersion",
                candidate.version(),
                "modelSpecId",
                modelSpecId
            )
        );
    }

    private record PublicationUnit(
        PublicationEntryEvidence observation,
        ModelSpecView model,
        ImplementationView implementation
    ) {}
}
