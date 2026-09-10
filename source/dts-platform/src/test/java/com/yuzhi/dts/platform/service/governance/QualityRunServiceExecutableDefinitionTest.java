package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
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
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class QualityRunServiceExecutableDefinitionTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000010");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000010");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000010");

    @Mock
    private GovRuleRepository ruleRepository;

    @Mock
    private GovRuleVersionRepository versionRepository;

    @Mock
    private GovRuleBindingRepository bindingRepository;

    @Mock
    private GovQualityRunRepository runRepository;

    @Mock
    private GovQualityMetricRepository metricRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private Executor taskExecutor;

    @Mock
    private QualityDatasetStatementExecutor statementExecutor;

    @Mock
    private QualityAuditRecorder qualityAuditRecorder;

    @Mock
    private IssueTicketService issueTicketService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    @Mock
    private QualityDatasetReadGuard qualityDatasetReadGuard;

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
    void rejectsEmptyPublishedRuleBeforeCreatingAQualityRun() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("空规则");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        assertThatThrownBy(() -> service.trigger(request, "actor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
        verify(runRepository, never()).save(any());
    }

    @Test
    void rejectsDisabledRuleBeforeCreatingAQualityRun() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("已停用规则");
        rule.setEnabled(Boolean.FALSE);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        assertThatThrownBy(() -> service.trigger(request, "actor"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("已停用");
        verify(runRepository, never()).save(any());
    }

    @Test
    void humanTriggerCannotImpersonateTheSchedulerAuditActor() {
        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setTriggerType(" SCHEDULED ");

        assertThatThrownBy(() -> service.trigger(request, "alice", "D01"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不允许使用调度触发类型");

        verify(ruleRepository, never()).findById(any());
        verify(qualityAuditRecorder, never()).recordMachine(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void trustedTwoArgumentTriggerCannotImpersonateTheSchedulerAuditActor() {
        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setTriggerType("SCHEDULED");

        assertThatThrownBy(() -> service.trigger(request, "service:dts-ingestion"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不允许使用调度触发类型");

        verify(ruleRepository, never()).findById(any());
        verify(qualityAuditRecorder, never()).recordMachine(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scheduledEntryRejectsEveryNonScheduledTriggerType() {
        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setTriggerType("AUTO");

        assertThatThrownBy(() -> service.triggerScheduled(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("仅允许使用调度触发类型");

        verify(ruleRepository, never()).findById(any());
    }

    @Test
    void rejectsNullSqlInLegacyPublishedRuleBeforeCreatingAQualityRun() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("空规则");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":null}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        assertThatThrownBy(() -> service.trigger(request, "actor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
        verify(runRepository, never()).save(any());
    }

    @Test
    void keepsExecutablePublishedRulesOnTheNormalQueuedRunPath() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("有效规则");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":\"select 1\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(UUID.fromString("50000000-0000-0000-0000-000000000010"));
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("QUEUED");
        verify(runRepository).save(any(GovQualityRun.class));
    }

    @Test
    void fallsBackToSqlWhenStatementsMapIsPresentButEmpty() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("混合定义规则");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"statements\":{},\"sql\":\"select 1\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(UUID.fromString("50000000-0000-0000-0000-000000000011"));
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("QUEUED");
        verify(runRepository).save(any(GovQualityRun.class));
    }

    @Test
    void dryRunUsesTheBoundDatasetExecutorInsteadOfTheHiveOnlyExecutor() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("默认数仓非空检查");
        rule.setSeverity("MEDIUM");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":\"select id from ods_budget_v2 where project_no is null\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        GovQualityRun[] persistedRun = new GovQualityRun[1];
        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.fromString("50000000-0000-0000-0000-000000000012"));
            }
            persistedRun[0] = run;
            return run;
        });
        when(runRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(persistedRun[0]));
        when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(new StatementExecutionResult("sql", "select 1", StatementExecutionResult.Status.SUCCEEDED, "未发现异常数据")),
                25,
                0
            )
        );

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setDryRun(true);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(run.getRowsTotal()).isEqualTo(25);
            assertThat(run.getFailingRowCount()).isZero();
            assertThat(run.getInputParamsJson()).isNull();
            assertThat(run.getMetricsJson()).isNull();
        });
    }

    @Test
    void executionContractErrorLeavesEveryUnavailableMetricUnmeasured() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("字段不存在的检查");
        rule.setSeverity("MEDIUM");
        rule.setEnabled(Boolean.TRUE);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":\"select id from ods_budget_v2 where project_code is null\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        GovQualityRun[] persistedRun = new GovQualityRun[1];
        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.fromString("50000000-0000-0000-0000-000000000013"));
            }
            persistedRun[0] = run;
            return run;
        });
        when(runRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(persistedRun[0]));
        when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(
                    new StatementExecutionResult(
                        "sql",
                        "select id from ods_budget_v2 where project_code is null",
                        StatementExecutionResult.Status.FAILED,
                        "发现 1 条不符合规则的数据"
                    ),
                    new StatementExecutionResult(
                        "__failed_rows__",
                        "",
                        StatementExecutionResult.Status.FAILED,
                        "质量检测结果必须返回稳定的 id 字段",
                        "RESULT_ID_REQUIRED"
                    )
                ),
                25,
                0
            )
        );

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);
        request.setDryRun(true);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).singleElement().satisfies(run -> assertThat(run.getStatus()).isEqualTo("FAILED"));
        ArgumentCaptor<GovQualityMetric> metric = ArgumentCaptor.forClass(GovQualityMetric.class);
        verify(metricRepository, times(2)).save(metric.capture());
        assertThat(metric.getAllValues()).allSatisfy(item ->
            assertThat(item.getMetricValue()).isNull()
        );
    }

    @Test
    void runDetailRequiresObjectReadAndRemovesSensitivePayloads() {
        UUID runId = UUID.fromString("50000000-0000-0000-0000-000000000014");
        GovQualityRun run = new GovQualityRun();
        run.setId(runId);
        run.setDatasetId(DATASET_ID);
        run.setStatus("FAILED");
        run.setMessage("driver password=top-secret-token");
        run.setInputParamsJson("{\"token\":\"top-secret-token\"}");
        run.setMetricsJson("[{\"sql\":\"select token\"}]");
        GovQualityMetric metric = new GovQualityMetric();
        metric.setRun(run);
        metric.setStatus("FAILED");
        metric.setDetail("jdbc password=top-secret-token");
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(metricRepository.findByRunId(runId)).thenReturn(List.of(metric));

        QualityRunDto dto = service.getRun(runId, "D01");

        verify(qualityDatasetReadGuard).requireReadable(DATASET_ID, "D01");
        assertThat(dto.getInputParamsJson()).isNull();
        assertThat(dto.getMetricsJson()).isNull();
        assertThat(dto.getMessage()).contains("质量检测未能完成", "暂无有效统计").doesNotContain("top-secret-token");
        assertThat(dto.getMetrics()).singleElement().satisfies(item ->
            assertThat(item.getDetail()).isEqualTo("质量检测项执行失败")
        );
        assertThat(String.valueOf(dto)).doesNotContain("top-secret-token");
    }

    @Test
    void unscopedRunListDropsDatasetsOutsideReadableSet() {
        UUID deniedDatasetId = UUID.fromString("40000000-0000-0000-0000-000000000011");
        GovQualityRun readable = new GovQualityRun();
        readable.setId(UUID.fromString("50000000-0000-0000-0000-000000000015"));
        readable.setDatasetId(DATASET_ID);
        readable.setStatus("SUCCEEDED");
        GovQualityRun denied = new GovQualityRun();
        denied.setId(UUID.fromString("50000000-0000-0000-0000-000000000016"));
        denied.setDatasetId(deniedDatasetId);
        denied.setStatus("SUCCEEDED");
        CatalogDataset readableDataset = new CatalogDataset();
        readableDataset.setId(DATASET_ID);
        CatalogDataset deniedDataset = new CatalogDataset();
        deniedDataset.setId(deniedDatasetId);
        when(runRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(
            new PageImpl<>(List.of(readable, denied))
        );
        when(datasetRepository.findAllById(any())).thenReturn(List.of(readableDataset, deniedDataset));
        when(qualityDatasetReadGuard.readableDatasetIds(any(), eq("D01"))).thenReturn(Set.of(DATASET_ID));
        when(metricRepository.findByRunIdIn(List.of(readable.getId()))).thenReturn(List.of());

        List<QualityRunDto> result = service.listRuns(null, null, null, null, null, null, 10, "D01");

        assertThat(result).extracting(QualityRunDto::getDatasetId).containsExactly(DATASET_ID);
        verify(metricRepository).findByRunIdIn(List.of(readable.getId()));
        verify(metricRepository, never()).findByRunId(any());
    }
}
