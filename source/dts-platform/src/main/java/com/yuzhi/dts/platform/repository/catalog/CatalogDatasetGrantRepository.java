package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogDatasetGrantRepository extends JpaRepository<CatalogDatasetGrant, UUID> {
    List<CatalogDatasetGrant> findByDatasetIdOrderByCreatedDateAsc(UUID datasetId);

    @Query(
        """
        select case when count(g) > 0 then true else false end
        from CatalogDatasetGrant g
        where g.dataset.id = :datasetId
          and (
            (:granteeId is not null and g.granteeId = :granteeId)
            or (:username is not null and lower(g.granteeUsername) = lower(:username))
          )
        """
    )
    boolean existsForDatasetAndUser(
        @Param("datasetId") UUID datasetId,
        @Param("granteeId") String granteeId,
        @Param("username") String username
    );

    @Query(
        """
        select g.dataset.id
        from CatalogDatasetGrant g
        where (:granteeId is not null and g.granteeId = :granteeId)
           or (:username is not null and lower(g.granteeUsername) = lower(:username))
        """
    )
    Set<UUID> findDatasetIdsByUser(@Param("granteeId") String granteeId, @Param("username") String username);

    void deleteByDatasetIdAndGranteeId(UUID datasetId, String granteeId);

    void deleteByDatasetIdAndGranteeUsernameIgnoreCase(UUID datasetId, String username);

    void deleteByDatasetId(UUID datasetId);
}
