package com.yuzhi.dts.platform.service.workbench.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response payload for the leader-overview workbench endpoint.
 *
 * <p>Field contract mirrors Sprint-15 F1/T01 design doc. {@code scope}
 * and {@code effectiveDeptCode} reflect server-side (post-downgrade)
 * truth — never the raw request values.
 */
public record LeaderOverviewResponse(
    Instant generatedAt,
    String scope,              // MINE|DEPT|ALL
    String effectiveDeptCode,  // 服务端降级后的 deptCode
    String timeRange,          // MONTH|QUARTER|YEAR
    Kpis kpis,
    List<TopReport> topReports,
    List<TopAsset> topAssets,
    List<DomainCell> domainMatrix
) {
    public record Kpis(
        long reportsTotal,
        long reportsNewInPeriod,
        long visitsInPeriod,
        BigDecimal visitsMoM,      // 环比，可为 null
        long assetsTotal,
        long assetsNewInPeriod,
        long assetsS1,
        long assetsS1S2,           // 部门领导卡"核心资产 S1+S2"直接用
        BigDecimal assetsS1Ratio   // 可为 null
    ) {}

    public record TopReport(
        String id,
        String title,
        long visits,
        String bizDomain,          // 可为 null
        String classification,
        Instant lastVisitedAt      // 可为 null
    ) {}

    public record TopAsset(
        String id,
        String name,
        String classification,
        Instant updatedAt,
        String bizDomain           // 可为 null
    ) {}

    public record DomainCell(
        String domain,             // 域 code；归到"其他"时使用常量 "__OTHER__"
        String domainName,
        long visits
    ) {}
}
