package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityWorkflowReconciler {

    private static final Logger log = LoggerFactory.getLogger(QualityWorkflowReconciler.class);
    private static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");
    private static final AtomicBoolean TABLE_NOT_READY_WARNED = new AtomicBoolean(false);

    private final GovQualityWorkflowRunRepository workflowRepository;
    private final GovQualityRunRepository runRepository;
    private final QualityAuditRecorder auditRecorder;
    private long workflowTimeoutSeconds = 1_800;

    public QualityWorkflowReconciler(
        GovQualityWorkflowRunRepository workflowRepository,
        GovQualityRunRepository runRepository,
        QualityAuditRecorder auditRecorder
    ) {
        this.workflowRepository = workflowRepository;
        this.runRepository = runRepository;
        this.auditRecorder = auditRecorder;
    }

    @Value("${dts.platform.governance.quality.workflow-timeout-seconds:1800}")
    void setWorkflowTimeoutSeconds(long workflowTimeoutSeconds) {
        this.workflowTimeoutSeconds = Math.max(60, workflowTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${dts.platform.governance.quality.workflow-reconcile-delay-ms:5000}")
    @Transactional
    public void reconcileActiveWorkflows() {
        List<GovQualityWorkflowRun> workflows;
        try {
            workflows = workflowRepository.findByStatusInOrderByCreatedDateAsc(
                ACTIVE_STATUSES,
                PageRequest.of(0, 100)
            );
        } catch (RuntimeException ex) {
            if (TABLE_NOT_READY_WARNED.compareAndSet(false, true)) {
                log.warn(
                    "event=quality_workflow_repository_unavailable errorType={} hint=verify_quality_workflow_migration",
                    ex.getClass().getSimpleName()
                );
            } else {
                log.debug("event=quality_workflow_repository_unavailable errorType={}", ex.getClass().getSimpleName());
            }
            return;
        }
        for (GovQualityWorkflowRun workflow : workflows) {
            reconcile(workflow);
        }
    }

    void reconcile(GovQualityWorkflowRun workflow) {
        List<GovQualityRun> runs = runRepository.findByJobIdOrderByCreatedDateAsc(workflow.getId());
        int passed = 0;
        int failed = 0;
        int completed = 0;
        for (GovQualityRun run : runs) {
            String status = StringUtils.trimToEmpty(run.getStatus()).toUpperCase(Locale.ROOT);
            if ("SUCCEEDED".equals(status)) {
                passed++;
                completed++;
            } else if ("FAILED".equals(status) || "SKIPPED".equals(status)) {
                failed++;
                completed++;
            }
        }
        workflow.setCompletedRunCount(completed);
        workflow.setPassedCount(passed);
        workflow.setFailedCount(failed);
        int expected = workflow.getExpectedRunCount() == null ? runs.size() : workflow.getExpectedRunCount();
        int dispatchFailures = workflow.getDispatchFailureCount() == null ? 0 : workflow.getDispatchFailureCount();
        Instant now = Instant.now();
        if (expected > 0 && completed >= expected) {
            workflow.setFinishedAt(now);
            if (failed > 0 || dispatchFailures > 0) {
                workflow.setStatus("FAILED");
                workflow.setErrorCategory(failed > 0 ? "QUALITY_RULE_FAILED" : "RULE_DISPATCH_FAILED");
                workflow.setMessage("数据质量工作流未通过");
            } else {
                workflow.setStatus("PASSED");
                workflow.setErrorCategory(null);
                workflow.setMessage("数据质量工作流已通过");
            }
            workflowRepository.save(workflow);
            recordCompletion(workflow);
        } else if (timedOut(workflow, now)) {
            workflow.setStatus("BLOCKED");
            workflow.setFinishedAt(now);
            workflow.setErrorCategory("WORKFLOW_TIMEOUT");
            workflow.setMessage("质量验证等待超时，请检查执行器后重新验证");
            workflowRepository.save(workflow);
            recordCompletion(workflow);
        } else {
            workflowRepository.save(workflow);
        }
    }

    private boolean timedOut(GovQualityWorkflowRun workflow, Instant now) {
        Instant startedAt = workflow.getStartedAt();
        Instant baseline = startedAt != null ? startedAt : workflow.getScheduledAt();
        return baseline != null && !baseline.plusSeconds(workflowTimeoutSeconds).isAfter(now);
    }

    private void recordCompletion(GovQualityWorkflowRun workflow) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "完成数据质量工作流");
        payload.put("workflowId", workflow.getId().toString());
        payload.put("datasetId", workflow.getDatasetId().toString());
        payload.put("status", workflow.getStatus());
        payload.put("completedRunCount", workflow.getCompletedRunCount());
        payload.put("passedCount", workflow.getPassedCount());
        payload.put("failedCount", workflow.getFailedCount());
        auditRecorder.recordMachine(
            "scheduler",
            workflow.getId() + ":FINALIZE:" + workflow.getStatus(),
            workflow.getFinishedAt(),
            "GOV_QUALITY_WORKFLOW_FINALIZE",
            "PASSED".equals(workflow.getStatus()) ? AuditStage.SUCCESS : AuditStage.FAIL,
            workflow.getId().toString(),
            payload
        );
    }
}
