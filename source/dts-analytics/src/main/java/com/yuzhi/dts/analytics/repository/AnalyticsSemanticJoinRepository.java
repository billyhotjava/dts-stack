package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsSemanticJoin;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsSemanticJoinRepository extends JpaRepository<AnalyticsSemanticJoin, Long> {
    List<AnalyticsSemanticJoin> findAllByFromModelIgnoreCaseOrderByToModelAsc(String fromModel);

    long deleteByFromModelIgnoreCase(String fromModel);
}
