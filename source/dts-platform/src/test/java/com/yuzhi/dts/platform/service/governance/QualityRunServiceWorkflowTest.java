package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
class QualityRunServiceWorkflowTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000102");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000102");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000102");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000102");
    private static final UUID WORKFLOW_ID = UUID.fromString("50000000-0000-0000-0000-000000000102");
    private static final UUID RUN_ID = UUID.fromString("60000000-0000-0000-0000-000000000102");

    @Mock private GovRuleRepository ruleRepository;
    @Mock private GovRuleVersionRepository versionRepository;
    @Mock private GovRuleBindingRepository bindingRepository;
    @Mock private GovQualityRunRepository runRepository;
    @Mock private GovQualityMetricRepository metricRepository;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private Executor taskExecutor;
    @Mock private QualityDatasetStatementExecutor statementExecutor;
    @Mock private QualityAuditRecorder auditRecorder;
    @Mock private IssueTicketService issueTicketService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    @Mock private QualityDatasetReadGuard readGuard;

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
            auditRecorder,
            issueTicketService,
            new ObjectMapper(),
            new GovernanceProperties(),
            transactionManager,
            defaultLakeDatasetGuard,
            readGuard
        );
    }

    @Test
    void workflowTriggerPersistsTheWorkflowIdentityWithoutChangingTheExecutionOwner() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setEnabled(true);
        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":\"select 1\"}");
        rule.setLatestVersion(version);
        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setDatasetId(DATASET_ID);
        binding.setRuleVersion(version);
        version.setBindings(new java.util.HashSet<>(List.of(binding)));
        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(RUN_ID);
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setDatasetId(DATASET_ID);
        request.setTriggerType("MANUAL");

        var result = service.triggerWorkflowAuthorized(
            request,
            "xiezm",
            "INST",
            WORKFLOW_ID,
            "quality-workflow:manual:102"
        );

        assertThat(result).singleElement().satisfies(run -> {
            assertThat(run.getId()).isEqualTo(RUN_ID);
            assertThat(run.getJobId()).isEqualTo(WORKFLOW_ID);
            assertThat(run.getTriggerRef()).isEqualTo("quality-workflow:manual:102");
        });
        ArgumentCaptor<GovQualityRun> saved = ArgumentCaptor.forClass(GovQualityRun.class);
        verify(runRepository).save(saved.capture());
        assertThat(saved.getValue().getJobId()).isEqualTo(WORKFLOW_ID);
        verify(readGuard).requireReadable(DATASET_ID, "INST");
    }
}
