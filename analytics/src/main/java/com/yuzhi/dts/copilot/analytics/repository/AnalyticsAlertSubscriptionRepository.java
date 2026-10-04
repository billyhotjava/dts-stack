package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAlertSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsAlertSubscriptionRepository extends JpaRepository<AnalyticsAlertSubscription, Long> {

    Optional<AnalyticsAlertSubscription> findByAlertIdAndUserId(Long alertId, Long userId);

    long deleteByAlertIdAndUserId(Long alertId, Long userId);

    long deleteAllByAlertIdIn(Iterable<Long> alertIds);
}
