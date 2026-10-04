package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetSummary;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogAssetOverviewAggregatorTest {

    private AssetSummary summary(String layer, UUID domainId, String classification, String lifecycle, String governanceStatus) {
        return new AssetSummary(
            UUID.randomUUID(),
            null,
            "fqn",
            "TABLE",
            null,
            null,
            null,
            null,
            "asset",
            classification,
            layer,
            null,
            null,
            domainId,
            lifecycle,
            null,
            governanceStatus,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Test
    @DisplayName("聚合：总数/未定密/未归域/失效/attention 与层×域矩阵")
    void aggregate_countsAndMatrix() {
        UUID domainA = UUID.randomUUID();
        List<AssetSummary> rows = List.of(
            summary("ODS", domainA, "INTERNAL", "ACTIVE", "GOVERNED"),
            summary("ods", domainA, null, "ACTIVE", "PENDING_CLASSIFICATION"), // 未定密 + PENDING → attention，层大小写归一
            summary("DWD", null, "SECRET", "DEPRECATED", "GOVERNED"), // 未归域 + 已弃用 → attention
            summary("未知层", domainA, "INTERNAL", "ACTIVE", "GOVERNED") // 非法层 → OTHER
        );

        CatalogAssetOverviewAggregator.AssetOverview overview = CatalogAssetOverviewAggregator.aggregate(rows, 4, false);

        assertThat(overview.total()).isEqualTo(4);
        assertThat(overview.unclassified()).isEqualTo(1);
        assertThat(overview.missingDomain()).isEqualTo(1);
        assertThat(overview.stale()).isEqualTo(1);
        assertThat(overview.attention()).isEqualTo(2);
        assertThat(overview.byLayer().get("ODS")).isEqualTo(2);
        assertThat(overview.byLayer().get("OTHER")).isEqualTo(1);
        assertThat(overview.governanceStatusCounts().get("PENDING_CLASSIFICATION")).isEqualTo(1);

        CatalogAssetOverviewAggregator.MatrixCell odsCell = overview
            .matrix()
            .stream()
            .filter(cell -> "ODS".equals(cell.layer()) && domainA.toString().equals(cell.domainId()))
            .findFirst()
            .orElseThrow();
        assertThat(odsCell.total()).isEqualTo(2);
        assertThat(odsCell.attention()).isEqualTo(1);

        CatalogAssetOverviewAggregator.MatrixCell dwdCell = overview
            .matrix()
            .stream()
            .filter(cell -> "DWD".equals(cell.layer()) && cell.domainId() == null)
            .findFirst()
            .orElseThrow();
        assertThat(dwdCell.attention()).isEqualTo(1);
    }

    @Test
    @DisplayName("按层统计去重后的主题域数，供前端诚实展示其他域")
    void aggregate_countsDistinctDomainsPerLayer() {
        UUID domainA = UUID.randomUUID();
        UUID domainB = UUID.randomUUID();
        CatalogAssetOverviewAggregator.AssetOverview overview = CatalogAssetOverviewAggregator.aggregate(
            List.of(
                summaryWithDomain("ODS", domainA),
                summaryWithDomain("ODS", domainA),
                summaryWithDomain("ODS", domainB),
                summaryWithDomain("DWD", null)
            ),
            0,
            false
        );

        assertThat(overview.domainCountByLayer()).containsEntry("ODS", 2L);
        assertThat(overview.domainCountByLayer()).doesNotContainKey("DWD");
        assertThat(overview.domainCountByLayer().get("OTHER")).isNull();
    }

    @Test
    @DisplayName("截断标记透传，空列表安全")
    void aggregate_truncatedAndEmpty() {
        CatalogAssetOverviewAggregator.AssetOverview overview = CatalogAssetOverviewAggregator.aggregate(List.of(), 0, true);
        assertThat(overview.total()).isZero();
        assertThat(overview.truncated()).isTrue();
        assertThat(overview.matrix()).isEmpty();
    }

    private AssetSummary summaryWithDomain(String layer, UUID domainId) {
        return summary(layer, domainId, "INTERNAL", null, "GOVERNED");
    }

    private AssetSummary summaryWithLifecycle(String lifecycle) {
        return summary("ODS", UUID.randomUUID(), "INTERNAL", lifecycle, "GOVERNED");
    }

    @Test
    void staleCountsUseReachableLifecycleStatuses() {
        var rows = List.of(
            summaryWithLifecycle("DEPRECATED"),
            summaryWithLifecycle("ARCHIVED"),
            summaryWithLifecycle("BLOCKED"),
            summaryWithLifecycle("ACTIVE")
        );

        var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

        assertThat(overview.stale()).isEqualTo(3);
    }

    @Test
    void unreachableStaleTokenIsNoLongerCounted() {
        var rows = List.of(summaryWithLifecycle("STALE"));

        var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

        assertThat(overview.stale()).isZero();
    }

    @Test
    void byDomainRollsUpTotalsAndAttentionPerDomain() {
        UUID domainA = UUID.randomUUID();
        var rows = List.of(
            summary("ODS", domainA, "INTERNAL", "ACTIVE", "GOVERNED"),
            summary("ODS", domainA, null, "ACTIVE", "GOVERNED"),
            summary("ODS", null, "INTERNAL", "ACTIVE", "GOVERNED")
        );

        var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

        assertThat(overview.byDomain()).containsOnlyKeys(domainA.toString());
        assertThat(overview.byDomain().get(domainA.toString()).total()).isEqualTo(2);
        assertThat(overview.byDomain().get(domainA.toString()).attention()).isEqualTo(1);
    }

    @Test
    void byDomainExcludesUnassignedRows() {
        var rows = List.of(summary("ODS", null, "INTERNAL", "ACTIVE", "GOVERNED"));

        var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

        assertThat(overview.byDomain()).isEmpty();
        assertThat(overview.missingDomain()).isEqualTo(1);
    }

    @Test
    void tagCoverageUsesTheSameVisibleAssetPopulation() {
        CatalogTagDto tag = new CatalogTagDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BUSINESS-ORDER",
            "订单",
            "#1677ff",
            false,
            true,
            "订单资产"
        );
        var rows = List.of(
            summary("ODS", UUID.randomUUID(), "INTERNAL", "ACTIVE", "GOVERNED").withAssetTags(List.of(tag)),
            summary("DWD", UUID.randomUUID(), "INTERNAL", "ACTIVE", "GOVERNED")
        );

        var overview = CatalogAssetOverviewAggregator.aggregate(rows, rows.size(), false);

        assertThat(overview.tagged()).isEqualTo(1);
        assertThat(overview.untagged()).isEqualTo(1);
        assertThat(overview.tagCoveragePercent()).isEqualTo(50);
    }
}
