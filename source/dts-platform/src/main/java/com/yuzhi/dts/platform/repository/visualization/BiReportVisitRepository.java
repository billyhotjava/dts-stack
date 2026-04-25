package com.yuzhi.dts.platform.repository.visualization;

import com.yuzhi.dts.platform.domain.visualization.BiReportVisit;
import com.yuzhi.dts.platform.service.workbench.dto.DomainAggregateRow;
import com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link BiReportVisit}. Provides the aggregation queries
 * backing Sprint-15 F1 leader-overview KPIs (T03), top-N report lists
 * (T04), and the domain matrix (T06).
 */
public interface BiReportVisitRepository extends JpaRepository<BiReportVisit, UUID> {
    // ------------------------------------------------------------------ T03: KPI visit counts

    /** Visits in window for a specific user (MINE scope). */
    @Query(
        "select count(v) from BiReportVisit v " +
        "where v.userLogin = :userLogin " +
        "  and v.visitedAt >= :start and v.visitedAt < :end"
    )
    long countVisitsForUser(
        @Param("userLogin") String userLogin,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    /** Visits in window for a specific department, optionally filtered by biz domain. */
    @Query(
        "select count(v) from BiReportVisit v " +
        "where v.deptCode = :deptCode " +
        "  and v.visitedAt >= :start and v.visitedAt < :end " +
        "  and (cast(:bizDomain as text) is null or v.bizDomain = :bizDomain)"
    )
    long countVisitsForDept(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    /** Visits in window across the institute (ALL), optionally filtered by dept/biz domain. */
    @Query(
        "select count(v) from BiReportVisit v " +
        "where v.visitedAt >= :start and v.visitedAt < :end " +
        "  and (cast(:deptCode as text) is null or v.deptCode = :deptCode) " +
        "  and (cast(:bizDomain as text) is null or v.bizDomain = :bizDomain)"
    )
    long countVisitsForAll(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    // ------------------------------------------------------------------ T04: TOP reports

    /**
     * MINE: most recently visited reports by a user.
     * <p>
     * P0-9: requires a {@code start} look-back to avoid scanning the user's
     * full visit history as the table grows. P0-7: groups on
     * {@code v.bizDomain} (visit-time snapshot) so the bucket label stays
     * consistent with the {@code countVisitsForDept} aggregation. P0-8: adds
     * {@code v.reportId} as a stable tie-breaker so paginated results don't
     * shuffle when two reports share the same {@code max(visitedAt)}.
     */
    @Query(
        "select new com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow(" +
        "  v.reportId, r.title, count(v), v.bizDomain, r.classification, max(v.visitedAt)) " +
        "from BiReportVisit v join BiReportLink r on r.id = v.reportId " +
        "where v.userLogin = :userLogin " +
        "  and v.visitedAt >= :start " +
        "group by v.reportId, r.title, v.bizDomain, r.classification " +
        "order by max(v.visitedAt) desc, v.reportId asc"
    )
    List<ReportVisitAggregateRow> findTopRecentByUser(
        @Param("userLogin") String userLogin,
        @Param("start") Instant start,
        Pageable pageable
    );

    /** DEPT: top reports by visit count in window, scoped to a single department. */
    @Query(
        "select new com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow(" +
        "  v.reportId, r.title, count(v), v.bizDomain, r.classification, max(v.visitedAt)) " +
        "from BiReportVisit v join BiReportLink r on r.id = v.reportId " +
        "where v.visitedAt >= :start and v.visitedAt < :end " +
        "  and v.deptCode = :deptCode " +
        "  and (cast(:bizDomain as text) is null or v.bizDomain = :bizDomain) " +
        "group by v.reportId, r.title, v.bizDomain, r.classification " +
        "order by count(v) desc, v.reportId asc"
    )
    List<ReportVisitAggregateRow> aggregateTopReportsForDept(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    /** ALL: top reports by visit count in window, unscoped (optionally filtered by bizDomain). */
    @Query(
        "select new com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow(" +
        "  v.reportId, r.title, count(v), v.bizDomain, r.classification, max(v.visitedAt)) " +
        "from BiReportVisit v join BiReportLink r on r.id = v.reportId " +
        "where v.visitedAt >= :start and v.visitedAt < :end " +
        "  and (cast(:bizDomain as text) is null or v.bizDomain = :bizDomain) " +
        "group by v.reportId, r.title, v.bizDomain, r.classification " +
        "order by count(v) desc, v.reportId asc"
    )
    List<ReportVisitAggregateRow> aggregateTopReportsAll(
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    // ------------------------------------------------------------------ T06: Domain matrix

    /**
     * Group visits by biz-domain for the ALL scope. Optional deptCode
     * drills down; optional bizDomain collapses the matrix to a single
     * row (useful when the user has already filtered by domain). P0-7:
     * uses the visit-time snapshot {@code v.bizDomain} to keep counts
     * consistent with {@code countVisitsForDept} even when reports change
     * domain after a visit was recorded.
     */
    @Query(
        "select new com.yuzhi.dts.platform.service.workbench.dto.DomainAggregateRow(" +
        "  v.bizDomain, count(v)) " +
        "from BiReportVisit v " +
        "where v.visitedAt >= :start and v.visitedAt < :end " +
        "  and (cast(:deptCode as text) is null or v.deptCode = :deptCode) " +
        "  and (cast(:bizDomain as text) is null or v.bizDomain = :bizDomain) " +
        "group by v.bizDomain"
    )
    List<DomainAggregateRow> aggregateByBizDomain(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
}
