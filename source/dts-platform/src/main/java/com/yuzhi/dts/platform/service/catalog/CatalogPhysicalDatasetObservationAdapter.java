package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Thin producer adapter for an already persisted physical dataset.
 *
 * <p>It never creates another catalog dataset. It preserves governance, publication, lifecycle and gate facts unless
 * the producer supplies an explicit operational advance, then verifies the command receipt against the durable
 * dataset identity.
 */
@Service
public class CatalogPhysicalDatasetObservationAdapter {

    private final CatalogAssetRegistrationService assets;

    public CatalogPhysicalDatasetObservationAdapter(CatalogAssetRegistrationService assets) {
        this.assets = assets;
    }

    public ObservationResult observe(CatalogDataset dataset, DatasetObservation observation) {
        Objects.requireNonNull(dataset, "catalog dataset is required");
        Objects.requireNonNull(observation, "dataset observation is required");
        UUID resourceId = Objects.requireNonNull(dataset.getId(), "catalog dataset id is required");
        String assetKey = CatalogAssetKey.dataset(dataset);
        Optional<AssetSemanticsView> current = assets.find(CatalogAssetType.DATASET, assetKey);
        current
            .map(AssetSemanticsView::resourceId)
            .filter(Objects::nonNull)
            .filter(existing -> !existing.equals(resourceId))
            .ifPresent(existing -> {
                throw new CatalogAssetObservationException(
                    "CATALOG_ASSET_RESOURCE_ID_CONFLICT",
                    "Catalog asset key is already bound to resource " + existing
                );
            });

        StatusAxes currentAxes = current.map(AssetSemanticsView::statusAxes).orElse(null);
        UUID domainId = firstNonNull(
            observation.domainId(),
            current.map(AssetSemanticsView::domainId).orElse(null),
            datasetDomainId(dataset)
        );
        GovernanceReadiness governance = advanceGovernance(
            currentAxes == null ? null : currentAxes.governance(),
            observedGovernance(dataset)
        );
        StatusAxes axes = new StatusAxes(
            firstNonNull(
                observation.discovery(),
                currentAxes == null ? null : currentAxes.discovery(),
                DiscoveryState.DISCOVERED
            ),
            governance,
            firstNonNull(
                observation.publication(),
                currentAxes == null ? null : currentAxes.publication(),
                PublicationState.UNPUBLISHED
            ),
            firstNonNull(
                observation.serving(),
                currentAxes == null ? null : currentAxes.serving(),
                ServingHealth.UNKNOWN
            ),
            firstNonNull(
                currentAxes == null ? null : currentAxes.lifecycle(),
                LifecycleState.ACTIVE
            )
        );
        ObservationCommand command = new ObservationCommand(
            CatalogAssetType.DATASET,
            assetKey,
            resourceId,
            observation.relationType(),
            false,
            false,
            domainId,
            firstText(observation.warehouseLayer(), dataset.getWarehouseLayer()),
            observation.assetRole(),
            observation.producerKind(),
            observation.producerId(),
            observation.producerVersion(),
            observation.evidenceChannel(),
            observation.evidenceRef(),
            observation.observedAt(),
            observation.evidenceStatus(),
            axes,
            current.map(AssetSemanticsView::qualityGatePassed).orElse(null),
            current.map(AssetSemanticsView::permissionGatePassed).orElse(null)
        );
        ObservationResult result = assets.observe(command);
        if (result == null || !result.admitted() || result.receipt() == null) {
            String code = result == null || !StringUtils.hasText(result.reasonCode())
                ? "CATALOG_ASSET_OBSERVATION_REJECTED"
                : result.reasonCode();
            throw new CatalogAssetObservationException(code, "Catalog asset observation was rejected for " + assetKey);
        }
        requireMatchingReceipt(assetKey, resourceId, result.receipt());
        return result;
    }

    private static void requireMatchingReceipt(String assetKey, UUID resourceId, RegistrationReceipt receipt) {
        if (
            receipt.assetType() != CatalogAssetType.DATASET ||
            !assetKey.equals(receipt.assetKey()) ||
            !resourceId.equals(receipt.resourceId())
        ) {
            throw new CatalogAssetObservationException(
                "CATALOG_ASSET_OBSERVATION_RECEIPT_MISMATCH",
                "Catalog observation receipt does not match the durable dataset identity"
            );
        }
    }

    private static UUID datasetDomainId(CatalogDataset dataset) {
        return dataset.getDomain() == null ? null : dataset.getDomain().getId();
    }

    private static GovernanceReadiness observedGovernance(CatalogDataset dataset) {
        CatalogAssetGovernanceProfile profile = CatalogAssetGovernanceInspector.inspect(dataset);
        if (profile.missingFields().isEmpty()) {
            return GovernanceReadiness.GOVERNED;
        }
        return profile.missingFields().contains("domain")
            ? GovernanceReadiness.UNASSIGNED
            : GovernanceReadiness.INCOMPLETE;
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

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    public record DatasetObservation(
        RelationType relationType,
        UUID domainId,
        String warehouseLayer,
        AssetRole assetRole,
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        EvidenceChannel evidenceChannel,
        String evidenceRef,
        Instant observedAt,
        EvidenceStatus evidenceStatus,
        DiscoveryState discovery,
        PublicationState publication,
        ServingHealth serving
    ) {}
}
