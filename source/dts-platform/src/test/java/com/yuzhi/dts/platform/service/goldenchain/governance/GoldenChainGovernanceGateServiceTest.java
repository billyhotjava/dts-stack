package com.yuzhi.dts.platform.service.goldenchain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import org.junit.jupiter.api.Test;

class GoldenChainGovernanceGateServiceTest {

    private final GoldenChainGovernanceGateService service = new GoldenChainGovernanceGateService();

    @Test
    void missingOwnerBlocksGovernanceGate() {
        GoldenChainGovernanceGateDecision decision = service.evaluate(
            new GoldenChainGovernanceGateRequest(
                "platform:asset-123",
                GoldenChainGovernedAssetType.DWD,
                null,
                "L2",
                true,
                true,
                true,
                "order_id",
                "order",
                true
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.GOVERNANCE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_GOVERNANCE);
        assertThat(decision.stageSnapshot().blockerReason()).contains("owner");
    }

    @Test
    void missingClassificationOrQualityBlocksGovernanceGate() {
        GoldenChainGovernanceGateDecision decision = service.evaluate(
            new GoldenChainGovernanceGateRequest(
                "platform:asset-123",
                GoldenChainGovernedAssetType.ADS,
                "sales-ops",
                " ",
                true,
                false,
                false,
                null,
                "customer_day",
                true
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().blockerReason()).contains("分级分类").contains("质量规则").contains("质量结果");
    }

    @Test
    void dwsAndAdsRequireGrainAndMetricDefinition() {
        GoldenChainGovernanceGateDecision decision = service.evaluate(
            new GoldenChainGovernanceGateRequest(
                "platform:asset-123",
                GoldenChainGovernedAssetType.DWS,
                "sales-ops",
                "L2",
                true,
                true,
                true,
                null,
                null,
                false
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().blockerReason()).contains("粒度").contains("指标口径");
    }

    @Test
    void completeAdsGovernanceSnapshotIsReady() {
        GoldenChainGovernanceGateDecision decision = service.evaluate(
            new GoldenChainGovernanceGateRequest(
                "platform:asset-123",
                GoldenChainGovernedAssetType.ADS,
                "sales-ops",
                "L2",
                true,
                true,
                true,
                null,
                "customer_day",
                true
            )
        );

        assertThat(decision.publishable()).isTrue();
        assertThat(decision.blockers()).isEmpty();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.GOVERNANCE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(decision.stageSnapshot().evidenceRef()).isEqualTo("governance-gate://platform:asset-123");
    }
}
