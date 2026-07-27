package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalLocator;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
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

    public static CatalogDataset prospective(
        PublicationEntryEvidence evidence,
        ResolvedCatalogTarget target
    ) {
        if (evidence == null) throw new IllegalArgumentException("publication evidence is required");
        if (target == null) throw new IllegalArgumentException("catalog target is required");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(
            catalogDatasetId(
                target.sourceId(),
                evidence.schemaName(),
                evidence.identifier()
            )
        );
        dataset.setName(evidence.identifier());
        dataset.setType("jdbc");
        dataset.setSourceId(target.sourceId());
        dataset.setClassification(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
        dataset.setHiveDatabase(evidence.schemaName());
        dataset.setHiveTable(evidence.identifier());
        dataset.setEnabled(Boolean.TRUE);
        dataset.setExposedBy("VIEW");
        dataset.setLifecycleStatus("PUBLISHED");
        return dataset;
    }
}
