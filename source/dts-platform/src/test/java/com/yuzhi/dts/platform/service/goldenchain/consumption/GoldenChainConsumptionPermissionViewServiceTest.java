package com.yuzhi.dts.platform.service.goldenchain.consumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainConsumptionSurface;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainConsumptionPermissionViewServiceTest {

    private final GoldenChainConsumptionPermissionViewService service = new GoldenChainConsumptionPermissionViewService();

    @Test
    void unifiedViewRequiresAllConsumerSurfacesToUseSamePlatformSnapshot() {
        GoldenChainConsumptionPermissionView view = service.buildView(
            List.of(
                snapshot(GoldenChainConsumptionSurface.ASSET_PORTAL, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.METRICS, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.BI, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.SCREEN, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.API_SERVICE, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.DATA_PRODUCT, true, "policy-a", "rls-a", "mask-a")
            )
        );

        assertThat(view.consumable()).isTrue();
        assertThat(view.assetKey()).isEqualTo("asset:orders-day");
        assertThat(view.allowedSurfaces()).containsExactlyInAnyOrder(GoldenChainConsumptionSurface.values());
        assertThat(view.deniedSurfaces()).isEmpty();
        assertThat(view.missingSurfaces()).isEmpty();
        assertThat(view.platformPolicyHash()).isEqualTo("policy-a");
        assertThat(view.rlsHash()).isEqualTo("rls-a");
        assertThat(view.maskingHash()).isEqualTo("mask-a");
        assertThat(view.stageSnapshot().stage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(view.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    @Test
    void missingBiOrScreenSnapshotBlocksConsumptionInsteadOfFallingBackLocally() {
        GoldenChainConsumptionPermissionView view = service.buildView(
            List.of(
                snapshot(GoldenChainConsumptionSurface.ASSET_PORTAL, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.METRICS, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.BI, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.API_SERVICE, true, "policy-a", "rls-a", "mask-a"),
                snapshot(GoldenChainConsumptionSurface.DATA_PRODUCT, true, "policy-a", "rls-a", "mask-a")
            )
        );

        assertThat(view.consumable()).isFalse();
        assertThat(view.missingSurfaces()).containsExactly(GoldenChainConsumptionSurface.SCREEN);
        assertThat(view.blockers()).contains("缺少 SCREEN 权限快照");
        assertThat(view.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(view.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_CONSUMPTION);
    }

    @Test
    void deniedConsumerSurfaceUsesSafeMessageWithoutAssetDetails() {
        GoldenChainConsumptionPermissionView view = service.buildView(
            List.of(snapshot(GoldenChainConsumptionSurface.BI, false, "policy-a", "rls-a", "mask-a"))
        );

        assertThat(view.consumable()).isFalse();
        assertThat(view.deniedSurfaces()).containsExactly(GoldenChainConsumptionSurface.BI);
        assertThat(view.safeMessage()).isEqualTo("无权限访问该资产");
        assertThat(view.stageSnapshot().blockerReason()).isEqualTo("无权限访问该资产");
        assertThat(view.stageSnapshot().blockerReason()).doesNotContain("asset:orders-day");
    }

    private GoldenChainPermissionSnapshot snapshot(
        GoldenChainConsumptionSurface surface,
        boolean allowed,
        String policyHash,
        String rlsHash,
        String maskingHash
    ) {
        return new GoldenChainPermissionSnapshot(
            surface,
            "asset:orders-day",
            "user:alice",
            allowed,
            policyHash,
            rlsHash,
            maskingHash,
            false,
            false
        );
    }
}
