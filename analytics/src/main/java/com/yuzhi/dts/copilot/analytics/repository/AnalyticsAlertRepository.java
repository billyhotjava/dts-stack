package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsAlertRepository extends JpaRepository<AnalyticsAlert, Long> {

    List<AnalyticsAlert> findAllByArchivedFalseOrderByIdAsc();

    List<AnalyticsAlert> findAllByArchivedFalseAndCardIdOrderByIdAsc(Long cardId);

    List<AnalyticsAlert> findAllByCardIdInOrderByIdAsc(Iterable<Long> cardIds);

    long deleteAllByCardIdIn(Iterable<Long> cardIds);
}
