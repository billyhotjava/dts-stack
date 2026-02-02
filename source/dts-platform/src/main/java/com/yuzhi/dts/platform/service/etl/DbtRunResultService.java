package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtRunResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtRunResultService.class);

    private final ObjectMapper objectMapper;
    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final ExternalRunLogService externalRunLogService;

    public DbtRunResultService(
        ObjectMapper objectMapper,
        DbtProperties properties,
        DbtConfigService configService,
        ExternalRunLogService externalRunLogService
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.configService = configService;
        this.externalRunLogService = externalRunLogService;
    }

    public DbtRunSyncResult syncFromRunResults() {
        if (!properties.isEnabled()) {
            return DbtRunSyncResult.disabled("dbt 未启用");
        }
        String projectDir = resolveProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return DbtRunSyncResult.empty("dbt 项目目录未配置");
        }
        String runResultsPath = projectDir + "/target/run_results.json";
        File file = java.nio.file.Paths.get(runResultsPath).toFile();
        if (!file.exists()) {
            return DbtRunSyncResult.empty("run_results.json 不存在，请先执行 dbt run");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(file, new TypeReference<>() {});
            Map<String, Object> metadata = asMap(raw.get("metadata"));
            String invocationId = text(metadata.get("invocation_id"));
            String generatedAt = text(metadata.get("generated_at"));
            List<Map<String, Object>> results = asList(raw.get("results"));

            int total = results.size();
            int success = 0;
            int failed = 0;
            int skipped = 0;
            Instant startedAt = null;
            Instant finishedAt = null;
            List<String> failedModels = new ArrayList<>();

            for (Map<String, Object> result : results) {
                String status = normalizeStatus(text(result.get("status")));
                String name = text(result.get("unique_id"));
                if (!StringUtils.hasText(name)) {
                    name = text(result.get("node"));
                }
                if (!StringUtils.hasText(name)) {
                    name = text(result.get("name"));
                }
                if ("SUCCESS".equals(status)) {
                    success++;
                } else if ("SKIPPED".equals(status)) {
                    skipped++;
                } else if (StringUtils.hasText(status)) {
                    failed++;
                    if (StringUtils.hasText(name)) {
                        failedModels.add(name);
                    }
                }
                Timing timing = extractTiming(result);
                if (timing.startedAt() != null) {
                    startedAt = minInstant(startedAt, timing.startedAt());
                }
                if (timing.finishedAt() != null) {
                    finishedAt = maxInstant(finishedAt, timing.finishedAt());
                }
            }

            if (startedAt == null && generatedAt != null) {
                startedAt = parseInstant(generatedAt);
            }
            String overall = failed > 0 ? "FAILED" : (success > 0 ? "SUCCESS" : "SKIPPED");
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("invocationId", invocationId);
            metrics.put("projectDir", projectDir);
            metrics.put("total", total);
            metrics.put("success", success);
            metrics.put("failed", failed);
            metrics.put("skipped", skipped);
            if (!failedModels.isEmpty()) {
                metrics.put("failedModels", failedModels.size() > 50 ? failedModels.subList(0, 50) : failedModels);
            }
            metrics.put("generatedAt", generatedAt);

            String externalRunId = StringUtils.hasText(invocationId) ? invocationId : "dbt:" + Instant.now().toString();
            externalRunLogService.upsertExternalRun(
                ExternalRunLogService.ENTRY_DBT,
                "DBT_RUN",
                "dbt_run",
                externalRunId,
                overall,
                startedAt != null ? startedAt : Instant.now(),
                finishedAt,
                failedModels.isEmpty() ? null : "failed_models=" + failedModels.size(),
                toJson(metrics),
                null
            );

            return new DbtRunSyncResult(
                true,
                true,
                "dbt 运行结果已同步",
                runResultsPath,
                invocationId,
                total,
                success,
                failed,
                skipped,
                overall
            );
        } catch (Exception ex) {
            LOG.warn("Failed to parse run_results.json: {}", ex.getMessage());
            return DbtRunSyncResult.empty("解析 run_results.json 失败: " + ex.getMessage());
        }
    }

    private String resolveProjectDir() {
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            return view.config().projectDir().trim();
        }
        return properties.getProjectDir();
    }

    private Timing extractTiming(Map<String, Object> result) {
        Object timingRaw = result.get("timing");
        if (!(timingRaw instanceof List<?> list)) {
            return new Timing(null, null);
        }
        Instant started = null;
        Instant finished = null;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String startedAt = text(map.get("started_at"));
            String completedAt = text(map.get("completed_at"));
            if (StringUtils.hasText(startedAt)) {
                Instant parsed = parseInstant(startedAt);
                started = minInstant(started, parsed);
            }
            if (StringUtils.hasText(completedAt)) {
                Instant parsed = parseInstant(completedAt);
                finished = maxInstant(finished, parsed);
            }
        }
        return new Timing(started, finished);
    }

    private Instant minInstant(Instant left, Instant right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
        return left.isBefore(right) ? left : right;
    }

    private Instant maxInstant(Instant left, Instant right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
        return left.isAfter(right) ? left : right;
    }

    private Instant parseInstant(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized) || "PASSED".equals(normalized)) {
            return "SUCCESS";
        }
        if ("SKIPPED".equals(normalized)) {
            return "SKIPPED";
        }
        if ("ERROR".equals(normalized) || "FAIL".equals(normalized) || "FAILED".equals(normalized)) {
            return "FAILED";
        }
        return normalized;
    }

    private Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> asList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                out.add(row);
            }
        }
        return out;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private record Timing(Instant startedAt, Instant finishedAt) {}

    public record DbtRunSyncResult(
        boolean enabled,
        boolean synced,
        String message,
        String runResultsPath,
        String invocationId,
        int total,
        int success,
        int failed,
        int skipped,
        String status
    ) {
        public static DbtRunSyncResult disabled(String message) {
            return new DbtRunSyncResult(false, false, message, null, null, 0, 0, 0, 0, null);
        }

        public static DbtRunSyncResult empty(String message) {
            return new DbtRunSyncResult(true, false, message, null, null, 0, 0, 0, 0, null);
        }
    }
}
