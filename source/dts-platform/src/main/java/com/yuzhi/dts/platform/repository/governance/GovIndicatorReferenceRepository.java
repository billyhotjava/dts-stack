package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorReferenceRepository extends JpaRepository<GovIndicatorReference, UUID> {
    List<GovIndicatorReference> findByIndicatorOrderByCreatedDateAsc(GovIndicatorDefinition indicator);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GovIndicatorReference r where r.indicator = :indicator")
    int deleteByIndicator(@Param("indicator") GovIndicatorDefinition indicator);
    Optional<GovIndicatorReference> findFirstByIndicatorAndRefTypeIgnoreCaseAndRefTargetIgnoreCase(
        GovIndicatorDefinition indicator,
        String refType,
        String refTarget
    );

    @Query(
        """
        select distinct i.id
          from GovIndicatorReference r
          join r.indicator i
         where upper(r.refType) = 'INDICATOR'
         order by i.id
        """
    )
    List<UUID> findIndicatorDependencySourceIdsForRelationshipGraph(Pageable pageable);

    @Query(
        """
        select r
          from GovIndicatorReference r
          join fetch r.indicator i
         where i.id in :indicatorIds
           and upper(r.refType) = 'INDICATOR'
         order by i.id, r.refTarget, r.id
        """
    )
    List<GovIndicatorReference> findIndicatorDependenciesForRelationshipGraph(
        @Param("indicatorIds") List<UUID> indicatorIds,
        Pageable pageable
    );
}
