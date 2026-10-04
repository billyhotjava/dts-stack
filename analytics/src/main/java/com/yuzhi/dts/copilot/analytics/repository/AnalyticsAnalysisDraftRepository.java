package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAnalysisDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsAnalysisDraftRepository extends JpaRepository<AnalyticsAnalysisDraft, Long> {

    List<AnalyticsAnalysisDraft> findAllByCreatorIdAndStatusNotOrderByUpdatedAtDesc(Long creatorId, String status);

    Optional<AnalyticsAnalysisDraft> findByIdAndCreatorId(Long id, Long creatorId);
}
