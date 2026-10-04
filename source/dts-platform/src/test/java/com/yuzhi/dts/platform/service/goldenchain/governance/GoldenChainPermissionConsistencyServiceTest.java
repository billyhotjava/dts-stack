package com.yuzhi.dts.platform.service.goldenchain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainPermissionConsistencyServiceTest {

    private final GoldenChainPermissionConsistencyService service = new GoldenChainPermissionConsistencyService();

    @Test
    void allConsumptionSurfacesUseSamePlatformPolicySnapshot() {
        GoldenChainPermissionConsistencyDecision decision = service.evaluate(
            List.of(
                snapshot(GoldenChainConsumptionSurface.ASSET_PORTAL, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.METRICS, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.BI, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.SCREEN, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.API_SERVICE, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.DATA_PRODUCT, true, "policy-a", "rls-a", "mask-a", false, false)
            )
        );

        assertThat(decision.consistent()).isTrue();
        assertThat(decision.blockers()).isEmpty();
        assertThat(decision.stageSnapshot().stage()).isEqualTo(GoldenChainStage.RELEASE_READY);
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(decision.stageSnapshot().evidenceRef()).isEqualTo("permission-consistency://platform:asset-123/user:alice/policy-a");
    }

    @Test
    void divergentPolicyHashBlocksConsumptionConsistency() {
        GoldenChainPermissionConsistencyDecision decision = service.evaluate(
            List.of(
                snapshot(GoldenChainConsumptionSurface.ASSET_PORTAL, true, "policy-a", "rls-a", "mask-a", false, false),
                snapshot(GoldenChainConsumptionSurface.METRICS, true, "policy-b", "rls-a", "mask-a", false, false)
            )
        );

        assertThat(decision.consistent()).isFalse();
        assertThat(decision.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(decision.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_PERMISSION);
        assertThat(decision.stageSnapshot().blockerReason()).contains("policy hash");
    }

    @Test
    void deniedPermissionDoesNotLeakAssetDetails() {
        GoldenChainPermissionConsistencyDecision decision = service.evaluate(
            List.of(snapshot(GoldenChainConsumptionSurface.API_SERVICE, false, "policy-a", "rls-a", "mask-a", false, false))
        );

        assertThat(decision.consistent()).isFalse();
        assertThat(decision.safeMessage()).isEqualTo("无权限访问该资产");
        assertThat(decision.safeMessage()).doesNotContain("platform:asset-123");
        assertThat(decision.stageSnapshot().blockerReason()).isEqualTo("无权限访问该资产");
    }

    @Test
    void legacyLocalFallbackIsBlockedUnlessBreakGlassIsExplicit() {
        GoldenChainPermissionConsistencyDecision decision = service.evaluate(
            List.of(snapshot(GoldenChainConsumptionSurface.SCREEN, true, "policy-a", "rls-a", "mask-a", true, false))
        );

        assertThat(decision.consistent()).isFalse();
        assertThat(decision.stageSnapshot().blockerReason()).contains("legacy local fallback");

        GoldenChainPermissionConsistencyDecision breakGlassDecision = service.evaluate(
            List.of(snapshot(GoldenChainConsumptionSurface.SCREEN, true, "policy-a", "rls-a", "mask-a", true, true))
        );

        assertThat(breakGlassDecision.consistent()).isTrue();
        assertThat(breakGlassDecision.warnings()).anyMatch(warning -> warning.contains("break-glass"));
    }

    private GoldenChainPermissionSnapshot snapshot(
        GoldenChainConsumptionSurface surface,
        boolean allowed,
        String policyHash,
        String rlsHash,
        String maskingHash,
        boolean localFallbackUsed,
        boolean breakGlass
    ) {
        return new GoldenChainPermissionSnapshot(
            surface,
            "platform:asset-123",
            "user:alice",
            allowed,
            policyHash,
            rlsHash,
            maskingHash,
            localFallbackUsed,
            breakGlass
        );
    }
}
