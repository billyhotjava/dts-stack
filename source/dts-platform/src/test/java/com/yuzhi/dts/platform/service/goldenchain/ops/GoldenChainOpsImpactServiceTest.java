package com.yuzhi.dts.platform.service.goldenchain.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainOpsImpactServiceTest {

    private final GoldenChainOpsImpactService service = new GoldenChainOpsImpactService();

    @Test
    void failedChainSummaryIncludesRecentRunMttrBackfillAndAffectedConsumers() {
        GoldenChainOpsImpactSummary summary = service.summarize(
            "chain-orders",
            List.of(
                new GoldenChainOpsInstanceSnapshot(
                    "run-1",
                    "chain-orders",
                    "task-load-orders",
                    "orders_daily",
                    "FAILED",
                    900000L,
                    "/logs/orders.log"
                )
            ),
            List.of(new GoldenChainOpsBackfillSnapshot("backfill-1", "chain-orders", "task-load-orders", "PENDING")),
            List.of("asset:orders"),
            List.of("report:sales-dashboard"),
            List.of("api:orders-service")
        );

        assertThat(summary.chainKey()).isEqualTo("chain-orders");
        assertThat(summary.latestInstanceId()).isEqualTo("run-1");
        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(summary.mttrMinutes()).isEqualTo(15.0);
        assertThat(summary.backfillStatus()).isEqualTo("PENDING");
        assertThat(summary.openLogRoute()).isEqualTo("/ops/logs?chainKey=chain-orders&runId=run-1");
        assertThat(summary.affectedReports()).containsExactly("report:sales-dashboard");
        assertThat(summary.affectedApis()).containsExactly("api:orders-service");
    }
}
