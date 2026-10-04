package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogColumnLineageRepository extends JpaRepository<CatalogColumnLineage, UUID> {
    List<CatalogColumnLineage> findByDatasetLineageIdIn(Collection<UUID> datasetLineageIds);

    /**
     * 与 {@code CatalogDatasetLineageRepository.findByEitherSideAt} 严格同构：
     * 只返回在 {@code at} 时刻有效的字段血缘（valid_from/valid_to 时间窗口）。
     */
    @Query(
        """
        select l from CatalogColumnLineage l
        where l.datasetLineageId in :datasetLineageIds
          and (l.validFrom is null or l.validFrom <= :at)
          and (l.validTo is null or l.validTo > :at)
        """
    )
    List<CatalogColumnLineage> findByDatasetLineageIdInAt(
        @Param("datasetLineageIds") Collection<UUID> datasetLineageIds,
        @Param("at") Instant at
    );

    List<CatalogColumnLineage> findByDownstreamDatasetIdAndRelationTypeIgnoreCase(UUID downstreamDatasetId, String relationType);

    Optional<CatalogColumnLineage> findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndUpstreamColumnIgnoreCaseAndDownstreamColumnIgnoreCaseAndRelationTypeIgnoreCase(
        UUID upstreamDatasetId,
        UUID downstreamDatasetId,
        String upstreamColumn,
        String downstreamColumn,
        String relationType
    );
}
