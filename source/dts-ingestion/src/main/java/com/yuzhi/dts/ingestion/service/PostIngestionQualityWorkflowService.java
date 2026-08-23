package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Durable, idempotent trigger boundary for the official quality workflow after a successful ingestion. */
@Service
public class PostIngestionQualityWorkflowService {

    static final int MAX_ATTEMPTS = 3;
    private final PostIngestionQualityWorkflowAttemptStore attemptStore;
    private final PlatformInfraClient platformInfraClient;

    public PostIngestionQualityWorkflowService(
        PostIngestionQualityWorkflowAttemptStore attemptStore,
        PlatformInfraClient platformInfraClient
    ) {
        this.attemptStore = attemptStore;
        this.platformInfraClient = platformInfraClient;
    }

    public AttemptResult trigger(Long executionId) {
        PostIngestionQualityWorkflowAttemptStore.AttemptDecision decision = attemptStore.prepare(executionId);
        if (!decision.dispatch()) {
            return decision.result();
        }
        try {
            PlatformInfraClient.QualityWorkflowReceipt receipt = platformInfraClient.triggerQualityWorkflowByPolicyRef(
                decision.qualityPolicyRef(),
                "INGESTION",
                executionId.toString()
            );
            if (!StringUtils.hasText(receipt.workflowId())) {
                throw new IllegalStateException("平台未返回质量工作流编号");
            }
            return attemptStore.complete(executionId, receipt);
        } catch (RuntimeException ex) {
            return attemptStore.fail(executionId, ex);
        }
    }

    public record AttemptResult(
        Long executionId,
        String workflowId,
        String qualityRunId,
        String status,
        int attemptCount,
        Instant nextRetryAt,
        String error
    ) {
        static AttemptResult notConfigured(Long executionId) {
            return new AttemptResult(executionId, null, null, "NOT_CONFIGURED", 0, null, null);
        }
    }
}
