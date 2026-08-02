package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ProjectionMutation;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.SuccessfulPublicationCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Projects canonical publication/materialization evidence to one stable Catalog asset. */
@Service
public class CatalogModelServingService {

    private final CatalogModelServingProjectionRepository repository;
    private final PlatformEventOutboxService outbox;
    private final CatalogModelStableClassificationProjector classifications;
    private final Clock clock;

    @Autowired
    public CatalogModelServingService(
        CatalogModelServingProjectionRepository repository,
        PlatformEventOutboxService outbox,
        CatalogModelStableClassificationProjector classifications
    ) {
        this(repository, outbox, classifications, Clock.systemUTC());
    }

    public CatalogModelServingService(
        CatalogModelServingProjectionRepository repository,
        PlatformEventOutboxService outbox,
        Clock clock
    ) {
        this(repository, outbox, null, clock);
    }

    CatalogModelServingService(
        CatalogModelServingProjectionRepository repository,
        PlatformEventOutboxService outbox,
        CatalogModelStableClassificationProjector classifications,
        Clock clock
    ) {
        this.repository = repository;
        this.outbox = outbox;
        this.classifications = classifications;
        this.clock = clock;
    }

    public ProjectionMutation projectSuccessfulPublication(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation,
        ResolvedCatalogTarget target,
        UUID physicalAssetId
    ) {
        Instant now = clock.instant();
        CatalogAssetType assetType = CatalogAssetType.SEMANTIC_MODEL;
        String assetKey = CatalogAssetKey.semanticModel(model.id().toString());
        SuccessfulPublicationCommand command = command(
            candidate,
            model,
            implementation,
            target,
            physicalAssetId,
            now
        );
        projectClassifications(candidate, model, implementation);
        ProjectionMutation latest = repository.projectLatestPublished(command);
        ProjectionMutation serving = repository.promoteServing(command);
        ProjectionMutation mutation = new ProjectionMutation(
            latest.latestPublishedChanged(),
            serving.servingChanged(),
            serving.version(),
            serving.outcomeCode()
        );
        emit(candidate, model, implementation, assetType, assetKey, mutation, now);
        return mutation;
    }

    /** Governance-only transition. A missing build/evidence never changes an existing serving ref. */
    public ProjectionMutation projectLatestPublication(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation,
        ResolvedCatalogTarget target,
        UUID physicalAssetId
    ) {
        Instant now = clock.instant();
        CatalogAssetType assetType = CatalogAssetType.SEMANTIC_MODEL;
        String assetKey = CatalogAssetKey.semanticModel(model.id().toString());
        projectClassifications(candidate, model, implementation);
        ProjectionMutation mutation = repository.projectLatestPublished(
            command(candidate, model, implementation, target, physicalAssetId, now)
        );
        emit(candidate, model, implementation, assetType, assetKey, mutation, now);
        return mutation;
    }

    /** Materialization-only transition; it cannot advance latestPublishedRef. */
    public ProjectionMutation promoteSuccessfulServing(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation,
        ResolvedCatalogTarget target,
        UUID physicalAssetId
    ) {
        Instant now = clock.instant();
        CatalogAssetType assetType = CatalogAssetType.SEMANTIC_MODEL;
        String assetKey = CatalogAssetKey.semanticModel(model.id().toString());
        ProjectionMutation mutation = repository.promoteServing(
            command(candidate, model, implementation, target, physicalAssetId, now)
        );
        emit(candidate, model, implementation, assetType, assetKey, mutation, now);
        return mutation;
    }

    private static SuccessfulPublicationCommand command(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation,
        ResolvedCatalogTarget target,
        UUID physicalAssetId,
        Instant now
    ) {
        return new SuccessfulPublicationCommand(
            candidate.tenantId(),
            model.id(),
            model.revision(),
            model.checksum(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            candidate.id(),
            candidate.version(),
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetKey.semanticModel(model.id().toString()),
            target.sourceId(),
            target.adapter(),
            physicalAssetId,
            now
        );
    }

    private void projectClassifications(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation
    ) {
        if (classifications != null) {
            classifications.project(
                model,
                implementation,
                "candidate:" + candidate.id() + ":v" + candidate.version()
            );
        }
    }

    private void emit(
        CandidateView candidate,
        ModelSpecView model,
        ImplementationView implementation,
        CatalogAssetType assetType,
        String assetKey,
        ProjectionMutation mutation,
        Instant now
    ) {
        if (mutation.latestPublishedChanged() || mutation.servingChanged() || !"NO_CHANGE".equals(mutation.outcomeCode())) {
            outbox.publishInternal(
                new PlatformEventRequest(
                    projectionEventId(model.id(), mutation.version(), mutation.outcomeCode()),
                    "MODELING_CATALOG_PROJECTION",
                    "modeling",
                    "dts-platform",
                    "MODEL_SPEC",
                    model.id().toString(),
                    model.name(),
                    "PROJECT",
                    "INFO",
                    mutation.servingNotReady() ? "BLOCKED" : "SUCCESS",
                    now,
                    candidate.lastModifiedBy(),
                    null,
                    null,
                    "MODELING_CATALOG_PROJECTION",
                    "SPRINT_83_CATALOG_SERVING",
                    Map.ofEntries(
                        Map.entry("tenantId", candidate.tenantId()),
                        Map.entry("catalogAssetType", assetType.name()),
                        Map.entry("catalogAssetKey", assetKey),
                        Map.entry("modelRevision", model.revision()),
                        Map.entry("implementationRevision", implementation.implementationRevision()),
                        Map.entry("candidateId", candidate.id()),
                        Map.entry("candidateVersion", candidate.version()),
                        Map.entry("latestPublishedChanged", mutation.latestPublishedChanged()),
                        Map.entry("servingChanged", mutation.servingChanged()),
                        Map.entry("projectionVersion", mutation.version()),
                        Map.entry("outcomeCode", mutation.outcomeCode())
                    )
                )
            );
        }
    }

    private static String projectionEventId(UUID modelId, long version, String outcomeCode) {
        String outcomeToken = switch (outcomeCode) {
            case "LATEST_PUBLISHED" -> "lp";
            case "SERVING_NOT_READY" -> "snr";
            case "SERVING_STALE_REJECTED" -> "ssr";
            case "SERVING_PROMOTED" -> "sp";
            default -> throw new IllegalArgumentException("Unsupported Catalog projection outcome: " + outcomeCode);
        };
        return "model-catalog:" + modelId + ":v" + version + ":" + outcomeToken;
    }
}
