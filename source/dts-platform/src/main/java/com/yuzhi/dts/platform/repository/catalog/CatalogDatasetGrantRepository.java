package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    @Query(
        """
        select case when count(g) > 0 then true else false end
        from CatalogDatasetGrant g
        where g.dataset.id = :datasetId
          and g.grantType = 'DATA_ACCESS'
          and g.canQuery = true
          and (g.validFrom is null or g.validFrom <= :now)
          and (g.validTo is null or g.validTo >= :now)
          and (
            (:granteeId is not null and g.granteeId = :granteeId)
            or (:username is not null and lower(g.granteeUsername) = lower(:username))
          )
        """
    )
    boolean existsActiveQueryGrantForUser(
        @Param("datasetId") UUID datasetId,
        @Param("granteeId") String granteeId,
        @Param("username") String username,
        @Param("now") Instant now
    );

    @Query(
        """
        select case when count(g) > 0 then true else false end
        from CatalogDatasetGrant g
        where g.dataset.id = :datasetId
          and g.grantType = 'DATA_ACCESS'
          and g.canPreview = true
          and (g.validFrom is null or g.validFrom <= :now)
          and (g.validTo is null or g.validTo >= :now)
          and (
            (:granteeId is not null and g.granteeId = :granteeId)
            or (:username is not null and lower(g.granteeUsername) = lower(:username))
          )
        """
    )
    boolean existsActivePreviewGrantForUser(
        @Param("datasetId") UUID datasetId,
        @Param("granteeId") String granteeId,
        @Param("username") String username,
        @Param("now") Instant now
    );

    @Query(
        """
        select g from CatalogDatasetGrant g
        where g.dataset.id = :datasetId
          and g.grantType = 'DATA_ACCESS'
          and (
            (:granteeId is not null and g.granteeId = :granteeId)
            or (:username is not null and lower(g.granteeUsername) = lower(:username))
          )
        order by g.createdDate desc
        """
    )
    Optional<CatalogDatasetGrant> findLatestDataAccessGrantForUser(
        @Param("datasetId") UUID datasetId,
        @Param("granteeId") String granteeId,
        @Param("username") String username
    );
}
