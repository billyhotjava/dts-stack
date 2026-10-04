package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsNl2SqlEvalCase;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsNl2SqlEvalCaseRepository extends JpaRepository<AnalyticsNl2SqlEvalCase, Long> {

    List<AnalyticsNl2SqlEvalCase> findAllByOrderByIdAsc(Pageable pageable);

    List<AnalyticsNl2SqlEvalCase> findAllByEnabledTrueOrderByIdAsc(Pageable pageable);

    List<AnalyticsNl2SqlEvalCase> findAllByIdIn(List<Long> ids);
}
