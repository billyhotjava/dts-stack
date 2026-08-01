package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityTaskRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@ExtendWith(MockitoExtension.class)
class QualityTaskServiceAuditTest {

    private static final UUID TASK_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000041");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000041");

    @Mock private GovQualityTaskRepository taskRepository;
    @Mock private GovRuleBindingRepository bindingRepository;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private QualityRunService qualityRunService;
    @Mock private QualityAuditRecorder qualityAuditRecorder;
    @Mock private IssueTicketService issueTicketService;
    @Mock private DataStandardSecurity security;
    @Mock private QualityEffectiveDepartmentResolver departmentResolver;
    @Mock private OrganizationVisibilityService organizationVisibilityService;
    @Mock private AccessChecker accessChecker;
    @Mock private DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    @Mock private PlatformTransactionManager transactionManager;

    private QualityTaskService service;

    @BeforeEach
    void setUp() {
        service = new QualityTaskService(
            taskRepository,
            bindingRepository,
            datasetRepository,
            qualityRunService,
            qualityAuditRecorder,
            issueTicketService,
            security,
            departmentResolver,
            organizationVisibilityService,
            accessChecker,
            defaultLakeDatasetGuard,
            transactionManager
        );
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        org.mockito.Mockito.lenient()
            .when(bindingRepository.findByDatasetIdAndRuleVersionStatus(DATASET_ID, "PUBLISHED"))
            .thenReturn(List.of(binding(rule(RULE_ID, true))));
        org.mockito.Mockito.lenient()
            .when(taskRepository.claimDueExecution(eq(TASK_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(1);
    }

    @Test
    void auditsTaskCreateSuccessAndFailureWithCatalogCodes() {
        GovQualityTask request = taskRequest("每小时空值检查");
        CatalogDataset dataset = mock(CatalogDataset.class);
        when(defaultLakeDatasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(security.hasInstituteScope()).thenReturn(true);
        when(taskRepository.save(any(GovQualityTask.class))).thenAnswer(invocation -> {
            GovQualityTask saved = invocation.getArgument(0);
            saved.setId(TASK_ID);
            return saved;
        });

        service.create(request, "alice", null);

        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_TASK_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(TASK_ID.toString()),
            any()
        );

        GovQualityTask rejected = taskRequest("失败计划");
        when(taskRepository.save(any(GovQualityTask.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.create(rejected, "alice", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database unavailable");
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_TASK_CREATE"),
            eq("UNASSIGNED"),
            any()
        );
    }

    @Test
    void scheduledTaskUsesTrustedMachineAuditIdentity() {
        GovQualityTask task = dueTask();
        when(taskRepository.findByEnabledTrue()).thenReturn(List.of(task));
        when(qualityRunService.triggerScheduled(any())).thenReturn(List.of());

        service.runDueTasks();

        ArgumentCaptor<String> eventIdentity = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> occurredAt = ArgumentCaptor.forClass(Instant.class);
        verify(qualityAuditRecorder).recordMachine(
            eq("scheduler"),
            eventIdentity.capture(),
            occurredAt.capture(),
            eq("GOV_QUALITY_TASK_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq(TASK_ID.toString()),
            any()
        );
        org.assertj.core.api.Assertions.assertThat(eventIdentity.getValue())
            .startsWith("quality-task:" + TASK_ID + ":scheduled:");
        org.assertj.core.api.Assertions.assertThat(occurredAt.getValue()).isNotNull();
    }

    @Test
    void scheduledIdentityIsStableInsideSlotAndChangesAtAdjacentSlot() {
        Instant first = Instant.parse("2026-08-01T01:59:59Z");
        Instant sameSlot = Instant.parse("2026-08-01T01:00:01Z");
        Instant adjacentSlot = Instant.parse("2026-08-01T02:00:00Z");

        assertThat(QualityTaskService.scheduledEventIdentity(TASK_ID, first, 60))
            .isEqualTo(QualityTaskService.scheduledEventIdentity(TASK_ID, sameSlot, 60))
            .isNotEqualTo(QualityTaskService.scheduledEventIdentity(TASK_ID, adjacentSlot, 60));
    }

    @Test
    void scheduledExecutionRunsOnlyForTheAtomicClaimWinner() {
        GovQualityTask task = dueTask();
        when(taskRepository.findByEnabledTrue()).thenReturn(List.of(task));
        when(taskRepository.claimDueExecution(eq(TASK_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(1, 0);
        when(qualityRunService.triggerScheduled(any())).thenReturn(List.of());

        service.runDueTasks();
        service.runDueTasks();

        verify(taskRepository, times(2)).claimDueExecution(eq(TASK_ID), any(Instant.class), any(Instant.class));
        verify(qualityRunService, times(1)).triggerScheduled(any());
        verify(qualityAuditRecorder, times(1)).recordMachineAttempt(
            eq("scheduler"),
            any(),
            any(Instant.class),
            eq("GOV_QUALITY_TASK_EXECUTE"),
            eq(AuditStage.BEGIN),
            eq(TASK_ID.toString()),
            any()
        );
    }

    @Test
    void scheduledClaimIsOneAtomicEnabledAndDueUpdate() throws Exception {
        Method claim = GovQualityTaskRepository.class.getMethod(
            "claimDueExecution",
            UUID.class,
            Instant.class,
            Instant.class
        );

        Modifying modifying = claim.getAnnotation(Modifying.class);
        Query query = claim.getAnnotation(Query.class);

        assertThat(modifying).isNotNull();
        assertThat(modifying.clearAutomatically()).isTrue();
        assertThat(query.value())
            .contains("update GovQualityTask task")
            .contains("task.enabled = true")
            .contains("task.lastTriggeredAt <= :dueBefore");
    }

    @Test
    void scheduledFailureCreatesExplicitMachineIssueAudit() {
        GovQualityTask task = dueTask();
        task.setRuleId(null);
        when(taskRepository.findByEnabledTrue()).thenReturn(List.of(task));
        when(bindingRepository.findByDatasetIdAndRuleVersionStatus(DATASET_ID, "PUBLISHED")).thenReturn(List.of());
        UUID issueId = UUID.fromString("40000000-0000-0000-0000-000000000041");
        IssueTicketDto issue = new IssueTicketDto();
        issue.setId(issueId);
        when(issueTicketService.createOrTouchWithDisposition(
            eq("QUALITY_TASK"), eq(TASK_ID), any(), eq("system"), any()
        )).thenReturn(new IssueTicketService.CreateOrTouchResult(
            issue,
            IssueTicketService.CreateOrTouchDisposition.CREATED
        ));

        service.runDueTasks();

        ArgumentCaptor<String> eventIdentity = ArgumentCaptor.forClass(String.class);
        verify(qualityAuditRecorder).recordMachine(
            eq("scheduler"),
            eventIdentity.capture(),
            any(Instant.class),
            eq("GOV_ISSUE_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(issueId.toString()),
            any()
        );
        org.assertj.core.api.Assertions.assertThat(eventIdentity.getValue())
            .startsWith("quality-task:" + TASK_ID + ":scheduled:")
            .endsWith(":ISSUE:CREATED");
    }

    @Test
    void taskFailureAuditIsSanitizedAndCannotMaskBusinessFailure() {
        GovQualityTask request = taskRequest("敏感失败计划");
        CatalogDataset dataset = mock(CatalogDataset.class);
        when(defaultLakeDatasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(security.hasInstituteScope()).thenReturn(true);
        IllegalStateException businessFailure = new IllegalStateException(
            "select secret_value from ods_secret password=top-secret-token"
        );
        when(taskRepository.save(any(GovQualityTask.class))).thenThrow(businessFailure);
        doAnswer(invocation -> {
            Object payload = invocation.getArgument(2);
            org.assertj.core.api.Assertions.assertThat(String.valueOf(payload))
                .doesNotContain("select secret_value")
                .doesNotContain("top-secret-token")
                .contains("errorType")
                .contains("errorCategory");
            throw new IllegalStateException("audit unavailable");
        }).when(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_TASK_CREATE"), eq("UNASSIGNED"), any()
        );

        assertThatThrownBy(() -> service.create(request, "alice", null)).isSameAs(businessFailure);
    }

    @Test
    void rejectsTaskRuleThatIsNotEnabledAndPublishedForSelectedDataset() {
        GovQualityTask request = taskRequest("跨资产规则");
        CatalogDataset dataset = mock(CatalogDataset.class);
        when(defaultLakeDatasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(bindingRepository.findByDatasetIdAndRuleVersionStatus(DATASET_ID, "PUBLISHED")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request, "alice", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未绑定当前数据资产");
        verify(taskRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void datasetWideTaskRunsOnlyEnabledPublishedRules() {
        GovQualityTask task = dueTask();
        task.setRuleId(null);
        GovRule enabledRule = rule(RULE_ID, true);
        GovRule disabledRule = rule(UUID.fromString("30000000-0000-0000-0000-000000000042"), false);
        when(taskRepository.findById(TASK_ID)).thenReturn(java.util.Optional.of(task));
        when(security.hasInstituteScope()).thenReturn(true);
        CatalogDataset dataset = mock(CatalogDataset.class);
        when(datasetRepository.findById(DATASET_ID)).thenReturn(java.util.Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(bindingRepository.findByDatasetIdAndRuleVersionStatus(DATASET_ID, "PUBLISHED"))
            .thenReturn(List.of(binding(enabledRule), binding(disabledRule)));
        when(qualityRunService.trigger(any(), eq("alice"))).thenReturn(List.of());

        service.trigger(TASK_ID, "alice", null);

        ArgumentCaptor<com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest> request =
            ArgumentCaptor.forClass(com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest.class);
        verify(qualityRunService).trigger(request.capture(), eq("alice"));
        assertThat(request.getValue().getRuleId()).isEqualTo(RULE_ID);
    }

    private GovQualityTask taskRequest(String name) {
        GovQualityTask request = new GovQualityTask();
        request.setName(name);
        request.setDatasetId(DATASET_ID);
        request.setRuleId(RULE_ID);
        request.setEnabled(Boolean.TRUE);
        return request;
    }

    private GovQualityTask dueTask() {
        GovQualityTask task = taskRequest("每小时空值检查");
        task.setId(TASK_ID);
        task.setLastTriggeredAt(null);
        return task;
    }

    private GovRule rule(UUID id, boolean enabled) {
        GovRule rule = new GovRule();
        rule.setId(id);
        rule.setEnabled(enabled);
        return rule;
    }

    private GovRuleBinding binding(GovRule rule) {
        GovRuleVersion version = new GovRuleVersion();
        version.setRule(rule);
        version.setStatus("PUBLISHED");
        GovRuleBinding binding = new GovRuleBinding();
        binding.setDatasetId(DATASET_ID);
        binding.setRuleVersion(version);
        return binding;
    }
}
