package com.yuzhi.dts.platform.service.goldenchain.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import org.junit.jupiter.api.Test;

class GoldenChainModelReleaseGateServiceTest {

    private final GoldenChainModelReleaseGateService service = new GoldenChainModelReleaseGateService();

    @Test
    void prodDwdModelPassesWhenDbtEvidenceAndGovernanceAreReady() {
        GoldenChainModelReleaseDecision decision = service.evaluate(
            new GoldenChainModelReleaseRequest(
                "dwd_orders",
                GoldenChainModelLayer.DWD,
                "sales-ops",
                GoldenChainReleaseEnvironment.PROD,
                dbtEvidence(true, true, true),
                governanceSnapshot(true, true, true, true),
                new GoldenChainModelSemanticContract("order_id", true, "order", true, true)
            )
        );

        assertThat(decision.publishable()).isTrue();
        assertThat(decision.warnings()).isEmpty();
        assertThat(decision.blockers()).isEmpty();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.RELEASE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(decision.stageSnapshot().evidenceRef()).isEqualTo("model-release://prod/dwd_orders");
    }

    @Test
    void prodReleaseIsBlockedWhenDbtTestFails() {
        GoldenChainModelReleaseDecision decision = service.evaluate(
            new GoldenChainModelReleaseRequest(
                "dwd_orders",
                GoldenChainModelLayer.DWD,
                "sales-ops",
                GoldenChainReleaseEnvironment.PROD,
                dbtEvidence(true, false, true),
                governanceSnapshot(true, true, true, true),
                new GoldenChainModelSemanticContract("order_id", true, "order", true, true)
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.MODEL_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_MODEL);
        assertThat(decision.stageSnapshot().blockerReason()).contains("dbt test");
    }

    @Test
    void dwsAndAdsReleaseIsBlockedWhenGrainIsInconsistent() {
        GoldenChainModelReleaseDecision decision = service.evaluate(
            new GoldenChainModelReleaseRequest(
                "ads_sales_summary",
                GoldenChainModelLayer.ADS,
                "sales-ops",
                GoldenChainReleaseEnvironment.PROD,
                dbtEvidence(true, true, true),
                governanceSnapshot(true, true, true, true),
                new GoldenChainModelSemanticContract(null, true, "customer_day", false, true)
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.MODEL_READY);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_MODEL);
        assertThat(decision.stageSnapshot().blockerReason()).contains("粒度");
    }

    @Test
    void devReleaseKeepsDbtFailureAsWarningInsteadOfProdBlocker() {
        GoldenChainModelReleaseDecision decision = service.evaluate(
            new GoldenChainModelReleaseRequest(
                "dws_order_day",
                GoldenChainModelLayer.DWS,
                "sales-ops",
                GoldenChainReleaseEnvironment.DEV,
                dbtEvidence(true, false, true),
                governanceSnapshot(true, true, true, true),
                new GoldenChainModelSemanticContract(null, true, "order_day", true, true)
            )
        );

        assertThat(decision.publishable()).isTrue();
        assertThat(decision.blockers()).isEmpty();
        assertThat(decision.warnings()).anyMatch(warning -> warning.contains("dbt test"));
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.RELEASE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    private GoldenChainDbtRunEvidence dbtEvidence(boolean compilePassed, boolean testPassed, boolean buildPassed) {
        return new GoldenChainDbtRunEvidence(
            "dbt://runs/compile-1",
            compilePassed,
            "dbt://runs/test-1",
            testPassed,
            "dbt://runs/build-1",
            buildPassed
        );
    }

    private GoldenChainModelGovernanceSnapshot governanceSnapshot(
        boolean schemaContractPassed,
        boolean qualityPassed,
        boolean lineageReady,
        boolean classificationReady
    ) {
        return new GoldenChainModelGovernanceSnapshot(
            "governance://assets/orders/snapshots/1",
            schemaContractPassed,
            qualityPassed,
            lineageReady,
            classificationReady
        );
    }
}
