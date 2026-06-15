package com.yuzhi.dts.platform.service.goldenchain.modeling;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GoldenChainModelReleaseGateService {

    private static final String UNASSIGNED_OWNER = "待分配";

    public GoldenChainModelReleaseDecision evaluate(GoldenChainModelReleaseRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<String> dbtIssues = collectDbtIssues(request.dbtEvidence());
        List<String> modelIssues = collectModelContractIssues(request);
        List<String> governanceIssues = collectGovernanceIssues(request.owner(), request.governanceSnapshot());
        List<String> warnings = new ArrayList<>();
        if (request.environment().isProd()) {
            modelIssues.addAll(dbtIssues);
        } else {
            warnings.addAll(dbtIssues);
        }

        String owner = hasText(request.owner()) ? request.owner() : UNASSIGNED_OWNER;
        if (!modelIssues.isEmpty()) {
            return blockedDecision(
                request,
                owner,
                modelIssues,
                warnings,
                GoldenChainStage.MODEL_READY,
                GoldenChainBlockerCode.BLOCKED_MODEL
            );
        }
        if (!governanceIssues.isEmpty()) {
            return blockedDecision(
                request,
                owner,
                governanceIssues,
                warnings,
                GoldenChainStage.GOVERNANCE_READY,
                GoldenChainBlockerCode.BLOCKED_GOVERNANCE
            );
        }
        return new GoldenChainModelReleaseDecision(
            true,
            request.modelName(),
            request.layer(),
            request.environment(),
            List.of(),
            warnings,
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.RELEASE_READY,
                owner,
                "model-release://%s/%s".formatted(request.environment().name().toLowerCase(), request.modelName())
            )
        );
    }

    private GoldenChainModelReleaseDecision blockedDecision(
        GoldenChainModelReleaseRequest request,
        String owner,
        List<String> blockers,
        List<String> warnings,
        GoldenChainStage stage,
        GoldenChainBlockerCode blockerCode
    ) {
        return new GoldenChainModelReleaseDecision(
            false,
            request.modelName(),
            request.layer(),
            request.environment(),
            blockers,
            warnings,
            GoldenChainStageSnapshot.blocked(stage, owner, blockerCode, String.join("；", blockers))
        );
    }

    private List<String> collectDbtIssues(GoldenChainDbtRunEvidence dbtEvidence) {
        List<String> issues = new ArrayList<>();
        if (dbtEvidence == null) {
            issues.add("缺少 dbt compile/test/build 运行证据");
            return issues;
        }
        if (!dbtEvidence.compilePassed() || !hasText(dbtEvidence.compileEvidenceRef())) {
            issues.add("dbt compile 未通过或缺少证据");
        }
        if (!dbtEvidence.testPassed() || !hasText(dbtEvidence.testEvidenceRef())) {
            issues.add("dbt test 未通过或缺少证据");
        }
        if (!dbtEvidence.buildPassed() || !hasText(dbtEvidence.buildEvidenceRef())) {
            issues.add("dbt build 未通过或缺少证据");
        }
        return issues;
    }

    private List<String> collectModelContractIssues(GoldenChainModelReleaseRequest request) {
        List<String> issues = new ArrayList<>();
        GoldenChainModelSemanticContract contract = request.semanticContract();
        if (request.layer() == GoldenChainModelLayer.STG) {
            issues.add("STG 只能作为内部建模层，不能直接发布");
        }
        if (contract == null) {
            issues.add("缺少模型语义契约");
            return issues;
        }
        if (request.layer() == GoldenChainModelLayer.DWD) {
            if (!hasText(contract.primaryKey())) {
                issues.add("DWD 发布缺少主键");
            }
            if (!contract.standardCodesMapped()) {
                issues.add("DWD 发布缺少标准码映射");
            }
        }
        if (request.layer() == GoldenChainModelLayer.DWS || request.layer() == GoldenChainModelLayer.ADS) {
            if (!hasText(contract.grain()) || !contract.grainConsistent()) {
                issues.add("DWS/ADS 发布粒度不一致");
            }
            if (!contract.permissionConsumable()) {
                issues.add("DWS/ADS 发布缺少可消费权限契约");
            }
        }
        return issues;
    }

    private List<String> collectGovernanceIssues(String owner, GoldenChainModelGovernanceSnapshot snapshot) {
        List<String> issues = new ArrayList<>();
        if (!hasText(owner)) {
            issues.add("缺少 owner");
        }
        if (snapshot == null) {
            issues.add("缺少治理快照");
            return issues;
        }
        if (!hasText(snapshot.evidenceRef())) {
            issues.add("缺少治理快照证据");
        }
        if (!snapshot.schemaContractPassed()) {
            issues.add("schema contract 未通过");
        }
        if (!snapshot.qualityPassed()) {
            issues.add("质量规则未通过");
        }
        if (!snapshot.lineageReady()) {
            issues.add("血缘证据未就绪");
        }
        if (!snapshot.classificationReady()) {
            issues.add("分级分类未就绪");
        }
        return issues;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
