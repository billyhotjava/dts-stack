package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainConsumptionSurface;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyDecision;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyService;
import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionSnapshot;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class GoldenChainConsumptionPermissionViewService {

    private static final String OWNER = "consumption-permission";
    private static final List<GoldenChainConsumptionSurface> REQUIRED_SURFACES = List.copyOf(
        Arrays.asList(GoldenChainConsumptionSurface.values())
    );

    private final GoldenChainPermissionConsistencyService consistencyService = new GoldenChainPermissionConsistencyService();

    public GoldenChainConsumptionPermissionView buildView(List<GoldenChainPermissionSnapshot> snapshots) {
        List<GoldenChainPermissionSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (safeSnapshots.isEmpty()) {
            return blocked(
                "",
                OWNER,
                List.of(),
                List.of(),
                REQUIRED_SURFACES,
                null,
                null,
                null,
                List.of("缺少权限快照"),
                "缺少权限快照"
            );
        }

        GoldenChainPermissionSnapshot first = safeSnapshots.get(0);
        List<GoldenChainConsumptionSurface> allowedSurfaces = safeSnapshots
            .stream()
            .filter(GoldenChainPermissionSnapshot::allowed)
            .map(GoldenChainPermissionSnapshot::surface)
            .toList();
        List<GoldenChainConsumptionSurface> deniedSurfaces = safeSnapshots
            .stream()
            .filter(snapshot -> !snapshot.allowed())
            .map(GoldenChainPermissionSnapshot::surface)
            .toList();
        List<GoldenChainConsumptionSurface> missingSurfaces = missingSurfaces(safeSnapshots);

        if (!deniedSurfaces.isEmpty()) {
            return blocked(
                first.assetKey(),
                first.userRef(),
                allowedSurfaces,
                deniedSurfaces,
                missingSurfaces,
                first.platformPolicyHash(),
                first.rlsHash(),
                first.maskingHash(),
                List.of("无权限访问该资产"),
                "无权限访问该资产"
            );
        }
        if (!missingSurfaces.isEmpty()) {
            List<String> blockers = missingSurfaces.stream().map(surface -> "缺少 " + surface + " 权限快照").toList();
            return blocked(
                first.assetKey(),
                first.userRef(),
                allowedSurfaces,
                List.of(),
                missingSurfaces,
                first.platformPolicyHash(),
                first.rlsHash(),
                first.maskingHash(),
                blockers,
                String.join("；", blockers)
            );
        }

        GoldenChainPermissionConsistencyDecision decision = consistencyService.evaluate(safeSnapshots);
        if (!decision.consistent()) {
            String safeMessage = hasText(decision.safeMessage()) ? decision.safeMessage() : String.join("；", decision.blockers());
            return blocked(
                first.assetKey(),
                first.userRef(),
                allowedSurfaces,
                List.of(),
                List.of(),
                first.platformPolicyHash(),
                first.rlsHash(),
                first.maskingHash(),
                decision.blockers(),
                safeMessage
            );
        }

        return new GoldenChainConsumptionPermissionView(
            true,
            first.assetKey(),
            first.userRef(),
            allowedSurfaces,
            List.of(),
            List.of(),
            first.platformPolicyHash(),
            first.rlsHash(),
            first.maskingHash(),
            List.of(),
            "权限一致",
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.CONSUMABLE,
                first.userRef(),
                "consumption-permission://%s/%s/%s".formatted(first.assetKey(), first.userRef(), first.platformPolicyHash())
            )
        );
    }

    private GoldenChainConsumptionPermissionView blocked(
        String assetKey,
        String userRef,
        List<GoldenChainConsumptionSurface> allowedSurfaces,
        List<GoldenChainConsumptionSurface> deniedSurfaces,
        List<GoldenChainConsumptionSurface> missingSurfaces,
        String platformPolicyHash,
        String rlsHash,
        String maskingHash,
        List<String> blockers,
        String safeMessage
    ) {
        String owner = hasText(userRef) ? userRef : OWNER;
        return new GoldenChainConsumptionPermissionView(
            false,
            assetKey,
            userRef,
            allowedSurfaces,
            deniedSurfaces,
            missingSurfaces,
            platformPolicyHash,
            rlsHash,
            maskingHash,
            blockers,
            safeMessage,
            GoldenChainStageSnapshot.blocked(
                GoldenChainStage.CONSUMABLE,
                owner,
                GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                safeMessage
            )
        );
    }

    private List<GoldenChainConsumptionSurface> missingSurfaces(List<GoldenChainPermissionSnapshot> snapshots) {
        Set<GoldenChainConsumptionSurface> present = snapshots
            .stream()
            .map(GoldenChainPermissionSnapshot::surface)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return REQUIRED_SURFACES.stream().filter(surface -> !present.contains(surface)).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
