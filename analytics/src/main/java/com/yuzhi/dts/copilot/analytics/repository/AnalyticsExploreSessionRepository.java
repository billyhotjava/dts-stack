package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsExploreSession;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsExploreSessionRepository extends JpaRepository<AnalyticsExploreSession, Long> {

    List<AnalyticsExploreSession> findAllByArchivedFalseOrderByUpdatedAtDesc(Pageable pageable);

    List<AnalyticsExploreSession> findAllByCreatorIdAndArchivedFalseOrderByUpdatedAtDesc(Long creatorId, Pageable pageable);

    List<AnalyticsExploreSession> findAllByCreatorIdOrderByUpdatedAtDesc(Long creatorId, Pageable pageable);
}
