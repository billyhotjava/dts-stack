package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityDashboardAuthorizationTest {

    private static final UUID READABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000091");
    private static final UUID HIDDEN_ID = UUID.fromString("40000000-0000-0000-0000-000000000092");

    @Test
    void excludesRulesBindingsAndRunsForUnreadableDatasets() {
        GovRuleRepository ruleRepository = mock(GovRuleRepository.class);
        GovRuleBindingRepository bindingRepository = mock(GovRuleBindingRepository.class);
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);

        CatalogDataset readable = dataset(READABLE_ID);
        CatalogDataset hidden = dataset(HIDDEN_ID);
        GovRule readableRule = rule("readable-rule", READABLE_ID);
        GovRule hiddenRule = rule("hidden-rule", HIDDEN_ID);
        GovRuleBinding readableBinding = binding(READABLE_ID, "readable-table");
        GovRuleBinding hiddenBinding = binding(HIDDEN_ID, "hidden-table");
        GovQualityRun readableRun = run(readableRule, readableBinding, READABLE_ID);
        GovQualityRun hiddenRun = run(hiddenRule, hiddenBinding, HIDDEN_ID);
        readableRun.setFailingRowCount(12);
        hiddenRun.setFailingRowCount(99);

        when(datasetRepository.findAll()).thenReturn(List.of(readable, hidden));
        when(readGuard.readableDatasetIds(List.of(readable, hidden), "dept-a")).thenReturn(Set.of(READABLE_ID));
        when(ruleRepository.findAll()).thenReturn(List.of(readableRule, hiddenRule));
        when(bindingRepository.findAll()).thenReturn(List.of(readableBinding, hiddenBinding));
        when(runRepository.findByCreatedDateAfterOrderByCreatedDateAsc(org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(readableRun, hiddenRun));
        when(runRepository.findTop100ByStatusOrderByCreatedDateDesc("FAILED"))
            .thenReturn(List.of(readableRun, hiddenRun));

        QualityDashboardService service = new QualityDashboardService(
            ruleRepository,
            bindingRepository,
            runRepository,
            datasetRepository,
            readGuard
        );

        var dashboard = service.getDashboard("dept-a");

        assertThat(dashboard.ruleCount()).isEqualTo(1);
        assertThat(dashboard.coveredDatasets()).isEqualTo(1);
        assertThat(dashboard.totalDatasets()).isEqualTo(1);
        assertThat(dashboard.todayFailed()).isEqualTo(1);
        assertThat(dashboard.pendingFixRows()).isEqualTo(12);
        assertThat(dashboard.topFailingDatasets()).singleElement().satisfies(dataset ->
            assertThat(dataset.name()).isEqualTo("readable-table")
        );
        assertThat(dashboard.topFailingDatasets().get(0).failingRows()).isEqualTo(12);
        assertThat(dashboard.recentFailedRuns()).singleElement().satisfies(run -> {
            assertThat(run.ruleName()).isEqualTo("readable-rule");
            assertThat(run.dataset()).isEqualTo("readable-table");
        });
    }

    private static CatalogDataset dataset(UUID id) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        return dataset;
    }

    private static GovRule rule(String name, UUID datasetId) {
        GovRule rule = new GovRule();
        rule.setName(name);
        rule.setDatasetId(datasetId);
        rule.setEnabled(true);
        return rule;
    }

    private static GovRuleBinding binding(UUID datasetId, String alias) {
        GovRuleBinding binding = new GovRuleBinding();
        binding.setDatasetId(datasetId);
        binding.setDatasetAlias(alias);
        return binding;
    }

    private static GovQualityRun run(GovRule rule, GovRuleBinding binding, UUID datasetId) {
        GovQualityRun run = new GovQualityRun();
        run.setRule(rule);
        run.setBinding(binding);
        run.setDatasetId(datasetId);
        run.setStatus("FAILED");
        run.setCreatedDate(Instant.now());
        run.setFinishedAt(Instant.now());
        return run;
    }
}
