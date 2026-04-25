package com.yuzhi.dts.platform.repository.visualization;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BiReportLinkRepository extends JpaRepository<BiReportLink, UUID> {
    List<BiReportLink> findByEnabledTrueOrderBySortOrderAscLastModifiedDateDesc();

    Optional<BiReportLink> findFirstByCodeIgnoreCase(String code);

    /** Sprint-17/F1 — used by ScreenReportLinkSyncService to scope reconcile to auto-synced rows only. */
    List<BiReportLink> findAllBySource(String source);

    /**
     * Sprint-17 hotfix — MINE-scope fallback when a user has no visits yet:
     * surface enabled reconcile rows so "我常用的报表" stops being permanently 0
     * for users who haven't opened any 大屏 preview (visit log empty).
     * Sorted by lastVisitedAt desc (newly synced rows have null and naturally
     * fall to the bottom), then code asc as a stable tie-breaker.
     */
    @Query(
        "select r from BiReportLink r " +
        "where r.enabled = true and r.source = :source " +
        "order by case when r.lastVisitedAt is null then 1 else 0 end asc, " +
        "         r.lastVisitedAt desc, r.code asc"
    )
    List<BiReportLink> findRecentBySourceForFallback(
        @Param("source") String source,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Counts enabled reports scoped to a department. {@code deptCode} is
     * matched as a CSV element inside {@code deptCodes} — we wrap both
     * sides with separators to avoid prefix collisions (P0-6: previously
     * "D1" matched "D10/D100" because of the unbounded LIKE pattern).
     * Optional {@code bizDomain} filters by exact match; optional time
     * window filters by {@code createdDate}. All params may be null except
     * the ones required by the scope (caller's responsibility).
     */
    @Query(
        "select count(r) from BiReportLink r " +
        "where r.enabled = true " +
        "  and (cast(:deptCode as text) is null " +
        "       or concat(',', r.deptCodes, ',') like concat('%,', :deptCode, ',%')) " +
        "  and (cast(:bizDomain as text) is null or r.bizDomain = :bizDomain) " +
        "  and (cast(:createdFrom as java.time.Instant) is null or r.createdDate >= :createdFrom) " +
        "  and (cast(:createdTo as java.time.Instant) is null or r.createdDate < :createdTo)"
    )
    long countForDept(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("createdFrom") Instant createdFrom,
        @Param("createdTo") Instant createdTo
    );

    /**
     * Counts enabled reports across the institute (no dept filter).
     */
    @Query(
        "select count(r) from BiReportLink r " +
        "where r.enabled = true " +
        "  and (cast(:bizDomain as text) is null or r.bizDomain = :bizDomain) " +
        "  and (cast(:createdFrom as java.time.Instant) is null or r.createdDate >= :createdFrom) " +
        "  and (cast(:createdTo as java.time.Instant) is null or r.createdDate < :createdTo)"
    )
    long countForAll(
        @Param("bizDomain") String bizDomain,
        @Param("createdFrom") Instant createdFrom,
        @Param("createdTo") Instant createdTo
    );

    /**
     * Counts distinct report ids that a given user has visited in the
     * last 30 days (used as the {@code MINE} scope's reportsTotal
     * proxy). Implemented as a sub-query against {@code BiReportVisit}.
     */
    @Query(
        "select count(distinct v.reportId) from BiReportVisit v " +
        "where v.userLogin = :userLogin " +
        "  and (cast(:createdFrom as java.time.Instant) is null or v.visitedAt >= :createdFrom) " +
        "  and (cast(:createdTo as java.time.Instant) is null or v.visitedAt < :createdTo)"
    )
    long countDistinctReportsVisitedByUser(
        @Param("userLogin") String userLogin,
        @Param("createdFrom") Instant createdFrom,
        @Param("createdTo") Instant createdTo
    );

    /**
     * P0-10: SQL-pushdown candidate query backing {@code listPublished}
     * and {@code listAll}. Filters that we can safely express in JPQL
     * (enabled / queryDatasetId / bizDomain / reportType /
     * deptCode CSV / not-yet-expired) are applied here so the service
     * layer no longer reads the entire table into the JVM. Filters that
     * depend on the caller's identity or full-text search (role check,
     * keyword, dataset visibility, classification clearance) are still
     * evaluated in memory because they cannot be pushed without
     * surfacing the security model in JPQL.
     *
     * <p>The dept CSV match wraps both sides with separators (same idiom
     * as {@link #countForDept}) to avoid prefix collisions. The order
     * matches the previous in-memory sort:
     * {@code sortOrder asc, lastModifiedDate desc} for the published
     * listing; callers wanting the {@code listAll} order can re-sort.
     */
    @Query(
        "select r from BiReportLink r " +
        "where (:enabledOnly = false or r.enabled = true) " +
        "  and (:queryDatasetId is null or r.queryDatasetId = :queryDatasetId) " +
        "  and (cast(:reportType as text) is null or upper(r.reportType) = upper(:reportType)) " +
        "  and (cast(:bizDomain as text) is null or upper(r.bizDomain) = upper(:bizDomain)) " +
        "  and (cast(:deptCode as text) is null " +
        "       or concat(',', r.deptCodes, ',') like concat('%,', :deptCode, ',%')) " +
        "  and (r.expiresAt is null or r.expiresAt >= :now) " +
        "order by r.sortOrder asc, r.lastModifiedDate desc"
    )
    List<BiReportLink> findCandidatesForListing(
        @Param("enabledOnly") boolean enabledOnly,
        @Param("queryDatasetId") UUID queryDatasetId,
        @Param("reportType") String reportType,
        @Param("bizDomain") String bizDomain,
        @Param("deptCode") String deptCode,
        @Param("now") Instant now
    );
}
