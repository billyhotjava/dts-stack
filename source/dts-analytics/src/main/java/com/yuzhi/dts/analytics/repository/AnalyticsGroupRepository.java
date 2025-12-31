package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsGroupRepository extends JpaRepository<AnalyticsGroup, Long> {
    Optional<AnalyticsGroup> findByNameIgnoreCase(String name);
}

