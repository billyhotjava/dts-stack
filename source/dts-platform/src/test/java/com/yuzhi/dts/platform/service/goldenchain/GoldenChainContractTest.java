package com.yuzhi.dts.platform.service.goldenchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoldenChainContractTest {

    @Test
    void stageOrderMatchesSprint39AcceptanceContract() {
        assertThat(GoldenChainContract.stages())
            .containsExactly(
                GoldenChainStage.DRAFT,
                GoldenChainStage.SOURCE_READY,
                GoldenChainStage.INGESTION_READY,
                GoldenChainStage.ODS_READY,
                GoldenChainStage.MODEL_READY,
                GoldenChainStage.GOVERNANCE_READY,
                GoldenChainStage.RELEASE_READY,
                GoldenChainStage.CONSUMABLE,
                GoldenChainStage.OPERATED
            );
        assertThat(GoldenChainStage.DRAFT.next()).contains(GoldenChainStage.SOURCE_READY);
        assertThat(GoldenChainStage.OPERATED.next()).isEmpty();
    }

    @Test
    void transitionsOnlyMoveForward() {
        assertThat(GoldenChainContract.isForwardTransition(GoldenChainStage.DRAFT, GoldenChainStage.SOURCE_READY)).isTrue();
        assertThat(GoldenChainContract.isForwardTransition(GoldenChainStage.MODEL_READY, GoldenChainStage.MODEL_READY)).isTrue();
        assertThat(GoldenChainContract.isForwardTransition(GoldenChainStage.GOVERNANCE_READY, GoldenChainStage.MODEL_READY)).isFalse();
    }

    @Test
    void blockerCodesCoverExpectedCommercialGates() {
        assertThat(GoldenChainBlockerCode.values())
            .extracting(GoldenChainBlockerCode::name)
            .containsExactly(
                "BLOCKED_SOURCE",
                "BLOCKED_INGESTION",
                "BLOCKED_MODEL",
                "BLOCKED_GOVERNANCE",
                "BLOCKED_PERMISSION",
                "BLOCKED_CONSUMPTION"
            );
        assertThat(GoldenChainBlockerCode.BLOCKED_PERMISSION.blockedStage()).isEqualTo(GoldenChainStage.RELEASE_READY);
        assertThat(GoldenChainBlockerCode.BLOCKED_CONSUMPTION.label()).contains("消费");
    }

    @Test
    void blockedSnapshotRequiresMatchingBlockerAndReason() {
        GoldenChainStageSnapshot snapshot = GoldenChainStageSnapshot.blocked(
            GoldenChainStage.RELEASE_READY,
            "治理负责人",
            GoldenChainBlockerCode.BLOCKED_PERMISSION,
            "缺少数据集授权审批"
        );

        assertThat(snapshot.status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(snapshot.owner()).isEqualTo("治理负责人");
        assertThat(snapshot.blockerReason()).isEqualTo("缺少数据集授权审批");
        assertThat(snapshot.evidenceRef()).isNull();

        assertThatThrownBy(() ->
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.RELEASE_READY,
                    "治理负责人",
                    GoldenChainBlockerCode.BLOCKED_GOVERNANCE,
                    "缺少质量或血缘证据"
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("阻断码与阶段不匹配");
        assertThatThrownBy(() ->
                new GoldenChainStageSnapshot(
                    GoldenChainStage.RELEASE_READY,
                    GoldenChainStageStatus.BLOCKED,
                    "治理负责人",
                    null,
                    null,
                    "缺少数据集授权审批"
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("阻断状态必须包含阻断码");
    }

    @Test
    void readySnapshotCarriesOwnerAndEvidenceReference() {
        GoldenChainStageSnapshot snapshot = GoldenChainStageSnapshot.ready(
            GoldenChainStage.MODEL_READY,
            "数据开发负责人",
            "dbt://build/orders_dws/latest"
        );

        assertThat(snapshot.status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(snapshot.owner()).isEqualTo("数据开发负责人");
        assertThat(snapshot.evidenceRef()).isEqualTo("dbt://build/orders_dws/latest");
        assertThat(snapshot.blockerCode()).isNull();
        assertThat(snapshot.blockerReason()).isNull();
    }
}
