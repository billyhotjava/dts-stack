package com.yuzhi.dts.platform.service.goldenchain.consumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyDecision;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelLayer;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseDecision;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainReleaseEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainReportDatasetPublisherServiceTest {

    private final GoldenChainReportDatasetPublisherService service = new GoldenChainReportDatasetPublisherService();

    @Test
    void publishesGovernedDwsAssetAsReportDatasetAndMetricEntryWithoutSql() {
        GoldenChainReportDatasetPublication publication = service.publish(
            new GoldenChainReportDatasetPublishRequest(
                "chain-orders",
                "asset:orders-day",
                "订单日汇总",
                "sales-ops",
                releaseDecision(GoldenChainModelLayer.DWS, true),
                permissionDecision(true),
                List.of(
                    new GoldenChainBusinessField(
                        "order_amount",
                        "成交金额",
                        GoldenChainBusinessFieldRole.METRIC,
                        "统计周期内完成交易的订单金额",
                        "元",
                        "低于目标值时预警",
                        "2026-06-14"
                    ),
                    new GoldenChainBusinessField(
                        "customer_name",
                        "客户名称",
                        GoldenChainBusinessFieldRole.DIMENSION,
                        "用于按客户查看订单表现",
                        null,
                        null,
                        "2026-06-14"
                    )
                )
            )
        );

        assertThat(publication.publishable()).isTrue();
        assertThat(publication.datasetRef()).isEqualTo("bi-dataset://asset:orders-day");
        assertThat(publication.metricCatalogRef()).isEqualTo("metrics://asset:orders-day");
        assertThat(publication.consumerSummary()).contains("订单日汇总", "成交金额", "客户名称");
        assertThat(publication.consumerSummary().toLowerCase()).doesNotContain("select", " dbt", ".sql");
        assertThat(publication.fields()).extracting(GoldenChainBusinessField::displayName).containsExactly("成交金额", "客户名称");
        assertThat(publication.stageSnapshot().stage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(publication.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    @Test
    void blocksReportDatasetWhenReleaseOrPermissionGateIsNotReady() {
        GoldenChainReportDatasetPublication publication = service.publish(
            new GoldenChainReportDatasetPublishRequest(
                "chain-orders",
                "asset:orders-day",
                "订单日汇总",
                "sales-ops",
                releaseDecision(GoldenChainModelLayer.DWS, true),
                permissionDecision(false),
                List.of(businessMetric())
            )
        );

        assertThat(publication.publishable()).isFalse();
        assertThat(publication.blockers()).contains("无权限访问该资产");
        assertThat(publication.stageSnapshot().stage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(publication.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(publication.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
        assertThat(publication.stageSnapshot().blockerReason()).doesNotContain("asset:orders-day");
    }

    @Test
    void blocksBusinessDescriptionsThatExposeSqlOrDbtInternals() {
        GoldenChainReportDatasetPublication publication = service.publish(
            new GoldenChainReportDatasetPublishRequest(
                "chain-orders",
                "asset:orders-day",
                "订单日汇总",
                "sales-ops",
                releaseDecision(GoldenChainModelLayer.ADS, true),
                permissionDecision(true),
                List.of(
                    new GoldenChainBusinessField(
                        "order_amount",
                        "成交金额",
                        GoldenChainBusinessFieldRole.METRIC,
                        "select amount from dws_order_day.sql",
                        "元",
                        null,
                        "2026-06-14"
                    )
                )
            )
        );

        assertThat(publication.publishable()).isFalse();
        assertThat(publication.blockers()).anyMatch(blocker -> blocker.contains("业务说明不能暴露 SQL/dbt"));
        assertThat(publication.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
    }

    private GoldenChainBusinessField businessMetric() {
        return new GoldenChainBusinessField(
            "order_amount",
            "成交金额",
            GoldenChainBusinessFieldRole.METRIC,
            "统计周期内完成交易的订单金额",
            "元",
            null,
            "2026-06-14"
        );
    }

    private GoldenChainModelReleaseDecision releaseDecision(GoldenChainModelLayer layer, boolean publishable) {
        GoldenChainStageSnapshot snapshot = publishable
            ? GoldenChainStageSnapshot.ready(GoldenChainStage.RELEASE_READY, "sales-ops", "model-release://prod/orders")
            : GoldenChainStageSnapshot.blocked(
                GoldenChainStage.MODEL_READY,
                "sales-ops",
                GoldenChainBlockerCode.BLOCKED_MODEL,
                "模型未发布"
            );
        return new GoldenChainModelReleaseDecision(
            publishable,
            "dws_order_day",
            layer,
            GoldenChainReleaseEnvironment.PROD,
            publishable ? List.of() : List.of("模型未发布"),
            List.of(),
            snapshot
        );
    }

    private GoldenChainPermissionConsistencyDecision permissionDecision(boolean consistent) {
        GoldenChainStageSnapshot snapshot = consistent
            ? GoldenChainStageSnapshot.ready(GoldenChainStage.RELEASE_READY, "sales-ops", "permission://orders")
            : GoldenChainStageSnapshot.blocked(
                GoldenChainStage.RELEASE_READY,
                "sales-ops",
                GoldenChainBlockerCode.BLOCKED_PERMISSION,
                "无权限访问该资产"
            );
        return new GoldenChainPermissionConsistencyDecision(
            consistent,
            "asset:orders-day",
            "user:alice",
            consistent ? List.of() : List.of("无权限访问该资产"),
            List.of(),
            consistent ? "权限一致" : "无权限访问该资产",
            snapshot
        );
    }
}
