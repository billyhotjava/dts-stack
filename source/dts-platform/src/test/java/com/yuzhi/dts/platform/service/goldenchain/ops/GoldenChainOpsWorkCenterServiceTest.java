package com.yuzhi.dts.platform.service.goldenchain.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainOpsWorkCenterServiceTest {

    private final GoldenChainOpsWorkCenterService service = new GoldenChainOpsWorkCenterService();

    @Test
    void failedInstanceIsLinkedWithAlertAndBackfillAction() {
        GoldenChainOpsWorkCenterView view = service.buildView(
            List.of(
                new GoldenChainOpsInstanceSnapshot(
                    "run-1",
                    "chain-orders",
                    "task-load-orders",
                    "orders_daily",
                    "FAILED",
                    120000L,
                    "/logs/orders.log"
                )
            ),
            List.of(
                new GoldenChainOpsAlertSnapshot(
                    "alert-1",
                    "chain-orders",
                    "task-load-orders",
                    "TASK_FAILED",
                    "HIGH",
                    "订单入湖失败"
                )
            ),
            List.of(new GoldenChainOpsBackfillSnapshot("backfill-1", "chain-orders", "task-load-orders", "PENDING"))
        );

        assertThat(view.failedItems()).hasSize(1);
        GoldenChainOpsFailureItem item = view.failedItems().get(0);
        assertThat(item.chainKey()).isEqualTo("chain-orders");
        assertThat(item.taskId()).isEqualTo("task-load-orders");
        assertThat(item.instanceId()).isEqualTo("run-1");
        assertThat(item.alertId()).isEqualTo("alert-1");
        assertThat(item.backfillId()).isEqualTo("backfill-1");
        assertThat(item.canBackfill()).isTrue();
        assertThat(item.logPath()).isEqualTo("/logs/orders.log");
    }

    @Test
    void healthyInstancesRemainVisibleButDoNotCreateFailureItems() {
        GoldenChainOpsWorkCenterView view = service.buildView(
            List.of(
                new GoldenChainOpsInstanceSnapshot(
                    "run-2",
                    "chain-orders",
                    "task-load-orders",
                    "orders_daily",
                    "SUCCESS",
                    9000L,
                    "/logs/orders-success.log"
                )
            ),
            List.of(),
            List.of()
        );

        assertThat(view.instances()).hasSize(1);
        assertThat(view.failedItems()).isEmpty();
    }
}
