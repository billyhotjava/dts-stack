package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDashboardCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsDashboardCardRepository extends JpaRepository<AnalyticsDashboardCard, Long> {
    List<AnalyticsDashboardCard> findAllByDashboardIdOrderByIdAsc(long dashboardId);

    long deleteAllByCardIdIn(Iterable<Long> cardIds);
}
