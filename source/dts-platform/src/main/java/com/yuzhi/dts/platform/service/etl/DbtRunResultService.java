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
import java.util.Set;
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
            return fallbackSummary(projectDir, runResultsPath, projectDir + MANIFEST_PATH, "run_results.json 不存在");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(runResultsFile, new TypeReference<>() {});
            Map<String, Object> metadata = asMap(raw.get("metadata"));
            String invocationId = text(metadata.get("invocation_id"));
            String generatedAt = text(metadata.get("generated_at"));
            String command = argsAsCommand(metadata.get("args"));
            if (!StringUtils.hasText(command)) {
                command = argsAsCommand(raw.get("args"));
            }
            List<Map<String, Object>> results = asList(raw.get("results"));
            Map<String, ManifestNode> manifestNodes = readManifestNodes(projectDir + MANIFEST_PATH);

            int total = results.size();
            int success = 0;
            int failed = 0;
            int skipped = 0;
            List<DbtRunFailure> failures = new ArrayList<>();
            List<DbtTestDetail> testDetails = new ArrayList<>();

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

                // Collect test details for test nodes
                if ("test".equalsIgnoreCase(resourceType)) {
                    String rawStatus = text(result.get("status"));
                    String compiledSql = text(result.get("compiled_code"));
                    if (compiledSql == null && manifestNode != null) {
                        compiledSql = manifestNode.compiledCode();
                    }
                    double execTime = asDouble(result.get("execution_time")) != null
                        ? asDouble(result.get("execution_time")) : 0.0;

                    int failuresCount = -1;
                    Map<String, Object> adapterResponse = asMap(result.get("adapter_response"));
                    Object rowsAffected = adapterResponse.get("rows_affected");
                    if (rowsAffected != null) {
                        try {
                            failuresCount = Integer.parseInt(String.valueOf(rowsAffected));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (failuresCount == -1) {
                        Object failuresRaw = result.get("failures");
                        if (failuresRaw instanceof Number n) {
                            failuresCount = n.intValue();
                        }
                    }

                    String testType = manifestNode != null ? manifestNode.testType() : null;
                    String testedColumn = manifestNode != null ? manifestNode.testedColumn() : null;
                    String testedModel = manifestNode != null ? manifestNode.testedModel() : null;
                    if (testType == null) {
                        testType = inferTestType(name);
                    }

                    testDetails.add(new DbtTestDetail(
                        uniqueId, name, testType, testedColumn, testedModel,
                        rawStatus, resolveFailureMessage(result), compiledSql,
                        execTime, failuresCount
                    ));
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
                failures,
                testDetails
            );
        } catch (Exception ex) {
            LOG.warn("Failed to parse run_results.json summary: {}", ex.getMessage());
            return fallbackSummary(projectDir, runResultsPath, projectDir + MANIFEST_PATH, "解析 run_results.json 失败: " + ex.getMessage());
        }
    }

    public DbtRunSummary loadLatestBuildSummary(int failureLimit) {
        DbtRunSummary localSummary = loadLatestSummary(failureLimit);
        if (!properties.isEnabled()) {
            return localSummary;
        }
        String projectDir = resolveProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return localSummary;
        }
        String runResultsPath = projectDir + RUN_RESULTS_PATH;
        String manifestPath = projectDir + MANIFEST_PATH;
        DbtRunSummary externalSummary = externalRunLogService
            .findLatestDbtBuildEvidence()
            .map(snapshot -> buildSummaryFromEvidence(projectDir, runResultsPath, manifestPath, snapshot))
            .orElse(null);

        if (shouldPreferExternalBuildSummary(localSummary, externalSummary)) {
            return externalSummary;
        }
        if (localSummary != null && localSummary.present() && isBuildLikeCommand(localSummary.command())) {
            return localSummary;
        }
        if (externalSummary != null) {
            return externalSummary;
        }
        return localSummary == null ? DbtRunSummary.empty("未发现可用构建记录") : localSummary;
    }

    public List<DbtTestDetail> getTestDetails() {
        if (!properties.isEnabled()) {
            return Collections.emptyList();
        }
        String projectDir = resolveProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return Collections.emptyList();
        }
        String runResultsPath = projectDir + RUN_RESULTS_PATH;
        File runResultsFile = Path.of(runResultsPath).toFile();
        if (!runResultsFile.exists()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(runResultsFile, new TypeReference<>() {});
            List<Map<String, Object>> results = asList(raw.get("results"));
            Map<String, ManifestNode> manifestNodes = readManifestNodes(projectDir + MANIFEST_PATH);

            List<DbtTestDetail> details = new ArrayList<>();
            for (Map<String, Object> result : results) {
                String uniqueId = resolveUniqueId(result);
                String resourceType = resolveResourceType(result,
                    StringUtils.hasText(uniqueId) ? manifestNodes.get(uniqueId) : null);
                if (!"test".equalsIgnoreCase(resourceType)) {
                    continue;
                }
                ManifestNode manifestNode = StringUtils.hasText(uniqueId) ? manifestNodes.get(uniqueId) : null;
                String name = resolveNodeName(result, manifestNode);
                String status = text(result.get("status"));
                String message = resolveFailureMessage(result);
                String compiledSql = text(result.get("compiled_code"));
                if (compiledSql == null && manifestNode != null) {
                    compiledSql = manifestNode.compiledCode();
                }
                double executionTime = asDouble(result.get("execution_time")) != null
                    ? asDouble(result.get("execution_time")) : 0.0;

                int failuresCount = -1;
                Map<String, Object> adapterResponse = asMap(result.get("adapter_response"));
                Object rowsAffected = adapterResponse.get("rows_affected");
                if (rowsAffected != null) {
                    try {
                        failuresCount = Integer.parseInt(String.valueOf(rowsAffected));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (failuresCount == -1) {
                    // try the top-level "failures" field (integer count in dbt)
                    Object failuresRaw = result.get("failures");
                    if (failuresRaw instanceof Number n) {
                        failuresCount = n.intValue();
                    }
                }

                String testType = manifestNode != null ? manifestNode.testType() : null;
                String testedColumn = manifestNode != null ? manifestNode.testedColumn() : null;
                String testedModel = manifestNode != null ? manifestNode.testedModel() : null;
                if (testType == null) {
                    testType = inferTestType(name);
                }

                details.add(new DbtTestDetail(
                    uniqueId, name, testType, testedColumn, testedModel,
                    status, message, compiledSql, executionTime, failuresCount
                ));
            }
            return details;
        } catch (Exception ex) {
            LOG.warn("Failed to parse test details from run_results.json: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String inferTestType(String name) {
        if (!StringUtils.hasText(name)) {
            return "custom";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("not_null_") || lower.contains("_not_null_")) {
            return "not_null";
        }
        if (lower.startsWith("unique_") || lower.contains("_unique_")) {
            return "unique";
        }
        if (lower.startsWith("accepted_values_") || lower.contains("_accepted_values_")) {
            return "accepted_values";
        }
        if (lower.startsWith("relationships_") || lower.contains("_relationships_")) {
            return "relationships";
        }
        return "custom";
    }

    private String resolveProjectDir() {
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            return view.config().projectDir().trim();
        }
        return properties.getProjectDir();
    }

    private DbtRunSummary fallbackSummary(String projectDir, String runResultsPath, String manifestPath, String message) {
        return externalRunLogService
            .findLatestDbtBuildEvidence()
            .map(snapshot -> buildSummaryFromEvidence(projectDir, runResultsPath, manifestPath, snapshot))
            .orElseGet(() -> DbtRunSummary.empty(message));
    }

    private DbtRunSummary buildSummaryFromEvidence(
        String projectDir,
        String runResultsPath,
        String manifestPath,
        ExternalRunLogService.DbtBuildEvidenceSnapshot snapshot
    ) {
        String status = normalizeStatus(snapshot.status());
        int total = resolveFallbackTotal(manifestPath, snapshot);
        int success = "SUCCESS".equals(status) ? total : 0;
        int failed = "FAILED".equals(status) ? Math.max(total, 1) : 0;
        int skipped = "SKIPPED".equals(status) ? total : 0;
        Instant generatedAt = snapshot.generatedAt() != null ? snapshot.generatedAt() : snapshot.startedAt();
        return new DbtRunSummary(
            true,
            projectDir,
            runResultsPath,
            manifestPath,
            snapshot.externalRunId(),
            generatedAt != null ? generatedAt.toString() : null,
            snapshot.command(),
            StringUtils.hasText(status) ? status : "UNKNOWN",
            total,
            success,
            failed,
            skipped,
            List.of(),
            List.of()
        );
    }

    private boolean shouldPreferExternalBuildSummary(DbtRunSummary localSummary, DbtRunSummary externalSummary) {
        if (externalSummary == null || !externalSummary.present()) {
            return false;
        }
        if (localSummary == null || !localSummary.present()) {
            return true;
        }
        if (!isBuildLikeCommand(localSummary.command())) {
            return true;
        }
        Instant localGeneratedAt = parseInstant(localSummary.generatedAt());
        Instant externalGeneratedAt = parseInstant(externalSummary.generatedAt());
        if (externalGeneratedAt != null && localGeneratedAt == null) {
            return true;
        }
        return externalGeneratedAt != null && localGeneratedAt != null && externalGeneratedAt.isAfter(localGeneratedAt);
    }

    private boolean isBuildLikeCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        return (
            "compile".equals(normalized) ||
            "test".equals(normalized) ||
            "build".equals(normalized) ||
            normalized.startsWith("compile ") ||
            normalized.startsWith("test ") ||
            normalized.startsWith("build ") ||
            normalized.startsWith("dbt compile") ||
            normalized.startsWith("dbt test") ||
            normalized.startsWith("dbt build") ||
            normalized.contains(" dbt compile") ||
            normalized.contains(" dbt test") ||
            normalized.contains(" dbt build")
        );
    }

    private int resolveFallbackTotal(String manifestPath, ExternalRunLogService.DbtBuildEvidenceSnapshot snapshot) {
        int selectedCount = Math.max(0, snapshot.selectedCount());
        if (selectedCount > 1 || !StringUtils.hasText(snapshot.command())) {
            return selectedCount;
        }
        int manifestCount = countManifestNodesForCommand(manifestPath, snapshot.command());
        return manifestCount > 0 ? manifestCount : selectedCount;
    }

    private int countManifestNodesForCommand(String manifestPath, String command) {
        if (!StringUtils.hasText(command)) {
            return 0;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        String resourceType = normalized.startsWith("dbt test") ? "test" : "model";
        String selector = extractSelector(command);
        int count = 0;
        for (ManifestNode node : readManifestNodes(manifestPath).values()) {
            if (!resourceType.equalsIgnoreCase(node.resourceType())) {
                continue;
            }
            if (selectorMatches(node, selector)) {
                count++;
            }
        }
        return count;
    }

    private boolean selectorMatches(ManifestNode node, String selector) {
        if (!StringUtils.hasText(selector)) {
            return true;
        }
        String normalizedSelector = selector.trim();
        if (normalizedSelector.isEmpty()) {
            return true;
        }
        for (String token : normalizedSelector.split(",")) {
            String current = token.trim();
            if (current.isEmpty()) {
                continue;
            }
            String lower = current.toLowerCase(Locale.ROOT);
            if (lower.startsWith("tag:")) {
                String tag = current.substring(4).trim().toLowerCase(Locale.ROOT);
                if (!tag.isEmpty() && node.tags().contains(tag)) {
                    return true;
                }
                continue;
            }
            if (current.equalsIgnoreCase(node.name()) || current.equalsIgnoreCase(node.uniqueId())) {
                return true;
            }
        }
        return false;
    }

    private String extractSelector(String command) {
        if (!StringUtils.hasText(command)) {
            return null;
        }
        String[] parts = command.trim().split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if ("--select".equals(part) || "--models".equals(part)) {
                return parts[i + 1];
            }
        }
        return null;
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
                String testType = null;
                String testedColumn = null;
                String testedModel = null;
                Map<String, Object> testMetadata = asMap(node.get("test_metadata"));
                if (!testMetadata.isEmpty()) {
                    testType = text(testMetadata.get("name"));
                    Map<String, Object> kwargs = asMap(testMetadata.get("kwargs"));
                    testedColumn = text(kwargs.get("column_name"));
                    testedModel = text(kwargs.get("model"));
                }
                if (testedModel == null) {
                    // fallback: try depends_on.nodes for the tested model
                    Map<String, Object> dependsOn = asMap(node.get("depends_on"));
                    Object depNodes = dependsOn.get("nodes");
                    if (depNodes instanceof List<?> depList && !depList.isEmpty()) {
                        testedModel = text(depList.get(0));
                    }
                }
                Set<String> tags = Set.copyOf(stringList(node.get("tags")));
                out.put(
                    uniqueId,
                    new ManifestNode(
                        uniqueId,
                        text(node.get("name")),
                        text(node.get("resource_type")),
                        text(node.get("path")),
                        testType,
                        testedColumn,
                        testedModel,
                        text(node.get("compiled_code")),
                        tags
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
        if ("SUCCESS".equals(normalized) || "PASSED".equals(normalized) || "PASS".equals(normalized)) {
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
        if (argsRaw instanceof Map<?, ?> map) {
            String invocationCommand = text(map.get("invocation_command"));
            if (StringUtils.hasText(invocationCommand)) {
                return invocationCommand.trim();
            }
            String which = text(map.get("which"));
            if (!StringUtils.hasText(which)) {
                return null;
            }
            List<String> parts = new ArrayList<>();
            parts.add("dbt");
            parts.add(which.trim());
            Object selectRaw = map.get("select");
            if (selectRaw instanceof List<?> selectList && !selectList.isEmpty()) {
                parts.add("--select");
                for (Object item : selectList) {
                    String value = text(item);
                    if (StringUtils.hasText(value)) {
                        parts.add(value);
                    }
                }
            }
            return String.join(" ", parts);
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

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String value = text(item);
            if (StringUtils.hasText(value)) {
                out.add(value.trim().toLowerCase(Locale.ROOT));
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

    private record ManifestNode(
        String uniqueId,
        String name,
        String resourceType,
        String path,
        String testType,
        String testedColumn,
        String testedModel,
        String compiledCode,
        Set<String> tags
    ) {}

    public record DbtTestDetail(
        String uniqueId,
        String name,
        String testType,
        String testedColumn,
        String testedModel,
        String status,
        String message,
        String compiledSql,
        double executionTime,
        int failuresCount
    ) {}

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
        List<DbtRunFailure> failures,
        List<DbtTestDetail> testDetails
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
                Collections.emptyList(),
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
