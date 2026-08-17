package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.Policy;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidenceRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Resolves and enforces governance quality independently from engineering build/test status. */
@Service
public class CandidateGovernanceQualityEvidenceService {

    private static final Set<DeliveryStatus> EVIDENCE_READY_STATES = EnumSet.of(
        DeliveryStatus.BUILT,
        DeliveryStatus.QUALITY_RUNNING,
        DeliveryStatus.QUALITY_FAILED,
        DeliveryStatus.QUALITY_PASSED,
        DeliveryStatus.REVIEW_PENDING,
        DeliveryStatus.REJECTED,
        DeliveryStatus.APPROVED,
        DeliveryStatus.PUBLISHING,
        DeliveryStatus.PARTIAL,
        DeliveryStatus.PUBLISHED,
        DeliveryStatus.ROLLED_BACK
    );

    private final CandidatePublicationEvidenceRepository publicationEvidence;
    private final ModelExecutionTargetCatalogResolver targetResolver;
    private final QualityEvidencePort qualityEvidence;
    private final ModelGovernancePolicyPort governancePolicy;
    private final Clock clock;
    private final ModelReleaseCandidateRepository candidateRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public CandidateGovernanceQualityEvidenceService(
        CandidatePublicationEvidenceRepository publicationEvidence,
        ModelExecutionTargetCatalogResolver targetResolver,
        QualityEvidencePort qualityEvidence,
        ModelGovernancePolicyPort governancePolicy,
        ModelReleaseCandidateRepository candidateRepository,
        ObjectMapper objectMapper
    ) {
        this(
            publicationEvidence,
            targetResolver,
            qualityEvidence,
            governancePolicy,
            Clock.systemUTC(),
            candidateRepository,
            objectMapper
        );
    }

    CandidateGovernanceQualityEvidenceService(
        CandidatePublicationEvidenceRepository publicationEvidence,
        ModelExecutionTargetCatalogResolver targetResolver,
        QualityEvidencePort qualityEvidence,
        ModelGovernancePolicyPort governancePolicy,
        Clock clock
    ) {
        this(
            publicationEvidence,
            targetResolver,
            qualityEvidence,
            governancePolicy,
            clock,
            null,
            new ObjectMapper().findAndRegisterModules()
        );
    }

    CandidateGovernanceQualityEvidenceService(
        CandidatePublicationEvidenceRepository publicationEvidence,
        ModelExecutionTargetCatalogResolver targetResolver,
        QualityEvidencePort qualityEvidence,
        ModelGovernancePolicyPort governancePolicy,
        Clock clock,
        ModelReleaseCandidateRepository candidateRepository,
        ObjectMapper objectMapper
    ) {
        this.publicationEvidence = publicationEvidence;
        this.targetResolver = targetResolver;
        this.qualityEvidence = qualityEvidence;
        this.governancePolicy = governancePolicy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.candidateRepository = candidateRepository;
        this.objectMapper = objectMapper == null
            ? new ObjectMapper().findAndRegisterModules()
            : objectMapper;
    }

    public GovernanceQualitySummaryView evaluate(CandidateView candidate) {
        try {
            CandidateQualityEvidenceSnapshot pinned = pinnedSnapshot(candidate).orElse(null);
            if (pinned != null) return pinned.governanceSummary();
        } catch (ModelReleaseCandidateException invalidSnapshot) {
            return unavailable(
                true,
                invalidSnapshot.code(),
                invalidSnapshot.getMessage(),
                ModelGovernancePolicyPort.DEFAULT_QUALITY_EVIDENCE_MAX_AGE_SECONDS
            );
        }
        return evaluateLive(candidate);
    }

    /** Reads current governance ledgers; used only while producing a new immutable candidate snapshot. */
    public GovernanceQualitySummaryView evaluateLive(CandidateView candidate) {
        Policy policy = policy();
        boolean required = !policy.available() || policy.qualityGate() == QualityGate.BLOCKING;
        if (!policy.available()) {
            return unavailable(
                true,
                "MODEL_SPEC_GOVERNANCE_QUALITY_POLICY_UNAVAILABLE",
                "平台模型治理质量策略不可用，无法确认发布条件",
                policy.qualityEvidenceMaxAgeSeconds()
            );
        }
        if (candidate == null || !EVIDENCE_READY_STATES.contains(candidate.status())) {
            return unavailable(
                required,
                "MODEL_SPEC_GOVERNANCE_QUALITY_BUILD_REQUIRED",
                "完成物理构建后才能核验治理数据质量",
                policy.qualityEvidenceMaxAgeSeconds()
            );
        }
        try {
            ResolvedCatalogTarget target = targetResolver.resolve(candidate);
            List<PublicationEntryEvidence> observations = publicationEvidence.requireCurrent(candidate, false);
            Instant asOf = clock.instant();
            List<QualityEvidenceRequest> requests = observations
                .stream()
                .map(observation ->
                    new QualityEvidenceRequest(
                        CatalogAssetType.DATASET,
                        CatalogAssetKey.dataset(
                            target.sourceId(),
                            observation.databaseName(),
                            observation.schemaName(),
                            observation.identifier(),
                            observation.identifier()
                        ),
                        List.of(),
                        asOf,
                        policy.qualityEvidenceMaxAgeSeconds()
                    )
                )
                .toList();
            List<QualityEvidence> evidence = qualityEvidence.read(requests);
            if (evidence != null && !evidence.isEmpty() && evidence.stream().allMatch(QualityEvidence::passed)) {
                return new GovernanceQualitySummaryView(
                    required,
                    EvidenceState.PASSED,
                    null,
                    null,
                    policy.qualityEvidenceMaxAgeSeconds(),
                    evidence
                );
            }
            List<QualityEvidence> safeEvidence = evidence == null ? List.of() : List.copyOf(evidence);
            String violation = safeEvidence
                .stream()
                .flatMap(item -> item.violations().stream())
                .findFirst()
                .orElse("MISSING");
            EvidenceState state = "RUNNING".equals(violation)
                ? EvidenceState.RUNNING
                : "EXPIRED".equals(violation)
                    ? EvidenceState.STALE
                    : EvidenceState.FAILED;
            return new GovernanceQualitySummaryView(
                required,
                state,
                "MODEL_SPEC_GOVERNANCE_QUALITY_" + violation,
                violationMessage(violation),
                policy.qualityEvidenceMaxAgeSeconds(),
                safeEvidence
            );
        } catch (RuntimeException unavailable) {
            return unavailable(
                required,
                "MODEL_SPEC_GOVERNANCE_QUALITY_EVIDENCE_UNAVAILABLE",
                "治理数据质量证据暂不可读取，请检查物理资产与质量服务",
                policy.qualityEvidenceMaxAgeSeconds()
            );
        }
    }

