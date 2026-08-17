package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsRevisionRepository extends JpaRepository<AnalyticsRevision, Long> {
    List<AnalyticsRevision> findAllByModelAndModelIdOrderByIdDesc(String model, Long modelId);

    List<AnalyticsRevision> findAllByModelAndModelIdOrderByVersionNoDesc(String model, Long modelId);

    @Query("select coalesce(max(r.versionNo), 0) from AnalyticsRevision r where r.model = :model and r.modelId = :modelId")
    int findMaxVersionNo(@Param("model") String model, @Param("modelId") Long modelId);

    @Query("select r from AnalyticsRevision r where r.model = :model and r.modelId = :modelId and r.status = 'PUBLISHED'")
    Optional<AnalyticsRevision> findCurrentPublished(@Param("model") String model, @Param("modelId") Long modelId);
}
