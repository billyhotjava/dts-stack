package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.IngestionRequiresNewExecutor;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Automatic retry service for failed ingestion executions.
 * Scans for failed executions whose nextRetryAt has passed and triggers retry.
 */
@Service
public class IngestionRetryService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRetryService.class);
    private static final long DISPATCH_CLAIM_SECONDS = 120L;
    private static final int RECONCILIATION_BATCH_SIZE = 100;

    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskService taskService;
    private final IngestionProperties properties;
    private final EntityManager entityManager;
    private final IngestionRequiresNewExecutor requiresNewExecutor;

    public IngestionRetryService(
        IngestionExecutionRepository executionRepository,
        IngestionTaskService taskService,
        IngestionProperties properties,
        EntityManager entityManager,
        IngestionRequiresNewExecutor requiresNewExecutor
    ) {
        this.executionRepository = executionRepository;
        this.taskService = taskService;
        this.properties = properties;
        this.entityManager = entityManager;
        this.requiresNewExecutor = requiresNewExecutor;
    }

    /**
     * Scheduled scan for executions due for retry.
     * Runs at a configurable interval (default 30s).
     */
    @Scheduled(fixedDelayString = "${dts.ingestion.auto-retry.scan-interval-ms:30000}")
    public void processRetryQueue() {
        if (!properties.getAutoRetry().isEnabled()) {
            return;
        }
        reconcileUnscheduledFailures();
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

    void retryExecution(IngestionExecution failedExecution) {
        Long executionId = failedExecution.getId();
        RetryClaim claim = requiresNewExecutor.execute(() -> claimRetry(executionId, Instant.now()));
        if (claim == null) {
            return;
        }
        Long taskId = claim.taskId();
        LOG.info("[auto-retry] retrying task id={} execution id={} (attempt {}/{})",
            taskId, executionId, claim.retryCount() + 1, claim.maxRetries());
        try {
            taskService.retryExecution(taskId, executionId, "FAILED_ONLY");
        } catch (Exception ex) {
            boolean accepted = requiresNewExecutor.execute(() -> completeAcceptedRetry(executionId));
            if (!accepted) {
                requiresNewExecutor.executeWithoutResult(() -> rescheduleFailedDispatch(executionId));
            }
            throw ex;
        }
        requiresNewExecutor.executeWithoutResult(() -> completeAcceptedRetry(executionId));
    }

    private RetryClaim claimRetry(Long executionId, Instant now) {
        IngestionExecution locked = entityManager.find(IngestionExecution.class, executionId, LockModeType.PESSIMISTIC_WRITE);
        if (locked == null
            || !"failed".equalsIgnoreCase(locked.getStatus())
            || locked.isRetryExhausted()
            || locked.getNextRetryAt() == null
            || locked.getNextRetryAt().isAfter(now)
            || locked.getRetryCount() >= locked.getMaxRetries()
            || locked.getTask() == null
            || !"active".equalsIgnoreCase(locked.getTask().getStatus())) {
            return null;
        }
        locked.setNextRetryAt(now.plus(DISPATCH_CLAIM_SECONDS, ChronoUnit.SECONDS));
        executionRepository.saveAndFlush(locked);
        return new RetryClaim(
            locked.getId(),
            locked.getTask().getId(),
            locked.getRetryCount(),
            locked.getMaxRetries()
        );
    }

    private boolean completeAcceptedRetry(Long parentExecutionId) {
        IngestionExecution child = entityManager.createQuery(
                "select e from IngestionExecution e where e.parentExecutionId = :parentId order by e.id asc",
                IngestionExecution.class
            )
            .setParameter("parentId", parentExecutionId)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
        if (child == null) {
            return false;
        }
        IngestionExecution parent = entityManager.find(
            IngestionExecution.class,
            parentExecutionId,
            LockModeType.PESSIMISTIC_WRITE
        );
        if (parent == null) {
            return true;
        }
        parent.setRetryCount(Math.max(parent.getRetryCount(), child.getRetryCount()));
        parent.setNextRetryAt(null);
        parent.setRetryExhausted(parent.getRetryCount() >= parent.getMaxRetries());
        executionRepository.saveAndFlush(parent);
        return true;
    }

    private void rescheduleFailedDispatch(Long executionId) {
        IngestionExecution locked = entityManager.find(IngestionExecution.class, executionId, LockModeType.PESSIMISTIC_WRITE);
        if (locked == null || locked.isRetryExhausted()) {
            return;
        }
        if (completeAcceptedRetry(executionId)) {
            return;
        }
        long delaySeconds = calculateDelay(locked.getRetryCount(), properties.getAutoRetry());
        locked.setNextRetryAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
        locked.setRetryExhausted(false);
        executionRepository.saveAndFlush(locked);
    }

    private void reconcileUnscheduledFailures() {
        requiresNewExecutor.executeWithoutResult(() -> {
            List<IngestionExecution> stranded = entityManager.createQuery(
                    "select e from IngestionExecution e join fetch e.task t "
                        + "where lower(e.status) = 'failed' and e.nextRetryAt is null "
                        + "and e.retryExhausted = false and lower(t.status) = 'active' "
                        + "and not exists (select c.id from IngestionExecution c where c.parentExecutionId = e.id)",
                    IngestionExecution.class
                )
                .setMaxResults(RECONCILIATION_BATCH_SIZE)
                .getResultList();
            for (IngestionExecution execution : stranded) {
                scheduleRetryIfEligible(execution);
                executionRepository.save(execution);
            }
            executionRepository.flush();
        });
    }

    private long calculateDelay(int retryCount, IngestionProperties.AutoRetry config) {
        double delay = config.getInitialDelaySeconds() * Math.pow(config.getBackoffMultiplier(), retryCount);
        return Math.min((long) delay, config.getMaxDelaySeconds());
    }

    private record RetryClaim(Long executionId, Long taskId, int retryCount, int maxRetries) {}
}
