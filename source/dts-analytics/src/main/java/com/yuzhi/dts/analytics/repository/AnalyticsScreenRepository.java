package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenRepository extends JpaRepository<AnalyticsScreen, Long> {
    List<AnalyticsScreen> findAllByArchivedFalseOrderByIdDesc();

    List<AnalyticsScreen> findAllByIdInAndArchivedFalse(List<Long> ids);
}
