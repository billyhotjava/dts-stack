package com.yuzhi.dts.platform.service.goldenchain.governance;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GoldenChainGovernanceGateService {

    private static final String UNASSIGNED_OWNER = "待分配";

    public GoldenChainGovernanceGateDecision evaluate(GoldenChainGovernanceGateRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<String> blockers = collectBlockers(request);
        String owner = hasText(request.owner()) ? request.owner() : UNASSIGNED_OWNER;
        if (!blockers.isEmpty()) {
            return new GoldenChainGovernanceGateDecision(
                false,
                request.assetKey(),
                blockers,
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.GOVERNANCE_READY,
                    owner,
                    GoldenChainBlockerCode.BLOCKED_GOVERNANCE,
                    String.join("；", blockers)
                )
            );
        }
        return new GoldenChainGovernanceGateDecision(
            true,
            request.assetKey(),
            List.of(),
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.GOVERNANCE_READY,
                owner,
                "governance-gate://%s".formatted(request.assetKey())
            )
        );
    }

    private List<String> collectBlockers(GoldenChainGovernanceGateRequest request) {
        List<String> blockers = new ArrayList<>();
        if (!hasText(request.owner())) {
            blockers.add("缺少 owner");
        }
        if (!hasText(request.classification())) {
            blockers.add("缺少分级分类");
        }
        if (!request.qualityRulesPresent()) {
            blockers.add("缺少质量规则");
        }
        if (!request.qualityPassed()) {
            blockers.add("质量结果未通过");
        }
        if (request.assetType() == GoldenChainGovernedAssetType.DWD) {
            if (!hasText(request.primaryKey())) {
                blockers.add("DWD 缺少主键");
            }
            if (!request.standardMapped()) {
                blockers.add("DWD 缺少标准码映射");
            }
        }
        if (request.assetType() == GoldenChainGovernedAssetType.DWS || request.assetType() == GoldenChainGovernedAssetType.ADS) {
            if (!hasText(request.grain())) {
                blockers.add("DWS/ADS 缺少粒度");
            }
            if (!request.metricDefinitionReady()) {
                blockers.add("DWS/ADS 缺少指标口径");
            }
        }
        return blockers;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
