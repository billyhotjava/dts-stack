package com.yuzhi.dts.platform.repository.permission;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetPermissionAuditRepository extends JpaRepository<AssetPermissionAudit, Long> {

    @Query("""
        select a from AssetPermissionAudit a
        where (:action is null or a.action = :action)
          and (:operator is null or lower(a.operator) = lower(:operator))
          and (:targetUser is null or lower(a.targetUser) = lower(:targetUser))
          and (:oaReference is null or a.oaReference = :oaReference)
          and (:dateFrom is null or a.createdDate >= :dateFrom)
          and (:dateTo is null or a.createdDate <= :dateTo)
        order by a.createdDate desc
    """)
    Page<AssetPermissionAudit> findByFilters(
        @Param("action") String action,
        @Param("operator") String operator,
        @Param("targetUser") String targetUser,
        @Param("oaReference") String oaReference,
        @Param("dateFrom") Instant dateFrom,
        @Param("dateTo") Instant dateTo,
        Pageable pageable
    );
}
