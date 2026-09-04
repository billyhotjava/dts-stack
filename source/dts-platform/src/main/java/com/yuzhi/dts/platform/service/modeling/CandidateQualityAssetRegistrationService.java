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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    @Autowired
    public CandidateQualityAssetRegistrationService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        ModelSpecReader models,
        ModelClassificationPublishGate classifications,
        CandidatePublicationRepository publications,
        CatalogDatasetRepository datasets,
        CatalogPhysicalDatasetObservationAdapter observations
    ) {
        this(
            evidence,
            targets,
            models,
            classifications,
            publications,
            datasets,
            observations,
            Clock.systemUTC()
        );
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
        this.evidence = Objects.requireNonNull(evidence, "evidence is required");
        this.targets = Objects.requireNonNull(targets, "targets are required");
        this.models = Objects.requireNonNull(models, "models are required");
        this.classifications = Objects.requireNonNull(classifications, "classifications are required");
        this.publications = Objects.requireNonNull(publications, "publications are required");
        this.datasets = Objects.requireNonNull(datasets, "datasets are required");
        this.observations = Objects.requireNonNull(observations, "observations are required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public List<UUID> ensureRegistered(CandidateView candidate) {
        requireQualityRunning(candidate);
        try {
            ResolvedCatalogTarget target = targets.resolve(candidate);
            List<UUID> registered = new ArrayList<>();
            for (PublicationEntryEvidence physical : evidence.requireCurrent(candidate, false)) {
                ModelSpecView model = models.revision(
                    candidate.tenantId(),
                    new ModelRevisionRef(
                        physical.modelSpecId(),
                        physical.modelRevision()
                    )
                );
                requirePinnedModel(candidate, physical, model);
                Decision classification = classifications.evaluate(
                    candidate.tenantId(),
                    model.id(),
                    model.revision(),
                    model.checksum()
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

    private static void requireQualityRunning(CandidateView candidate) {
        if (candidate == null || candidate.status() != DeliveryStatus.QUALITY_RUNNING) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_QUALITY_RUNNING_REQUIRED",
                "Candidate must be running quality validation before physical assets are prepared",
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
            "Verified physical output requires a resolved classification before quality execution",
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
