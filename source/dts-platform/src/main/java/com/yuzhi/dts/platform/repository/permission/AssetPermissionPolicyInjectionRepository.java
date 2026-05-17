package com.yuzhi.dts.platform.repository.permission;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetPermissionPolicyInjectionRepository extends JpaRepository<AssetPermissionPolicyInjection, UUID> {

    @Query("""
        select a from AssetPermissionPolicyInjection a
        where (:assetId is null or a.assetId = :assetId)
          and (:packId is null or a.packId = :packId)
          and (:since is null or a.occurredAt >= :since)
        order by a.occurredAt desc
    """)
    Page<AssetPermissionPolicyInjection> findByFilters(
        @Param("assetId") String assetId,
        @Param("packId") String packId,
        @Param("since") Instant since,
        Pageable pageable
    );
}
