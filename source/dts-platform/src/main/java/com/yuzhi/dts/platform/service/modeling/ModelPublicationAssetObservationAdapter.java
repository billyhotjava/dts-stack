package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService.ObservationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetSemanticsView;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.RegistrationReceipt;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.AssetRole;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.DiscoveryState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceChannel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceStatus;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.LifecycleState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ObservationCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.PublicationState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.RelationType;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ServingHealth;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.StatusAxes;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Projects one verified model publication through the single physical-asset observation boundary. */
@Service
public class ModelPublicationAssetObservationAdapter {

    private final CatalogAssetRegistrationService assets;

    public ModelPublicationAssetObservationAdapter(CatalogAssetRegistrationService assets) {
        this.assets = assets;
    }

    public ObservationResult observePublishedAsset(
        CandidateView candidate,
        ResolvedCatalogTarget target,
        PublicationEntryEvidence observation,
        ModelSpecView model,
        PublishedModelBinding binding,
        Instant now
    ) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(target, "catalog target is required");
        Objects.requireNonNull(observation, "physical observation is required");
        Objects.requireNonNull(model, "published model is required");
        Objects.requireNonNull(binding, "published binding is required");
        Objects.requireNonNull(now, "publication time is required");
        UUID physicalAssetId = Objects.requireNonNull(
            binding.physicalAssetId(),
            "published physical asset id is required"
        );
        String assetKey = CatalogAssetKey.dataset(
            target.sourceId(),
            observation.databaseName(),
            observation.schemaName(),
            observation.identifier(),
            model.name()
        );
        Optional<AssetSemanticsView> current = assets.find(CatalogAssetType.DATASET, assetKey);
        current
            .map(AssetSemanticsView::resourceId)
            .filter(Objects::nonNull)
            .filter(existing -> !existing.equals(physicalAssetId))
            .ifPresent(existing -> {
                throw identityConflict(candidate, model, assetKey, existing, physicalAssetId);
            });

        StatusAxes existingAxes = current.map(AssetSemanticsView::statusAxes).orElse(null);
        String warehouseLayer = warehouseLayer(model);
        GovernanceReadiness observedGovernance = model.domainId() == null
            ? GovernanceReadiness.UNASSIGNED
            : warehouseLayer == null || warehouseLayer.isBlank()
                ? GovernanceReadiness.INCOMPLETE
                : GovernanceReadiness.GOVERNED;
        GovernanceReadiness governance = advanceGovernance(
            existingAxes == null ? null : existingAxes.governance(),
            observedGovernance
        );
        LifecycleState lifecycle = existingAxes == null
            ? LifecycleState.ACTIVE
            : existingAxes.lifecycle();
        UUID domainId = model.domainId() != null
            ? model.domainId()
            : current.map(AssetSemanticsView::domainId).orElse(null);
        Boolean qualityGatePassed = current.map(AssetSemanticsView::qualityGatePassed).orElse(null);
        Boolean permissionGatePassed = current.map(AssetSemanticsView::permissionGatePassed).orElse(null);

        ObservationCommand command = new ObservationCommand(
            CatalogAssetType.DATASET,
            assetKey,
            physicalAssetId,
            relationType(observation),
            false,
            false,
            domainId,
            warehouseLayer,
            model.modelType() == ModelType.DIMENSION ? AssetRole.DIMENSION_TABLE : AssetRole.RELATION,
            ProducerKind.MODELING,
            model.id().toString(),
            producerVersion(candidate, observation),
            EvidenceChannel.MATERIALIZATION_OBSERVATION,
            evidenceRef(candidate, observation),
            observation.observedAt() == null ? now : observation.observedAt(),
            EvidenceStatus.ACTIVE,
            new StatusAxes(
                DiscoveryState.VERIFIED,
                governance,
                PublicationState.PUBLISHED,
                ServingHealth.HEALTHY,
                lifecycle
            ),
            qualityGatePassed,
            permissionGatePassed
        );
        ObservationResult result = assets.observe(command);
        if (result == null || !result.admitted() || result.receipt() == null) {
            String reasonCode = result == null || result.reasonCode() == null
                ? "CATALOG_ASSET_OBSERVATION_REJECTED"
                : result.reasonCode();
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CATALOG_ASSET_OBSERVATION_REJECTED",
                "Verified materialization could not be registered as the published Catalog asset",
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId", candidate.id(),
                    "modelSpecId", model.id(),
                    "assetKey", assetKey,
                    "reasonCode", reasonCode
                )
            );
        }
        requireMatchingReceipt(candidate, model, assetKey, physicalAssetId, result.receipt());
        return result;
    }

    private void requireMatchingReceipt(
        CandidateView candidate,
        ModelSpecView model,
        String assetKey,
        UUID physicalAssetId,
        RegistrationReceipt receipt
    ) {
        if (
            receipt.assetType() != CatalogAssetType.DATASET ||
            !assetKey.equals(receipt.assetKey()) ||
            !physicalAssetId.equals(receipt.resourceId())
        ) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CATALOG_ASSET_RECEIPT_MISMATCH",
                "Catalog observation receipt does not match the published physical asset",
                Kind.CONFLICT,
                Map.of(
                    "candidateId", candidate.id(),
                    "modelSpecId", model.id(),
                    "assetKey", assetKey,
                    "physicalAssetId", physicalAssetId
                )
            );
        }
    }

    private static RelationType relationType(PublicationEntryEvidence observation) {
        return switch (observation.relationType()) {
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

    private static GovernanceReadiness advanceGovernance(
        GovernanceReadiness current,
        GovernanceReadiness observed
    ) {
        if (current == GovernanceReadiness.GOVERNED || observed == GovernanceReadiness.GOVERNED) {
            return GovernanceReadiness.GOVERNED;
        }
        if (current == GovernanceReadiness.INCOMPLETE || observed == GovernanceReadiness.INCOMPLETE) {
            return GovernanceReadiness.INCOMPLETE;
        }
        return GovernanceReadiness.UNASSIGNED;
    }

    private static String producerVersion(CandidateView candidate, PublicationEntryEvidence observation) {
        return (
            "model-r" +
            observation.modelRevision() +
            "/implementation-r" +
            observation.implementationRevision() +
            "/candidate-v" +
            candidate.version()
        );
    }

    private static String evidenceRef(CandidateView candidate, PublicationEntryEvidence observation) {
        return (
            "candidate:" +
            candidate.id() +
            ":v" +
            candidate.version() +
            ":pipeline:" +
            observation.pipelineRunId() +
            ":metadata:" +
            observation.metadataChecksum()
        );
    }

    private static ModelReleaseCandidateException identityConflict(
        CandidateView candidate,
        ModelSpecView model,
        String assetKey,
        UUID existingResourceId,
        UUID physicalAssetId
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CATALOG_ASSET_IDENTITY_CONFLICT",
            "Catalog asset key is already bound to a different physical resource",
            Kind.CONFLICT,
            Map.of(
                "candidateId", candidate.id(),
                "modelSpecId", model.id(),
                "assetKey", assetKey,
                "existingResourceId", existingResourceId,
                "physicalAssetId", physicalAssetId
            )
        );
    }
}
