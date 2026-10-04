package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsMetric;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsMetricRepository extends JpaRepository<AnalyticsMetric, Long> {

    List<AnalyticsMetric> findAllByArchivedFalseOrderByIdAsc();

    List<AnalyticsMetric> findAllByArchivedFalseAndBaseTableIdOrderByIdAsc(Long baseTableId);
}
