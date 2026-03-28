package com.yuzhi.dts.platform.repository.permission;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetGrantRepository extends JpaRepository<AssetGrant, Long> {

    List<AssetGrant> findByAssetTypeAndAssetId(String assetType, String assetId);

    @Query("""
        select g from AssetGrant g
        where g.assetType = :assetType
          and g.assetId = :assetId
          and (
            (g.granteeType = 'USER' and lower(g.granteeId) = lower(:username))
            or (g.granteeType = 'ROLE' and g.granteeId in :roles)
            or (g.granteeType = 'DEPT' and g.granteeId = :deptCode)
          )
          and (g.validFrom is null or g.validFrom <= :now)
          and (g.validTo is null or g.validTo >= :now)
    """)
    List<AssetGrant> findActiveGrantsForUser(
        @Param("assetType") String assetType,
        @Param("assetId") String assetId,
        @Param("username") String username,
        @Param("roles") List<String> roles,
        @Param("deptCode") String deptCode,
        @Param("now") Instant now
    );

    @Query("""
        select distinct g.assetId from AssetGrant g
        where g.assetType = :assetType
          and (
            (g.granteeType = 'USER' and lower(g.granteeId) = lower(:username))
            or (g.granteeType = 'ROLE' and g.granteeId in :roles)
            or (g.granteeType = 'DEPT' and g.granteeId = :deptCode)
          )
          and (g.validFrom is null or g.validFrom <= :now)
          and (g.validTo is null or g.validTo >= :now)
    """)
    List<String> findAccessibleAssetIdsByGrant(
        @Param("assetType") String assetType,
        @Param("username") String username,
        @Param("roles") List<String> roles,
        @Param("deptCode") String deptCode,
        @Param("now") Instant now
    );

    @Query("""
        select g from AssetGrant g
        where g.granteeType = 'USER' and lower(g.granteeId) = lower(:username)
        order by g.createdDate desc
    """)
    Page<AssetGrant> findGrantsForUser(@Param("username") String username, Pageable pageable);

    Page<AssetGrant> findByGrantedByOrderByCreatedDateDesc(String grantedBy, Pageable pageable);
}
