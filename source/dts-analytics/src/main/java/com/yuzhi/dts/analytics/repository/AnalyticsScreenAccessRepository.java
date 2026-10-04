package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AnalyticsScreenAccessRepository extends JpaRepository<AnalyticsScreenAccess, Long> {

    List<AnalyticsScreenAccess> findByScreenId(Long screenId);

    Optional<AnalyticsScreenAccess> findByScreenIdAndGranteeTypeAndGranteeId(
            Long screenId, String granteeType, String granteeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AnalyticsScreenAccess a WHERE a.screenId = :screenId")
    void deleteByScreenId(@Param("screenId") Long screenId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AnalyticsScreenAccess a WHERE a.id = :grantId AND a.screenId = :screenId")
    int deleteByIdAndScreenId(@Param("grantId") Long grantId, @Param("screenId") Long screenId);

    /**
     * Returns screen IDs where the user has a direct USER grant
     * OR the user holds one of the given roles with a ROLE grant.
     * Pass roles = ["__NO_ROLE__"] when the user has no roles, to avoid empty IN clause.
     */
    @Query("SELECT DISTINCT a.screenId FROM AnalyticsScreenAccess a WHERE " +
           "(a.granteeType = 'USER' AND a.granteeId = :userId) OR " +
           "(a.granteeType = 'ROLE' AND a.granteeId IN :roles)")
    List<Long> findAccessibleScreenIds(
            @Param("userId") String userId,
            @Param("roles") List<String> roles);

    /**
     * Returns matching grant rows for a single screen, for a given user + roles.
     * Used by ScreenPermissionService.snapshot().
     */
    @Query("SELECT a FROM AnalyticsScreenAccess a WHERE a.screenId = :screenId AND " +
           "((a.granteeType = 'USER' AND a.granteeId = :userId) OR " +
           "(a.granteeType = 'ROLE' AND a.granteeId IN :roles))")
    List<AnalyticsScreenAccess> findGrantsForUser(
            @Param("screenId") Long screenId,
            @Param("userId") String userId,
            @Param("roles") List<String> roles);
}
