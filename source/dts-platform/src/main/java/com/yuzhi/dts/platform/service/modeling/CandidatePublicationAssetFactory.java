package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalLocator;
import java.util.UUID;

/** Deterministic Catalog identity and relation locator projection for one physical output. */
public final class CandidatePublicationAssetFactory {

    private CandidatePublicationAssetFactory() {}

    public static UUID catalogDatasetId(
        UUID sourceId,
        String schemaName,
        String identifier
    ) {
        return new CatalogPhysicalLocator(
            sourceId,
            schemaName,
            identifier
        ).assetId();
    }
}
