package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateQualityRuleContextServiceTest {

    @Test
    void returnsThePersistedCatalogIdentityInsteadOfConstructingOneFromThePhysicalName() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID catalogDatasetId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        CandidateView candidate = mock(CandidateView.class);
        CandidatePublicationEvidenceRepository evidence = mock(CandidatePublicationEvidenceRepository.class);
        ModelExecutionTargetCatalogResolver targets = mock(ModelExecutionTargetCatalogResolver.class);
        CatalogDatasetRepository datasets = mock(CatalogDatasetRepository.class);
        CandidatePublicationRepository publications = mock(CandidatePublicationRepository.class);
        DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        QualityRuleService rules = mock(QualityRuleService.class);
        CandidateGovernanceQualityEvidenceService governance = mock(CandidateGovernanceQualityEvidenceService.class);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(catalogDatasetId);
        dataset.setSourceId(sourceId);
        dataset.setHiveDatabase("warehouse");
        dataset.setHiveTable("orders");

        when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
        when(candidate.id()).thenReturn(UUID.fromString("30000000-0000-0000-0000-000000000003"));
        when(candidate.version()).thenReturn(4);
        when(targets.resolve(candidate)).thenReturn(new ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget("warehouse", sourceId, "postgres"));
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(physical()));
        when(publications.findRegisteredQualityDataset(sourceId, "warehouse", "orders")).thenReturn(Optional.of(catalogDatasetId));
        when(datasets.findById(catalogDatasetId)).thenReturn(Optional.of(dataset));
        when(defaultLake.currentDefaultLakeSourceId()).thenReturn(Optional.of(sourceId));
        when(rules.findByDataset(eq(catalogDatasetId), any())).thenReturn(List.of());
        when(governance.evaluateLive(candidate)).thenReturn(new GovernanceQualitySummaryView(true, EvidenceState.FAILED, "MODEL_SPEC_GOVERNANCE_QUALITY_MISSING", "缺少质量规则", 60, List.of()));

        var context = new CandidateQualityRuleContextService(evidence, targets, datasets, publications, defaultLake, rules, governance)
            .context(candidate, "dept-a");

        assertThat(context.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.datasetId()).isEqualTo(catalogDatasetId);
            assertThat(asset.assetKey()).isEqualTo(sourceId + ":warehouse.orders");
            assertThat(asset.configurable()).isTrue();
        });
        assertThat(context.primaryAction().code()).isEqualTo("CONFIGURE_QUALITY_RULES");
    }

    private static PublicationEntryEvidence physical() {
        return new PublicationEntryEvidence(
            UUID.fromString("40000000-0000-0000-0000-000000000004"),
            UUID.fromString("50000000-0000-0000-0000-000000000005"),
            3,
            "a".repeat(64),
            1,
            "b".repeat(64),
            "model.orders",
            "orders",
            "c".repeat(64),
            "d".repeat(64),
            UUID.fromString("60000000-0000-0000-0000-000000000006"),
            UUID.fromString("70000000-0000-0000-0000-000000000007"),
            UUID.fromString("80000000-0000-0000-0000-000000000008"),
            "postgres",
            "warehouse",
            "warehouse",
            "orders",
            ExpectedRelationType.TABLE,
            List.of(new PhysicalColumn(1, "id", "uuid", false)),
            "e".repeat(64),
            Instant.parse("2026-09-07T00:00:00Z")
        );
    }
}
