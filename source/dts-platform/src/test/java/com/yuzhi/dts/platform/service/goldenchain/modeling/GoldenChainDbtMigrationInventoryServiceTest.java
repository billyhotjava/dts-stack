package com.yuzhi.dts.platform.service.goldenchain.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainDbtMigrationInventoryServiceTest {

    private final GoldenChainDbtMigrationInventoryService service = new GoldenChainDbtMigrationInventoryService();

    @Test
    void manualDbtAssetMissingProductLinksProducesMigrationActions() {
        GoldenChainDbtMigrationReport report = service.inventory(
            List.of(
                new GoldenChainDbtAssetSnapshot(
                    "pm",
                    "biz_dwd_project_node_enriched",
                    false,
                    false,
                    false,
                    false,
                    false
                )
            )
        );

        assertThat(report.totalAssets()).isEqualTo(1);
        assertThat(report.items()).hasSize(1);
        GoldenChainDbtMigrationItem item = report.items().get(0);
        assertThat(item.packageName()).isEqualTo("pm");
        assertThat(item.modelName()).isEqualTo("biz_dwd_project_node_enriched");
        assertThat(item.statuses())
            .contains(
                GoldenChainDbtMigrationStatus.NEED_SOURCE,
                GoldenChainDbtMigrationStatus.NEED_ASSET,
                GoldenChainDbtMigrationStatus.NEED_LINEAGE,
                GoldenChainDbtMigrationStatus.NEED_RUNTIME_GRAPH,
                GoldenChainDbtMigrationStatus.NEED_CONFIRMATION
            );
        assertThat(item.riskLevel()).isEqualTo(GoldenChainDbtMigrationRisk.HIGH);
        assertThat(item.nextAction()).contains("dbt source").contains("资产").contains("血缘").contains("运行图");
    }

    @Test
    void managedDbtAssetHasLowRiskAndNoMigrationAction() {
        GoldenChainDbtMigrationReport report = service.inventory(
            List.of(new GoldenChainDbtAssetSnapshot("sales", "ads_sales_summary", true, true, true, true, true))
        );

        assertThat(report.totalAssets()).isEqualTo(1);
        assertThat(report.highRiskAssets()).isZero();
        GoldenChainDbtMigrationItem item = report.items().get(0);
        assertThat(item.statuses()).containsExactly(GoldenChainDbtMigrationStatus.MANAGED);
        assertThat(item.riskLevel()).isEqualTo(GoldenChainDbtMigrationRisk.LOW);
        assertThat(item.nextAction()).isEqualTo("已纳入黄金链路，保持例行发布门禁");
    }
}
