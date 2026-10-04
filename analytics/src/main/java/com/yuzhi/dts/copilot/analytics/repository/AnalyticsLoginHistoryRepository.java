package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsLoginHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsLoginHistoryRepository extends JpaRepository<AnalyticsLoginHistory, Long> {
    List<AnalyticsLoginHistory> findTop20ByUserIdOrderByLoggedInAtDesc(Long userId);
}

