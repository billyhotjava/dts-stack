package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetLineageRepository extends JpaRepository<CatalogDatasetLineage, UUID> {

    List<CatalogDatasetLineage> findByUpstreamDatasetId(UUID upstreamDatasetId);

    List<CatalogDatasetLineage> findByDownstreamDatasetId(UUID downstreamDatasetId);

    List<CatalogDatasetLineage> findByDownstreamDatasetIdAndRelationTypeIgnoreCase(UUID downstreamDatasetId, String relationType);

    Optional<CatalogDatasetLineage> findFirstByUpstreamDatasetIdAndDownstreamDatasetId(UUID upstreamDatasetId, UUID downstreamDatasetId);

    @Query("select l from CatalogDatasetLineage l where l.upstreamDatasetId = :datasetId or l.downstreamDatasetId = :datasetId")
    List<CatalogDatasetLineage> findByEitherSide(@Param("datasetId") UUID datasetId);

    boolean existsByUpstreamDatasetIdAndDownstreamDatasetId(UUID upstreamDatasetId, UUID downstreamDatasetId);
}
