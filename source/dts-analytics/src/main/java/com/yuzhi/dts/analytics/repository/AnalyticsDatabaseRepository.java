package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsDatabaseRepository extends JpaRepository<AnalyticsDatabase, Long> {

    Optional<AnalyticsDatabase> findByTenantIdAndPlatformDataSourceId(String tenantId, UUID platformDataSourceId);
}
