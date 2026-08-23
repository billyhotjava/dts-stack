package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostIngestionQualityWorkflowRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostIngestionQualityWorkflowRetryScheduler.class);

    private final IngestionExecutionRepository executionRepository;
    private final PostIngestionQualityWorkflowService workflowService;

    public PostIngestionQualityWorkflowRetryScheduler(
        IngestionExecutionRepository executionRepository,
        PostIngestionQualityWorkflowService workflowService
    ) {
        this.executionRepository = executionRepository;
        this.workflowService = workflowService;
    }

    @Scheduled(fixedDelayString = "${dts.ingestion.quality-workflow-retry-delay-ms:30000}")
    public void retryDueWorkflows() {
        executionRepository
            .findDueQualityWorkflowRetries(Instant.now(), PageRequest.of(0, 50))
            .forEach(execution -> {
                try {
                    workflowService.trigger(execution.getId());
                } catch (RuntimeException ex) {
                    log.warn(
                        "event=post_ingestion_quality_workflow_retry_failed executionId={} errorType={}",
                        execution.getId(),
                        ex.getClass().getSimpleName()
                    );
                }
            });
    }
}
