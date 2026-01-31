package com.yuzhi.dts.platform.service.ops;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpsRunSyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OpsRunSyncScheduler.class);
    private static final int MAX_TASKS = 200;
    private static final int MAX_RUNS = 50;

    private final IngestionServiceClient ingestionServiceClient;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;
    private final ExternalRunLogService externalRunLogService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OpsRunSyncScheduler(
        IngestionServiceClient ingestionServiceClient,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties,
        ExternalRunLogService externalRunLogService
    ) {
        this.ingestionServiceClient = ingestionServiceClient;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
        this.externalRunLogService = externalRunLogService;
    }

    @Scheduled(fixedDelayString = "${dts.ops.sync-interval-ms:60000}")
    public void syncRuns() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            syncIngestionRuns();
            syncAirflowRuns();
        } catch (Exception ex) {
            LOG.warn("[ops] sync runs failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    private void syncIngestionRuns() {
        if (!ingestionServiceClient.isEnabled()) {
            return;
        }
        ApiResponse<Map<String, Object>> response = ingestionServiceClient.listTasks(Map.of("page", 0, "size", MAX_TASKS));
        Map<String, Object> data = response == null ? null : response.getData();
        if (data == null) {
            return;
        }
        Object content = data.get("content");
        if (!(content instanceof List<?> list)) {
            return;
        }
        LOG.debug("[ops] syncing ingestion runs: tasks={}", list.size());
        for (Object item : list) {
            Long taskId = extractLong(item, "id");
            if (taskId == null) {
                continue;
            }
            ApiResponse<Map<String, Object>> executions = ingestionServiceClient.listExecutions(taskId, Map.of("page", 0, "size", MAX_RUNS));
            if (executions != null && executions.getData() != null) {
                externalRunLogService.syncIngestionExecutions(executions.getData(), Constants.SYSTEM);
            }
        }
    }

    private void syncAirflowRuns() {
        if (!airflowProperties.isEnabled()) {
            return;
        }
        Map<String, Object> payload = airflowClient.listDags(MAX_TASKS).orElse(Map.of());
        Object dags = payload.get("dags");
        if (!(dags instanceof List<?> list)) {
            return;
        }
        LOG.debug("[ops] syncing airflow runs: dags={}", list.size());
        for (Object item : list) {
            String dagId = extractString(item, "dag_id");
            if (!StringUtils.hasText(dagId)) {
                continue;
            }
            Map<String, Object> runsPayload = airflowClient.listDagRuns(dagId, MAX_RUNS).orElse(null);
            if (runsPayload == null || runsPayload.isEmpty()) {
                continue;
            }
            String entryKey = isDbtDag(dagId) ? ExternalRunLogService.ENTRY_DBT : ExternalRunLogService.ENTRY_AIRFLOW;
            externalRunLogService.syncAirflowRuns(entryKey, dagId, runsPayload, Constants.SYSTEM);
        }
    }

    private boolean isDbtDag(String dagId) {
        if (!StringUtils.hasText(dagId)) {
            return false;
        }
        String normalized = dagId.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("_dbt_") || normalized.startsWith("dbt_") || normalized.endsWith("_dbt")) {
            return true;
        }
        String configured = airflowProperties.getDagId();
        return StringUtils.hasText(configured) && configured.trim().equalsIgnoreCase(dagId.trim());
    }

    private Long extractLong(Object item, String key) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String extractString(Object item, String key) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
