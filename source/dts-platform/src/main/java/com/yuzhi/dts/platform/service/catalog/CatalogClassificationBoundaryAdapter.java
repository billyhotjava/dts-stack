package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned entity-to-contract mapper for classification consumers. */
@Component
@Transactional(readOnly = true)
public class CatalogClassificationBoundaryAdapter implements CatalogClassificationBoundary {

    private final CatalogClassificationService classifications;

    public CatalogClassificationBoundaryAdapter(CatalogClassificationService classifications) {
        this.classifications = classifications;
    }

    @Override
    public java.util.Optional<ClassificationFact> resolve(String subjectType, String subjectKey) {
        return classifications.resolve(subjectType, subjectKey).map(CatalogClassificationBoundaryAdapter::toFact);
    }

    @Override
    @Transactional
    public ClassificationFact sealOrRaise(SealRequest request) {
        CatalogClassificationSnapshot snapshot = classifications.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                request.subjectType(),
                request.subjectKey(),
                request.assetType(),
                request.declaredLevel(),
                request.detectedLevel(),
                request.manualFloor(),
                request.upstreamLevels(),
                request.originType(),
                request.originRef(),
                request.evidenceChecksum(),
                request.evidenceJson()
            )
        );
        return toFact(snapshot);
    }

    private static ClassificationFact toFact(CatalogClassificationSnapshot snapshot) {
        return new ClassificationFact(
            snapshot.getSubjectType(),
            snapshot.getSubjectKey(),
            snapshot.getEffectiveLevel(),
            snapshot.getPropagationStatus()
        );
    }
}
