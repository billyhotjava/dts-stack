package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class QualityRunServicePinnedTriggerTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000010");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000010");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000010");
    private static final UUID RUN_ID = UUID.fromString("50000000-0000-0000-0000-000000000010");

    @Mock private GovRuleRepository ruleRepository;
    @Mock private GovRuleVersionRepository versionRepository;
    @Mock private GovRuleBindingRepository bindingRepository;
    @Mock private GovQualityRunRepository runRepository;
    @Mock private GovQualityMetricRepository metricRepository;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private Executor taskExecutor;
    @Mock private QualityDatasetStatementExecutor statementExecutor;
    @Mock private QualityAuditRecorder qualityAuditRecorder;
    @Mock private IssueTicketService issueTicketService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    @Mock private QualityDatasetReadGuard qualityDatasetReadGuard;

    private QualityRunService service;

    @BeforeEach
    void setUp() {
        service = new QualityRunService(
            ruleRepository,
            versionRepository,
            bindingRepository,
            runRepository,
            metricRepository,
            datasetRepository,
            taskExecutor,
            statementExecutor,
            qualityAuditRecorder,
            issueTicketService,
            new ObjectMapper(),
            new GovernanceProperties(),
            transactionManager,
            defaultLakeDatasetGuard,
            qualityDatasetReadGuard
        );
    }

    @Test
    void runsTheExplicitArchivedVersionAndPersistsTheModelingTriggerRef() {
        GovRule rule = rule();
        GovRuleVersion archived = version(rule, "ARCHIVED");
        GovRuleBinding binding = binding(archived);
        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(archived));
        when(bindingRepository.findById(BINDING_ID)).thenReturn(Optional.of(binding));
        when(runRepository.findByTriggerRefOrderByCreatedDateAsc("mcq:retry:0")).thenReturn(List.of());
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(RUN_ID);
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setBindingId(BINDING_ID);
        request.setTriggerType("MODEL_RELEASE");

        List<QualityRunDto> result = service.triggerPinnedAuthorized(
            request,
            VERSION_ID,
            "xiezm",
            "INST",
            "mcq:retry:0"
        );

        assertThat(result).singleElement().satisfies(run -> {
            assertThat(run.getId()).isEqualTo(RUN_ID);
            assertThat(run.getRuleVersionId()).isEqualTo(VERSION_ID);
            assertThat(run.getTriggerRef()).isEqualTo("mcq:retry:0");
        });
        ArgumentCaptor<GovQualityRun> saved = ArgumentCaptor.forClass(GovQualityRun.class);
        verify(runRepository).save(saved.capture());
        assertThat(saved.getValue().getRuleVersion().getStatus()).isEqualTo("ARCHIVED");
        verify(qualityDatasetReadGuard).requireReadable(DATASET_ID, "INST");
    }

    @Test
    void returnsTheOriginalRunForTheSameTriggerRef() {
        GovQualityRun existing = new GovQualityRun();
        existing.setId(RUN_ID);
        existing.setDatasetId(DATASET_ID);
        existing.setRule(rule());
        existing.setRuleVersion(version(existing.getRule(), "ARCHIVED"));
        existing.setBinding(binding(existing.getRuleVersion()));
        existing.setStatus("RUNNING");
        existing.setTriggerType("MODEL_RELEASE");
        existing.setTriggerRef("mcq:retry:0");
        when(runRepository.findByTriggerRefOrderByCreatedDateAsc("mcq:retry:0")).thenReturn(List.of(existing));
        when(metricRepository.findByRunId(RUN_ID)).thenReturn(List.of());

        List<QualityRunDto> result = service.triggerPinnedAuthorized(
            new QualityRunTriggerRequest(),
            VERSION_ID,
            "xiezm",
            "INST",
            "mcq:retry:0"
        );

        assertThat(result).singleElement().extracting(QualityRunDto::getId).isEqualTo(RUN_ID);
        verify(qualityDatasetReadGuard).requireReadable(DATASET_ID, "INST");
        verify(runRepository, never()).save(any());
        verify(ruleRepository, never()).findById(any());
    }

    private static GovRule rule() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("预算完整性");
        rule.setEnabled(Boolean.TRUE);
        return rule;
    }

    private static GovRuleVersion version(GovRule rule, String status) {
        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus(status);
        version.setDefinition("{\"sql\":\"select 1\"}");
        return version;
    }

    private static GovRuleBinding binding(GovRuleVersion version) {
        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        return binding;
    }
}
