package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsSegmentRepository extends JpaRepository<AnalyticsSegment, Long> {

    List<AnalyticsSegment> findAllByArchivedFalseOrderByIdAsc();
}

