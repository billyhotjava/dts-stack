package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirflowAdapter {

    private final AirflowClient client;
    private final AirflowProperties properties;
    private final IngestionSettingsService settingsService;

    public AirflowAdapter(AirflowClient client, AirflowProperties properties, IngestionSettingsService settingsService) {
        this.client = client;
        this.properties = properties;
        this.settingsService = settingsService;
    }

    public record AirflowRequest(Boolean enabled, String dagId, String scheduleType, String cron, Integer intervalMinutes) {}

    public Map<String, Object> triggerIfRequested(AirflowRequest request, Map<String, Object> conf, boolean runNow) {
        Map<String, Object> result = new LinkedHashMap<>();
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        boolean enabled = settings.getBoolean("enabled", properties.isEnabled());
        if (!enabled) {
            result.put("enabled", false);
            result.put("message", "Airflow 未启用");
            return result;
        }
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            result.put("enabled", false);
            result.put("message", "未启用编排");
            return result;
        }
        String requestedDagId = normalize(request.dagId());
        String fallbackDagId = normalize(settings.getString("dagId", properties.getDagId()));
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (StringUtils.hasText(requestedDagId)) {
            candidates.add(requestedDagId);
        } else {
            if (StringUtils.hasText(fallbackDagId) && !candidates.contains(fallbackDagId)) {
                candidates.add(fallbackDagId);
            }
        }
        if (candidates.isEmpty()) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "缺少 DAG 标识");
            return result;
        }
        result.put("enabled", true);
        result.put("dagId", candidates.get(0));
        if (!runNow) {
            result.put("status", "ready");
            return result;
        }
        Map<String, Object> payload = Map.of("conf", conf == null ? Map.of() : conf);
        int waitSeconds = settings.getInteger("dagReadyWaitSeconds", properties.getDagReadyWaitSeconds());
        int pollSeconds = settings.getInteger("dagReadyPollSeconds", properties.getDagReadyPollSeconds());
        AirflowClient.TriggerResult lastFailure = null;
        for (String dagId : candidates) {
            if (waitSeconds > 0) {
                boolean ready = client.waitForDag(
                    dagId,
                    java.time.Duration.ofSeconds(waitSeconds),
                    java.time.Duration.ofSeconds(Math.max(1, pollSeconds))
                );
                if (!ready) {
                    lastFailure = new AirflowClient.TriggerResult(false, 404, "DAG 未就绪: " + dagId, null);
                    continue;
                }
            }
            AirflowClient.TriggerResult response = triggerWithRetry(dagId, payload);
            if (response.success()) {
                result.put("status", "triggered");
                result.put("dagId", dagId);
                result.put("payload", response.payload());
                return result;
            }
            lastFailure = response;
            if (response.statusCode() != 404) {
                break;
            }
        }
        result.put("status", "failed");
        if (lastFailure != null && lastFailure.statusCode() == 404 && StringUtils.hasText(requestedDagId)) {
            result.put("message", "DAG 未就绪: " + requestedDagId);
        } else if (lastFailure != null && StringUtils.hasText(lastFailure.message())) {
            result.put("message", lastFailure.message());
        } else {
            result.put("message", "DAG 触发失败");
        }
        return result;
    }

    private AirflowClient.TriggerResult triggerWithRetry(String dagId, Map<String, Object> payload) {
        AirflowClient.TriggerResult response = client.triggerDag(dagId, payload);
        if (response.success() || response.statusCode() != 404) {
            return response;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
            response = client.triggerDag(dagId, payload);
            if (response.success() || response.statusCode() != 404) {
                return response;
            }
        }
        return response;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
