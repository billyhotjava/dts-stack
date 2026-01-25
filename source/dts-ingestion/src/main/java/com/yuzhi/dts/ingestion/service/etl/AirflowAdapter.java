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
        String dagId = StringUtils.hasText(request.dagId())
            ? request.dagId().trim()
            : settings.getString("dagId", properties.getDagId());
        if (!StringUtils.hasText(dagId)) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "缺少 DAG 标识");
            return result;
        }
        result.put("enabled", true);
        result.put("dagId", dagId);
        if (!runNow) {
            result.put("status", "ready");
            return result;
        }
        Map<String, Object> payload = Map.of("conf", conf == null ? Map.of() : conf);
        Map<String, Object> response = client.triggerDag(dagId, payload).orElse(null);
        if (response == null || response.isEmpty()) {
            result.put("status", "failed");
            result.put("message", "DAG 触发失败");
        } else {
            result.put("status", "triggered");
            result.put("payload", response);
        }
        return result;
    }
}
