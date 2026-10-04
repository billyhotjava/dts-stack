package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportRun;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsReportRunRepository extends JpaRepository<AnalyticsReportRun, Long> {

    List<AnalyticsReportRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AnalyticsReportRun> findAllByCreatorIdOrderByCreatedAtDesc(Long creatorId, Pageable pageable);
}
