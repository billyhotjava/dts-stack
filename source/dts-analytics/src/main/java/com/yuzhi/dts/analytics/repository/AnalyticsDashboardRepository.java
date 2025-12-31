package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsDashboardRepository extends JpaRepository<AnalyticsDashboard, Long> {
    List<AnalyticsDashboard> findAllByArchivedFalseOrderByIdAsc();

    List<AnalyticsDashboard> findAllByArchivedFalseAndCollectionIdOrderByIdAsc(Long collectionId);

    List<AnalyticsDashboard> findAllByArchivedFalseAndCollectionIdIsNullOrderByIdAsc();
}
