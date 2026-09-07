package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsDatabaseRepository extends JpaRepository<AnalyticsDatabase, Long> {

    Optional<AnalyticsDatabase> findByTenantIdAndPlatformDataSourceId(String tenantId, UUID platformDataSourceId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select database from AnalyticsDatabase database where database.id = :id")
    Optional<AnalyticsDatabase> findByIdForBindingUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
