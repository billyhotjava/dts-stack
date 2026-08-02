package com.yuzhi.dts.platform.service.modeling.representation;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelRepresentationEvidenceRepository;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationPin;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.PublicationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RuntimeEvidence;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates immutable representation evidence from the existing canonical ledgers; it owns no parallel state. */
@Component
@Transactional(readOnly = true)
public class CanonicalModelRepresentationEvidenceAdapter implements ModelRepresentationEvidencePort {

    private final ModelRepresentationEvidenceRepository evidence;
    private final CatalogModelServingProjectionRepository catalog;

    public CanonicalModelRepresentationEvidenceAdapter(
        ModelRepresentationEvidenceRepository evidence,
        CatalogModelServingProjectionRepository catalog
    ) {
        this.evidence = evidence;
        this.catalog = catalog;
    }

    @Override
    public Optional<ImplementationPin> findCurrentPin(String tenantId, UUID modelSpecId) {
        return evidence.findCurrentPin(tenantId, modelSpecId);
    }

    @Override
    public Optional<RepresentationEvidence> findExact(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        boolean includeTechnicalContent
    ) {
        return evidence
            .findExactImplementation(tenantId, modelSpecId, modelRevision, modelChecksum, implementationRevision)
            .map(implementation -> {
                var artifacts = evidence.listExactArtifacts(
                    tenantId,
                    modelSpecId,
                    modelRevision,
                    modelChecksum,
                    implementationRevision,
                    implementation.implementationChecksum(),
                    includeTechnicalContent
                );
                ModelServingProjection projection = catalog.findProjection(tenantId, modelSpecId).orElse(null);
                RelationEvidence relation = catalog
                    .findLatestSuccessfulCandidateEvidence(
                        tenantId,
                        modelSpecId,
                        modelRevision,
                        modelChecksum,
                        implementationRevision,
                        implementation.implementationChecksum()
                    )
                    .orElse(null);
                return new RepresentationEvidence(
                    implementation,
                    artifacts,
                    projection == null ? null : publication(projection.latestPublishedRef(), projection.catalogAssetKey()),
                    projection == null ? null : publication(projection.servingRef(), projection.catalogAssetKey()),
                    runtime(relation),
                    List.of()
                );
            });
    }

    private static PublicationEvidence publication(PublishedRef reference, String assetRef) {
        if (reference == null) return null;
        return new PublicationEvidence(
            reference.modelRevision(),
            reference.modelChecksum(),
            reference.implementationRevision(),
            reference.implementationChecksum(),
            assetRef
        );
    }

    private static PublicationEvidence publication(ServingRef reference, String assetRef) {
        if (reference == null) return null;
        return new PublicationEvidence(
            reference.modelRevision(),
            reference.modelChecksum(),
            reference.implementationRevision(),
            reference.implementationChecksum(),
            assetRef
        );
    }

    private static RuntimeEvidence runtime(RelationEvidence relation) {
        if (relation == null) return null;
        return new RuntimeEvidence(
            relation.relationEvidenceId(),
            relation.modelSpecId(),
            relation.modelRevision(),
            relation.modelChecksum(),
            relation.implementationRevision(),
            relation.implementationChecksum(),
            relation.verified(),
            relation.relationExists(),
            relation.evidenceChecksum(),
            relation.observedAt(),
            relation.columns().stream().map(column -> new ObservedField(column.name(), column.dataType())).toList()
        );
    }
}
