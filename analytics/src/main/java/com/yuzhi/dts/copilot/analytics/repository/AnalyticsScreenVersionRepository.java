package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsScreenVersionRepository extends JpaRepository<AnalyticsScreenVersion, Long> {

    List<AnalyticsScreenVersion> findAllByScreenIdOrderByVersionNoDesc(Long screenId);

    Optional<AnalyticsScreenVersion> findFirstByScreenIdAndCurrentPublishedTrue(Long screenId);

    Optional<AnalyticsScreenVersion> findFirstByScreenIdOrderByVersionNoDesc(Long screenId);

    Optional<AnalyticsScreenVersion> findByIdAndScreenId(Long id, Long screenId);

    @Modifying
    @Query("update AnalyticsScreenVersion v set v.currentPublished = false where v.screenId = :screenId and v.currentPublished = true")
    int clearCurrentPublished(@Param("screenId") Long screenId);
}
