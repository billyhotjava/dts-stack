package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.AssetRole;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.DiscoveryState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceChannel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceStatus;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.PublicationState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.RelationType;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ServingHealth;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter.DatasetObservation;
import com.yuzhi.dts.platform.service.modeling.ModelClassificationPublishGate.Blocker;
import com.yuzhi.dts.platform.service.modeling.ModelClassificationPublishGate.Decision;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.TaskView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Makes verified Candidate outputs addressable by the governance quality workflow before publication. */
@Service
public class CandidateQualityAssetRegistrationService {

    private static final String SERVICE_ACTOR = "service:dts-platform-quality";

    private final CandidatePublicationEvidenceRepository evidence;
    private final ModelExecutionTargetCatalogResolver targets;
    private final ModelSpecReader models;
    private final ModelClassificationPublishGate classifications;
    private final CandidatePublicationRepository publications;
    private final CatalogDatasetRepository datasets;
    private final CatalogPhysicalDatasetObservationAdapter observations;
    private final Clock clock;
    private final ModelAssetRegistrationTaskRepository tasks;

    /** Registration status of a built candidate as seen by the publication checks (F15 K3). */
    public enum RegistrationState {
        /** No task store is wired (legacy wiring); callers register inline as before. */
        UNTRACKED,
        /** A task store exists but this candidate has no task yet (built before the handoff existed). */
        MISSING,
        PENDING,
        SUCCEEDED,
        FAILED,
    }

    public record RegistrationStatus(RegistrationState state, String errorCode, String errorMessage, int attempts) {}

