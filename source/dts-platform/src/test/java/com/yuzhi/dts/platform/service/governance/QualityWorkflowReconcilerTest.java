package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityWorkflowReconcilerTest {

    private static final UUID WORKFLOW_ID = UUID.fromString("40000000-0000-0000-0000-000000000103");

    @Mock private GovQualityWorkflowRunRepository workflowRepository;
    @Mock private GovQualityRunRepository runRepository;
    @Mock private QualityAuditRecorder auditRecorder;

    @Test
    void aggregatesChildRuleRunsIntoOneTerminalWorkflowOutcome() {
        GovQualityWorkflowRun workflow = new GovQualityWorkflowRun();
        workflow.setId(WORKFLOW_ID);
        workflow.setDatasetId(UUID.fromString("20000000-0000-0000-0000-000000000103"));
        workflow.setStatus("RUNNING");
        workflow.setExpectedRunCount(2);
        workflow.setDispatchFailureCount(0);
        GovQualityRun passed = new GovQualityRun();
        passed.setStatus("SUCCEEDED");
        GovQualityRun failed = new GovQualityRun();
        failed.setStatus("FAILED");
        when(runRepository.findByJobIdOrderByCreatedDateAsc(WORKFLOW_ID)).thenReturn(List.of(passed, failed));
        QualityWorkflowReconciler reconciler = new QualityWorkflowReconciler(
            workflowRepository,
            runRepository,
            auditRecorder
        );

        reconciler.reconcile(workflow);

        assertThat(workflow.getStatus()).isEqualTo("FAILED");
        assertThat(workflow.getCompletedRunCount()).isEqualTo(2);
        assertThat(workflow.getPassedCount()).isEqualTo(1);
        assertThat(workflow.getFailedCount()).isEqualTo(1);
        verify(auditRecorder).recordMachine(
            org.mockito.ArgumentMatchers.eq("quality-workflow-reconciler"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("GOV_QUALITY_WORKFLOW_FINALIZE"),
            org.mockito.ArgumentMatchers.eq(AuditStage.FAIL),
            org.mockito.ArgumentMatchers.eq(WORKFLOW_ID.toString()),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void marksTheWorkflowPassedWhenEveryExpectedRunSucceeds() {
        GovQualityWorkflowRun workflow = new GovQualityWorkflowRun();
        workflow.setId(WORKFLOW_ID);
        workflow.setDatasetId(UUID.fromString("20000000-0000-0000-0000-000000000103"));
        workflow.setStatus("RUNNING");
        workflow.setExpectedRunCount(2);
        workflow.setDispatchFailureCount(0);
        GovQualityRun first = new GovQualityRun();
        first.setStatus("SUCCEEDED");
        GovQualityRun second = new GovQualityRun();
        second.setStatus("SUCCEEDED");
        when(runRepository.findByJobIdOrderByCreatedDateAsc(WORKFLOW_ID)).thenReturn(List.of(first, second));
        QualityWorkflowReconciler reconciler = new QualityWorkflowReconciler(
            workflowRepository,
            runRepository,
            auditRecorder
        );

        reconciler.reconcile(workflow);

        assertThat(workflow.getStatus()).isEqualTo("PASSED");
        assertThat(workflow.getPassedCount()).isEqualTo(2);
        assertThat(workflow.getFailedCount()).isZero();
        assertThat(workflow.getFinishedAt()).isNotNull();
    }

    @Test
    void blocksAWorkflowThatExceededTheExecutionTimeout() {
        GovQualityWorkflowRun workflow = new GovQualityWorkflowRun();
        workflow.setId(WORKFLOW_ID);
        workflow.setDatasetId(UUID.fromString("20000000-0000-0000-0000-000000000103"));
        workflow.setStatus("RUNNING");
        workflow.setStartedAt(Instant.now().minusSeconds(61));
        workflow.setExpectedRunCount(1);
        workflow.setDispatchFailureCount(0);
        when(runRepository.findByJobIdOrderByCreatedDateAsc(WORKFLOW_ID)).thenReturn(List.of());
        QualityWorkflowReconciler reconciler = new QualityWorkflowReconciler(
            workflowRepository,
            runRepository,
            auditRecorder
        );
        reconciler.setWorkflowTimeoutSeconds(60);

        reconciler.reconcile(workflow);

        assertThat(workflow.getStatus()).isEqualTo("BLOCKED");
        assertThat(workflow.getErrorCategory()).isEqualTo("WORKFLOW_TIMEOUT");
        assertThat(workflow.getFinishedAt()).isNotNull();
        verify(auditRecorder).recordMachine(
            org.mockito.ArgumentMatchers.eq("quality-workflow-reconciler"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("GOV_QUALITY_WORKFLOW_FINALIZE"),
            org.mockito.ArgumentMatchers.eq(AuditStage.FAIL),
            org.mockito.ArgumentMatchers.eq(WORKFLOW_ID.toString()),
            org.mockito.ArgumentMatchers.any()
        );
    }
}
