package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only quality-rule projection for the exact verified outputs of one release candidate. */
@Service
public class CandidateQualityRuleContextService {

    private static final Set<String> RERUNNABLE_VIOLATIONS = Set.of("MISSING", "FAILED", "ERROR", "EXPIRED");

    private final CandidatePublicationEvidenceRepository evidence;
    private final ModelExecutionTargetCatalogResolver targets;
    private final CatalogDatasetRepository datasets;
    private final CandidatePublicationRepository publications;
    private final DefaultLakeDatasetGuard defaultLake;
    private final QualityRuleService rules;
    private final CandidateGovernanceQualityEvidenceService governanceQuality;

    public CandidateQualityRuleContextService(
        CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets,
        CatalogDatasetRepository datasets,
        CandidatePublicationRepository publications,
        DefaultLakeDatasetGuard defaultLake,
        QualityRuleService rules,
        CandidateGovernanceQualityEvidenceService governanceQuality
    ) {
        this.evidence = Objects.requireNonNull(evidence, "evidence is required");
        this.targets = Objects.requireNonNull(targets, "targets are required");
        this.datasets = Objects.requireNonNull(datasets, "datasets is required");
        this.publications = Objects.requireNonNull(publications, "publications is required");
        this.defaultLake = Objects.requireNonNull(defaultLake, "defaultLake is required");
        this.rules = Objects.requireNonNull(rules, "rules is required");
        this.governanceQuality = Objects.requireNonNull(governanceQuality, "governanceQuality is required");
    }

    @Transactional(readOnly = true)
    public CandidateQualityContextView context(CandidateView candidate, String activeDeptHeader) {
        Objects.requireNonNull(candidate, "candidate is required");
        try {
            UUID defaultLakeSourceId = defaultLake.currentDefaultLakeSourceId().orElse(null);
            ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget target = targets.resolve(candidate);
            List<QualityAssetView> assets = evidence.requireCurrent(candidate, false).stream()
                .map(item -> asset(target, item, defaultLakeSourceId, activeDeptHeader))
                .toList();
            GovernanceQualitySummaryView summary = governanceQuality.evaluateLive(candidate);
            return new CandidateQualityContextView(
                candidate.id(),
                candidate.version(),
                candidate.status(),
                assets,
                decision(candidate, assets, summary),
                summary.code(),
                summary.message()
            );
        } catch (RuntimeException unavailable) {
            return new CandidateQualityContextView(
                candidate.id(),
                candidate.version(),
                candidate.status(),
                List.of(),
                QualityActionView.none("MODEL_SPEC_GOVERNANCE_QUALITY_CONTEXT_UNAVAILABLE"),
                "MODEL_SPEC_GOVERNANCE_QUALITY_CONTEXT_UNAVAILABLE",
                "治理质量目标暂不可读取，请重试物化构建或检查执行目标配置"
            );
        }
    }

    private QualityAssetView asset(
        ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget target,
        PublicationEntryEvidence physical,
        UUID defaultLakeSourceId,
        String activeDeptHeader
    ) {
        UUID datasetId = publications
            .findRegisteredQualityDataset(target.sourceId(), physical.schemaName(), physical.identifier())
            .orElse(null);
        CatalogDataset dataset = datasetId == null ? null : datasets.findById(datasetId).orElse(null);
        boolean locatorMatches = dataset != null &&
            target.sourceId().equals(dataset.getSourceId()) &&
            equalsIgnoreCase(physical.schemaName(), dataset.getHiveDatabase()) &&
            equalsIgnoreCase(physical.identifier(), dataset.getHiveTable());
        boolean configurable = locatorMatches && defaultLakeSourceId != null && defaultLakeSourceId.equals(dataset.getSourceId());
        List<QualityRuleDto> currentRules = locatorMatches ? rules.findByDataset(datasetId, activeDeptHeader) : List.of();
        return new QualityAssetView(
            physical.modelSpecId(),
            physical.modelRevision(),
            datasetId,
            target.sourceId() + ":" + physical.schemaName() + "." + physical.identifier(),
            physical.schemaName() + "." + physical.identifier(),
            configurable,
            locatorMatches
                ? configurable ? null : "QUALITY_DATASET_NOT_DEFAULT_LAKE"
                : "QUALITY_DATASET_REGISTRATION_MISSING",
            currentRules
        );
    }

    private static QualityActionView decision(
        CandidateView candidate,
        List<QualityAssetView> assets,
        GovernanceQualitySummaryView summary
    ) {
        boolean allAssetsConfigurable = !assets.isEmpty() && assets.stream().allMatch(QualityAssetView::configurable);
        boolean allConfigured = allAssetsConfigurable && assets.stream()
            .allMatch(asset -> asset.rules().stream().anyMatch(CandidateQualityRuleContextService::publishedBinding));
        if (candidate.status() == DeliveryStatus.BUILT && allAssetsConfigurable && !allConfigured) {
            return new QualityActionView("CONFIGURE_QUALITY_RULES", "MODEL_SPEC_GOVERNANCE_QUALITY_RULE_REQUIRED");
        }
        if (candidate.status() == DeliveryStatus.BUILT && allConfigured) {
            return new QualityActionView("RUN_QUALITY", null);
        }
        if (candidate.status() == DeliveryStatus.QUALITY_RUNNING && rerunnable(summary)) {
            return new QualityActionView("RERUN_GOVERNANCE_QUALITY", null);
        }
        return QualityActionView.none(summary == null ? "MODEL_SPEC_GOVERNANCE_QUALITY_UNAVAILABLE" : summary.code());
    }

    private static boolean publishedBinding(QualityRuleDto rule) {
        return rule != null && rule.getLatestVersion() != null && "PUBLISHED".equals(rule.getLatestVersion().getStatus());
    }

    private static boolean rerunnable(GovernanceQualitySummaryView summary) {
        return summary != null && summary.evidence().stream().anyMatch(item ->
            item != null && item.violations().stream().map(String::toUpperCase).anyMatch(RERUNNABLE_VIOLATIONS::contains) &&
                item.violations().stream().map(String::toUpperCase).noneMatch(Set.of("ASSET_MISMATCH", "VERSION_MISMATCH", "BINDING_MISMATCH")::contains)
        );
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    public record CandidateQualityContextView(
        UUID candidateId,
        int candidateVersion,
        DeliveryStatus status,
        List<QualityAssetView> assets,
        QualityActionView primaryAction,
        String governanceQualityCode,
        String governanceQualityMessage
    ) {
        public CandidateQualityContextView {
            assets = List.copyOf(assets == null ? List.of() : assets);
            primaryAction = primaryAction == null ? QualityActionView.none(null) : primaryAction;
        }
    }

    public record QualityAssetView(
        UUID modelSpecId,
        int modelRevision,
        UUID datasetId,
        String assetKey,
        String qualifiedName,
        boolean configurable,
        String configurationBlockerCode,
        List<QualityRuleDto> rules
    ) {
        public QualityAssetView {
            rules = List.copyOf(rules == null ? List.of() : rules);
        }
    }

    public record QualityActionView(String code, String reasonCode) {
        static QualityActionView none(String reasonCode) {
            return new QualityActionView("NONE", reasonCode);
        }
    }
}
