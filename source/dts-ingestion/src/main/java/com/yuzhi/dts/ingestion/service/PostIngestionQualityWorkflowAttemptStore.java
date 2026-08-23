package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Commits trigger intent and outcome independently from the external platform call. */
@Service
public class PostIngestionQualityWorkflowAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(PostIngestionQualityWorkflowAttemptStore.class);
    private static final long TRIGGER_LEASE_SECONDS = 120;

    private final IngestionExecutionRepository executionRepository;
    private final AuditService auditService;

    public PostIngestionQualityWorkflowAttemptStore(
        IngestionExecutionRepository executionRepository,
        AuditService auditService
    ) {
        this.executionRepository = executionRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AttemptDecision prepare(Long executionId) {
        IngestionExecution execution = load(executionId);
        if (!StringUtils.hasText(execution.getQualityPolicyRef())) {
            return AttemptDecision.finished(PostIngestionQualityWorkflowService.AttemptResult.notConfigured(executionId));
        }
        if (StringUtils.hasText(execution.getQualityWorkflowId())) {
            return AttemptDecision.finished(result(execution));
        }

        Instant now = Instant.now();
        String status = execution.getQualityWorkflowStatus();
        if (
            ("TRIGGERING".equals(status) || "RETRY_WAIT".equals(status)) &&
            execution.getQualityWorkflowNextRetryAt() != null &&
            execution.getQualityWorkflowNextRetryAt().isAfter(now)
        ) {
            return AttemptDecision.finished(result(execution));
        }
        int attempts = Math.max(0, execution.getQualityWorkflowAttemptCount());
        if (attempts >= PostIngestionQualityWorkflowService.MAX_ATTEMPTS) {
            execution.setQualityWorkflowStatus("EXHAUSTED");
            execution.setQualityWorkflowNextRetryAt(null);
            executionRepository.save(execution);
            return AttemptDecision.finished(result(execution));
        }

        execution.setQualityWorkflowAttemptCount(attempts + 1);
        execution.setQualityWorkflowStatus("TRIGGERING");
        execution.setQualityWorkflowError(null);
        execution.setQualityWorkflowNextRetryAt(now.plusSeconds(TRIGGER_LEASE_SECONDS));
        executionRepository.save(execution);
        return AttemptDecision.dispatch(execution.getQualityPolicyRef(), result(execution));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PostIngestionQualityWorkflowService.AttemptResult complete(
        Long executionId,
        PlatformInfraClient.QualityWorkflowReceipt receipt
    ) {
        IngestionExecution execution = load(executionId);
        if (StringUtils.hasText(execution.getQualityWorkflowId())) {
            return result(execution);
        }
        execution.setQualityWorkflowId(receipt.workflowId());
        execution.setQualityRunId(receipt.qualityRunId());
        execution.setQualityWorkflowStatus("TRIGGERED");
        execution.setQualityWorkflowError(null);
        execution.setQualityWorkflowNextRetryAt(null);
        executionRepository.save(execution);
        audit(execution, AuditStage.SUCCESS, null);
        return result(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PostIngestionQualityWorkflowService.AttemptResult fail(Long executionId, RuntimeException failure) {
        IngestionExecution execution = load(executionId);
        if (StringUtils.hasText(execution.getQualityWorkflowId())) {
            return result(execution);
        }
        int attempts = Math.max(1, execution.getQualityWorkflowAttemptCount());
        boolean exhausted = attempts >= PostIngestionQualityWorkflowService.MAX_ATTEMPTS;
        execution.setQualityWorkflowStatus(exhausted ? "EXHAUSTED" : "RETRY_WAIT");
        execution.setQualityWorkflowNextRetryAt(
            exhausted ? null : Instant.now().plusSeconds(30L * (1L << (attempts - 1)))
        );
        execution.setQualityWorkflowError("平台质量验证启动失败（" + failure.getClass().getSimpleName() + "）");
        executionRepository.save(execution);
        audit(execution, AuditStage.FAIL, failure);
        log.warn(
            "event=post_ingestion_quality_workflow_trigger_failed executionId={} attempt={} exhausted={} errorType={}",
            executionId,
            attempts,
            exhausted,
            failure.getClass().getSimpleName()
        );
        return result(execution);
    }

    private IngestionExecution load(Long executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("接入后质量验证缺少执行记录");
        }
        return executionRepository
            .findByIdForQualityWorkflowUpdate(executionId)
            .orElseThrow(() -> new IllegalArgumentException("接入执行记录不存在"));
    }

    private PostIngestionQualityWorkflowService.AttemptResult result(IngestionExecution execution) {
        return new PostIngestionQualityWorkflowService.AttemptResult(
            execution.getId(),
            execution.getQualityWorkflowId(),
            execution.getQualityRunId(),
            execution.getQualityWorkflowStatus(),
            execution.getQualityWorkflowAttemptCount(),
            execution.getQualityWorkflowNextRetryAt(),
            execution.getQualityWorkflowError()
        );
    }

    private void audit(IngestionExecution execution, AuditStage stage, RuntimeException failure) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", execution.getTask() == null ? null : execution.getTask().getId());
        meta.put("executionId", execution.getId());
        meta.put("qualityPolicyRef", execution.getQualityPolicyRef());
        meta.put("qualityWorkflowStatus", execution.getQualityWorkflowStatus());
        meta.put("qualityWorkflowAttemptCount", execution.getQualityWorkflowAttemptCount());
        if (StringUtils.hasText(execution.getQualityWorkflowId())) {
            meta.put("qualityWorkflowId", execution.getQualityWorkflowId());
        }
        if (failure != null) meta.put("errorType", failure.getClass().getSimpleName());
        try {
            auditService.auditAction(
                "INGESTION_TASK_QUALITY_TRIGGER",
                stage,
                execution.getTask() == null ? String.valueOf(execution.getId()) : execution.getTask().getName(),
                meta
            );
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=post_ingestion_quality_workflow_audit_failed executionId={} errorType={}",
                execution.getId(),
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    public record AttemptDecision(
        boolean dispatch,
        String qualityPolicyRef,
        PostIngestionQualityWorkflowService.AttemptResult result
    ) {
        static AttemptDecision dispatch(
            String qualityPolicyRef,
            PostIngestionQualityWorkflowService.AttemptResult result
        ) {
            return new AttemptDecision(true, qualityPolicyRef, result);
        }

        static AttemptDecision finished(PostIngestionQualityWorkflowService.AttemptResult result) {
            return new AttemptDecision(false, null, result);
        }
    }
}
