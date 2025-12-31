package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsSegmentRepository extends JpaRepository<AnalyticsSegment, Long> {

    List<AnalyticsSegment> findAllByArchivedFalseOrderByIdAsc();
}

