package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsPulseSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsPulseSubscriptionRepository extends JpaRepository<AnalyticsPulseSubscription, Long> {

    Optional<AnalyticsPulseSubscription> findByPulseIdAndUserId(Long pulseId, Long userId);

    long deleteByPulseIdAndUserId(Long pulseId, Long userId);
}