    public CandidateQualityEvidenceSnapshot requirePublishableSnapshot(CandidateView candidate) {
        GovernanceQualitySummaryView summary = requirePublishable(candidate);
        return pinnedSnapshot(candidate).orElseGet(() -> CandidateQualityEvidenceSnapshot.captureLegacy(candidate, summary));
    }

    public GovernanceQualitySummaryView requirePublishable(CandidateView candidate) {
        GovernanceQualitySummaryView summary = evaluate(candidate);
        if (summary.required() && !summary.passed()) {
            throw new ModelReleaseCandidateException(
                summary.code() == null ? "MODEL_SPEC_GOVERNANCE_QUALITY_REQUIRED" : summary.code(),
                summary.message() == null ? "Governance quality evidence is required before publication" : summary.message(),
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "governanceQualityState",
                    summary.state(),
                    "evidenceCount",
                    summary.evidence().size()
                )
            );
        }
        return summary;
    }

    private Optional<CandidateQualityEvidenceSnapshot> pinnedSnapshot(CandidateView candidate) {
        if (
            candidateRepository == null ||
            candidate == null ||
            !Set.of(
                DeliveryStatus.QUALITY_PASSED,
                DeliveryStatus.REVIEW_PENDING,
                DeliveryStatus.REJECTED,
                DeliveryStatus.APPROVED,
                DeliveryStatus.PUBLISHING,
                DeliveryStatus.PARTIAL,
                DeliveryStatus.PUBLISHED,
                DeliveryStatus.ROLLED_BACK
            ).contains(candidate.status())
        ) {
            return Optional.empty();
        }
        try {
            return candidateRepository
                .findLatestQualityEvidenceSnapshot(candidate.tenantId(), candidate.id())
                .flatMap(event -> CandidateQualityEvidenceSnapshot.read(event.responseSnapshot(), objectMapper))
                .filter(snapshot -> candidate.id().equals(snapshot.candidateId()));
        } catch (RuntimeException invalidSnapshot) {
            Policy policy = policy();
            boolean required = !policy.available() || policy.qualityGate() == QualityGate.BLOCKING;
            throw new ModelReleaseCandidateException(
                "MODEL_SPEC_GOVERNANCE_QUALITY_SNAPSHOT_INVALID",
                "候选版本的治理质量证据快照损坏，不能作为发布依据",
                required ? Kind.UNPROCESSABLE : Kind.CONFLICT,
                Map.of("candidateId", candidate.id())
            );
        }
    }

    private Policy policy() {
        try {
            Policy policy = governancePolicy.resolve();
            return policy == null
                ? Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE")
                : policy;
        } catch (RuntimeException unavailable) {
            return Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE");
        }
    }

    private static GovernanceQualitySummaryView unavailable(
        boolean required,
        String code,
        String message,
        long maxAgeSeconds
    ) {
        return new GovernanceQualitySummaryView(
            required,
            EvidenceState.UNAVAILABLE,
            code,
            message,
            maxAgeSeconds,
            List.of()
        );
    }

    private static String violationMessage(String violation) {
        return switch (violation) {
            case "RUNNING" -> "治理数据质量检查仍在运行，完成前不能发布";
            case "FAILED" -> "治理数据质量检查未通过，请修复数据后重新运行";
            case "ERROR" -> "治理数据质量检查发生错误，请查看运行详情";
            case "EXPIRED" -> "治理数据质量证据已过期，请重新运行";
            case "ASSET_MISMATCH" -> "质量证据不属于当前候选物理资产";
            case "VERSION_MISMATCH" -> "质量证据未使用候选要求的规则版本";
            case "BINDING_MISMATCH" -> "质量证据与当前规则绑定不一致";
            default -> "当前物理资产缺少已发布规则的治理质量运行证据";
        };
    }
}
