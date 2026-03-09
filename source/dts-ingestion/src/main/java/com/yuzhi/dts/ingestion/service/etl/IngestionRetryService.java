package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatic retry service for failed ingestion executions.
 * Scans for failed executions whose nextRetryAt has passed and triggers retry.
 */
@Service
public class IngestionRetryService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRetryService.class);

    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskService taskService;
    private final IngestionProperties properties;

    public IngestionRetryService(
        IngestionExecutionRepository executionRepository,
        IngestionTaskService taskService,
        IngestionProperties properties
    ) {
        this.executionRepository = executionRepository;
        this.taskService = taskService;
        this.properties = properties;
    }

    /**
     * Scheduled scan for executions due for retry.
     * Runs at a configurable interval (default 30s).
     */
    @Scheduled(fixedDelayString = "${dts.ingestion.auto-retry.scan-interval-ms:30000}")
    @Transactional
    public void processRetryQueue() {
        if (!properties.getAutoRetry().isEnabled()) {
            return;
        }
        List<IngestionExecution> dueRetries = executionRepository.findDueForRetry(Instant.now());
        if (dueRetries.isEmpty()) {
            return;
        }
        LOG.info("[auto-retry] found {} execution(s) due for retry", dueRetries.size());
        for (IngestionExecution execution : dueRetries) {
            try {
                retryExecution(execution);
            } catch (Exception ex) {
                LOG.warn("[auto-retry] failed to retry execution id={} taskId={}: {}",
                    execution.getId(), execution.getTask().getId(), ex.getMessage());
                // Don't let one failure block others; mark as exhausted if retries exceeded
                if (execution.getRetryCount() >= execution.getMaxRetries()) {
                    execution.setRetryExhausted(true);
                    execution.setNextRetryAt(null);
                    executionRepository.save(execution);
                }
            }
        }
    }

    /**
     * Schedule an automatic retry for a failed execution if eligible.
     * Called from the execution failure handler.
     */
    public void scheduleRetryIfEligible(IngestionExecution execution) {
        IngestionProperties.AutoRetry config = properties.getAutoRetry();
        if (!config.isEnabled()) {
            return;
        }
        String category = execution.getFailureCategory();
        if (category == null || category.isBlank()) {
            return;
        }
        Set<String> retryable = config.getRetryableCategorySet();
        if (!retryable.contains(category)) {
            LOG.debug("[auto-retry] category '{}' not retryable for execution id={}", category, execution.getId());
            return;
        }
        IngestionTask task = execution.getTask();
        if (task == null || !"active".equalsIgnoreCase(task.getStatus())) {
            return;
        }
        int maxRetries = config.getMaxRetries();
        int currentRetry = execution.getRetryCount();
        if (currentRetry >= maxRetries) {
            execution.setRetryExhausted(true);
            execution.setNextRetryAt(null);
            LOG.info("[auto-retry] retries exhausted for execution id={} ({}/{})", execution.getId(), currentRetry, maxRetries);
            return;
        }
        // Calculate next retry with exponential backoff
        long delaySeconds = calculateDelay(currentRetry, config);
        Instant nextRetry = Instant.now().plus(delaySeconds, ChronoUnit.SECONDS);
        execution.setMaxRetries(maxRetries);
        execution.setNextRetryAt(nextRetry);
        LOG.info("[auto-retry] scheduled retry for execution id={} taskId={} attempt={}/{} at {}",
            execution.getId(), task.getId(), currentRetry + 1, maxRetries, nextRetry);
    }

    private void retryExecution(IngestionExecution failedExecution) {
        IngestionTask task = failedExecution.getTask();
        Long taskId = task.getId();
        Long executionId = failedExecution.getId();
        LOG.info("[auto-retry] retrying task id={} execution id={} (attempt {}/{})",
            taskId, executionId, failedExecution.getRetryCount() + 1, failedExecution.getMaxRetries());

        // Clear the retry schedule on the failed execution
        failedExecution.setRetryCount(failedExecution.getRetryCount() + 1);
        failedExecution.setNextRetryAt(null);
        if (failedExecution.getRetryCount() >= failedExecution.getMaxRetries()) {
            failedExecution.setRetryExhausted(true);
        }
        executionRepository.save(failedExecution);

        // Trigger retry via the public retryExecution API
        try {
            taskService.retryExecution(taskId, executionId, "FAILED_ONLY");
        } catch (Exception ex) {
            LOG.warn("[auto-retry] retry execution failed for task id={}: {}", taskId, ex.getMessage());
            // The new execution's failure handler will schedule its own retry if eligible
        }
    }

    private long calculateDelay(int retryCount, IngestionProperties.AutoRetry config) {
        double delay = config.getInitialDelaySeconds() * Math.pow(config.getBackoffMultiplier(), retryCount);
        return Math.min((long) delay, config.getMaxDelaySeconds());
    }
}
