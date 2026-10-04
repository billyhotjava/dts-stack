package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GoldenChainLineageGovernanceService {

    private static final String LINEAGE_OWNER = "lineage-governance";

    public GoldenChainLineageGovernanceDecision evaluate(GoldenChainLineageEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        List<String> diagnostics = collectDiagnostics(evidence);
        if (!diagnostics.isEmpty()) {
            return new GoldenChainLineageGovernanceDecision(
                false,
                GoldenChainGovernanceState.PENDING_GOVERNANCE,
                diagnostics,
                "补齐 source/target 映射、血缘证据和任务声明后重新导入血缘",
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.GOVERNANCE_READY,
                    LINEAGE_OWNER,
                    GoldenChainBlockerCode.BLOCKED_GOVERNANCE,
                    String.join("；", diagnostics)
                )
            );
        }
        return new GoldenChainLineageGovernanceDecision(
            true,
            GoldenChainGovernanceState.READY,
            List.of(),
            "血缘证据已就绪",
            GoldenChainStageSnapshot.ready(GoldenChainStage.GOVERNANCE_READY, LINEAGE_OWNER, evidence.evidenceRef())
        );
    }

    private List<String> collectDiagnostics(GoldenChainLineageEvidence evidence) {
        List<String> diagnostics = new ArrayList<>();
        if (!evidence.parsed()) {
            diagnostics.add(hasText(evidence.failureReason()) ? evidence.failureReason() : "血缘解析失败");
        }
        if (!hasText(evidence.sourceRef()) || !hasText(evidence.targetRef())) {
            diagnostics.add("不可解析 source/target，不能作为发布血缘证据");
        }
        if (!hasText(evidence.evidenceRef())) {
            diagnostics.add("缺少血缘证据");
        }
        return diagnostics;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
