package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.service.catalog.CatalogPublicationPolicyPort;
import com.yuzhi.dts.platform.service.catalog.CatalogPublicationPolicyPort.Decision;
import com.yuzhi.dts.platform.service.catalog.CatalogPublicationPolicyPort.Status;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Applies the asset-action gate while the authenticated release operator is still on the request thread.
 */
@Service
public class CandidatePublicationAdmissionService {

    private final CandidatePublicationEvidenceRepository evidence;
    private final CatalogPublicationPolicyPort catalogPolicy;
    private final ModelExecutionTargetCatalogResolver targetResolver;

    public CandidatePublicationAdmissionService(
        CandidatePublicationEvidenceRepository evidence,
        CatalogPublicationPolicyPort catalogPolicy,
        ModelExecutionTargetCatalogResolver targetResolver
    ) {
        this.evidence = evidence;
        this.catalogPolicy = catalogPolicy;
        this.targetResolver = targetResolver;
    }

    public List<PublicationEntryEvidence> requireAllowed(CandidateView candidate) {
        ResolvedCatalogTarget target = targetResolver.resolve(candidate);
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, false);
        for (PublicationEntryEvidence observation : observations) {
            Decision decision = catalogPolicy.authorizeUpsert(
                target.sourceId(),
                observation.schemaName(),
                observation.identifier()
            );
            requireAllowedDecision(
                candidate,
                observation,
                decision,
                target.sourceId(),
                "Current release operator cannot create or update every Candidate output asset"
            );
        }
        return observations;
    }

    public List<PublicationEntryEvidence> requireArchiveAllowed(CandidateView candidate) {
        List<PublicationEntryEvidence> observations = evidence.requireCurrent(candidate, false);
        for (PublicationEntryEvidence observation : observations) {
            var assetId = evidence.requirePublishedAssetId(candidate, observation);
            Decision decision = catalogPolicy.authorizeArchive(assetId);
            requireAllowedDecision(
                candidate,
                observation,
                decision,
                null,
                "Current release operator cannot archive every Candidate output asset"
            );
        }
        return observations;
    }

    private static void requireAllowedDecision(
        CandidateView candidate,
        PublicationEntryEvidence observation,
        Decision decision,
        UUID sourceId,
        String forbiddenMessage
    ) {
        if (decision.allowed()) {
            return;
        }
        if (decision.status() == Status.AMBIGUOUS) {
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
                    sourceId,
                    "schema",
                    observation.schemaName(),
                    "table",
                    observation.identifier(),
                    "matchingAssetIds",
                    decision.matchingAssetIds()
                )
            );
        }
        if (decision.status() == Status.PUBLISHED_ASSET_REQUIRED) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_PUBLISHED_ASSET_REQUIRED",
                "Candidate rollback requires its current published Catalog asset",
                Kind.CONFLICT,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "modelSpecId",
                    observation.modelSpecId(),
                    "assetId", decision.assetId()
                )
            );
        }
        String code = decision.status() == Status.POLICY_UNAVAILABLE
            ? "MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE"
            : "MODEL_RELEASE_ASSET_ACTION_FORBIDDEN";
        String message = decision.status() == Status.POLICY_UNAVAILABLE
            ? "Candidate asset-action policy cannot be evaluated"
            : forbiddenMessage;
        throw new ModelReleaseCandidateException(
            code,
            message,
            Kind.FORBIDDEN,
            Map.of(
                "candidateId",
                candidate.id(),
                "modelSpecId",
                observation.modelSpecId(),
                "assetId", decision.assetId(),
                "assetAction", decision.action()
            )
        );
    }
}
