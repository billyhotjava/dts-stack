package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AirflowExecutionSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowExecutionSyncService.class);

    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskRepository taskRepository;
    private final AirflowClient airflowClient;
    private final AirflowProperties properties;
    private final IngestionSettingsService settingsService;
    private final PlatformInfraClient platformInfraClient;
    private final AuditService auditService;

    public AirflowExecutionSyncService(
        IngestionExecutionRepository executionRepository,
        IngestionTaskRepository taskRepository,
        AirflowClient airflowClient,
        AirflowProperties properties,
        IngestionSettingsService settingsService,
        PlatformInfraClient platformInfraClient,
        AuditService auditService
    ) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.airflowClient = airflowClient;
        this.properties = properties;
        this.settingsService = settingsService;
        this.platformInfraClient = platformInfraClient;
        this.auditService = auditService;
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
        Page<IngestionExecution> page = executionRepository.findByStatus("running", PageRequest.of(0, batchSize));
        for (IngestionExecution execution : page.getContent()) {
            IngestionTask task = execution.getTask();
            if (task == null || !Boolean.TRUE.equals(task.getAirflowEnabled())) {
                continue;
            }
            String dagId = task.getAirflowDagId();
            String dagRunId = execution.getExecutionId();
            if (!StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId)) {
                continue;
            }
            Map<String, Object> dagRun = airflowClient.getDagRun(dagId, dagRunId).orElse(null);
            if (dagRun == null || dagRun.isEmpty()) {
                continue;
            }
            String state = toText(dagRun.get("state"));
            if (!StringUtils.hasText(state)) {
                continue;
            }
            String normalized = state.trim().toLowerCase(Locale.ROOT);
            if ("success".equals(normalized)) {
                markExecution(execution, task, "success", null);
            } else if ("failed".equals(normalized) || "error".equals(normalized)) {
                markExecution(execution, task, "failed", "Airflow state: " + state);
            }
        }
    }

    private void markExecution(IngestionExecution execution, IngestionTask task, String status, String errorMessage) {
        if (status.equalsIgnoreCase(execution.getStatus())) {
            return;
        }
        execution.setStatus(status);
        execution.setEndTime(Instant.now());
        if (StringUtils.hasText(errorMessage)) {
            execution.setErrorMessage(errorMessage);
        }
        executionRepository.save(execution);

        task.setLastExecutionStatus(status);
        if ("success".equalsIgnoreCase(status)) {
            task.setLastExecutedAt(execution.getEndTime());
        }
        taskRepository.save(task);
        LOG.info("[airflow] synced execution {} status={}", execution.getId(), status);
        if ("success".equalsIgnoreCase(status)) {
            triggerDbtIfConfigured(task, execution);
        }
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
}
