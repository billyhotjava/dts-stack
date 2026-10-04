package com.yuzhi.dts.platform.service.goldenchain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import org.junit.jupiter.api.Test;

class GoldenChainLineageGovernanceServiceTest {

    private final GoldenChainLineageGovernanceService service = new GoldenChainLineageGovernanceService();

    @Test
    void unresolvedSourceOrTargetMovesAssetToPendingGovernance() {
        GoldenChainLineageGovernanceDecision decision = service.evaluate(
            new GoldenChainLineageEvidence(
                GoldenChainLineageSource.OPENLINEAGE,
                "platform:asset-123",
                null,
                "ads_sales_summary",
                "lineage://openlineage/run-1",
                false,
                "无法解析 source dataset"
            )
        );

        assertThat(decision.state()).isEqualTo(GoldenChainGovernanceState.PENDING_GOVERNANCE);
        assertThat(decision.publishable()).isFalse();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.GOVERNANCE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_GOVERNANCE);
        assertThat(decision.stageSnapshot().blockerReason()).contains("无法解析 source dataset");
        assertThat(decision.remediation()).contains("补齐 source/target");
    }

    @Test
    void dbtManifestLineageEvidenceMakesGovernanceReady() {
        GoldenChainLineageGovernanceDecision decision = service.evaluate(
            new GoldenChainLineageEvidence(
                GoldenChainLineageSource.DBT_MANIFEST,
                "platform:asset-123",
                "source.pm_ods.orders",
                "model.pm.ads_sales_summary",
                "lineage://dbt/manifest/ads_sales_summary",
                true,
                null
            )
        );

        assertThat(decision.state()).isEqualTo(GoldenChainGovernanceState.READY);
        assertThat(decision.publishable()).isTrue();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.GOVERNANCE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(decision.stageSnapshot().evidenceRef()).isEqualTo("lineage://dbt/manifest/ads_sales_summary");
    }

    @Test
    void addaxDeclaredLineageWithoutEvidenceIsBlockedWithBusinessDiagnostic() {
        GoldenChainLineageGovernanceDecision decision = service.evaluate(
            new GoldenChainLineageEvidence(
                GoldenChainLineageSource.ADDAX_DECLARED,
                "platform:asset-123",
                "ods.ods_orders",
                "dwd.dwd_orders",
                null,
                true,
                null
            )
        );

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.diagnostics()).anyMatch(diagnostic -> diagnostic.contains("缺少血缘证据"));
        assertThat(decision.stageSnapshot().blockerReason()).contains("缺少血缘证据");
    }
}
