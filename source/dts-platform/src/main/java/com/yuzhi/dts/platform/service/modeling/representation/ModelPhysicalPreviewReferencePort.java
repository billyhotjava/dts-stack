package com.yuzhi.dts.platform.service.modeling.representation;

import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewProjection;
import java.util.UUID;

/** Read port that turns canonical Catalog/relation observations into complete, server-issued preview pins. */
public interface ModelPhysicalPreviewReferencePort {
    PhysicalPreviewProjection resolve(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    );
}
