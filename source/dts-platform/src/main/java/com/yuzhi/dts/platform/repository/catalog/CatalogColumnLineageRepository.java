package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogColumnLineageRepository extends JpaRepository<CatalogColumnLineage, UUID> {
    List<CatalogColumnLineage> findByDatasetLineageIdIn(Collection<UUID> datasetLineageIds);

    List<CatalogColumnLineage> findByDownstreamDatasetIdAndRelationTypeIgnoreCase(UUID downstreamDatasetId, String relationType);

    Optional<CatalogColumnLineage> findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndUpstreamColumnIgnoreCaseAndDownstreamColumnIgnoreCaseAndRelationTypeIgnoreCase(
        UUID upstreamDatasetId,
        UUID downstreamDatasetId,
        String upstreamColumn,
        String downstreamColumn,
        String relationType
    );
}
