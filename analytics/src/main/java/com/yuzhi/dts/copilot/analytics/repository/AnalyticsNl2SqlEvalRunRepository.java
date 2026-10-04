package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsNl2SqlEvalRun;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsNl2SqlEvalRunRepository extends JpaRepository<AnalyticsNl2SqlEvalRun, Long> {

    List<AnalyticsNl2SqlEvalRun> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
