package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies the asset-action gate while the authenticated release operator is still on the request thread.
 */
@Service
public class CandidatePublicationAdmissionService {

    private static final Logger log = LoggerFactory.getLogger(CandidatePublicationAdmissionService.class);

    private final CandidatePublicationEvidenceRepository evidence;
    private final CatalogDatasetRepository catalogs;
    private final AccessChecker accessChecker;
    private final ModelExecutionTargetCatalogResolver targetResolver;

    public CandidatePublicationAdmissionService(
        CandidatePublicationEvidenceRepository evidence,
        CatalogDatasetRepository catalogs,
        AccessChecker accessChecker,
        ModelExecutionTargetCatalogResolver targetResolver
    ) {
        this.evidence = evidence;
        this.catalogs = catalogs;
        this.accessChecker = accessChecker;
        this.targetResolver = targetResolver;
    }

    public List<PublicationEntryEvidence> requireAllowed(CandidateView candidate) {
        ResolvedCatalogTarget target = targetResolver.resolve(candidate);
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, false);
        for (PublicationEntryEvidence observation : observations) {
            CatalogDataset prospective = CandidatePublicationAssetFactory.prospective(
                observation,
                target
            );
            CatalogDataset current = currentAsset(
                candidate,
                observation,
                target
            );
            AssetAction action = current == null ? AssetAction.CREATE : AssetAction.UPDATE;
            CatalogDataset subject = current == null ? prospective : current;
            if (!canPerform(candidate, observation, subject, action)) {
                throw assetActionForbidden(
                    candidate,
                    observation,
                    subject,
                    action,
                    "Current release operator cannot create or update every Candidate output asset"
                );
            }
        }
        return observations;
    }

    public List<PublicationEntryEvidence> requireArchiveAllowed(CandidateView candidate) {
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, false);
        for (PublicationEntryEvidence observation : observations) {
            var assetId = evidence.requirePublishedAssetId(candidate, observation);
            CatalogDataset current = catalogs
                .findById(assetId)
                .filter(asset ->
                    Boolean.TRUE.equals(asset.getEnabled()) &&
                    "PUBLISHED".equalsIgnoreCase(asset.getLifecycleStatus())
                )
                .orElseThrow(() ->
                    new ModelReleaseCandidateException(
                        "MODEL_RELEASE_PUBLISHED_ASSET_REQUIRED",
                        "Candidate rollback requires its current published Catalog asset",
                        Kind.CONFLICT,
                        Map.of(
                            "candidateId",
                            candidate.id(),
                            "modelSpecId",
                            observation.modelSpecId(),
                            "assetId",
                            assetId
                        )
                    )
                );
            if (!canPerform(candidate, observation, current, AssetAction.ARCHIVE)) {
                throw assetActionForbidden(
                    candidate,
                    observation,
                    current,
                    AssetAction.ARCHIVE,
                    "Current release operator cannot archive every Candidate output asset"
                );
            }
        }
        return observations;
    }

    private CatalogDataset currentAsset(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        ResolvedCatalogTarget target
    ) {
        List<CatalogDataset> matches = catalogs
            .findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                observation.schemaName(),
                observation.identifier()
            )
            .stream()
            .filter(dataset ->
                dataset.getSourceId() == null ||
                target.sourceId().equals(dataset.getSourceId())
            )
            .toList();
        if (matches.size() > 1) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CATALOG_LOCATOR_AMBIGUOUS",
                "More than one Catalog asset claims the Candidate physical relation",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "modelSpecId",
                    observation.modelSpecId(),
                    "sourceId",
                    target.sourceId(),
                    "schema",
                    observation.schemaName(),
                    "table",
                    observation.identifier(),
                    "matchingAssetIds",
                    matches.stream().map(CatalogDataset::getId).toList()
                )
            );
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private boolean canPerform(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        CatalogDataset subject,
        AssetAction action
    ) {
        try {
            return accessChecker.canPerform(subject, action);
        } catch (RuntimeException failure) {
            log.warn(
                "Candidate asset-action policy evaluation failed: candidateId={}, modelSpecId={}, assetId={}, action={}",
                candidate.id(),
                observation.modelSpecId(),
                subject.getId(),
                action,
                failure
            );
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE",
                "Candidate asset-action policy cannot be evaluated",
                Kind.FORBIDDEN,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "modelSpecId",
                    observation.modelSpecId(),
                    "assetId",
                    subject.getId(),
                    "assetAction",
                    action
                )
            );
        }
    }

    private static ModelReleaseCandidateException assetActionForbidden(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        CatalogDataset subject,
        AssetAction action,
        String message
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_ASSET_ACTION_FORBIDDEN",
            message,
            Kind.FORBIDDEN,
            Map.of(
                "candidateId",
                candidate.id(),
                "modelSpecId",
                observation.modelSpecId(),
                "assetId",
                subject.getId(),
                "assetAction",
                action
            )
        );
    }
}
