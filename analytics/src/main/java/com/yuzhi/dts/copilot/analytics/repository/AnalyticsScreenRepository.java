package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenRepository extends JpaRepository<AnalyticsScreen, Long> {
    List<AnalyticsScreen> findAllByArchivedFalseOrderByIdDesc();
}
