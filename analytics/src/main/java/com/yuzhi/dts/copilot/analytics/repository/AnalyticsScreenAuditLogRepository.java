package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenAuditLogRepository extends JpaRepository<AnalyticsScreenAuditLog, Long> {
    List<AnalyticsScreenAuditLog> findAllByScreenIdOrderByCreatedAtDesc(Long screenId);
}
