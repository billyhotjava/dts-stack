package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.Optional;

/** Public immutable boundary for classification facts and monotonic sealing. */
public interface CatalogClassificationBoundary {

    Optional<ClassificationFact> resolve(String subjectType, String subjectKey);

    ClassificationFact sealOrRaise(SealRequest request);

    record ClassificationFact(
        String subjectType,
        String subjectKey,
        String effectiveLevel,
        String propagationStatus
    ) {
        public boolean propagated() {
            return "PROPAGATED".equals(propagationStatus);
        }
    }

    record SealRequest(
        String subjectType,
        String subjectKey,
        String assetType,
        String declaredLevel,
        String detectedLevel,
        String manualFloor,
        List<String> upstreamLevels,
        String originType,
        String originRef,
        String evidenceChecksum,
        String evidenceJson
    ) {
        public SealRequest {
            upstreamLevels = upstreamLevels == null ? List.of() : List.copyOf(upstreamLevels);
        }
    }
}
