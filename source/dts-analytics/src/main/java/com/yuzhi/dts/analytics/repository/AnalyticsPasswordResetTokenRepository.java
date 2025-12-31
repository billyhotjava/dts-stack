package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsPasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsPasswordResetTokenRepository extends JpaRepository<AnalyticsPasswordResetToken, Long> {
    Optional<AnalyticsPasswordResetToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);
}

