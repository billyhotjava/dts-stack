package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtRunResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtRunResultService.class);
    private static final String RUN_RESULTS_PATH = "/target/run_results.json";
    private static final String MANIFEST_PATH = "/target/manifest.json";

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
        DbtRunSummary summary = loadLatestSummary(50);
        if (summary == null || !summary.present()) {
            return DbtRunSyncResult.empty("run_results.json 不存在，请先执行 dbt run");
        }
        try {
            String invocationId = summary.invocationId();
            String generatedAt = summary.generatedAt();
            int total = summary.total();
            int success = summary.success();
            int failed = summary.failed();
            int skipped = summary.skipped();
            String overall = normalizeStatus(summary.status());
            Instant startedAt = parseInstant(generatedAt);
            Instant finishedAt = parseInstant(generatedAt);

            List<String> failedModels = new ArrayList<>();
            for (DbtRunFailure failure : summary.failures()) {
                if (StringUtils.hasText(failure.uniqueId())) {
                    failedModels.add(failure.uniqueId());
                } else if (StringUtils.hasText(failure.name())) {
                    failedModels.add(failure.name());
                }
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("invocationId", invocationId);
            metrics.put("projectDir", projectDir);
            metrics.put("command", summary.command());
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
                StringUtils.hasText(overall) ? overall : "RUNNING",
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
                summary.runResultsPath(),
                invocationId,
                total,
                success,
                failed,
                skipped,
                StringUtils.hasText(overall) ? overall : "UNKNOWN",
                summary
            );
        } catch (Exception ex) {
            LOG.warn("Failed to parse run_results.json: {}", ex.getMessage());
            return DbtRunSyncResult.empty("解析 run_results.json 失败: " + ex.getMessage());
        }
    }

    public DbtRunSummary loadLatestSummary(int failureLimit) {
        if (!properties.isEnabled()) {
            return DbtRunSummary.empty("dbt 未启用");
        }
        String projectDir = resolveProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return DbtRunSummary.empty("dbt 项目目录未配置");
        }
        String runResultsPath = projectDir + RUN_RESULTS_PATH;
        File runResultsFile = Path.of(runResultsPath).toFile();
        if (!runResultsFile.exists()) {
            return DbtRunSummary.empty("run_results.json 不存在");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(runResultsFile, new TypeReference<>() {});
            Map<String, Object> metadata = asMap(raw.get("metadata"));
            String invocationId = text(metadata.get("invocation_id"));
            String generatedAt = text(metadata.get("generated_at"));
            String command = argsAsCommand(metadata.get("args"));
            List<Map<String, Object>> results = asList(raw.get("results"));
            Map<String, ManifestNode> manifestNodes = readManifestNodes(projectDir + MANIFEST_PATH);

            int total = results.size();
            int success = 0;
            int failed = 0;
            int skipped = 0;
            List<DbtRunFailure> failures = new ArrayList<>();

            for (Map<String, Object> result : results) {
                String status = normalizeStatus(text(result.get("status")));
                String uniqueId = resolveUniqueId(result);
                ManifestNode manifestNode = StringUtils.hasText(uniqueId) ? manifestNodes.get(uniqueId) : null;
                String name = resolveNodeName(result, manifestNode);
                String resourceType = resolveResourceType(result, manifestNode);
                String path = resolvePath(result, manifestNode);

                if ("SUCCESS".equals(status)) {
                    success++;
                } else if ("SKIPPED".equals(status)) {
                    skipped++;
                } else {
                    failed++;
                    if (failures.size() < Math.max(1, failureLimit)) {
                        failures.add(
                            new DbtRunFailure(
                                uniqueId,
                                name,
                                resourceType,
                                path,
                                StringUtils.hasText(status) ? status : "FAILED",
                                resolveFailureMessage(result),
                                asDouble(result.get("execution_time"))
                            )
                        );
                    }
                }
            }

            String status = failed > 0 ? "FAILED" : (success > 0 ? "SUCCESS" : "SKIPPED");
            return new DbtRunSummary(
                true,
                projectDir,
                runResultsPath,
                projectDir + MANIFEST_PATH,
                invocationId,
                generatedAt,
                command,
                status,
                total,
                success,
                failed,
                skipped,
                failures
            );
        } catch (Exception ex) {
            LOG.warn("Failed to parse run_results.json summary: {}", ex.getMessage());
            return DbtRunSummary.empty("解析 run_results.json 失败: " + ex.getMessage());
        }
    }

    private String resolveProjectDir() {
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            return view.config().projectDir().trim();
        }
        return properties.getProjectDir();
    }

    private Map<String, ManifestNode> readManifestNodes(String manifestPath) {
        File file = Path.of(manifestPath).toFile();
        if (!file.exists()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(file, new TypeReference<>() {});
            Object nodesRaw = raw.get("nodes");
            if (!(nodesRaw instanceof Map<?, ?> nodes)) {
                return Map.of();
            }
            Map<String, ManifestNode> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nodes.entrySet()) {
                if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> node)) {
                    continue;
                }
                String uniqueId = String.valueOf(entry.getKey());
                out.put(
                    uniqueId,
                    new ManifestNode(
                        uniqueId,
                        text(node.get("name")),
                        text(node.get("resource_type")),
                        text(node.get("path"))
                    )
                );
            }
            return out;
        } catch (Exception ex) {
            return Map.of();
        }
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

    private String resolveUniqueId(Map<String, Object> result) {
        String uniqueId = text(result.get("unique_id"));
        if (StringUtils.hasText(uniqueId)) {
            return uniqueId;
        }
        Map<String, Object> node = asMap(result.get("node"));
        uniqueId = text(node.get("unique_id"));
        if (StringUtils.hasText(uniqueId)) {
            return uniqueId;
        }
        return text(result.get("node"));
    }

    private String resolveNodeName(Map<String, Object> result, ManifestNode manifestNode) {
        String name = text(result.get("name"));
        if (StringUtils.hasText(name)) {
            return name;
        }
        Map<String, Object> node = asMap(result.get("node"));
        name = text(node.get("name"));
        if (StringUtils.hasText(name)) {
            return name;
        }
        if (manifestNode != null && StringUtils.hasText(manifestNode.name())) {
            return manifestNode.name();
        }
        return null;
    }

    private String resolveResourceType(Map<String, Object> result, ManifestNode manifestNode) {
        String type = text(result.get("resource_type"));
        if (StringUtils.hasText(type)) {
            return type;
        }
        Map<String, Object> node = asMap(result.get("node"));
        type = text(node.get("resource_type"));
        if (StringUtils.hasText(type)) {
            return type;
        }
        if (manifestNode != null && StringUtils.hasText(manifestNode.resourceType())) {
            return manifestNode.resourceType();
        }
        return null;
    }

    private String resolvePath(Map<String, Object> result, ManifestNode manifestNode) {
        String path = text(result.get("path"));
        if (StringUtils.hasText(path)) {
            return path;
        }
        Map<String, Object> node = asMap(result.get("node"));
        path = text(node.get("path"));
        if (StringUtils.hasText(path)) {
            return path;
        }
        if (manifestNode != null && StringUtils.hasText(manifestNode.path())) {
            return manifestNode.path();
        }
        return null;
    }

    private String resolveFailureMessage(Map<String, Object> result) {
        String message = text(result.get("message"));
        if (StringUtils.hasText(message)) {
            return message;
        }
        String failures = text(result.get("failures"));
        if (StringUtils.hasText(failures)) {
            return failures;
        }
        Map<String, Object> adapterResponse = asMap(result.get("adapter_response"));
        message = text(adapterResponse.get("message"));
        if (StringUtils.hasText(message)) {
            return message;
        }
        return "执行失败";
    }

    private String argsAsCommand(Object argsRaw) {
        if (argsRaw == null) {
            return null;
        }
        if (argsRaw instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object part : list) {
                String text = text(part);
                if (StringUtils.hasText(text)) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join(" ", parts);
        }
        return text(argsRaw);
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
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

    private record ManifestNode(String uniqueId, String name, String resourceType, String path) {}

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
        String status,
        DbtRunSummary summary
    ) {
        public static DbtRunSyncResult disabled(String message) {
            return new DbtRunSyncResult(false, false, message, null, null, 0, 0, 0, 0, null, null);
        }

        public static DbtRunSyncResult empty(String message) {
            return new DbtRunSyncResult(true, false, message, null, null, 0, 0, 0, 0, null, null);
        }
    }

    public record DbtRunSummary(
        boolean present,
        String projectDir,
        String runResultsPath,
        String manifestPath,
        String invocationId,
        String generatedAt,
        String command,
        String status,
        int total,
        int success,
        int failed,
        int skipped,
        List<DbtRunFailure> failures
    ) {
        public static DbtRunSummary empty(String message) {
            return new DbtRunSummary(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                Collections.emptyList()
            );
        }
    }

    public record DbtRunFailure(
        String uniqueId,
        String name,
        String resourceType,
        String path,
        String status,
        String message,
        Double executionTime
    ) {}
}
