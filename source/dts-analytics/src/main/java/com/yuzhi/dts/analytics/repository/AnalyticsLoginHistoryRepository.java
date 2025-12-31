package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsLoginHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsLoginHistoryRepository extends JpaRepository<AnalyticsLoginHistory, Long> {
    List<AnalyticsLoginHistory> findTop20ByUserIdOrderByLoggedInAtDesc(Long userId);
}

