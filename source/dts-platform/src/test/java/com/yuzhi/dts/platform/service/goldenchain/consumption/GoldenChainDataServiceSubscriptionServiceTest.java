package com.yuzhi.dts.platform.service.goldenchain.consumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainDataServiceSubscriptionServiceTest {

    private final GoldenChainDataServiceSubscriptionService service = new GoldenChainDataServiceSubscriptionService();

    @Test
    void approvesSubscriptionWithSecretRefAndOperationalStats() {
        GoldenChainDataServiceSubscriptionDecision decision = service.evaluate(
            request(permissionDecision(true), authorization(true, "secretRef://tokens/orders-api", 128L))
        );

        assertThat(decision.operable()).isTrue();
        assertThat(decision.status()).isEqualTo(GoldenChainDataServiceSubscriptionStatus.APPROVED);
        assertThat(decision.serviceViewRef()).isEqualTo("data-service://orders-api/v1");
        assertThat(decision.tokenRef()).isEqualTo("secretRef://tokens/orders-api");
        assertThat(decision.callCount()).isEqualTo(128L);
        assertThat(decision.dependencyAssetKeys()).containsExactly("asset:orders-day");
        assertThat(decision.serviceSummary()).contains("订单 API", "日更新", "P1 工作日 4 小时响应", "成交金额");
        assertThat(decision.serviceSummary()).doesNotContain("select", "dbt", ".sql");
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    @Test
    void deniesUnauthorizedTokenWithSafeMessage() {
        GoldenChainDataServiceSubscriptionDecision decision = service.evaluate(
            request(permissionDecision(false), authorization(true, "secretRef://tokens/orders-api", 1L))
        );

        assertThat(decision.operable()).isFalse();
        assertThat(decision.status()).isEqualTo(GoldenChainDataServiceSubscriptionStatus.DENIED);
        assertThat(decision.safeMessage()).isEqualTo("无权限访问该资产");
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
        assertThat(decision.stageSnapshot().blockerReason()).doesNotContain("asset:orders-day");
    }

    @Test
    void keepsApiPendingUntilSubscriptionApprovalAndSecretRefAreReady() {
        GoldenChainDataServiceSubscriptionDecision pendingApproval = service.evaluate(
            request(permissionDecision(true), authorization(false, "secretRef://tokens/orders-api", 0L))
        );

        assertThat(pendingApproval.operable()).isFalse();
        assertThat(pendingApproval.status()).isEqualTo(GoldenChainDataServiceSubscriptionStatus.PENDING_APPROVAL);
        assertThat(pendingApproval.blockers()).contains("订阅审批未完成");
        assertThat(pendingApproval.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.PENDING);

        GoldenChainDataServiceSubscriptionDecision rawToken = service.evaluate(
            request(permissionDecision(true), authorization(true, "raw-token-value", 0L))
        );

        assertThat(rawToken.operable()).isFalse();
        assertThat(rawToken.status()).isEqualTo(GoldenChainDataServiceSubscriptionStatus.DENIED);
        assertThat(rawToken.blockers()).contains("token 必须使用 secretRef 引用");
        assertThat(rawToken.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
    }

    private GoldenChainDataServiceSubscriptionRequest request(
        GoldenChainPermissionConsistencyDecision permissionDecision,
        GoldenChainApiAuthorizationSnapshot authorization
    ) {
        return new GoldenChainDataServiceSubscriptionRequest(
            "orders-api",
            "订单 API",
            "asset:orders-day",
            "v1",
            "sales-ops",
            "user:alice",
            List.of(
                new GoldenChainBusinessField(
                    "order_amount",
                    "成交金额",
                    GoldenChainBusinessFieldRole.METRIC,
                    "统计周期内完成交易的订单金额",
                    "元",
                    null,
                    "2026-06-14"
                )
            ),
            List.of(GoldenChainDataServiceConsumptionMode.REST_API, GoldenChainDataServiceConsumptionMode.DATA_PRODUCT),
            "P1 工作日 4 小时响应",
            "日更新",
            permissionDecision,
            authorization
        );
    }

    private GoldenChainApiAuthorizationSnapshot authorization(boolean approved, String tokenRef, long callCount) {
        return new GoldenChainApiAuthorizationSnapshot(tokenRef, approved, 1000L, callCount, "2026-06-14T10:00:00+08:00");
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
