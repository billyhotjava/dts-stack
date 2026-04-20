package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtScopedProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtScopedProjectService.class);

    private static final Pattern REF_PATTERN = Pattern.compile(
        "ref\\s*\\(\\s*(?:['\"]([^'\"]+)['\"]\\s*,\\s*)?['\"]([^'\"]+)['\"]\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
        "source\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final String SCOPED_ROOT_DIR = ".dts-scoped-runs";
    private static final Duration MAX_SCOPED_AGE = Duration.ofHours(24);
    private static final int MAX_SCOPED_DIRS = 30;

    private final DbtConfigService dbtConfigService;
    private final DbtProperties dbtProperties;
    private final ModelingSqlModelRepository modelingSqlModelRepository;

    public DbtScopedProjectService(
        DbtConfigService dbtConfigService,
        DbtProperties dbtProperties,
        ModelingSqlModelRepository modelingSqlModelRepository
    ) {
        this.dbtConfigService = dbtConfigService;
        this.dbtProperties = dbtProperties;
        this.modelingSqlModelRepository = modelingSqlModelRepository;
    }

    public Optional<ScopedProject> prepare(String selector) {
        ParsedSelector parsedSelector = parseSelector(selector);
        if (parsedSelector == null || !parsedSelector.supported() || parsedSelector.modelNames().isEmpty()) {
            return Optional.empty();
        }
        Path workspaceDir = resolveWorkspaceDir();
        cleanupScopedRuns(workspaceDir.resolve(SCOPED_ROOT_DIR));

        List<ModelingSqlModel> allModels = modelingSqlModelRepository.findAll();
        Map<String, ModelingSqlModel> modelByName = buildModelByName(allModels);
        validateRequestedModels(parsedSelector.modelNames(), modelByName);

        Map<String, Path> seedsByName = indexResourceFiles(workspaceDir.resolve("seeds"), Set.of(".csv", ".tsv"));
        Map<String, Path> snapshotsByName = indexResourceFiles(workspaceDir.resolve("snapshots"), Set.of(".sql"));
        // Filesystem fallback: include workspace model files that were not registered in the logical
        // modeling DB (e.g. STG-layer views, which ModelingSqlModelService.normalizeLayer does not
        // accept). DWD models that ref() them would otherwise fail dbt compilation with
        // "depends on a node named '...' which was not found".
        Map<String, Path> modelFilesByName = indexResourceFiles(workspaceDir.resolve("models"), Set.of(".sql"));

        Set<String> requestedModelNames = new LinkedHashSet<>(parsedSelector.modelNames());
        Set<String> includedModelNames = new LinkedHashSet<>();
        Set<String> includedFilesystemModelNames = new LinkedHashSet<>();
        Set<String> requiredSourceNames = new LinkedHashSet<>();
        Set<String> requiredSeedNames = new LinkedHashSet<>();
        Set<String> requiredSnapshotNames = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(requestedModelNames);

        while (!queue.isEmpty()) {
            String modelName = queue.removeFirst();
            String normalizedName = normalizeName(modelName);
            if (
                !StringUtils.hasText(normalizedName)
                || includedModelNames.contains(normalizedName)
                || includedFilesystemModelNames.contains(normalizedName)
            ) {
                continue;
            }
            ModelingSqlModel model = modelByName.get(normalizedName);
            String dependencyText;
            if (model != null) {
                includedModelNames.add(normalizedName);
                dependencyText = buildDependencyText(workspaceDir, model);
            } else if (modelFilesByName.containsKey(normalizedName)) {
                includedFilesystemModelNames.add(normalizedName);
                dependencyText = readWorkspaceModelDependencyText(modelFilesByName.get(normalizedName));
            } else {
                continue;
            }
            requiredSourceNames.addAll(extractSources(dependencyText));
            for (String refName : extractRefs(dependencyText)) {
                String normalizedRefName = normalizeName(refName);
                if (!StringUtils.hasText(normalizedRefName)) {
                    continue;
                }
                if (modelByName.containsKey(normalizedRefName) || modelFilesByName.containsKey(normalizedRefName)) {
                    if (
                        !includedModelNames.contains(normalizedRefName)
                        && !includedFilesystemModelNames.contains(normalizedRefName)
                    ) {
                        queue.addLast(normalizedRefName);
                    }
                    continue;
                }
                if (seedsByName.containsKey(normalizedRefName)) {
                    requiredSeedNames.add(normalizedRefName);
                    continue;
                }
                if (snapshotsByName.containsKey(normalizedRefName)) {
                    requiredSnapshotNames.add(normalizedRefName);
                }
            }
        }

        Path scopedRoot = workspaceDir.resolve(SCOPED_ROOT_DIR);
        Path scopedProjectDir = scopedRoot.resolve(buildScopedRunId(requestedModelNames));
        try {
            Files.createDirectories(scopedProjectDir);
            copyRootFiles(workspaceDir, scopedProjectDir);
            copyDirectoryIfExists(workspaceDir.resolve("macros"), scopedProjectDir.resolve("macros"));
            copyDirectoryIfExists(workspaceDir.resolve("dbt_packages"), scopedProjectDir.resolve("dbt_packages"));
            copyDirectoryIfExists(workspaceDir.resolve("packages"), scopedProjectDir.resolve("packages"));

            for (String modelName : includedModelNames) {
                ModelingSqlModel model = modelByName.get(modelName);
                if (model != null) {
                    copyModelResources(workspaceDir, scopedProjectDir, model);
                }
            }
            for (String modelName : includedFilesystemModelNames) {
                copyResourceWithCompanions(workspaceDir, scopedProjectDir, modelFilesByName.get(modelName));
            }
            for (String seedName : requiredSeedNames) {
                copyResourceWithCompanions(workspaceDir, scopedProjectDir, seedsByName.get(seedName));
            }
            for (String snapshotName : requiredSnapshotNames) {
                copyResourceWithCompanions(workspaceDir, scopedProjectDir, snapshotsByName.get(snapshotName));
            }
            copyRelevantSourceFiles(workspaceDir, scopedProjectDir, requiredSourceNames);
        } catch (IOException ex) {
            deleteRecursively(scopedProjectDir);
            throw new IllegalStateException("构造临时 dbt 项目失败: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            deleteRecursively(scopedProjectDir);
            throw ex;
        }

        List<String> requested = requestedModelNames.stream().toList();
        List<String> included = includedModelNames.stream().toList();
        String externalProjectDir = toExternalProjectDir(workspaceDir, scopedProjectDir);
        LOG.info(
            "[dbt-scoped] prepared scoped project {} (external view: {}) with {} requested, {} DB-registered, {} filesystem-only models",
            scopedProjectDir,
            externalProjectDir,
            requested.size(),
            included.size(),
            includedFilesystemModelNames.size()
        );
        return Optional.of(new ScopedProject(externalProjectDir, requested, included));
    }

    /**
     * Translate a scoped-project path from the container view ({@code workspaceDir}) to the host view
     * ({@code hostProjectDir}). Used so that paths handed to Airflow as {@code docker -v HOST:CONTAINER}
     * bind-mount sources resolve to the same physical directory the backend wrote to. When no host
     * mapping is configured, returns the path unchanged — correct for deployments where the backend is
     * not containerized or container and host paths are identical.
     */
    private String toExternalProjectDir(Path workspaceDir, Path scopedProjectDir) {
        String hostProjectDir = dbtProperties == null ? null : dbtProperties.getHostProjectDir();
        String scopedPath = scopedProjectDir.toString();
        if (!StringUtils.hasText(hostProjectDir)) {
            return scopedPath;
        }
        String workspacePath = workspaceDir.toString();
        String normalizedHost = Path.of(hostProjectDir).normalize().toString();
        if (workspacePath.equals(normalizedHost)) {
            return scopedPath;
        }
        if (!scopedPath.startsWith(workspacePath)) {
            LOG.warn(
                "[dbt-scoped] scoped path {} does not start with workspace path {}; returning container view",
                scopedPath,
                workspacePath
            );
            return scopedPath;
        }
        return normalizedHost + scopedPath.substring(workspacePath.length());
    }

    private Path resolveWorkspaceDir() {
        String projectDir = dbtConfigService.resolveProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            throw new IllegalStateException("dbt projectDir 未配置，无法构造临时项目");
        }
        Path workspaceDir = Path.of(projectDir).normalize();
        if (!Files.isDirectory(workspaceDir)) {
            throw new IllegalStateException("dbt projectDir 不存在: " + workspaceDir);
        }
        // 关键前置校验：dbt_project.yml 必须存在
        // 若缺失，后续 copyRootFiles 会静默跳过，生成的 scoped 目录无法被 dbt 识别，
        // 容器里才报 "No dbt_project.yml found at /opt/dbt/dbt_project.yml"，
        // 该错误指向容器挂载路径而非实际 workspace，排查非常困难。
        Path dbtProjectYml = workspaceDir.resolve("dbt_project.yml");
        if (!Files.isRegularFile(dbtProjectYml)) {
            throw new IllegalStateException(
                "dbt workspace 不完整：缺少 " + dbtProjectYml
                + "。请确认已将完整 dbt 项目部署到 " + workspaceDir
                + "（或在平台的 dbt 配置中将 projectDir 指向实际项目根目录）。"
            );
        }
        return workspaceDir;
    }

    private ParsedSelector parseSelector(String selector) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        String normalized = selector.trim();
        if (normalized.isEmpty() || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        Set<String> modelNames = new LinkedHashSet<>();
        for (String rawToken : normalized.split("[,\\s]+")) {
            if (!StringUtils.hasText(rawToken)) {
                continue;
            }
            String token = rawToken.trim();
            if (token.isEmpty() || "all".equalsIgnoreCase(token)) {
                continue;
            }
            String candidate = token;
            if (candidate.regionMatches(true, 0, "model:", 0, 6)) {
                candidate = candidate.substring(6);
            } else if (candidate.contains(":")) {
                return new ParsedSelector(false, List.of());
            }
            candidate = stripGraphOperators(candidate);
            if (!candidate.matches("[A-Za-z0-9_]+")) {
                return new ParsedSelector(false, List.of());
            }
            modelNames.add(candidate);
        }
        return new ParsedSelector(true, modelNames.stream().toList());
    }

    private String stripGraphOperators(String token) {
        String normalized = token == null ? "" : token.trim();
        while (normalized.startsWith("+") || normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("+") || normalized.endsWith("@")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.trim();
    }

    private Map<String, ModelingSqlModel> buildModelByName(List<ModelingSqlModel> models) {
        Map<String, ModelingSqlModel> modelByName = new LinkedHashMap<>();
        for (ModelingSqlModel model : models) {
            if (model == null || !StringUtils.hasText(model.getName())) {
                continue;
            }
            String normalizedName = normalizeName(model.getName());
            modelByName.putIfAbsent(normalizedName, model);
        }
        return modelByName;
    }

    private void validateRequestedModels(List<String> requestedModelNames, Map<String, ModelingSqlModel> modelByName) {
        List<String> missing = new ArrayList<>();
        for (String modelName : requestedModelNames) {
            String normalizedName = normalizeName(modelName);
            if (StringUtils.hasText(normalizedName) && !modelByName.containsKey(normalizedName)) {
                missing.add(modelName);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下模型在逻辑建模中不存在，无法构造隔离编译工程: " + String.join(", ", missing));
        }
    }

    private Map<String, Path> indexResourceFiles(Path rootDir, Set<String> extensions) {
        if (rootDir == null || extensions == null || extensions.isEmpty() || !Files.isDirectory(rootDir)) {
            return Map.of();
        }
        Map<String, Path> indexed = new LinkedHashMap<>();
        try (var walk = Files.walk(rootDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName() == null ? null : file.getFileName().toString();
                if (!StringUtils.hasText(fileName)) {
                    continue;
                }
                String lower = fileName.toLowerCase(Locale.ROOT);
                boolean matched = extensions.stream().anyMatch(lower::endsWith);
                if (!matched) {
                    continue;
                }
                String stem = stripExtension(fileName);
                String normalizedStem = normalizeName(stem);
                if (StringUtils.hasText(normalizedStem)) {
                    indexed.putIfAbsent(normalizedStem, file);
                }
            }
        } catch (IOException ex) {
            LOG.warn("[dbt-scoped] failed to index {}: {}", rootDir, ex.getMessage());
        }
        return indexed;
    }

    private String buildDependencyText(Path workspaceDir, ModelingSqlModel model) {
        StringBuilder builder = new StringBuilder();
        String modelSql = readModelSql(workspaceDir, model);
        if (StringUtils.hasText(modelSql)) {
            builder.append(modelSql).append('\n');
        }
        Path modelFile = resolveWorkspacePath(workspaceDir, model.getModelPath());
        if (modelFile != null) {
            appendIfPresent(builder, resolveCompanionPath(modelFile, ".yml"));
            appendIfPresent(builder, resolveCompanionPath(modelFile, ".yaml"));
        }
        return builder.toString();
    }

    private String readModelSql(Path workspaceDir, ModelingSqlModel model) {
        Path modelFile = resolveWorkspacePath(workspaceDir, model == null ? null : model.getModelPath());
        if (modelFile != null && Files.isRegularFile(modelFile)) {
            try {
                return Files.readString(modelFile, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOG.warn("[dbt-scoped] failed to read model file {}: {}", modelFile, ex.getMessage());
            }
        }
        return model == null ? null : model.getSqlText();
    }

    /**
     * Build the ref()/source() dependency text for a workspace model that is not registered in the
     * logical modeling DB (e.g. STG-layer views). Appends adjacent .yml/.yaml companion files so
     * sources referenced via schema files are still discovered.
     */
    private String readWorkspaceModelDependencyText(Path modelFile) {
        StringBuilder builder = new StringBuilder();
        if (modelFile != null && Files.isRegularFile(modelFile)) {
            try {
                builder.append(Files.readString(modelFile, StandardCharsets.UTF_8)).append('\n');
            } catch (IOException ex) {
                LOG.warn("[dbt-scoped] failed to read workspace model file {}: {}", modelFile, ex.getMessage());
            }
            appendIfPresent(builder, resolveCompanionPath(modelFile, ".yml"));
            appendIfPresent(builder, resolveCompanionPath(modelFile, ".yaml"));
        }
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            builder.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
        } catch (IOException ex) {
            LOG.warn("[dbt-scoped] failed to read companion file {}: {}", path, ex.getMessage());
        }
    }

    private Set<String> extractRefs(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = REF_PATTERN.matcher(text);
        while (matcher.find()) {
            String packageName = normalizeName(matcher.group(1));
            String refName = normalizeName(matcher.group(2));
            if (!StringUtils.hasText(refName)) {
                continue;
            }
            if (StringUtils.hasText(packageName) && !isLocalPackageReference(packageName)) {
                continue;
            }
            refs.add(refName);
        }
        return refs;
    }

    private boolean isLocalPackageReference(String packageName) {
        // Two-arg ref(package, model) is treated as external by default.
        // Scoped projects only include local workspace resources.
        return false;
    }

    private Set<String> extractSources(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<String> sources = new LinkedHashSet<>();
        Matcher matcher = SOURCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sourceName = normalizeName(matcher.group(1));
            if (StringUtils.hasText(sourceName)) {
                sources.add(sourceName);
            }
        }
        return sources;
    }

    private void copyRootFiles(Path workspaceDir, Path scopedProjectDir) throws IOException {
        copyRootFileIfExists(workspaceDir, scopedProjectDir, "dbt_project.yml");
        copyRootFileIfExists(workspaceDir, scopedProjectDir, "packages.yml");
        copyRootFileIfExists(workspaceDir, scopedProjectDir, "dependencies.yml");
        copyRootFileIfExists(workspaceDir, scopedProjectDir, "package-lock.yml");
        copyRootFileIfExists(workspaceDir, scopedProjectDir, ".gitignore");
    }

    private void copyRootFileIfExists(Path workspaceDir, Path scopedProjectDir, String relativePath) throws IOException {
        Path source = workspaceDir.resolve(relativePath).normalize();
        if (!Files.isRegularFile(source)) {
            return;
        }
        Path target = scopedProjectDir.resolve(relativePath).normalize();
        ensureInside(scopedProjectDir, target);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyDirectoryIfExists(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null || targetDir == null || !Files.isDirectory(sourceDir)) {
            return;
        }
        try (var walk = Files.walk(sourceDir)) {
            for (Path source : walk.toList()) {
                Path relative = sourceDir.relativize(source);
                Path target = targetDir.resolve(relative).normalize();
                ensureInside(targetDir, target);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void copyModelResources(Path workspaceDir, Path scopedProjectDir, ModelingSqlModel model) throws IOException {
        Path source = resolveWorkspacePath(workspaceDir, model == null ? null : model.getModelPath());
        if (source != null && Files.isRegularFile(source)) {
            copyRelativeFile(workspaceDir, scopedProjectDir, source);
            copyCompanionFiles(workspaceDir, scopedProjectDir, source);
            return;
        }
        if (model == null || !StringUtils.hasText(model.getModelPath())) {
            throw new IOException("模型缺少有效 modelPath: " + (model == null ? null : model.getName()));
        }
        Path target = scopedProjectDir.resolve(model.getModelPath()).normalize();
        ensureInside(scopedProjectDir, target);
        Files.createDirectories(target.getParent());
        Files.writeString(target, StringUtils.hasText(model.getSqlText()) ? model.getSqlText() : "", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void copyResourceWithCompanions(Path workspaceDir, Path scopedProjectDir, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            return;
        }
        copyRelativeFile(workspaceDir, scopedProjectDir, source);
        copyCompanionFiles(workspaceDir, scopedProjectDir, source);
    }

    private void copyRelativeFile(Path workspaceDir, Path scopedProjectDir, Path source) throws IOException {
        Path normalizedSource = source.normalize();
        ensureInside(workspaceDir, normalizedSource);
        Path relative = workspaceDir.relativize(normalizedSource);
        Path target = scopedProjectDir.resolve(relative).normalize();
        ensureInside(scopedProjectDir, target);
        Files.createDirectories(target.getParent());
        Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyCompanionFiles(Path workspaceDir, Path scopedProjectDir, Path source) throws IOException {
        for (String extension : List.of(".yml", ".yaml", ".csv")) {
            Path companion = resolveCompanionPath(source, extension);
            if (companion != null && Files.isRegularFile(companion)) {
                copyRelativeFile(workspaceDir, scopedProjectDir, companion);
            }
        }
    }

    private Path resolveCompanionPath(Path source, String extension) {
        if (source == null || !StringUtils.hasText(extension) || source.getFileName() == null) {
            return null;
        }
        String fileName = source.getFileName().toString();
        String stem = stripExtension(fileName);
        return source.resolveSibling(stem + extension);
    }

    private void copyRelevantSourceFiles(Path workspaceDir, Path scopedProjectDir, Set<String> requiredSources) throws IOException {
        if (requiredSources == null || requiredSources.isEmpty()) {
            return;
        }
        Path modelsDir = workspaceDir.resolve("models").normalize();
        if (!Files.isDirectory(modelsDir)) {
            return;
        }
        Set<String> normalizedSources = requiredSources.stream().map(this::normalizeName).filter(StringUtils::hasText).collect(
            LinkedHashSet::new,
            Set::add,
            Set::addAll
        );
        try (var walk = Files.walk(modelsDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String lower = file.toString().toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
                    continue;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!containsRelevantSourceDefinition(content, normalizedSources)) {
                    continue;
                }
                copyRelativeFile(workspaceDir, scopedProjectDir, file);
            }
        }
    }

    private boolean containsRelevantSourceDefinition(String content, Set<String> requiredSources) {
        if (!StringUtils.hasText(content) || requiredSources == null || requiredSources.isEmpty()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (!lower.contains("sources:")) {
            return false;
        }
        for (String sourceName : requiredSources) {
            Pattern pattern = Pattern.compile(
                "(?im)^\\s*-\\s*name\\s*:\\s*['\"]?" + Pattern.quote(sourceName) + "['\"]?\\s*(?:#.*)?$"
            );
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    private Path resolveWorkspacePath(Path workspaceDir, String relativePath) {
        if (workspaceDir == null || !StringUtils.hasText(relativePath)) {
            return null;
        }
        Path resolved = workspaceDir.resolve(relativePath).normalize();
        ensureInside(workspaceDir, resolved);
        return resolved;
    }

    private void ensureInside(Path root, Path candidate) {
        if (root == null || candidate == null || !candidate.normalize().startsWith(root.normalize())) {
            throw new IllegalStateException("非法路径访问: " + candidate);
        }
    }

    private void cleanupScopedRuns(Path scopedRoot) {
        if (scopedRoot == null || !Files.isDirectory(scopedRoot)) {
            return;
        }
        try (var children = Files.list(scopedRoot)) {
            List<Path> directories = children.filter(Files::isDirectory).sorted(Comparator.comparing(this::lastModified).reversed()).toList();
            Instant cutoff = Instant.now().minus(MAX_SCOPED_AGE);
            for (int i = 0; i < directories.size(); i++) {
                Path directory = directories.get(i);
                FileTime modified = lastModified(directory);
                boolean expired = modified.toInstant().isBefore(cutoff);
                boolean overflow = i >= MAX_SCOPED_DIRS;
                if (expired || overflow) {
                    deleteRecursively(directory);
                }
            }
        } catch (IOException ex) {
            LOG.warn("[dbt-scoped] failed to cleanup scoped runs {}: {}", scopedRoot, ex.getMessage());
        }
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ex) {
            return FileTime.from(Instant.EPOCH);
        }
    }

    private void deleteRecursively(Path directory) {
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            LOG.warn("[dbt-scoped] failed to delete {}: {}", directory, ex.getMessage());
        }
    }

    private String buildScopedRunId(Set<String> requestedModelNames) {
        String prefix = requestedModelNames.isEmpty() ? "scoped" : requestedModelNames.iterator().next();
        String normalizedPrefix = normalizeName(prefix);
        if (!StringUtils.hasText(normalizedPrefix)) {
            normalizedPrefix = "scoped";
        }
        return normalizedPrefix + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return fileName;
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 ? fileName : fileName.substring(0, index);
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private record ParsedSelector(boolean supported, List<String> modelNames) {}

    public record ScopedProject(String projectDir, List<String> requestedModels, List<String> includedModels) {}
}
