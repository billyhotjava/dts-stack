package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsQueryTrace;
import java.time.Instant;
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

    @Query("select count(q) from AnalyticsQueryTrace q where q.createdAt >= :since and (:chain is null or q.chain = :chain)")
    long countAllSince(@Param("since") Instant since, @Param("chain") String chain);

    @Query("select count(q) from AnalyticsQueryTrace q where q.createdAt >= :since and q.status = :status and (:chain is null or q.chain = :chain)")
    long countByStatusSince(@Param("since") Instant since, @Param("status") String status, @Param("chain") String chain);

    @Query(
            "select coalesce(q.errorCode, 'UNKNOWN'), count(q) from AnalyticsQueryTrace q "
                    + "where q.createdAt >= :since and q.status <> 'success' and (:chain is null or q.chain = :chain) "
                    + "group by q.errorCode order by count(q) desc")
    List<Object[]> summarizeFailedErrorCodesSince(
            @Param("since") Instant since,
            @Param("chain") String chain,
            Pageable pageable);

    @Query("select distinct q.metricVersion from AnalyticsQueryTrace q where q.metricId = :metricId and q.metricVersion is not null and q.metricVersion <> '' order by q.metricVersion desc")
    List<String> findDistinctMetricVersions(@Param("metricId") Long metricId);

    @Query("select distinct q.cardId from AnalyticsQueryTrace q where q.metricId = :metricId and q.cardId is not null")
    List<Long> findDistinctCardIdsByMetricId(@Param("metricId") Long metricId);

    @Query(
            "select q from AnalyticsQueryTrace q where q.metricId = :metricId and q.metricVersion = :metricVersion "
                    + "order by q.createdAt desc")
    Page<AnalyticsQueryTrace> findAllByMetricIdAndMetricVersion(
            @Param("metricId") Long metricId,
            @Param("metricVersion") String metricVersion,
            Pageable pageable);
}
