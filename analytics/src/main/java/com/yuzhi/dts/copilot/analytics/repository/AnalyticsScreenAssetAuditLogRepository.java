package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAssetAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenAssetAuditLogRepository extends JpaRepository<AnalyticsScreenAssetAuditLog, Long> {

    List<AnalyticsScreenAssetAuditLog> findTop200ByAssetTypeOrderByCreatedAtDesc(String assetType);
}
