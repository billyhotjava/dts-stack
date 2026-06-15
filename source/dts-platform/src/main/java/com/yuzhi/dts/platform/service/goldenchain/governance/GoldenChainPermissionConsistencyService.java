package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoldenChainPermissionConsistencyService {

    private static final String PERMISSION_OWNER = "permission-gate";
    private static final String DENIED_MESSAGE = "无权限访问该资产";

    public GoldenChainPermissionConsistencyDecision evaluate(List<GoldenChainPermissionSnapshot> snapshots) {
        List<GoldenChainPermissionSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (safeSnapshots.isEmpty()) {
            return blocked("", PERMISSION_OWNER, List.of("缺少权限快照"), List.of(), "权限快照未就绪");
        }

        GoldenChainPermissionSnapshot first = safeSnapshots.get(0);
        if (safeSnapshots.stream().anyMatch(snapshot -> !snapshot.allowed())) {
            return blocked(first.assetKey(), first.userRef(), List.of(DENIED_MESSAGE), List.of(), DENIED_MESSAGE);
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        collectHashBlocker(blockers, "policy hash", safeSnapshots.stream().map(GoldenChainPermissionSnapshot::platformPolicyHash).toList());
        collectHashBlocker(blockers, "RLS hash", safeSnapshots.stream().map(GoldenChainPermissionSnapshot::rlsHash).toList());
        collectHashBlocker(blockers, "masking hash", safeSnapshots.stream().map(GoldenChainPermissionSnapshot::maskingHash).toList());
        collectHashBlocker(blockers, "asset key", safeSnapshots.stream().map(GoldenChainPermissionSnapshot::assetKey).toList());
        collectHashBlocker(blockers, "user ref", safeSnapshots.stream().map(GoldenChainPermissionSnapshot::userRef).toList());
        for (GoldenChainPermissionSnapshot snapshot : safeSnapshots) {
            if (snapshot.localFallbackUsed() && !snapshot.breakGlass()) {
                blockers.add(snapshot.surface() + " 使用 legacy local fallback，必须切换到 platform 权限事实源");
            } else if (snapshot.localFallbackUsed()) {
                warnings.add(snapshot.surface() + " 使用 break-glass fallback，需审计复核");
            }
        }
        if (!blockers.isEmpty()) {
            return blocked(first.assetKey(), first.userRef(), blockers, warnings, String.join("；", blockers));
        }

        return new GoldenChainPermissionConsistencyDecision(
            true,
            first.assetKey(),
            first.userRef(),
            List.of(),
            warnings,
            "权限一致",
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.RELEASE_READY,
                first.userRef(),
                "permission-consistency://%s/%s/%s".formatted(first.assetKey(), first.userRef(), first.platformPolicyHash())
            )
        );
    }

    private GoldenChainPermissionConsistencyDecision blocked(
        String assetKey,
        String owner,
        List<String> blockers,
        List<String> warnings,
        String safeMessage
    ) {
        return new GoldenChainPermissionConsistencyDecision(
            false,
            assetKey,
            owner,
            blockers,
            warnings,
            safeMessage,
            GoldenChainStageSnapshot.blocked(
                GoldenChainStage.RELEASE_READY,
                hasText(owner) ? owner : PERMISSION_OWNER,
                GoldenChainBlockerCode.BLOCKED_PERMISSION,
                safeMessage
            )
        );
    }

    private void collectHashBlocker(List<String> blockers, String label, List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (hasText(value)) {
                distinct.add(value);
            }
        }
        if (distinct.isEmpty()) {
            blockers.add("缺少 " + label);
        } else if (distinct.size() > 1) {
            blockers.add(label + " 不一致");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
