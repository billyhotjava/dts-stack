package com.yuzhi.dts.platform.service.modeling.representation;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewEvidenceState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewReferenceProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewScope;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Canonical preview-reference projector. Only persisted, verified relation observations can produce a READY ref. */
@Component
public class CatalogModelPhysicalPreviewReferenceAdapter implements ModelPhysicalPreviewReferencePort {

    private final CatalogModelServingProjectionRepository repository;

    public CatalogModelPhysicalPreviewReferenceAdapter(CatalogModelServingProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public PhysicalPreviewProjection resolve(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        ModelServingProjection projection = repository.findProjection(tenantId, modelSpecId).orElse(null);
        ServingRef servingPointer = projection == null ? null : projection.servingRef();
        RelationEvidence servingEvidence = servingPointer == null
            ? null
            : repository.findRelationEvidence(tenantId, servingPointer.relationEvidenceId()).orElse(null);
        boolean servingReady = servingPointer != null && exactServing(servingPointer, servingEvidence);
        PhysicalPreviewReferenceProjection serving = servingReady
            ? reference(servingEvidence, PhysicalPreviewScope.SERVING)
            : null;

        RelationEvidence candidateEvidence = repository
            .findLatestSuccessfulCandidateEvidence(
                tenantId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum
            )
            .filter(CatalogModelPhysicalPreviewReferenceAdapter::complete)
            .orElse(null);
        if (
            candidateEvidence != null && servingEvidence != null &&
            Objects.equals(candidateEvidence.relationEvidenceId(), servingEvidence.relationEvidenceId())
        ) {
            candidateEvidence = null;
        }
        PhysicalPreviewReferenceProjection candidate = candidateEvidence == null
            ? null
            : reference(candidateEvidence, PhysicalPreviewScope.CANDIDATE);

        return new PhysicalPreviewProjection(
            serving,
            candidate,
            serving != null
                ? PhysicalPreviewEvidenceState.READY
                : servingPointer == null
                    ? PhysicalPreviewEvidenceState.UNAVAILABLE
                    : PhysicalPreviewEvidenceState.FAILED_STALE,
            candidate != null ? PhysicalPreviewEvidenceState.READY : PhysicalPreviewEvidenceState.UNAVAILABLE
        );
    }

    private static PhysicalPreviewReferenceProjection reference(RelationEvidence evidence, PhysicalPreviewScope scope) {
        return new PhysicalPreviewReferenceProjection(
            evidence.modelSpecId(),
            evidence.modelRevision(),
            evidence.modelChecksum(),
            evidence.implementationRevision(),
            evidence.implementationChecksum(),
            scope,
            evidence.candidateId(),
            evidence.candidateVersion(),
            evidence.attempt(),
            evidence.pipelineRunId(),
            evidence.observationAttempt(),
            evidence.relationEvidenceId(),
            evidence.evidenceChecksum()
        );
    }

    private static boolean exactServing(ServingRef pointer, RelationEvidence evidence) {
        return complete(evidence) &&
            Objects.equals(pointer.modelSpecId(), evidence.modelSpecId()) &&
            pointer.modelRevision() == evidence.modelRevision() &&
            Objects.equals(pointer.modelChecksum(), evidence.modelChecksum()) &&
            pointer.implementationRevision() == evidence.implementationRevision() &&
            Objects.equals(pointer.implementationChecksum(), evidence.implementationChecksum()) &&
            Objects.equals(pointer.candidateId(), evidence.candidateId()) &&
            pointer.candidateVersion() == evidence.candidateVersion() &&
            pointer.attempt() == evidence.attempt() &&
            Objects.equals(pointer.pipelineRunId(), evidence.pipelineRunId()) &&
            pointer.observationAttempt() == evidence.observationAttempt() &&
            Objects.equals(pointer.relationEvidenceId(), evidence.relationEvidenceId()) &&
            Objects.equals(pointer.evidenceChecksum(), evidence.evidenceChecksum()) &&
            Objects.equals(pointer.schemaName(), evidence.schemaName()) &&
            Objects.equals(pointer.identifier(), evidence.identifier());
    }

    private static boolean complete(RelationEvidence evidence) {
        return evidence != null && evidence.verified() && evidence.relationExists() &&
            "COMPLETED".equals(evidence.dispatchStatus()) && "BUILT".equals(evidence.pipelineStatus()) &&
            evidence.modelChecksum() != null && evidence.modelChecksum().matches("^[0-9a-f]{64}$") &&
            evidence.implementationChecksum() != null && evidence.implementationChecksum().matches("^[0-9a-f]{64}$") &&
            evidence.evidenceChecksum() != null && evidence.evidenceChecksum().matches("^[0-9a-f]{64}$");
    }
}
