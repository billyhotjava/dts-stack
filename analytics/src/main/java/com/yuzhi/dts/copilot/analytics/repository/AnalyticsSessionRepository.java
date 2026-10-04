package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsSessionRepository extends JpaRepository<AnalyticsSession, UUID> {
    Optional<AnalyticsSession> findByIdAndRevokedFalseAndExpiresAtAfter(UUID id, Instant now);
}

