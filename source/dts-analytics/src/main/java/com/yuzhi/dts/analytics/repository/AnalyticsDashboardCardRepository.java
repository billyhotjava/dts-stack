package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsDashboardCardRepository extends JpaRepository<AnalyticsDashboardCard, Long> {
    List<AnalyticsDashboardCard> findAllByDashboardIdOrderByIdAsc(long dashboardId);
}

