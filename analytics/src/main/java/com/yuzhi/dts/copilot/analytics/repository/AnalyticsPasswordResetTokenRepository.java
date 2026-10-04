package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsPasswordResetTokenRepository extends JpaRepository<AnalyticsPasswordResetToken, Long> {
    Optional<AnalyticsPasswordResetToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);
}

