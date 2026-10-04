package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsRevisionRepository extends JpaRepository<AnalyticsRevision, Long> {
    List<AnalyticsRevision> findAllByModelAndModelIdOrderByIdDesc(String model, Long modelId);
}

