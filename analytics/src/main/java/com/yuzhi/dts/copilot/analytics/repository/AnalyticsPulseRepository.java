package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPulse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsPulseRepository extends JpaRepository<AnalyticsPulse, Long> {

    List<AnalyticsPulse> findAllByArchivedFalseOrderByIdAsc();
}

