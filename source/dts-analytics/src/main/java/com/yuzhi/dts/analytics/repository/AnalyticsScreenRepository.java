package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsScreenRepository extends JpaRepository<AnalyticsScreen, Long> {
    List<AnalyticsScreen> findAllByArchivedFalseOrderByIdDesc();

    List<AnalyticsScreen> findAllByIdInAndArchivedFalse(List<Long> ids);

    @Query("SELECT s.id FROM AnalyticsScreen s WHERE s.creatorId = :creatorId AND s.archived = false")
    List<Long> findIdsByCreatorIdAndArchivedFalse(@Param("creatorId") Long creatorId);
}
