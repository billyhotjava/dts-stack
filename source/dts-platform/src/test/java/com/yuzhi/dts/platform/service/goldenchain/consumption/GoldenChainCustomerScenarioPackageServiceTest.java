package com.yuzhi.dts.platform.service.goldenchain.consumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainConsumptionSurface;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainCustomerScenarioPackageServiceTest {

    private final GoldenChainCustomerScenarioPackageService service = new GoldenChainCustomerScenarioPackageService();

    @Test
    void buildsCustomerReadableAcceptancePackageForStructuredDataChain() {
        GoldenChainCustomerAcceptancePackage acceptancePackage = service.create(
            new GoldenChainCustomerScenarioPackageRequest(
                "项目订单结构化黄金链路",
                "sales-ops",
                List.of("JDBC", "API", "file"),
                List.of(
                    step(1, "确认数据源", "客户、项目、订单主数据已进入接入中心", GoldenChainAcceptanceEvidenceType.SCREENSHOT),
                    step(2, "确认入湖结果", "订单日数据可按业务日期追踪", GoldenChainAcceptanceEvidenceType.SCRIPT_OUTPUT),
                    step(3, "确认治理门禁", "owner、分级、质量和血缘证据齐全", GoldenChainAcceptanceEvidenceType.API_OUTPUT),
                    step(4, "确认报表消费", "业务用户可选择成交金额和客户名称生成报表", GoldenChainAcceptanceEvidenceType.SCREENSHOT),
                    step(5, "确认数据服务", "授权用户可通过订单 API 查看调用统计", GoldenChainAcceptanceEvidenceType.API_OUTPUT)
                ),
                List.of(businessMetric()),
                reportPublication(true),
                dataServiceDecision(true),
                permissionView(true)
            )
        );

        assertThat(acceptancePackage.ready()).isTrue();
        assertThat(acceptancePackage.customerNarrative()).contains("项目订单结构化黄金链路", "报表", "订单 API");
        assertThat(acceptancePackage.customerNarrative().toLowerCase()).doesNotContain("select", "dbt", ".sql");
        assertThat(acceptancePackage.evidenceChecklist()).hasSize(5);
        assertThat(acceptancePackage.businessMetrics()).extracting(GoldenChainBusinessField::displayName).containsExactly("成交金额");
        assertThat(acceptancePackage.stageSnapshot().stage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(acceptancePackage.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    @Test
    void blocksAcceptancePackageWhenEvidenceIsMissingOrNarrativeLeaksSql() {
        GoldenChainCustomerAcceptancePackage acceptancePackage = service.create(
            new GoldenChainCustomerScenarioPackageRequest(
                "项目订单结构化黄金链路",
                "sales-ops",
                List.of("JDBC"),
                List.of(
                    new GoldenChainCustomerScenarioStep(
                        1,
                        "确认报表消费",
                        "select amount from dws_order_day.sql",
                        GoldenChainAcceptanceEvidenceType.SCREENSHOT,
                        null
                    )
                ),
                List.of(businessMetric()),
                reportPublication(true),
                dataServiceDecision(true),
                permissionView(true)
            )
        );

        assertThat(acceptancePackage.ready()).isFalse();
        assertThat(acceptancePackage.blockers()).contains("第 1 步缺少证据引用");
        assertThat(acceptancePackage.blockers()).anyMatch(blocker -> blocker.contains("客户说明不能暴露 SQL/dbt"));
        assertThat(acceptancePackage.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(acceptancePackage.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
    }

    private GoldenChainCustomerScenarioStep step(
        int sequence,
        String title,
        String businessOutcome,
        GoldenChainAcceptanceEvidenceType evidenceType
    ) {
        return new GoldenChainCustomerScenarioStep(
            sequence,
            title,
            businessOutcome,
            evidenceType,
            "it/evidence/customer-demo/%02d-%s.md".formatted(sequence, title)
        );
    }

    private GoldenChainBusinessField businessMetric() {
        return new GoldenChainBusinessField(
            "order_amount",
            "成交金额",
            GoldenChainBusinessFieldRole.METRIC,
            "统计周期内完成交易的订单金额",
            "元",
            "低于目标值时预警",
            "2026-06-14"
        );
    }

    private GoldenChainReportDatasetPublication reportPublication(boolean publishable) {
        return new GoldenChainReportDatasetPublication(
            publishable,
            "bi-dataset://asset:orders-day",
            "metrics://asset:orders-day",
            "订单日汇总已发布，业务用户可选择成交金额和客户名称。",
            List.of(businessMetric()),
            publishable ? List.of() : List.of("报表未发布"),
            publishable
                ? GoldenChainStageSnapshot.ready(GoldenChainStage.CONSUMABLE, "sales-ops", "consumption://dataset/orders")
                : GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.CONSUMABLE,
                    "sales-ops",
                    GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                    "报表未发布"
                )
        );
    }

    private GoldenChainDataServiceSubscriptionDecision dataServiceDecision(boolean operable) {
        return new GoldenChainDataServiceSubscriptionDecision(
            operable,
            operable ? GoldenChainDataServiceSubscriptionStatus.APPROVED : GoldenChainDataServiceSubscriptionStatus.DENIED,
            "data-service://orders-api/v1",
            "secretRef://tokens/orders-api",
            128L,
            List.of("asset:orders-day"),
            "订单 API 已授权，schema 包含成交金额；刷新频率：日更新；SLA：P1 工作日 4 小时响应。",
            operable ? List.of() : List.of("服务未授权"),
            operable ? "服务可用" : "服务未授权",
            operable
                ? GoldenChainStageSnapshot.ready(GoldenChainStage.CONSUMABLE, "sales-ops", "data-service://orders-api/v1")
                : GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.CONSUMABLE,
                    "sales-ops",
                    GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                    "服务未授权"
                )
        );
    }

    private GoldenChainConsumptionPermissionView permissionView(boolean consumable) {
        return new GoldenChainConsumptionPermissionView(
            consumable,
            "asset:orders-day",
            "user:alice",
            List.of(GoldenChainConsumptionSurface.values()),
            List.of(),
            List.of(),
            "policy-a",
            "rls-a",
            "mask-a",
            consumable ? List.of() : List.of("权限未就绪"),
            consumable ? "权限一致" : "权限未就绪",
            consumable
                ? GoldenChainStageSnapshot.ready(GoldenChainStage.CONSUMABLE, "sales-ops", "permission://orders")
                : GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.CONSUMABLE,
                    "sales-ops",
                    GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                    "权限未就绪"
                )
        );
    }
}
