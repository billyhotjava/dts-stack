package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtQualityGateService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtQualityGateService.class);

    private final ModelingSqlModelRepository sqlModelRepository;
    private final DbtConfigService dbtConfigService;
    private final DbtRunResultService dbtRunResultService;

    public DbtQualityGateService(
        ModelingSqlModelRepository sqlModelRepository,
        DbtConfigService dbtConfigService,
        DbtRunResultService dbtRunResultService
    ) {
        this.sqlModelRepository = sqlModelRepository;
        this.dbtConfigService = dbtConfigService;
        this.dbtRunResultService = dbtRunResultService;
    }

    public DbtQualityGateResult evaluate(String selector) {
        List<String> selectedModels = resolveModelsBySelector(selector);
        List<ModelingSqlModel> allModels = sqlModelRepository.findAll();
        Set<String> missingTests = new LinkedHashSet<>();
        Set<String> missingTypeMeta = new LinkedHashSet<>();
        if (!selectedModels.isEmpty()) {
            String projectDir = resolveProjectDir();
            for (String modelName : selectedModels) {
                Path ymlPath = resolveModelYmlPath(projectDir, modelName, allModels);
                if (ymlPath == null || !Files.exists(ymlPath)) {
                    missingTests.add(modelName);
                    continue;
                }
                String content = readText(ymlPath);
                if (!StringUtils.hasText(content) || !content.contains("tests:")) {
                    missingTests.add(modelName);
                }
                if (!StringUtils.hasText(content) || !content.contains("expected_data_type")) {
                    missingTypeMeta.add(modelName);
                }
            }
        }

        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestSummary(10);
        String latestStatus = latestRun != null && StringUtils.hasText(latestRun.status())
            ? latestRun.status().toUpperCase(Locale.ROOT)
            : "UNKNOWN";
        String command = latestRun != null ? latestRun.command() : null;
        boolean latestFailed = "FAILED".equals(latestStatus);
        boolean qualityCommand = isQualityCommand(command);
        boolean missingUnbuiltRelations = isMissingUnbuiltRelationTestFailure(latestRun);
        boolean blocking = latestFailed && qualityCommand && !missingUnbuiltRelations;

        List<String> warnings = new ArrayList<>();
        if (!missingTests.isEmpty()) {
            warnings.add("以下模型未发现测试模板: " + String.join(", ", missingTests));
        }
        if (!missingTypeMeta.isEmpty()) {
            warnings.add("以下模型缺少类型元信息(expected_data_type): " + String.join(", ", missingTypeMeta));
        }
        if (missingUnbuiltRelations) {
            warnings.add("最近一次 dbt test 失败是因为目标关系尚未生成，首次上线可继续执行 dbt build");
        }
        if (latestFailed && !qualityCommand) {
            warnings.add("最近一次构建状态为 FAILED，但不是测试命令，请确认是否继续上线");
        }

        List<String> blockers = new ArrayList<>();
        if (blocking) {
            blockers.add("最近一次质量构建失败（" + defaultText(command, "unknown command") + "），请先修复后再上线");
        }

        return new DbtQualityGateResult(
            selector,
            selectedModels,
            !blockers.isEmpty(),
            !warnings.isEmpty(),
            latestStatus,
            command,
            latestRun != null ? latestRun.generatedAt() : null,
            latestRun != null ? latestRun.failed() : 0,
            blockers,
            warnings
        );
    }

    private boolean isQualityCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.contains(" test") || normalized.startsWith("test ") || normalized.contains(" build");
    }

    private boolean isMissingUnbuiltRelationTestFailure(DbtRunResultService.DbtRunSummary latestRun) {
        if (latestRun == null || !"FAILED".equalsIgnoreCase(defaultText(latestRun.status(), ""))) {
            return false;
        }
        if (!isTestCommand(latestRun.command()) || latestRun.failures() == null || latestRun.failures().isEmpty()) {
            return false;
        }
        return latestRun.failures().stream().allMatch(this::isMissingRelationFailure);
    }

    private boolean isTestCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.contains(" test") || normalized.startsWith("test ");
    }

    private boolean isMissingRelationFailure(DbtRunResultService.DbtRunFailure failure) {
        if (failure == null || !StringUtils.hasText(failure.message())) {
            return false;
        }
        String normalized = failure.message().toLowerCase(Locale.ROOT);
        return normalized.contains("relation \"") && normalized.contains("does not exist");
    }

    private List<String> resolveModelsBySelector(String selector) {
        String normalized = StringUtils.hasText(selector) ? selector.trim() : "all";
        List<ModelingSqlModel> allModels = sqlModelRepository.findAll();
        if (allModels.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>();
        String[] tokens = normalized.split("[,\\s]+");
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            if ("all".equals(lower) || "*".equals(lower)) {
                for (ModelingSqlModel model : allModels) {
                    if (model != null && StringUtils.hasText(model.getName())) {
                        selected.add(model.getName().trim());
                    }
                }
                continue;
            }
            if (lower.startsWith("model:")) {
                String modelName = token.substring(6).trim();
                if (StringUtils.hasText(modelName)) {
                    selected.add(modelName);
                }
                continue;
            }
            if (lower.startsWith("tag:")) {
                String tag = token.substring(4).trim().toLowerCase(Locale.ROOT);
                if (!StringUtils.hasText(tag)) {
                    continue;
                }
                for (ModelingSqlModel model : allModels) {
                    if (model == null || !StringUtils.hasText(model.getName())) {
                        continue;
                    }
                    String tags = model.getTags() == null ? "" : model.getTags().toLowerCase(Locale.ROOT);
                    if (tags.contains(tag)) {
                        selected.add(model.getName().trim());
                    }
                }
                continue;
            }
            selected.add(token.trim());
        }
        return new ArrayList<>(selected);
    }

    private String resolveProjectDir() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            return view.config().projectDir().trim();
        }
        return null;
    }

    private Path resolveModelYmlPath(String projectDir, String modelName, List<ModelingSqlModel> allModels) {
        if (!StringUtils.hasText(projectDir) || !StringUtils.hasText(modelName)) {
            return null;
        }
        for (ModelingSqlModel model : allModels) {
            if (model == null || !StringUtils.hasText(model.getName()) || !StringUtils.hasText(model.getModelPath())) {
                continue;
            }
            if (!modelName.equalsIgnoreCase(model.getName().trim())) {
                continue;
            }
            Path project = Path.of(projectDir).normalize();
            Path sqlPath = project.resolve(model.getModelPath()).normalize();
            if (!sqlPath.startsWith(project) || sqlPath.getFileName() == null) {
                return null;
            }
            // 1) 优先查同名 yml（如 biz_dwd_quality_issue.yml）
            String baseName = sqlPath.getFileName().toString();
            String ymlName = baseName.endsWith(".sql") ? baseName.substring(0, baseName.length() - 4) + ".yml" : baseName + ".yml";
            Path ymlPath = sqlPath.resolveSibling(ymlName).normalize();
            if (ymlPath.startsWith(project) && Files.exists(ymlPath)) {
                return ymlPath;
            }
            // 2) 回退：扫描同目录及父目录的所有 yml，查找包含该模型名的 schema 文件
            Path searchDir = sqlPath.getParent();
            Path matched = findSchemaYmlContainingModel(project, searchDir, modelName);
            if (matched != null) {
                return matched;
            }
            // 3) 再往上找一层（models/ 根目录的 schema yml）
            if (searchDir != null && searchDir.getParent() != null && searchDir.getParent().startsWith(project)) {
                matched = findSchemaYmlContainingModel(project, searchDir.getParent(), modelName);
                if (matched != null) {
                    return matched;
                }
            }
            return null;
        }
        return null;
    }

    private Path findSchemaYmlContainingModel(Path projectRoot, Path dir, String modelName) {
        if (dir == null || !Files.isDirectory(dir) || !dir.startsWith(projectRoot)) {
            return null;
        }
        try (var entries = Files.list(dir)) {
            for (Path candidate : entries.filter(Files::isRegularFile).toList()) {
                String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
                    continue;
                }
                String content = readText(candidate);
                if (content != null && content.contains("name: " + modelName)) {
                    return candidate;
                }
            }
        } catch (IOException ex) {
            LOG.debug("[dbt-quality-gate] failed to scan dir {}: {}", dir, ex.getMessage());
        }
        return null;
    }

    private String readText(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.warn("[dbt-quality-gate] failed to read {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record DbtQualityGateResult(
        String selector,
        List<String> selectedModels,
        boolean blocking,
        boolean warning,
        String latestStatus,
        String latestCommand,
        String latestGeneratedAt,
        int latestFailedCount,
        List<String> blockers,
        List<String> warnings
    ) {}
}
