package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsDatabaseRepository extends JpaRepository<AnalyticsDatabase, Long> {

    Optional<AnalyticsDatabase> findFirstByNameIgnoreCase(String name);
}
