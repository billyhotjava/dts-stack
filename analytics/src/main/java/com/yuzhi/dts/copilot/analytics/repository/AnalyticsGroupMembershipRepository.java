package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroupMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsGroupMembershipRepository extends JpaRepository<AnalyticsGroupMembership, Long> {
    List<AnalyticsGroupMembership> findAllByGroupId(Long groupId);

    List<AnalyticsGroupMembership> findAllByUserId(Long userId);

    Optional<AnalyticsGroupMembership> findByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    @Query("select m from AnalyticsGroupMembership m where (:groupId is null or m.groupId = :groupId)")
    List<AnalyticsGroupMembership> findAllFiltered(@Param("groupId") Long groupId);
}

