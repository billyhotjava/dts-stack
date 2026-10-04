package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsGroupRepository extends JpaRepository<AnalyticsGroup, Long> {
    Optional<AnalyticsGroup> findByNameIgnoreCase(String name);

    @Query("SELECT g FROM AnalyticsGroup g JOIN AnalyticsGroupMembership m ON g.id = m.groupId WHERE m.userId = :userId")
    List<AnalyticsGroup> findGroupsByUserId(@Param("userId") Long userId);
}

