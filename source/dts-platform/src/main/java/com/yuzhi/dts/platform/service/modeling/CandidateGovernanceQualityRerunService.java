package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.GovernanceQualityRerunPort.QualityRunRef;
import com.yuzhi.dts.platform.service.modeling.GovernanceQualityRerunPort.RerunReceipt;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plan-scoped command owner for immutable governance-quality reruns. */
@Service
public class CandidateGovernanceQualityRerunService {

    private static final Set<DeliveryStatus> RERUNNABLE_CANDIDATE_STATES = Set.of(
        DeliveryStatus.BUILT,
        DeliveryStatus.QUALITY_RUNNING
    );
    private static final Set<String> RERUNNABLE_VIOLATIONS = Set.of("MISSING", "FAILED", "ERROR", "EXPIRED");

    private final ModelReleaseCandidateRepository candidates;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver duties;
    private final CandidateGovernanceQualityEvidenceService evidence;
    private final GovernanceQualityRerunPort reruns;

    public CandidateGovernanceQualityRerunService(
        ModelReleaseCandidateRepository candidates,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties,
        CandidateGovernanceQualityEvidenceService evidence,
        GovernanceQualityRerunPort reruns
    ) {
        this.candidates = candidates;
        this.planAccess = planAccess;
        this.duties = duties;
        this.evidence = evidence;
        this.reruns = reruns;
    }

    @Transactional
    public RerunResult rerun(
        String tenantId,
        String actorId,
        UUID planId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String activeDepartmentId
    ) {
        String tenant = requiredText(tenantId, "tenantId");
        String actor = requiredText(actorId, "actorId");
        String key = requiredText(idempotencyKey, "idempotencyKey");
        requireMaintainer(tenant, actor, planId);
        candidates.lockPlanForCandidate(tenant, planId);
        CandidateView candidate = candidates.find(tenant, candidateId).orElseThrow(() -> notFound(candidateId));
        if (!planId.equals(candidate.planId())) throw notFound(candidateId);

        planAccess.requireOperation(tenant, candidate.entries().stream().map(entry -> entry.modelSpecId()).toList(), actor);
        RerunReceipt replay = reruns.findReplay(candidate.id(), key).orElse(null);
        if (replay != null) return result(candidate.id(), replay);
        if (candidate.version() != expectedVersion) {
            throw new ModelReleaseCandidateException(
                ModelReleaseCandidateContract.VERSION_CONFLICT_ERROR_CODE,
                "Release candidate changed before governance quality rerun",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "expectedVersion", expectedVersion, "currentVersion", candidate.version())
            );
        }
        if (!RERUNNABLE_CANDIDATE_STATES.contains(candidate.status())) {
            throw new ModelReleaseCandidateException(
                "MODEL_SPEC_GOVERNANCE_QUALITY_RERUN_STATE_INVALID",
                "Governance quality can only be rerun after a successful physical build",
                Kind.CONFLICT,
                Map.of("candidateId", candidate.id(), "status", candidate.status())
            );
        }

        GovernanceQualitySummaryView current = evidence.evaluateLive(candidate);
        List<QualityEvidence> eligible = current
            .evidence()
            .stream()
            .filter(CandidateGovernanceQualityRerunService::rerunnable)
            .toList();
        if (eligible.isEmpty() && current.passed()) {
            return result(candidate.id(), new RerunReceipt(false, List.of()));
        }
        if (eligible.isEmpty()) {
            throw new ModelReleaseCandidateException(
                "MODEL_SPEC_GOVERNANCE_QUALITY_BINDING_REQUIRED",
                "当前资产没有可启动的已发布质量规则，请在“配置质量规则”中检查规则发布状态和资产关联后重试",
                Kind.UNPROCESSABLE,
                Map.of("candidateId", candidate.id(), "governanceQualityState", current.state())
            );
        }
        return result(candidate.id(), reruns.rerun(candidate.id(), key, actor, activeDepartmentId, eligible));
    }

    private void requireMaintainer(String tenantId, String actorId, UUID planId) {
        Set<DeliveryActorRole> current = duties.currentDuties();
        boolean maintainer = current != null && current.contains(DeliveryActorRole.MODEL_MAINTAINER);
        if (planId == null || !maintainer || !planAccess.canMaintain(tenantId, planId, actorId)) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_PLAN_FORBIDDEN",
                "Warehouse plan is not available for this release duty",
                Kind.FORBIDDEN
            );
        }
    }

    private static boolean rerunnable(QualityEvidence item) {
        return item != null &&
            item.ruleId() != null &&
            item.ruleVersionId() != null &&
            item.bindingId() != null &&
            item.violations().stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(RERUNNABLE_VIOLATIONS::contains) &&
            item.violations().stream().map(value -> value.toUpperCase(Locale.ROOT)).noneMatch(Set.of("ASSET_MISMATCH", "VERSION_MISMATCH", "BINDING_MISMATCH")::contains);
    }

    private static RerunResult result(UUID candidateId, RerunReceipt receipt) {
        return new RerunResult(candidateId, receipt.replayed(), receipt.runs());
    }

    private static ModelReleaseCandidateException notFound(UUID candidateId) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CANDIDATE_NOT_FOUND",
            "Release candidate was not found",
            Kind.NOT_FOUND,
            Map.of("candidateId", candidateId == null ? "missing" : candidateId)
        );
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_REQUEST_INVALID",
                name + " is required",
                Kind.BAD_REQUEST
            );
        }
        return value.trim();
    }

    public record RerunResult(UUID candidateId, boolean replayed, List<QualityRunRef> runs) {
        public RerunResult {
            if (candidateId == null) throw new IllegalArgumentException("candidateId is required");
            runs = List.copyOf(runs == null ? List.of() : runs);
        }
    }
}
