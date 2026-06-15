package com.yuzhi.dts.platform.service.goldenchain.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainOdsDbtSourceContractServiceTest {

    private final GoldenChainOdsDbtSourceContractService service = new GoldenChainOdsDbtSourceContractService();

    @Test
    void completeOdsSnapshotBuildsPublishableDbtSourceCandidate() {
        GoldenChainOdsDbtSourceCandidate candidate = service.buildCandidate(
            new GoldenChainOdsDbtSourceRequest(
                "erp",
                "ods",
                "ods_orders",
                "sales-ops",
                "daily",
                List.of(
                    new GoldenChainOdsColumnSnapshot("order_id", "bigint", false, "订单ID"),
                    new GoldenChainOdsColumnSnapshot("amount", "decimal(18,2)", true, "订单金额"),
                    new GoldenChainOdsColumnSnapshot("updated_at", "timestamp", false, "更新时间")
                )
            )
        );

        assertThat(candidate.publishable()).isTrue();
        assertThat(candidate.sourceName()).isEqualTo("erp");
        assertThat(candidate.schemaName()).isEqualTo("ods");
        assertThat(candidate.tableName()).isEqualTo("ods_orders");
        assertThat(candidate.owner()).isEqualTo("sales-ops");
        assertThat(candidate.refreshCadence()).isEqualTo("daily");
        assertThat(candidate.stageSnapshot().stage()).isEqualTo(GoldenChainStage.MODEL_READY);
        assertThat(candidate.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(candidate.stageSnapshot().evidenceRef()).isEqualTo("dbt-source://erp/ods_orders");
        assertThat(candidate.sourceYaml())
            .contains("version: 2")
            .contains("name: erp")
            .contains("schema: ods")
            .contains("name: ods_orders")
            .contains("owner: sales-ops")
            .contains("refresh_cadence: daily")
            .contains("name: order_id")
            .contains("data_type: bigint")
            .contains("nullable: false");
    }

    @Test
    void missingOwnerBlocksDbtSourcePublishing() {
        GoldenChainOdsDbtSourceCandidate candidate = service.buildCandidate(
            new GoldenChainOdsDbtSourceRequest(
                "erp",
                "ods",
                "ods_orders",
                " ",
                "daily",
                List.of(new GoldenChainOdsColumnSnapshot("order_id", "bigint", false, "订单ID"))
            )
        );

        assertThat(candidate.publishable()).isFalse();
        assertThat(candidate.stageSnapshot().stage()).isEqualTo(GoldenChainStage.MODEL_READY);
        assertThat(candidate.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(candidate.stageSnapshot().owner()).isEqualTo("待分配");
        assertThat(candidate.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_MODEL);
        assertThat(candidate.stageSnapshot().blockerReason()).contains("owner");
        assertThat(candidate.sourceYaml()).isBlank();
    }

    @Test
    void missingColumnSnapshotBlocksDbtSourcePublishing() {
        GoldenChainOdsDbtSourceCandidate candidate = service.buildCandidate(
            new GoldenChainOdsDbtSourceRequest("erp", "ods", "ods_orders", "sales-ops", "daily", List.of())
        );

        assertThat(candidate.publishable()).isFalse();
        assertThat(candidate.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(candidate.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_MODEL);
        assertThat(candidate.stageSnapshot().blockerReason()).contains("字段快照");
        assertThat(candidate.sourceYaml()).isBlank();
    }

    @Test
    void stgTableIsBlockedFromBusinessSourcePublishing() {
        GoldenChainOdsDbtSourceCandidate candidate = service.buildCandidate(
            new GoldenChainOdsDbtSourceRequest(
                "erp",
                "stg",
                "stg_orders",
                "sales-ops",
                "daily",
                List.of(new GoldenChainOdsColumnSnapshot("order_id", "bigint", false, "订单ID"))
            )
        );

        assertThat(candidate.publishable()).isFalse();
        assertThat(candidate.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(candidate.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_MODEL);
        assertThat(candidate.stageSnapshot().blockerReason()).contains("STG");
        assertThat(candidate.sourceYaml()).isBlank();
    }
}
