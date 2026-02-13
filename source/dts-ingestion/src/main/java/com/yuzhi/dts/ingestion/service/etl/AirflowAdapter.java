package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirflowAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowAdapter.class);

    private final AirflowClient client;
    private final AirflowProperties properties;
    private final AddaxProperties addaxProperties;
    private final IngestionSettingsService settingsService;

    public AirflowAdapter(
        AirflowClient client,
        AirflowProperties properties,
        AddaxProperties addaxProperties,
        IngestionSettingsService settingsService
    ) {
        this.client = client;
        this.properties = properties;
        this.addaxProperties = addaxProperties;
        this.settingsService = settingsService;
    }

    public record AirflowRequest(Boolean enabled, String dagId, String scheduleType, String cron, Integer intervalMinutes) {}

    public boolean isEnabled() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        return settings.getBoolean("enabled", properties.isEnabled());
    }

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
        String consistencyError = validateAddaxDagConsistency();
        if (StringUtils.hasText(consistencyError)) {
            result.put("enabled", true);
            result.put("status", "failed");
            result.put("message", consistencyError);
            return result;
        }

        String requestedDagId = normalize(request.dagId());
        String fallbackDagId = normalize(settings.getString("dagId", properties.getDagId()));
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (StringUtils.hasText(requestedDagId)) {
            candidates.add(requestedDagId);
        } else if (StringUtils.hasText(fallbackDagId) && !candidates.contains(fallbackDagId)) {
            candidates.add(fallbackDagId);
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
        int triggerRetrySeconds = settings.getInteger("dagTriggerRetrySeconds", properties.getDagTriggerRetrySeconds());
        int notFoundRetryWaitSeconds = settings.getInteger(
            "dagNotFoundRetryWaitSeconds",
            properties.getDagNotFoundRetryWaitSeconds()
        );
        AirflowClient.TriggerResult lastFailure = null;

        for (String dagId : candidates) {
            boolean dagReady = true;
            if (waitSeconds > 0) {
                dagReady = client.waitForDag(
                    dagId,
                    java.time.Duration.ofSeconds(waitSeconds),
                    java.time.Duration.ofSeconds(Math.max(1, pollSeconds))
                );
            }
            if (!dagReady) {
                lastFailure = buildDagNotReadyResult(dagId);
                continue;
            }

            AirflowClient.TriggerResult response = triggerWithRetry(dagId, payload, triggerRetrySeconds, pollSeconds);
            if (response.success()) {
                result.put("status", "triggered");
                result.put("dagId", dagId);
                result.put("payload", response.payload());
                return result;
            }

            if (response.statusCode() == 404 && notFoundRetryWaitSeconds > 0) {
                LOG.info(
                    "[airflow] dag {} trigger returned 404, waiting up to {}s for scheduler refresh",
                    dagId,
                    notFoundRetryWaitSeconds
                );
                boolean readyAfterRetry = client.waitForDag(
                    dagId,
                    java.time.Duration.ofSeconds(notFoundRetryWaitSeconds),
                    java.time.Duration.ofSeconds(Math.max(1, pollSeconds))
                );
                if (!readyAfterRetry) {
                    lastFailure = buildDagNotReadyResult(dagId);
                    continue;
                }
                response = triggerWithRetry(dagId, payload, triggerRetrySeconds, pollSeconds);
                if (response.success()) {
                    result.put("status", "triggered");
                    result.put("dagId", dagId);
                    result.put("payload", response.payload());
                    return result;
                }
            }

            lastFailure = response;
            if (response.statusCode() != 404) {
                break;
            }
        }

        result.put("status", "failed");
        if (lastFailure != null && lastFailure.statusCode() == 404) {
            result.put("code", "AIRFLOW_DAG_NOT_READY_TIMEOUT");
        }
        if (lastFailure != null && StringUtils.hasText(lastFailure.message())) {
            result.put("message", lastFailure.message());
        } else if (StringUtils.hasText(requestedDagId)) {
            result.put("message", buildDagNotReadyMessage(requestedDagId));
        } else {
            result.put("message", "DAG 触发失败");
        }
        return result;
    }

    private AirflowClient.TriggerResult triggerWithRetry(
        String dagId,
        Map<String, Object> payload,
        int retryWindowSeconds,
        int pollSeconds
    ) {
        AirflowClient.TriggerResult response = client.triggerDag(dagId, payload);
        if (response.success() || response.statusCode() != 404) {
            return response;
        }

        int safeRetryWindowSeconds = Math.max(0, retryWindowSeconds);
        if (safeRetryWindowSeconds == 0) {
            return response;
        }

        int safePollSeconds = Math.max(1, pollSeconds);
        int maxAttempts = Math.max(1, safeRetryWindowSeconds / safePollSeconds);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(safePollSeconds * 1000L);
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

    private AirflowClient.TriggerResult buildDagNotReadyResult(String dagId) {
        return new AirflowClient.TriggerResult(false, 404, buildDagNotReadyMessage(dagId), null);
    }

    private String buildDagNotReadyMessage(String dagId) {
        String importError = findDagImportError(dagId);
        if (StringUtils.hasText(importError)) {
            return "DAG 导入失败: " + importError;
        }
        return "DAG 未就绪: " + dagId;
    }

    private String findDagImportError(String dagId) {
        if (!StringUtils.hasText(dagId)) {
            return null;
        }
        List<Map<String, Object>> importErrors = client.listImportErrors(100).orElse(List.of());
        String fileNameNeedle = dagId + ".py";
        for (Map<String, Object> row : importErrors) {
            String fileName = asText(row.get("filename"));
            String stackTrace = asText(row.get("stack_trace"));
            boolean matched = containsIgnoreCase(fileName, fileNameNeedle)
                || containsIgnoreCase(stackTrace, fileNameNeedle)
                || containsIgnoreCase(stackTrace, dagId);
            if (!matched) {
                continue;
            }
            if (StringUtils.hasText(fileName) && StringUtils.hasText(stackTrace)) {
                return fileName + " | " + summarize(stackTrace);
            }
            if (StringUtils.hasText(fileName)) {
                return fileName;
            }
            if (StringUtils.hasText(stackTrace)) {
                return summarize(stackTrace);
            }
            return "DAG 文件导入失败";
        }
        return null;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean containsIgnoreCase(String text, String needle) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(needle)) {
            return false;
        }
        return text.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private String summarize(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        String compact = input.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (compact.length() <= 200) {
            return compact;
        }
        return compact.substring(0, 200) + "...";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String validateAddaxDagConsistency() {
        IngestionSettingsService.SettingsSnapshot addaxSettings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        IngestionSettingsService.SettingsSnapshot airflowSettings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        boolean addaxEnabled = addaxSettings.getBoolean("enabled", addaxProperties.isEnabled());
        boolean airflowEnabled = airflowSettings.getBoolean("enabled", properties.isEnabled());
        if (!addaxEnabled || !airflowEnabled) {
            return null;
        }
        String jobDir = addaxSettings.getString("jobDir", addaxProperties.getJobDir());
        String dagsDir = airflowSettings.getString("dagsDir", properties.getDagsDir());
        if (!StringUtils.hasText(jobDir) || !StringUtils.hasText(dagsDir)) {
            return "请先在集成配置中补齐 Addax 作业目录与 Airflow DAG 目录";
        }
        if (!pathEquals(jobDir, dagsDir)) {
            return "Addax 作业目录与 Airflow DAG 目录必须保持一致";
        }
        return null;
    }

    private boolean pathEquals(String left, String right) {
        String normalizedLeft = normalizeDir(left);
        String normalizedRight = normalizeDir(right);
        if (!StringUtils.hasText(normalizedLeft) || !StringUtils.hasText(normalizedRight)) {
            return false;
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private String normalizeDir(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
