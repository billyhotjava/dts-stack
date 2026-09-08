package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
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
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class QualityRunAuditTest {

    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000051");

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
        lenient().when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(new StatementExecutionResult("sql", "select 1", StatementExecutionResult.Status.SUCCEEDED, "通过")),
                1,
                0
            )
        );
    }

    @Test
    void scheduledCompletionUsesDeterministicTrustedMachineAudit() {
        GovQualityRun run = executableRun("SCHEDULED", "system");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        verify(qualityAuditRecorder).recordMachine(
            eq("scheduler"),
            eq("quality-run:" + RUN_ID + ":SUCCESS"),
            any(Instant.class),
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq(RUN_ID.toString()),
            any()
        );
        verify(qualityAuditRecorder, never()).recordAction(any(), any(), any(), any());
    }

    @Test
    void modelCorrelationKeyIsNeverUsedAsIssueActor() {
        var coordinator = new QualityRunAuditCoordinator(qualityAuditRecorder, issueTicketService);
        GovQualityRun run = executableRun("MANUAL", "mcq:" + UUID.randomUUID() + ":" + "a".repeat(64) + ":");
        run.setCreatedBy("xiezm");
        assertThat(coordinator.resolveRunActor(run)).isEqualTo("xiezm");
        run.setCreatedBy(null);
        assertThat(coordinator.resolveRunActor(run)).isNull();
        run.setTriggerRef("alice");
        assertThat(coordinator.resolveRunActor(run)).isEqualTo("alice");
    }

    @Test
    void manualCompletionKeepsTheExplicitTriggerActorAndCatalogCode() {
        GovQualityRun run = executableRun("MANUAL", "alice");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq(RUN_ID.toString()),
            payload.capture()
        );
        org.assertj.core.api.Assertions.assertThat(String.valueOf(payload.getValue())).contains("triggerActor=alice");
    }

    @Test
    void ingestionCompletionUsesTheTrustedMachineAuditPath() {
        GovQualityRun run = executableRun("INGESTION", "service:dts-ingestion");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        verify(qualityAuditRecorder).recordMachine(
            eq("ingestion"),
            eq("quality-run:" + RUN_ID + ":SUCCESS"),
            any(Instant.class),
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq(RUN_ID.toString()),
            any()
        );
        verify(qualityAuditRecorder, never()).recordAction(any(), any(), any(), any());
    }

    @Test
    void sameNamedManualActorIsNeverPromotedToIngestionMachineAudit() {
        GovQualityRun run = executableRun("MANUAL", "service:dts-ingestion");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq(RUN_ID.toString()),
            any()
        );
        verify(qualityAuditRecorder, never()).recordMachine(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scheduledFailureAuditsSanitizedCompletionAndAutomaticIssue() {
        GovQualityRun run = executableRun("SCHEDULED", "system");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        String sensitiveSql = "select token from secret_table where password='top-secret-token'";
        when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(new StatementExecutionResult("sql", sensitiveSql, StatementExecutionResult.Status.FAILED,
                    "driver leaked top-secret-token")),
                1,
                1
            )
        );
        UUID issueId = UUID.fromString("40000000-0000-0000-0000-000000000051");
        IssueTicketDto issue = new IssueTicketDto();
        issue.setId(issueId);
        when(issueTicketService.createOrTouchQualityProblem(
            any(), eq(RUN_ID), any(), eq("system"), any()
        )).thenReturn(new IssueTicketService.CreateOrTouchResult(
            issue,
            IssueTicketService.CreateOrTouchDisposition.CREATED
        ));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        org.mockito.ArgumentCaptor<Object> completionPayload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(qualityAuditRecorder).recordMachine(
            eq("scheduler"),
            eq("quality-run:" + RUN_ID + ":FAIL"),
            any(Instant.class),
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.FAIL),
            eq(RUN_ID.toString()),
            completionPayload.capture()
        );
        org.assertj.core.api.Assertions.assertThat(String.valueOf(completionPayload.getValue()))
            .doesNotContain(sensitiveSql)
            .doesNotContain("top-secret-token")
            .contains("errorCategory");
        org.assertj.core.api.Assertions.assertThat(run.getMessage()).isEqualTo("存在1个检测失败");
        org.assertj.core.api.Assertions.assertThat(run.getMetricsJson())
            .doesNotContain(sensitiveSql)
            .doesNotContain("top-secret-token")
            .contains("EXECUTION_ERROR");
        verify(qualityAuditRecorder).recordMachine(
            eq("scheduler"),
            eq("quality-run:" + RUN_ID + ":FAIL:ISSUE:CREATED"),
            any(Instant.class),
            eq("GOV_ISSUE_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(issueId.toString()),
            any()
        );
    }

    @Test
    void manualFailureKeepsRealActorForAutomaticIssueAudit() {
        GovQualityRun run = executableRun("MANUAL", "alice");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(new StatementExecutionResult("sql", "select 1", StatementExecutionResult.Status.FAILED, "失败")),
                1,
                1
            )
        );
        UUID issueId = UUID.fromString("50000000-0000-0000-0000-000000000051");
        IssueTicketDto issue = new IssueTicketDto();
        issue.setId(issueId);
        when(issueTicketService.createOrTouchQualityProblem(
            any(), eq(RUN_ID), any(), eq("alice"), any()
        )).thenReturn(new IssueTicketService.CreateOrTouchResult(
            issue,
            IssueTicketService.CreateOrTouchDisposition.CREATED
        ));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        org.mockito.ArgumentCaptor<Object> issuePayload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_ISSUE_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(issueId.toString()),
            issuePayload.capture()
        );
        org.assertj.core.api.Assertions.assertThat(String.valueOf(issuePayload.getValue())).contains("triggerActor=alice");
    }

    @Test
    void scheduledAutomaticIssueFailureWritesASeparateFailureAudit() {
        GovQualityRun run = executableRun("SCHEDULED", "system");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(statementExecutor.execute(any(), any())).thenReturn(
            new QualityDatasetStatementExecutor.Execution(
                List.of(new StatementExecutionResult("sql", "select 1", StatementExecutionResult.Status.FAILED, "失败")),
                1,
                1
            )
        );
        when(issueTicketService.createOrTouchQualityProblem(
            any(), eq(RUN_ID), any(), eq("system"), any()
        )).thenThrow(new IllegalStateException("issue repository unavailable"));

        ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of());

        verify(qualityAuditRecorder).recordMachineAttempt(
            eq("scheduler"),
            eq("quality-run:" + RUN_ID + ":FAIL:ISSUE:FAIL"),
            any(Instant.class),
            eq("GOV_ISSUE_CREATE"),
            eq(AuditStage.FAIL),
            eq(RUN_ID.toString()),
            any()
        );
    }

    @Test
    void strictCompletionAuditFailurePropagatesWithoutBeingReclassifiedAsExecutionFailure() {
        GovQualityRun run = executableRun("MANUAL", "alice");
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        doThrow(new IllegalStateException("audit unavailable"))
            .when(qualityAuditRecorder)
            .recordAction(
                eq("GOV_QUALITY_RUN_EXECUTE"),
                eq(AuditStage.SUCCESS),
                eq(RUN_ID.toString()),
                any()
            );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "doExecuteRun", RUN_ID, Map.of()))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("质量运行审计写入失败")
            .hasRootCauseMessage("audit unavailable");

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        verify(qualityAuditRecorder, never()).recordAction(
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.FAIL),
            eq(RUN_ID.toString()),
            any()
        );
    }

    @Test
    void rejectedDispatchMovesTheCommittedRunToFailedAndWritesTerminalAudit() {
        GovQualityRun run = executableRun("MANUAL", "alice");
        TransactionStatus transactionStatus = org.mockito.Mockito.mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        doThrow(new RejectedExecutionException("executor saturated"))
            .when(taskExecutor)
            .execute(any(Runnable.class));

        ReflectionTestUtils.invokeMethod(service, "dispatchOne", RUN_ID, Map.of());

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorCategory()).isEqualTo("DISPATCH_REJECTED");
        assertThat(run.getMessage()).isEqualTo("质量检测任务分发失败");
        verify(runRepository).save(run);
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_RUN_EXECUTE"),
            eq(AuditStage.FAIL),
            eq(RUN_ID.toString()),
            any()
        );
        verify(transactionManager).commit(transactionStatus);
    }

    private GovQualityRun executableRun(String triggerType, String triggerRef) {
        GovRule rule = new GovRule();
        rule.setId(UUID.fromString("20000000-0000-0000-0000-000000000051"));
        rule.setName("空值检查");
        GovRuleVersion version = new GovRuleVersion();
        version.setId(UUID.fromString("30000000-0000-0000-0000-000000000051"));
        version.setRule(rule);
        version.setDefinition("{\"sql\":\"select 1\"}");
        GovQualityRun run = new GovQualityRun();
        run.setId(RUN_ID);
        run.setRule(rule);
        run.setRuleVersion(version);
        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(UUID.fromString("60000000-0000-0000-0000-000000000051"));
        binding.setDatasetId(UUID.fromString("70000000-0000-0000-0000-000000000051"));
        binding.setRuleVersion(version);
        run.setBinding(binding);
        run.setDatasetId(binding.getDatasetId());
        run.setTriggerType(triggerType);
        run.setTriggerRef(triggerRef);
        run.setStatus("QUEUED");
        return run;
    }
}
