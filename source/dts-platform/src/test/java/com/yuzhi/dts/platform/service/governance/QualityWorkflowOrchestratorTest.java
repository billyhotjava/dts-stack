package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityWorkflowOrchestratorTest {

    private static final UUID TASK_ID = UUID.fromString("10000000-0000-0000-0000-000000000102");
    private static final UUID DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000102");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000102");
    private static final UUID WORKFLOW_ID = UUID.fromString("40000000-0000-0000-0000-000000000102");
    private static final UUID RULE_VERSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000102");
    private static final UUID BINDING_ID = UUID.fromString("70000000-0000-0000-0000-000000000102");

    @Mock private GovQualityWorkflowRunRepository workflowRepository;
    @Mock private GovRuleBindingRepository bindingRepository;
    @Mock private QualityRunService qualityRunService;
    @Mock private QualityDatasetReadGuard datasetReadGuard;
    @Mock private DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    @Mock private QualityAuditRecorder auditRecorder;

    private QualityWorkflowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new QualityWorkflowOrchestrator(
            workflowRepository,
            bindingRepository,
            qualityRunService,
            datasetReadGuard,
            defaultLakeDatasetGuard,
            auditRecorder
        );
    }

    @Test
    void startsOneDurableWorkflowAndDelegatesRuleExecution() {
        GovQualityTask task = task();
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        when(workflowRepository.insertQueued(any(), eq(TASK_ID), eq(DATASET_ID), eq(null), eq(null), eq(1), eq(1), eq(0), eq("MANUAL"), any(), eq("manual-102"), any(), eq("xiezm"), any()))
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED"))
            .thenReturn(List.of(binding()));
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000102"));
        run.setJobId(WORKFLOW_ID);
        when(qualityRunService.triggerWorkflowAuthorized(any(), eq("xiezm"), eq("INST"), eq(WORKFLOW_ID), any()))
            .thenReturn(List.of(run));

        var result = orchestrator.startAuthorizedTask(task, "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.expectedRunCount()).isEqualTo(1);
        assertThat(result.ruleRuns()).singleElement().extracting(QualityRunDto::getJobId).isEqualTo(WORKFLOW_ID);
        verify(datasetReadGuard).requireReadable(DATASET_ID, "INST");
        verify(qualityRunService).triggerWorkflowAuthorized(any(), eq("xiezm"), eq("INST"), eq(WORKFLOW_ID), any());
    }

    @Test
    void startsManualRuleExecutionThroughTheWorkflowBoundary() {
        GovRuleBinding executable = binding();
        when(bindingRepository.findPublishedWorkflowBindingsByRuleId(RULE_ID, "PUBLISHED"))
            .thenReturn(List.of(executable));
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        workflow.setTaskId(null);
        workflow.setRuleId(RULE_ID);
        when(
            workflowRepository.insertQueued(
                any(),
                eq(null),
                eq(DATASET_ID),
                eq(RULE_ID),
                eq(null),
                eq(1),
                eq(1),
                eq(0),
                eq("MANUAL"),
                any(),
                eq("manual-rule-102"),
                any(),
                eq("xiezm"),
                any()
            )
        )
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("manual-rule-102")).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(executable));
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000106"));
        run.setJobId(WORKFLOW_ID);
        when(qualityRunService.triggerWorkflowAuthorized(any(), eq("xiezm"), eq("INST"), eq(WORKFLOW_ID), any()))
            .thenReturn(List.of(run));
        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        var result = orchestrator.startAuthorizedRule(request, "xiezm", "INST", "manual-rule-102");

        assertThat(result.id()).isEqualTo(WORKFLOW_ID);
        assertThat(result.ruleId()).isEqualTo(RULE_ID);
        assertThat(result.ruleRuns()).singleElement().extracting(QualityRunDto::getJobId).isEqualTo(WORKFLOW_ID);
        verify(datasetReadGuard).requireReadable(DATASET_ID, "INST");
    }

    @Test
    void replaysTheExistingWorkflowForTheSameIdempotencyKey() {
        GovQualityWorkflowRun workflow = workflow("PASSED");
        workflow.setExpectedRunCount(1);
        when(workflowRepository.insertQueued(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), eq("manual-102"), any(), any(), any()))
            .thenReturn(0);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(workflow));
        when(qualityRunService.runsByWorkflow(WORKFLOW_ID, "INST")).thenReturn(List.of());

        var result = orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.id()).isEqualTo(WORKFLOW_ID);
        assertThat(result.status()).isEqualTo("PASSED");
        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnIdempotencyKeyThatBelongsToAnotherWorkflowScope() {
        GovQualityWorkflowRun other = workflow("PASSED");
        other.setDatasetId(UUID.fromString("20000000-0000-0000-0000-000000000199"));
        when(
            workflowRepository.insertQueued(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class),
                any(),
                any(),
                eq("manual-102"),
                any(),
                any(),
                any()
            )
        )
            .thenReturn(0);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("幂等标识已用于其他验证");

        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void trustedIngestionUsesTheSameLedgerAndExecutionEngine() {
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        workflow.setTaskId(null);
        workflow.setTriggerType("INGESTION");
        workflow.setTriggerRef("ingestion:203");
        when(workflowRepository.insertQueued(
            any(),
            eq(null),
            eq(DATASET_ID),
            eq(null),
            eq(null),
            eq(1),
            eq(1),
            eq(30),
            eq("INGESTION"),
            eq("ingestion:203"),
            eq("ingestion-quality-203"),
            any(),
            eq("service:dts-ingestion"),
            any()
        )).thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("ingestion-quality-203")).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(binding()));
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000103"));
        run.setJobId(WORKFLOW_ID);
        when(qualityRunService.triggerWorkflowTrustedIngestion(any(), eq(WORKFLOW_ID), eq("ingestion:203")))
            .thenReturn(List.of(run));

        var result = orchestrator.startTrustedIngestion(DATASET_ID, "ingestion:203", "ingestion-quality-203");

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.ruleRuns()).hasSize(1);
        verify(defaultLakeDatasetGuard).requireDefaultLakeDataset(DATASET_ID);
        verify(qualityRunService).triggerWorkflowTrustedIngestion(any(), eq(WORKFLOW_ID), eq("ingestion:203"));
        verify(auditRecorder).recordMachine(
            eq("ingestion"),
            any(),
            any(),
            eq("GOV_QUALITY_WORKFLOW_START"),
            eq(com.yuzhi.dts.common.audit.AuditStage.SUCCESS),
            eq(WORKFLOW_ID.toString()),
            any()
        );
    }

    @Test
    void pinnedModelQualityPreservesTheExactPublishedBindingSnapshot() {
        GovRuleBinding binding = binding();
        binding.setId(BINDING_ID);
        binding.getRuleVersion().setId(RULE_VERSION_ID);
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        workflow.setTaskId(null);
        workflow.setTriggerType("MODEL_RELEASE");
        workflow.setTriggerRef("candidate:102:binding:1");
        workflow.setIdempotencyKey("candidate-quality-102");
        when(bindingRepository.findById(BINDING_ID)).thenReturn(Optional.of(binding));
        when(
            workflowRepository.insertQueued(
                any(),
                eq(null),
                eq(DATASET_ID),
                eq(RULE_ID),
                eq(null),
                eq(1),
                eq(1),
                eq(0),
                eq("MODEL_RELEASE"),
                eq("candidate:102:binding:1"),
                eq("candidate-quality-102"),
                any(),
                eq("xiezm"),
                any()
            )
        )
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("candidate-quality-102"))
            .thenReturn(Optional.empty(), Optional.of(workflow));
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000104"));
        run.setRuleId(RULE_ID);
        run.setRuleVersionId(RULE_VERSION_ID);
        run.setBindingId(BINDING_ID);
        run.setJobId(WORKFLOW_ID);
        when(
            qualityRunService.triggerPinnedWorkflowAuthorized(
                any(),
                eq(RULE_VERSION_ID),
                eq("xiezm"),
                eq("INST"),
                eq(WORKFLOW_ID),
                eq("candidate:102:binding:1")
            )
        )
            .thenReturn(List.of(run));

        var result = orchestrator.startPinnedModelQuality(
            RULE_ID,
            RULE_VERSION_ID,
            BINDING_ID,
            "xiezm",
            "INST",
            "candidate:102:binding:1",
            "candidate-quality-102"
        );

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.ruleRuns()).singleElement().extracting(QualityRunDto::getBindingId).isEqualTo(BINDING_ID);
        verify(datasetReadGuard).requireReadable(DATASET_ID, "INST");
        verify(qualityRunService).triggerPinnedWorkflowAuthorized(
            any(),
            eq(RULE_VERSION_ID),
            eq("xiezm"),
            eq("INST"),
            eq(WORKFLOW_ID),
            eq("candidate:102:binding:1")
        );
    }

    @Test
    void failsPinnedModelWorkflowWhenNoUniqueRuleRunWasCreated() {
        GovRuleBinding binding = binding();
        binding.setId(BINDING_ID);
        binding.getRuleVersion().setId(RULE_VERSION_ID);
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        workflow.setTaskId(null);
        workflow.setTriggerType("MODEL_RELEASE");
        workflow.setTriggerRef("candidate:102:binding:empty");
        workflow.setIdempotencyKey("candidate-quality-empty-102");
        when(bindingRepository.findById(BINDING_ID)).thenReturn(Optional.of(binding));
        when(
            workflowRepository.insertQueued(
                any(),
                eq(null),
                eq(DATASET_ID),
                eq(RULE_ID),
                eq(null),
                eq(1),
                eq(1),
                eq(0),
                eq("MODEL_RELEASE"),
                eq("candidate:102:binding:empty"),
                eq("candidate-quality-empty-102"),
                any(),
                eq("xiezm"),
                any()
            )
        )
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("candidate-quality-empty-102"))
            .thenReturn(Optional.empty(), Optional.of(workflow));
        when(
            qualityRunService.triggerPinnedWorkflowAuthorized(
                any(),
                eq(RULE_VERSION_ID),
                eq("xiezm"),
                eq("INST"),
                eq(WORKFLOW_ID),
                eq("candidate:102:binding:empty")
            )
        )
            .thenReturn(List.of());

        var result = orchestrator.startPinnedModelQuality(
            RULE_ID,
            RULE_VERSION_ID,
            BINDING_ID,
            "xiezm",
            "INST",
            "candidate:102:binding:empty",
            "candidate-quality-empty-102"
        );

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCategory()).isEqualTo("PINNED_RULE_DISPATCH_INVALID");
        assertThat(result.finishedAt()).isNotNull();
    }

    @Test
    void startsAllPinnedModelRulesInOneWorkflow() {
        UUID secondRuleId = UUID.fromString("30000000-0000-0000-0000-000000000103");
        UUID secondVersionId = UUID.fromString("60000000-0000-0000-0000-000000000103");
        UUID secondBindingId = UUID.fromString("70000000-0000-0000-0000-000000000103");
        GovRuleBinding first = pinnedBinding(RULE_ID, RULE_VERSION_ID, BINDING_ID, "PUBLISHED");
        GovRuleBinding second = pinnedBinding(secondRuleId, secondVersionId, secondBindingId, "PUBLISHED");
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        workflow.setTaskId(null);
        workflow.setRuleId(null);
        workflow.setTriggerType("MODEL_RELEASE");
        workflow.setTriggerRef("candidate:102:");
        when(bindingRepository.findById(BINDING_ID)).thenReturn(Optional.of(first));
        when(bindingRepository.findById(secondBindingId)).thenReturn(Optional.of(second));
        when(
            workflowRepository.insertQueued(
                any(),
                eq(null),
                eq(DATASET_ID),
                eq(null),
                eq(null),
                eq(1),
                eq(1),
                eq(0),
                eq("MODEL_RELEASE"),
                eq("candidate:102:"),
                eq("candidate-batch-102"),
                any(),
                eq("xiezm"),
                any()
            )
        )
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("candidate-batch-102"))
            .thenReturn(Optional.empty(), Optional.of(workflow));
        when(
            qualityRunService.triggerPinnedWorkflowAuthorized(
                any(),
                eq(RULE_VERSION_ID),
                eq("xiezm"),
                eq("INST"),
                eq(WORKFLOW_ID),
                eq("candidate:102:0")
            )
        )
            .thenReturn(List.of(pinnedRun(RULE_ID, RULE_VERSION_ID, BINDING_ID)));
        when(
            qualityRunService.triggerPinnedWorkflowAuthorized(
                any(),
                eq(secondVersionId),
                eq("xiezm"),
                eq("INST"),
                eq(WORKFLOW_ID),
                eq("candidate:102:1")
            )
        )
            .thenReturn(List.of(pinnedRun(secondRuleId, secondVersionId, secondBindingId)));

        var result = orchestrator.startPinnedModelQuality(
            List.of(
                new PinnedQualityBinding(RULE_ID, RULE_VERSION_ID, BINDING_ID),
                new PinnedQualityBinding(secondRuleId, secondVersionId, secondBindingId)
            ),
            "xiezm",
            "INST",
            "candidate:102:",
            "candidate-batch-102"
        );

        assertThat(result.id()).isEqualTo(WORKFLOW_ID);
        assertThat(result.ruleId()).isNull();
        assertThat(result.expectedRunCount()).isEqualTo(2);
        assertThat(result.ruleRuns()).hasSize(2);
        assertThat(workflow.getContextJson()).contains(RULE_VERSION_ID.toString(), secondVersionId.toString());
    }

    @Test
    void retriesPinnedModelWorkflowWithTheOriginalRuleVersion() {
        GovQualityWorkflowRun source = workflow("FAILED");
        source.setTaskId(null);
        source.setRuleId(RULE_ID);
        source.setTriggerType("MODEL_RELEASE");
        source.setFinishedAt(Instant.now().minusSeconds(10));
        source.setContextJson(
            PinnedQualitySnapshotCodec.encode(
                List.of(new PinnedQualityBinding(RULE_ID, RULE_VERSION_ID, BINDING_ID))
            )
        );
        UUID retryWorkflowId = UUID.fromString("40000000-0000-0000-0000-000000000103");
        GovQualityWorkflowRun retry = workflow("QUEUED");
        retry.setId(retryWorkflowId);
        retry.setTaskId(null);
        retry.setRuleId(RULE_ID);
        retry.setRetryOfId(WORKFLOW_ID);
        retry.setAttemptNo(2);
        retry.setTriggerType("RETRY");
        retry.setTriggerRef("quality-workflow:retry:" + WORKFLOW_ID);
        GovRuleBinding archived = pinnedBinding(RULE_ID, RULE_VERSION_ID, BINDING_ID, "ARCHIVED");
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(source));
        when(bindingRepository.findById(BINDING_ID)).thenReturn(Optional.of(archived));
        when(
            workflowRepository.insertQueued(
                any(),
                eq(null),
                eq(DATASET_ID),
                eq(RULE_ID),
                eq(WORKFLOW_ID),
                eq(2),
                eq(1),
                eq(0),
                eq("RETRY"),
                eq("quality-workflow:retry:" + WORKFLOW_ID),
                eq("candidate-retry-102"),
                any(),
                eq("xiezm"),
                any()
            )
        )
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("candidate-retry-102"))
            .thenReturn(Optional.empty(), Optional.of(retry));
        when(
            qualityRunService.triggerPinnedWorkflowAuthorized(
                any(),
                eq(RULE_VERSION_ID),
                eq("xiezm"),
                eq("INST"),
                eq(retryWorkflowId),
                eq("quality-workflow:retry:" + WORKFLOW_ID)
            )
        )
            .thenReturn(List.of(pinnedRun(RULE_ID, RULE_VERSION_ID, BINDING_ID)));

        var result = orchestrator.retryAuthorized(WORKFLOW_ID, "xiezm", "INST", "candidate-retry-102");

        assertThat(result.retryOfId()).isEqualTo(WORKFLOW_ID);
        assertThat(result.attemptNo()).isEqualTo(2);
        verify(qualityRunService).triggerPinnedWorkflowAuthorized(
            any(),
            eq(RULE_VERSION_ID),
            eq("xiezm"),
            eq("INST"),
            eq(retryWorkflowId),
            any()
        );
        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void skipsAConcurrentTaskAndReturnsItsActiveWorkflow() {
        GovQualityWorkflowRun active = workflow("RUNNING");
        when(workflowRepository.insertQueued(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), eq("manual-102"), any(), any(), any()))
            .thenReturn(0);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.empty());
        when(workflowRepository.findFirstByTaskIdAndStatusInOrderByCreatedDateDesc(eq(TASK_ID), any()))
            .thenReturn(Optional.of(active));
        when(qualityRunService.runsByWorkflow(WORKFLOW_ID, "INST")).thenReturn(List.of());

        var result = orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.id()).isEqualTo(WORKFLOW_ID);
        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void blocksMoreThanOneHundredRulesWithoutDispatchingThem() {
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        when(workflowRepository.insertQueued(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), eq("manual-102"), any(), any(), any()))
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED"))
            .thenReturn(IntStream.range(0, 101).mapToObj(index -> binding(new UUID(0L, index + 1L))).toList());

        var result = orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCategory()).isEqualTo("QUALITY_WORKFLOW_RULE_LIMIT_EXCEEDED");
        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void blocksAWorkflowWithNoExecutableRules() {
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        when(workflowRepository.insertQueued(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), eq("manual-102"), any(), any(), any()))
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of());

        var result = orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCategory()).isEqualTo("NO_EXECUTABLE_RULES");
        verify(qualityRunService, never()).triggerWorkflowAuthorized(any(), any(), any(), any(), any());
    }

    @Test
    void recordsPartialDispatchFailureForFinalAggregation() {
        GovQualityWorkflowRun workflow = workflow("QUEUED");
        when(workflowRepository.insertQueued(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), eq("manual-102"), any(), any(), any()))
            .thenReturn(1);
        when(workflowRepository.findByIdempotencyKey("manual-102")).thenReturn(Optional.of(workflow));
        GovRuleBinding first = binding(RULE_ID);
        GovRuleBinding second = binding(UUID.fromString("30000000-0000-0000-0000-000000000103"));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(first, second));
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000105"));
        run.setJobId(WORKFLOW_ID);
        when(qualityRunService.triggerWorkflowAuthorized(any(), eq("xiezm"), eq("INST"), eq(WORKFLOW_ID), any()))
            .thenReturn(List.of(run))
            .thenThrow(new IllegalStateException("executor unavailable"));

        var result = orchestrator.startAuthorizedTask(task(), "xiezm", "INST", "MANUAL", "manual-102");

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.expectedRunCount()).isEqualTo(1);
        assertThat(result.dispatchFailureCount()).isEqualTo(1);
    }

    @Test
    void retriesOnlyFailedOrBlockedWorkflowsWithinTheSnapshotLimit() {
        GovQualityWorkflowRun passed = workflow("PASSED");
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(passed));

        assertThatThrownBy(() -> orchestrator.retryAuthorized(WORKFLOW_ID, "xiezm", "INST", "retry-102"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未通过或被阻断");

        GovQualityWorkflowRun exhausted = workflow("FAILED");
        exhausted.setAttemptNo(2);
        exhausted.setMaxRetryAttempts(1);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> orchestrator.retryAuthorized(WORKFLOW_ID, "xiezm", "INST", "retry-102"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("最大重试次数");
    }

    @Test
    void cancelsAnActiveWorkflowAndReleasesTheTaskSlot() {
        GovQualityWorkflowRun running = workflow("RUNNING");
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(running));

        var result = orchestrator.cancelAuthorized(WORKFLOW_ID, "xiezm", "INST");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.errorCategory()).isEqualTo("USER_CANCELLED");
        verify(datasetReadGuard).requireReadable(DATASET_ID, "INST");
        verify(auditRecorder).recordAction(
            eq("GOV_QUALITY_WORKFLOW_CANCEL"),
            eq(com.yuzhi.dts.common.audit.AuditStage.SUCCESS),
            eq(WORKFLOW_ID.toString()),
            any()
        );
    }

    private GovQualityTask task() {
        GovQualityTask task = new GovQualityTask();
        task.setId(TASK_ID);
        task.setDatasetId(DATASET_ID);
        return task;
    }

    private GovRuleBinding binding() {
        return binding(RULE_ID);
    }

    private GovRuleBinding binding(UUID ruleId) {
        GovRule rule = new GovRule();
        rule.setId(ruleId);
        rule.setEnabled(true);
        GovRuleVersion version = new GovRuleVersion();
        version.setStatus("PUBLISHED");
        version.setRule(rule);
        GovRuleBinding binding = new GovRuleBinding();
        binding.setDatasetId(DATASET_ID);
        binding.setRuleVersion(version);
        return binding;
    }

    private GovRuleBinding pinnedBinding(
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        String status
    ) {
        GovRuleBinding result = binding(ruleId);
        result.setId(bindingId);
        result.getRuleVersion().setId(ruleVersionId);
        result.getRuleVersion().setStatus(status);
        return result;
    }

    private QualityRunDto pinnedRun(UUID ruleId, UUID ruleVersionId, UUID bindingId) {
        QualityRunDto run = new QualityRunDto();
        run.setId(UUID.randomUUID());
        run.setRuleId(ruleId);
        run.setRuleVersionId(ruleVersionId);
        run.setBindingId(bindingId);
        run.setJobId(WORKFLOW_ID);
        return run;
    }

    private GovQualityWorkflowRun workflow(String status) {
        GovQualityWorkflowRun workflow = new GovQualityWorkflowRun();
        workflow.setId(WORKFLOW_ID);
        workflow.setTaskId(TASK_ID);
        workflow.setDatasetId(DATASET_ID);
        workflow.setAttemptNo(1);
        workflow.setMaxRetryAttempts(1);
        workflow.setRetryBackoffSeconds(0);
        workflow.setTriggerType("MANUAL");
        workflow.setTriggerRef("quality-workflow:manual:test");
        workflow.setIdempotencyKey("manual-102");
        workflow.setStatus(status);
        workflow.setExpectedRunCount(0);
        workflow.setCompletedRunCount(0);
        workflow.setPassedCount(0);
        workflow.setFailedCount(0);
        workflow.setDispatchFailureCount(0);
        workflow.setScheduledAt(Instant.now());
        workflow.setCreatedDate(Instant.now());
        workflow.setCreatedBy("xiezm");
        return workflow;
    }
}
