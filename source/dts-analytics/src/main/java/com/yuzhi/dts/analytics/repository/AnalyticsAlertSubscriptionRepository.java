package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsAlertSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsAlertSubscriptionRepository extends JpaRepository<AnalyticsAlertSubscription, Long> {

    Optional<AnalyticsAlertSubscription> findByAlertIdAndUserId(Long alertId, Long userId);

    long deleteByAlertIdAndUserId(Long alertId, Long userId);
}

