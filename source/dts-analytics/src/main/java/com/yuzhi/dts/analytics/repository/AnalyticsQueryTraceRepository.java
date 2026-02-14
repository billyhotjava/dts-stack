package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsQueryTrace;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsQueryTraceRepository extends JpaRepository<AnalyticsQueryTrace, Long> {

    Page<AnalyticsQueryTrace> findAllByMetricId(Long metricId, Pageable pageable);

    Page<AnalyticsQueryTrace> findAllByCardId(Long cardId, Pageable pageable);

    @Query("select distinct q.metricVersion from AnalyticsQueryTrace q where q.metricId = :metricId and q.metricVersion is not null and q.metricVersion <> '' order by q.metricVersion desc")
    List<String> findDistinctMetricVersions(@Param("metricId") Long metricId);
}
