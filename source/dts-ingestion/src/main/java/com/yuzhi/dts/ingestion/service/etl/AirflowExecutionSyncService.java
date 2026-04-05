package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AirflowExecutionSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowExecutionSyncService.class);
    private static final int MAX_FAILURE_MSG_LEN = 4000;

    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskRepository taskRepository;
    private final AirflowClient airflowClient;
    private final AirflowProperties properties;
    private final IngestionSettingsService settingsService;
    private final PlatformInfraClient platformInfraClient;
    private final IncrementalSyncService incrementalSyncService;
    private final AuditService auditService;

    public AirflowExecutionSyncService(
        IngestionExecutionRepository executionRepository,
        IngestionTaskRepository taskRepository,
        AirflowClient airflowClient,
        AirflowProperties properties,
        IngestionSettingsService settingsService,
        PlatformInfraClient platformInfraClient,
        IncrementalSyncService incrementalSyncService,
        AuditService auditService
    ) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.airflowClient = airflowClient;
        this.properties = properties;
        this.settingsService = settingsService;
        this.platformInfraClient = platformInfraClient;
        this.incrementalSyncService = incrementalSyncService;
        this.auditService = auditService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleExecutions() {
        long thresholdMinutes = properties.getStaleExecutionThresholdMinutes() != null
            ? properties.getStaleExecutionThresholdMinutes() : 60L;
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));

        List<IngestionExecution> staleExecutions = executionRepository.findByStatusesIgnoreCase(
            List.of("running", "preparing")
        );

        int recovered = 0;
        for (IngestionExecution execution : staleExecutions) {
            try {
                Instant referenceTime = execution.getStartTime() != null
                    ? execution.getStartTime() : execution.getCreatedAt();
                if (referenceTime != null && referenceTime.isAfter(cutoff)) {
                    continue; // not stale yet
                }

                IngestionTask task = execution.getTask();
                boolean resolvedFromAirflow = false;

                if (task != null && Boolean.TRUE.equals(task.getAirflowEnabled())) {
                    String dagId = task.getAirflowDagId();
                    String dagRunId = execution.getExecutionId();
                    if (StringUtils.hasText(dagId) && StringUtils.hasText(dagRunId)) {
                        try {
                            AirflowClient.DagRunLookupResult dagRunLookup = airflowClient.getDagRunLookup(dagId, dagRunId);
                            Map<String, Object> dagRun = dagRunLookup.dagRun();
                            if (dagRunLookup.found()) {
                                String state = toText(dagRun.get("state"));
                                if (StringUtils.hasText(state)) {
                                    String normalized = state.trim().toLowerCase(Locale.ROOT);
                                    if ("success".equals(normalized)) {
                                        markExecution(execution, task, "success", null, dagId, dagRunId);
                                        LOG.warn("[recovery] execution {} recovered from Airflow as success", execution.getId());
                                        resolvedFromAirflow = true;
                                    } else if ("failed".equals(normalized) || "error".equals(normalized)) {
                                        String failureMessage = resolveAirflowFailureMessage(dagId, dagRunId, state);
                                        markExecution(execution, task, "failed", failureMessage, dagId, dagRunId);
                                        LOG.warn("[recovery] execution {} recovered from Airflow as failed", execution.getId());
                                        resolvedFromAirflow = true;
                                    }
                                }
                            } else if (dagRunLookup.notFound()) {
                                String failureMessage = resolveMissingDagRunMessage(dagId, dagRunId, dagRunLookup.message());
                                markExecution(execution, task, "failed", failureMessage, dagId, dagRunId);
                                LOG.warn("[recovery] execution {} recovered from Airflow as missing dag run", execution.getId());
                                resolvedFromAirflow = true;
                            }
                        } catch (Exception airflowEx) {
                            LOG.warn("[recovery] execution {} failed to query Airflow: {}", execution.getId(), airflowEx.getMessage());
                        }
                    }
                }

                if (!resolvedFromAirflow) {
                    execution.setStatus("failed");
                    execution.setEndTime(Instant.now());
                    execution.setErrorMessage("执行状态在服务重启后无法恢复，已标记为失败");
                    execution.setFailureCategory("RUNTIME");
                    execution.setFailureAdvice(ExecutionFailureClassifier.advice("RUNTIME"));
                    try {
                        executionRepository.save(execution);
                    } catch (OptimisticLockException ole) {
                        LOG.warn("[recovery] optimistic lock conflict on execution {} — skipping", execution.getId());
                        continue;
                    }

                    if (task != null) {
                        task.setLastExecutionStatus("failed");
                        taskRepository.save(task);
                    }
                    LOG.warn("[recovery] execution {} marked as failed (zombie recovery)", execution.getId());
                }
                recovered++;
            } catch (Exception ex) {
                LOG.warn("[recovery] failed to recover execution {}: {}", execution.getId(), ex.getMessage(), ex);
            }
        }
        if (recovered > 0) {
            LOG.warn("[recovery] recovered {} stale execution(s) on startup", recovered);
        } else if (!staleExecutions.isEmpty()) {
            LOG.info("[recovery] found {} in-progress execution(s) but none exceeded stale threshold of {} minutes",
                staleExecutions.size(), thresholdMinutes);
        }
    }

    @Scheduled(fixedDelayString = "${dts.airflow.execution-poll-interval-ms:15000}")
    @Transactional
    public void syncRunningExecutions() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        boolean enabled = settings.getBoolean("executionPollEnabled", properties.isExecutionPollEnabled());
        if (!enabled) {
            return;
        }
        int batchSize = settings.getInteger("executionPollBatchSize", properties.getExecutionPollBatchSize());
        if (batchSize <= 0) {
            return;
        }
        List<IngestionExecution> runningExecutions = executionRepository.findByStatusWithTask("running");
        if (runningExecutions.isEmpty()) {
            return;
        }
        // Apply batch limit
        List<IngestionExecution> batch = runningExecutions.size() > batchSize
            ? runningExecutions.subList(0, batchSize)
            : runningExecutions;

        int syncedCount = 0;
        int errorCount = 0;

        for (IngestionExecution execution : batch) {
            // Stop polling if we've hit too many consecutive errors (circuit breaker is open)
            if (errorCount >= 3) {
                LOG.warn("[airflow-sync] stopping poll after {} consecutive errors, {} remaining executions skipped",
                    errorCount, batch.size() - syncedCount);
                break;
            }

            IngestionTask task = execution.getTask();
            if (task == null || !Boolean.TRUE.equals(task.getAirflowEnabled())) {
                continue;
            }
            String dagId = task.getAirflowDagId();
            String dagRunId = execution.getExecutionId();
            if (!StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId)) {
                continue;
            }
            // Skip executions still in "preparing" phase — they haven't triggered Airflow yet
            if (dagRunId.startsWith("preparing-")) {
                continue;
            }

            AirflowClient.DagRunLookupResult dagRunLookup = airflowClient.getDagRunLookup(dagId, dagRunId);

            // Track Airflow connectivity errors for early termination
            if (dagRunLookup.statusCode() < 0 || dagRunLookup.statusCode() >= 500) {
                errorCount++;
                continue;
            }
            errorCount = 0; // reset on successful API call

            if (!dagRunLookup.found()) {
                if (dagRunLookup.notFound() && shouldFailMissingDagRun(execution)) {
                    String failureMessage = resolveMissingDagRunMessage(dagId, dagRunId, dagRunLookup.message());
                    markExecution(execution, task, "failed", failureMessage, dagId, dagRunId);
                }
                syncedCount++;
                continue;
            }
            Map<String, Object> dagRun = dagRunLookup.dagRun();
            String state = toText(dagRun.get("state"));
            if (!StringUtils.hasText(state)) {
                syncedCount++;
                continue;
            }
            String normalized = state.trim().toLowerCase(Locale.ROOT);
            if ("success".equals(normalized)) {
                markExecution(execution, task, "success", null, dagId, dagRunId);
            } else if ("failed".equals(normalized) || "error".equals(normalized)) {
                String failureMessage = resolveAirflowFailureMessage(dagId, dagRunId, state);
                markExecution(execution, task, "failed", failureMessage, dagId, dagRunId);
            }
            syncedCount++;
        }
    }

    private void markExecution(
        IngestionExecution execution,
        IngestionTask task,
        String status,
        String errorMessage,
        String dagId,
        String dagRunId
    ) {
        if (status.equalsIgnoreCase(execution.getStatus())) {
            return;
        }
        execution.setStatus(status);
        execution.setEndTime(Instant.now());
        if (StringUtils.hasText(errorMessage)) {
            String normalizedMessage = truncate(errorMessage, MAX_FAILURE_MSG_LEN);
            execution.setErrorMessage(normalizedMessage);
            String category = ExecutionFailureClassifier.classify(normalizedMessage);
            execution.setFailureCategory(category);
            execution.setFailureAdvice(ExecutionFailureClassifier.advice(category));
        } else {
            execution.setErrorMessage(null);
            execution.setFailureCategory(null);
            execution.setFailureAdvice(null);
        }
        try {
            executionRepository.save(execution);
        } catch (OptimisticLockException ole) {
            LOG.warn("[airflow] optimistic lock conflict on execution {} — skipping (concurrent update)", execution.getId());
            return;
        }

        task.setLastExecutionStatus(status);
        if ("success".equalsIgnoreCase(status)) {
            task.setLastExecutedAt(execution.getEndTime());
        }
        taskRepository.save(task);
        LOG.info("[airflow] synced execution {} status={}", execution.getId(), status);
        if ("success".equalsIgnoreCase(status)) {
            incrementalSyncService.updateCheckpointOnSuccess(task, execution);
            triggerDbtIfConfigured(task, execution);
            triggerPostIngestionQualityCheck(task, execution);
            return;
        }

        // 补齐失败审计（启动阶段成功后，最终失败需要单独审计记录）
        if ("failed".equalsIgnoreCase(status)) {
            String message = toText(execution.getErrorMessage());
            String category = ExecutionFailureClassifier.classify(message);
            String advice = ExecutionFailureClassifier.advice(category);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("dagId", dagId);
            meta.put("dagRunId", dagRunId);
            meta.put("failureCategory", category);
            meta.put("failureAdvice", advice);
            if (StringUtils.hasText(message)) {
                meta.put("error", message);
            }
            auditService.auditAction("INGESTION_TASK_EXECUTE", AuditStage.FAIL, task.getName(), meta);
        }
    }

    private String resolveAirflowFailureMessage(String dagId, String dagRunId, String dagState) {
        String state = StringUtils.hasText(dagState) ? dagState.trim() : "failed";
        List<Map<String, Object>> instances = airflowClient.listTaskInstances(dagId, dagRunId).orElse(List.of());
        if (instances.isEmpty()) {
            return "Airflow state: " + state;
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        for (Map<String, Object> instance : instances) {
            String taskState = normalize(toText(instance.get("state")));
            if ("failed".equals(taskState) || "error".equals(taskState) || "upstream_failed".equals(taskState)) {
                failed.add(instance);
            }
        }
        List<Map<String, Object>> candidates = failed.isEmpty() ? instances : failed;
        List<String> details = new ArrayList<>();
        int maxTasks = Math.min(3, candidates.size());
        for (int i = 0; i < maxTasks; i++) {
            Map<String, Object> instance = candidates.get(i);
            String taskId = toText(firstNonNull(instance.get("task_id"), instance.get("taskId")));
            if (!StringUtils.hasText(taskId)) {
                continue;
            }
            String taskState = toText(instance.get("state"));
            Integer hintTry = resolveTryNumber(instance);
            String excerpt = null;
            for (Integer candidateTry : resolveTryCandidates(hintTry)) {
                String logText = airflowClient.getTaskLog(dagId, dagRunId, taskId, candidateTry).orElse(null);
                excerpt = extractFailureExcerpt(logText);
                if (StringUtils.hasText(excerpt)) {
                    break;
                }
            }
            if (StringUtils.hasText(excerpt)) {
                details.add("task=" + taskId + " state=" + taskState + " msg=" + excerpt);
            } else {
                details.add("task=" + taskId + " state=" + taskState);
            }
        }
        if (details.isEmpty()) {
            return "Airflow state: " + state;
        }
        return truncate("Airflow state: " + state + "; " + String.join(" | ", details), MAX_FAILURE_MSG_LEN);
    }

    private boolean shouldFailMissingDagRun(IngestionExecution execution) {
        long graceSeconds = properties.getDagNotFoundRetryWaitSeconds() == null
            ? 120L
            : Math.max(30L, properties.getDagNotFoundRetryWaitSeconds().longValue());
        Instant referenceTime = execution.getStartTime() != null ? execution.getStartTime() : execution.getCreatedAt();
        if (referenceTime == null) {
            return true;
        }
        return referenceTime.isBefore(Instant.now().minusSeconds(graceSeconds));
    }

    private String resolveMissingDagRunMessage(String dagId, String dagRunId, String detail) {
        StringBuilder message = new StringBuilder("Airflow DAGRun not found: dag=");
        message.append(dagId).append(", run=").append(dagRunId);
        String normalizedDetail = normalizeDetail(detail);
        if (StringUtils.hasText(normalizedDetail)) {
            message.append("; detail=").append(normalizedDetail);
        }
        return truncate(message.toString(), MAX_FAILURE_MSG_LEN);
    }

    private List<Integer> resolveTryCandidates(Integer hintTry) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        if (hintTry != null && hintTry > 0) {
            ordered.add(hintTry);
            if (hintTry > 1) {
                ordered.add(hintTry - 1);
            }
        }
        ordered.add(1);
        return new ArrayList<>(ordered);
    }

    private Integer resolveTryNumber(Map<String, Object> instance) {
        return firstInteger(instance.get("try_number"), instance.get("tryNumber"), instance.get("try"));
    }

    private Integer firstInteger(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Integer parsed = toInteger(value);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            String text = value.toString().trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extractFailureExcerpt(String logText) {
        if (!StringUtils.hasText(logText)) {
            return null;
        }
        String normalized = logText.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "").replace("\r", "");
        String[] lines = normalized.split("\\n");
        if (lines.length == 0) {
            return null;
        }

        int focusIndex = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            String lower = normalize(lines[i]);
            if (containsAny(lower, "error", "exception", "traceback", "failed", "denied", "not found", "cannot", "sqlstate")) {
                focusIndex = i;
                break;
            }
        }

        int from;
        int to;
        if (focusIndex >= 0) {
            from = Math.max(0, focusIndex - 3);
            to = Math.min(lines.length, focusIndex + 9);
        } else {
            from = Math.max(0, lines.length - 20);
            to = lines.length;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(line);
        }
        String excerpt = sb.toString().trim();
        return StringUtils.hasText(excerpt) ? truncate(excerpt, 1200) : null;
    }

    private boolean containsAny(String text, String... markers) {
        if (!StringUtils.hasText(text) || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (StringUtils.hasText(marker) && text.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDetail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return truncate(value.replaceAll("\\s+", " ").trim(), 512);
    }

    private String truncate(String value, int maxLen) {
        if (!StringUtils.hasText(value) || maxLen <= 0) {
            return value;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - 3) + "...";
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private void triggerDbtIfConfigured(IngestionTask task, IngestionExecution execution) {
        if (task == null) {
            return;
        }
        String models = task.getDbtModelSelector();
        if (!StringUtils.hasText(models)) {
            return;
        }
        String dagSelector = task.getDbtDagSelector();
        try {
            Map<String, Object> result = platformInfraClient.triggerDbtRun(models, dagSelector);
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("models", models);
            if (StringUtils.hasText(dagSelector)) {
                meta.put("dagSelector", dagSelector);
            }
            meta.put("result", result);
            auditService.auditAction(
                "INGESTION_TASK_DBT_TRIGGER",
                AuditStage.SUCCESS,
                task.getName(),
                meta
            );
        } catch (Exception ex) {
            LOG.warn("[dbt] trigger failed task={} err={}", task.getId(), ex.getMessage());
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("models", models);
            if (StringUtils.hasText(dagSelector)) {
                meta.put("dagSelector", dagSelector);
            }
            meta.put("error", ex.getMessage());
            auditService.auditAction(
                "INGESTION_TASK_DBT_TRIGGER",
                AuditStage.FAIL,
                task.getName(),
                meta
            );
        }
    }

    /**
     * After a successful ingestion, trigger cleansing + quality check on the platform.
     * Only fires for non-pre-check tasks (pre-check tasks already ran their quality validation
     * before data was committed). This is best-effort — failures are logged but do not
     * affect the ingestion result.
     */
    private void triggerPostIngestionQualityCheck(IngestionTask task, IngestionExecution execution) {
        if (task == null) {
            return;
        }
        // Pre-check tasks already went through quality validation — skip
        if (Boolean.TRUE.equals(task.getQualityPreCheckEnabled())) {
            return;
        }
        java.util.UUID datasetId = task.getSourceDataSourceId();
        if (datasetId == null) {
            return;
        }
        try {
            LOG.info("[quality] triggering post-ingestion quality check for task={} datasetId={}", task.getId(), datasetId);
            platformInfraClient.triggerQualityRun(datasetId, "INGESTION");
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("datasetId", datasetId.toString());
            meta.put("triggerType", "INGESTION");
            auditService.auditAction(
                "INGESTION_TASK_QUALITY_TRIGGER",
                AuditStage.SUCCESS,
                task.getName(),
                meta
            );
        } catch (Exception ex) {
            LOG.warn("[quality] post-ingestion quality check trigger failed for task={}: {}", task.getId(), ex.getMessage());
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("datasetId", datasetId.toString());
            meta.put("error", ex.getMessage());
            auditService.auditAction(
                "INGESTION_TASK_QUALITY_TRIGGER",
                AuditStage.FAIL,
                task.getName(),
                meta
            );
        }
    }
}
