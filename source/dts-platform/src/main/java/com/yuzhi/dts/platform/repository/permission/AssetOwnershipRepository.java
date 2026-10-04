package com.yuzhi.dts.platform.repository.permission;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetOwnershipRepository extends JpaRepository<AssetOwnership, Long> {

    Optional<AssetOwnership> findByAssetTypeAndAssetId(String assetType, String assetId);

    List<AssetOwnership> findByOwnerDeptCode(String ownerDeptCode);

    List<AssetOwnership> findBySourceId(String sourceId);

    @Query("""
        select o from AssetOwnership o
        where (:assetType is null or o.assetType = :assetType)
          and (:ownerDeptCode is null or o.ownerDeptCode = :ownerDeptCode)
          and (:keyword is null or lower(o.assetId) like lower(concat('%', :keyword, '%')))
        order by o.lastModifiedDate desc
    """)
    Page<AssetOwnership> findByFilters(
        @Param("assetType") String assetType,
        @Param("ownerDeptCode") String ownerDeptCode,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    @Query("""
        select o.assetId from AssetOwnership o
        where o.assetType = :assetType
          and o.ownerDeptCode = :ownerDeptCode
    """)
    List<String> findAssetIdsByTypeAndDeptCode(
        @Param("assetType") String assetType,
        @Param("ownerDeptCode") String ownerDeptCode
    );
}