    public CandidateQualityAssetRegistrationService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        ModelSpecReader models,
        ModelClassificationPublishGate classifications,
        CandidatePublicationRepository publications,
        CatalogDatasetRepository datasets,
        CatalogPhysicalDatasetObservationAdapter observations
    ) {
        this(evidence, targets, models, classifications, publications, datasets, observations, Clock.systemUTC(), null);
    }

    @Autowired
    public CandidateQualityAssetRegistrationService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        ModelSpecReader models,
        ModelClassificationPublishGate classifications,
        CandidatePublicationRepository publications,
        CatalogDatasetRepository datasets,
        CatalogPhysicalDatasetObservationAdapter observations,
        ModelAssetRegistrationTaskRepository tasks
    ) {
        this(evidence, targets, models, classifications, publications, datasets, observations, Clock.systemUTC(), tasks);
    }

    CandidateQualityAssetRegistrationService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        ModelSpecReader models,
        ModelClassificationPublishGate classifications,
        CandidatePublicationRepository publications,
        CatalogDatasetRepository datasets,
        CatalogPhysicalDatasetObservationAdapter observations,
        Clock clock
    ) {
        this(evidence, targets, models, classifications, publications, datasets, observations, clock, null);
    }

    CandidateQualityAssetRegistrationService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        ModelSpecReader models,
        ModelClassificationPublishGate classifications,
        CandidatePublicationRepository publications,
        CatalogDatasetRepository datasets,
        CatalogPhysicalDatasetObservationAdapter observations,
        Clock clock,
        ModelAssetRegistrationTaskRepository tasks
    ) {
        this.evidence = Objects.requireNonNull(evidence, "evidence is required");
        this.targets = Objects.requireNonNull(targets, "targets are required");
        this.models = Objects.requireNonNull(models, "models are required");
        this.classifications = Objects.requireNonNull(classifications, "classifications are required");
        this.publications = Objects.requireNonNull(publications, "publications are required");
        this.datasets = Objects.requireNonNull(datasets, "datasets are required");
        this.observations = Objects.requireNonNull(observations, "observations are required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.tasks = tasks;
    }

    /**
     * F15 K3: hands a candidate that has just become BUILT to asset registration. Called inside the transaction
     * that commits BUILT so the handoff cannot be lost; the registration itself runs later in
     * {@link ModelAssetRegistrationWorker} and can never roll back the confirmed build.
     */
    public void requestRegistration(CandidateView built) {
        if (tasks == null || built == null) return;
        tasks.enqueue(built.tenantId(), built.id(), built.version(), built.planId(), built.environment(), clock.instant());
    }

    public RegistrationStatus registrationStatus(CandidateView candidate) {
        if (tasks == null) return new RegistrationStatus(RegistrationState.UNTRACKED, null, null, 0);
        TaskView task = tasks.findByCandidate(candidate.tenantId(), candidate.id()).orElse(null);
        if (task == null) return new RegistrationStatus(RegistrationState.MISSING, null, null, 0);
        RegistrationState state = switch (task.state()) {
            case PENDING -> RegistrationState.PENDING;
            case SUCCEEDED -> RegistrationState.SUCCEEDED;
            case FAILED -> RegistrationState.FAILED;
        };
        return new RegistrationStatus(state, task.lastErrorCode(), task.lastErrorMessage(), task.attempts());
    }

    /** A registration performed directly (manual retry in the catalog) completes the pending task too. */
    public void recordRegistered(CandidateView candidate) {
        if (tasks == null || candidate == null) return;
        tasks.markSucceeded(candidate.tenantId(), candidate.id(), clock.instant());
    }

    @Transactional
    public List<UUID> ensureRegistered(CandidateView candidate) {
        requireBuildVerified(candidate);
        try {
            ResolvedCatalogTarget target = targets.resolve(candidate);
            PreparedScope scope = prepareScope(candidate);
            List<UUID> registered = new ArrayList<>();
            for (PublicationEntryEvidence physical : scope.physical()) {
                ModelSpecView model = scope.models().get(physical.modelSpecId());
                Decision classification = classifications.admitAndSeal(
                    candidate.tenantId(), model.id(), model.revision(), model.checksum(),
                    "candidate-quality:" + candidate.id(), scope.levels()
                );
                requireClassification(candidate, model, classification);
                Instant now = clock.instant();
                UUID datasetId = publications.prepareQualityDataset(
                    candidate,
                    target,
                    physical,
                    model,
                    classification.effectiveLevel(),
                    SERVICE_ACTOR,
                    now
                );
                CatalogDataset dataset = datasets
                    .findById(datasetId)
                    .orElseThrow(() -> registrationFailure(candidate, model, "Catalog dataset was not persisted"));
                observations.observe(
                    dataset,
                    observation(candidate, physical, model, now)
                );
                registered.add(datasetId);
            }
            if (registered.isEmpty()) {
                throw registrationFailure(candidate, null, "Candidate has no verified physical outputs");
            }
            return List.copyOf(registered);
        } catch (ModelReleaseCandidateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ModelReleaseCandidateException(
                "MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED",
                "Verified physical outputs could not be prepared for governance quality",
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "failureType",
                    failure.getClass().getSimpleName()
                )
            );
        }
    }

    /** Read the same registration prerequisites used by the reconciler, without writing assets. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ModelReleaseCandidateContract.BlockerView previewBlocker(CandidateView candidate) {
        try {
            prepareScope(candidate);
            return null;
        } catch (ModelReleaseCandidateException blocked) {
            return new ModelReleaseCandidateContract.BlockerView(blocked.code(), blocked.getMessage());
        }
    }

    private PreparedScope prepareScope(CandidateView candidate) {
        requireBuildVerified(candidate);
        List<PublicationEntryEvidence> physical = evidence.requireCurrent(candidate, false);
        Map<UUID, ModelSpecView> modelViews = new LinkedHashMap<>();
        for (PublicationEntryEvidence item : physical) {
            ModelSpecView model = models.revision(candidate.tenantId(),
                new ModelRevisionRef(item.modelSpecId(), item.modelRevision()));
            requirePinnedModel(candidate, item, model);
            modelViews.put(model.id(), model);
        }
        Map<String, String> levels = new LinkedHashMap<>();
        for (Decision decision : classifications.evaluateVerifiedScope(candidate.tenantId(), List.copyOf(modelViews.values()))) {
            ModelSpecView model = modelViews.get(decision.modelSpecId());
            requireClassification(candidate, model, decision);
            levels.put(decision.outputSubjectKey(), decision.effectiveLevel());
        }
        return new PreparedScope(physical, modelViews, levels);
    }

    private record PreparedScope(List<PublicationEntryEvidence> physical, Map<UUID, ModelSpecView> models,
        Map<String, String> levels) {}

    private static void requireBuildVerified(CandidateView candidate) {
        if (
            candidate == null ||
            (candidate.status() != DeliveryStatus.BUILT &&
                candidate.status() != DeliveryStatus.QUALITY_RUNNING)
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_BUILT_REQUIRED",
                "Candidate must have verified physical outputs before governance assets are prepared",
                Kind.CONFLICT
            );
        }
    }

    private static void requirePinnedModel(
        CandidateView candidate,
        PublicationEntryEvidence physical,
        ModelSpecView model
    ) {
        if (
            model == null ||
            !physical.modelSpecId().equals(model.id()) ||
            physical.modelRevision() != model.revision() ||
            !physical.modelChecksum().equals(model.checksum())
        ) {
            throw registrationFailure(candidate, model, "Model revision no longer matches the verified output");
        }
    }

    private static void requireClassification(
        CandidateView candidate,
        ModelSpecView model,
        Decision decision
    ) {
        if (
            decision != null &&
            decision.ready() &&
            decision.effectiveLevel() != null &&
            !decision.effectiveLevel().isBlank()
        ) {
            return;
        }
        List<String> blockerCodes = decision == null
            ? List.of("CLASSIFICATION_UNAVAILABLE")
            : decision.blockers().stream().map(Blocker::code).toList();
        throw new ModelReleaseCandidateException(
            "MODEL_SPEC_GOVERNANCE_CLASSIFICATION_REQUIRED",
            "模型“" + model.name() + "”的密级证据未满足：" + String.join("、", blockerCodes),
            Kind.UNPROCESSABLE,
            Map.of(
                "candidateId",
                candidate.id(),
                "modelSpecId",
                model.id(),
                "blockers",
                blockerCodes
            )
        );
    }

    private static DatasetObservation observation(
        CandidateView candidate,
        PublicationEntryEvidence physical,
        ModelSpecView model,
        Instant now
    ) {
        return new DatasetObservation(
            relationType(physical),
            model.domainId(),
            warehouseLayer(model),
            model.modelType() == ModelType.DIMENSION
                ? AssetRole.DIMENSION_TABLE
                : AssetRole.RELATION,
            ProducerKind.MODELING,
            model.id().toString(),
            "model-r" + model.revision() + "/implementation-r" + physical.implementationRevision(),
            EvidenceChannel.MATERIALIZATION_OBSERVATION,
            "candidate:" + candidate.id() + ":v" + candidate.version() + ":pipeline:" + physical.pipelineRunId() + ":metadata:" + physical.metadataChecksum(),
            physical.observedAt() == null ? now : physical.observedAt(),
            EvidenceStatus.ACTIVE,
            DiscoveryState.VERIFIED,
            PublicationState.UNPUBLISHED,
            ServingHealth.HEALTHY
        );
    }

    private static RelationType relationType(PublicationEntryEvidence physical) {
        return switch (physical.relationType()) {
            case TABLE -> RelationType.TABLE;
            case VIEW -> RelationType.VIEW;
            case MATERIALIZED_VIEW -> RelationType.MATERIALIZED_VIEW;
        };
    }

    private static String warehouseLayer(ModelSpecView model) {
        // Catalog semantics use the canonical layer, not a plan-specific sublayer.
        if (model.layer() != null) return model.layer().name();
        if (model.warehouseLayerCode() != null && !model.warehouseLayerCode().isBlank()) {
            return model.warehouseLayerCode();
        }
        return model.layer() == null ? null : model.layer().name();
    }

    private static ModelReleaseCandidateException registrationFailure(
        CandidateView candidate,
        ModelSpecView model,
        String message
    ) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("candidateId", candidate.id());
        if (model != null && model.id() != null) {
            details.put("modelSpecId", model.id());
        }
        return new ModelReleaseCandidateException(
            "MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED",
            message,
            Kind.UNPROCESSABLE,
            details
        );
    }
}
