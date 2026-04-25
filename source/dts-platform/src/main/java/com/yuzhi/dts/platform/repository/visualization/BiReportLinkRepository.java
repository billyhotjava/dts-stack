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

    /**
     * Counts enabled reports scoped to a department. {@code deptCode} is
     * searched inside the CSV-packed {@code deptCodes} column using
     * {@code like %,deptCode,%} semantics. Optional {@code bizDomain}
     * filters by exact match; optional time window filters by
     * {@code createdDate}. All params may be null except the ones
     * required by the scope (caller's responsibility).
     */
    @Query(
        "select count(r) from BiReportLink r " +
        "where r.enabled = true " +
        "  and (cast(:deptCode as text) is null or r.deptCodes like concat('%', :deptCode, '%')) " +
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
}
