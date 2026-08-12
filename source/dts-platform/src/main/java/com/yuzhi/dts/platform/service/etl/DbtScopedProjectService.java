package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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
    private static final String ACTIVE_MARKER = ".dts-active";
    private static final Duration MAX_SCOPED_AGE = Duration.ofHours(24);
    private static final int MAX_SCOPED_DIRS = 30;
    private static final int MAX_CANDIDATE_ENTRIES = 100;
    private static final long MAX_ARTIFACT_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_BUNDLE_BYTES = 100L * 1024L * 1024L;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DBT_UNIQUE_ID = Pattern.compile(
        "^model\\.[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*$"
    );

    private final DbtConfigService dbtConfigService;
    private final DbtProperties dbtProperties;

    public DbtScopedProjectService(DbtConfigService dbtConfigService, DbtProperties dbtProperties) {
        this.dbtConfigService = dbtConfigService;
        this.dbtProperties = dbtProperties;
    }

    /**
     * Builds one immutable candidate-scoped dbt project from lifecycle artifacts without creating
     * or updating legacy SQL-model rows. Workspace sources, macros and referenced nodes remain
     * dependencies; the candidate artifacts are the only overlay owner.
     */
    public ScopedCandidateProject prepareCandidate(List<CandidateArtifactEntry> entries) {
        Path workspaceDir = resolveWorkspaceDir();
        Path scopedRoot = workspaceDir.resolve(SCOPED_ROOT_DIR);
        cleanupScopedRuns(scopedRoot);
        CandidateOverlay overlay = validateCandidateOverlay(workspaceDir, entries);
        Path temporary = scopedRoot.resolve(".candidate-" + UUID.randomUUID());
        try {
            Files.createDirectories(temporary);
            copyRootFiles(workspaceDir, temporary);
            copyDirectoryIfExists(workspaceDir.resolve("macros"), temporary.resolve("macros"));
            copyDirectoryIfExists(workspaceDir.resolve("dbt_packages"), temporary.resolve("dbt_packages"));
            copyDirectoryIfExists(workspaceDir.resolve("packages"), temporary.resolve("packages"));
            copyCandidateDependencies(workspaceDir, temporary, overlay);
            writeCandidateArtifacts(temporary, overlay);
            BundleDigest digest = digestDirectory(temporary);
            Path stableProject = scopedRoot.resolve("candidate-" + digest.checksum());
            promoteCandidateProject(temporary, stableProject, digest.checksum());
            touchActiveMarker(stableProject);
            return new ScopedCandidateProject(
                toExternalProjectDir(workspaceDir, stableProject),
                overlay.selectors().stream().map(selector -> "+" + selector).collect(java.util.stream.Collectors.joining(" ")),
                digest.checksum(),
                overlay.entries()
            );
        } catch (IOException failure) {
            deleteRecursively(temporary);
            throw new ScopedProjectException(
                "MATERIALIZATION_BUNDLE_WRITE_FAILED",
                "Unable to construct the candidate dbt project",
                failure
            );
        } catch (RuntimeException failure) {
            deleteRecursively(temporary);
            throw failure;
        }
    }

    /** Marks a deterministic candidate bundle eligible for normal age/count cleanup. */
    public void releaseCandidateProject(String bundleChecksum) {
        if (bundleChecksum == null || !SHA_256.matcher(bundleChecksum).matches()) {
            throw new ScopedProjectException("MATERIALIZATION_BUNDLE_INVALID", "Invalid candidate bundle checksum");
        }
        Path workspaceDir = resolveWorkspaceDir();
        Path project = workspaceDir.resolve(SCOPED_ROOT_DIR).resolve("candidate-" + bundleChecksum).normalize();
        ensureInside(workspaceDir.resolve(SCOPED_ROOT_DIR), project);
        try {
            Files.deleteIfExists(project.resolve(ACTIVE_MARKER));
        } catch (IOException failure) {
            throw new ScopedProjectException(
                "MATERIALIZATION_BUNDLE_RELEASE_FAILED",
                "Unable to release the candidate dbt project",
                failure
            );
        }
    }

    /**
     * Resolves a deterministic candidate project for artifact synchronization and verifies that
     * dbt did not mutate any immutable input. Runtime-owned target/log output is deliberately
     * excluded from the original bundle digest.
     */
    public Path verifyCandidateProject(String bundleChecksum) {
        if (bundleChecksum == null || !SHA_256.matcher(bundleChecksum).matches()) {
            throw new ScopedProjectException("MATERIALIZATION_BUNDLE_INVALID", "Invalid candidate bundle checksum");
        }
        Path workspaceDir = resolveWorkspaceDir();
        Path scopedRoot = workspaceDir.resolve(SCOPED_ROOT_DIR).normalize();
        Path project = scopedRoot.resolve("candidate-" + bundleChecksum).normalize();
        ensureInside(scopedRoot, project);
        if (
            Files.isSymbolicLink(project) ||
            !Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw new ScopedProjectException(
                "MATERIALIZATION_BUNDLE_MISSING",
                "Candidate dbt project is unavailable"
            );
        }
        try (var walk = Files.walk(project)) {
            if (walk.anyMatch(Files::isSymbolicLink)) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_BUNDLE_CONFLICT",
                    "Candidate dbt project contains a symbolic link"
                );
            }
        } catch (IOException failure) {
            throw new ScopedProjectException(
                "MATERIALIZATION_BUNDLE_READ_FAILED",
                "Unable to inspect the candidate dbt project",
                failure
            );
        }
        try {
            if (!bundleChecksum.equals(digestDirectory(project).checksum())) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_BUNDLE_CONFLICT",
                    "Candidate dbt project input does not match its bundle checksum"
                );
            }
            return project;
        } catch (IOException failure) {
            throw new ScopedProjectException(
                "MATERIALIZATION_BUNDLE_READ_FAILED",
                "Unable to verify the candidate dbt project",
                failure
            );
        }
    }

    private CandidateOverlay validateCandidateOverlay(
        Path workspaceDir,
        List<CandidateArtifactEntry> requestedEntries
    ) {
        if (requestedEntries == null || requestedEntries.isEmpty()) {
            throw new ScopedProjectException("MATERIALIZATION_SCOPE_EMPTY", "Candidate artifact scope is empty");
        }
        if (requestedEntries.size() > MAX_CANDIDATE_ENTRIES) {
            throw new ScopedProjectException("MATERIALIZATION_SCOPE_TOO_LARGE", "Candidate artifact scope exceeds 100 entries");
        }
        requestedEntries.forEach(this::validateCandidateEntry);
        List<CandidateArtifactEntry> entries = requestedEntries
            .stream()
            .sorted(Comparator.comparing(entry -> entry.modelSpecId().toString()))
            .toList();
        Set<UUID> modelIds = new LinkedHashSet<>();
        Map<String, CandidateArtifact> artifactsByPath = new LinkedHashMap<>();
        Map<String, CandidateArtifact> artifactsByNode = new LinkedHashMap<>();
        Set<String> selectors = new LinkedHashSet<>();
        long candidateBytes = 0;
        for (CandidateArtifactEntry entry : entries) {
            if (!modelIds.add(entry.modelSpecId())) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_SCOPE_DUPLICATE",
                    "Candidate scope contains the same ModelSpec more than once"
                );
            }
            String selector = dbtSelector(entry.dbtUniqueId());
            if (!selectors.add(selector)) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_SCOPE_DUPLICATE",
                    "Candidate scope contains the same dbt selector more than once"
                );
            }
            for (CandidateArtifact artifact : entry.artifacts()) {
                String normalizedPath = candidateArtifactPath(artifact.path());
                byte[] bytes = artifact.content().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_ARTIFACT_BYTES) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_ARTIFACT_TOO_LARGE",
                        "Candidate artifact exceeds 5 MiB: " + normalizedPath
                    );
                }
                candidateBytes += bytes.length;
                if (candidateBytes > MAX_BUNDLE_BYTES) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_BUNDLE_TOO_LARGE",
                        "Candidate artifact bundle exceeds 100 MiB"
                    );
                }
                String actualChecksum = sha256(bytes);
                if (!actualChecksum.equals(artifact.contentChecksum())) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_ARTIFACT_STALE",
                        "Candidate artifact checksum does not match content: " + normalizedPath
                    );
                }
                CandidateArtifact normalized = new CandidateArtifact(
                    normalizedPath,
                    artifact.contentChecksum(),
                    artifact.content()
                );
                CandidateArtifact existingPath = artifactsByPath.get(normalizedPath);
                if (existingPath != null && !existingPath.contentChecksum().equals(normalized.contentChecksum())) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_NODE_CONFLICT",
                        "Candidate artifacts disagree on path: " + normalizedPath
                    );
                }
                if (existingPath != null) continue;
                String node = sqlNodeName(normalizedPath);
                if (node != null) {
                    CandidateArtifact existingNode = artifactsByNode.get(node);
                    if (
                        existingNode != null &&
                        !existingNode.contentChecksum().equals(normalized.contentChecksum())
                    ) {
                        throw new ScopedProjectException(
                            "MATERIALIZATION_NODE_CONFLICT",
                            "Candidate artifacts disagree on dbt node: " + node
                        );
                    }
                    if (existingNode != null) continue;
                    artifactsByNode.put(node, normalized);
                }
                artifactsByPath.put(normalizedPath, normalized);
            }
            if (!artifactsByNode.containsKey(selector)) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_ARTIFACT_MISSING",
                    "Candidate does not contain its selected dbt SQL node: " + selector
                );
            }
        }
        validateCandidateDependencyGraph(artifactsByNode);
        Map<String, Path> workspaceNodes = indexResourceFiles(
            workspaceDir.resolve("models"),
            Set.of(".sql")
        );
        for (Map.Entry<String, CandidateArtifact> node : artifactsByNode.entrySet()) {
            Path workspaceNode = workspaceNodes.get(node.getKey());
            if (workspaceNode == null) continue;
            try {
                String workspaceChecksum = sha256(Files.readAllBytes(workspaceNode));
                if (!workspaceChecksum.equals(node.getValue().contentChecksum())) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_NODE_CONFLICT",
                        "Candidate node conflicts with the dbt workspace: " + node.getKey()
                    );
                }
            } catch (IOException failure) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_WORKSPACE_READ_FAILED",
                    "Unable to inspect workspace node: " + node.getKey(),
                    failure
                );
            }
        }
        return new CandidateOverlay(
            List.copyOf(entries),
            List.copyOf(selectors),
            Map.copyOf(artifactsByPath),
            Set.copyOf(artifactsByNode.keySet())
        );
    }

    private void validateCandidateDependencyGraph(Map<String, CandidateArtifact> artifactsByNode) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        artifactsByNode
            .keySet()
            .stream()
            .sorted()
            .forEach(node -> visitCandidateDependency(node, artifactsByNode, visited, active, path));
    }

    private void visitCandidateDependency(
        String node,
        Map<String, CandidateArtifact> artifactsByNode,
        Set<String> visited,
        Set<String> active,
        ArrayDeque<String> path
    ) {
        if (visited.contains(node)) return;
        active.add(node);
        path.addLast(node);
        List<String> dependencies = extractRefs(artifactsByNode.get(node).content())
            .stream()
            .map(this::normalizeName)
            .filter(artifactsByNode::containsKey)
            .distinct()
            .sorted()
            .toList();
        for (String dependency : dependencies) {
            if (active.contains(dependency)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(dependency);
                throw new ScopedProjectException(
                    "MATERIALIZATION_DEPENDENCY_CYCLE",
                    "Candidate dbt dependency cycle detected: " + String.join(" -> ", cycle)
                );
            }
            visitCandidateDependency(dependency, artifactsByNode, visited, active, path);
        }
        path.removeLast();
        active.remove(node);
        visited.add(node);
    }

    private void validateCandidateEntry(CandidateArtifactEntry entry) {
        if (
            entry == null ||
            entry.modelSpecId() == null ||
            entry.modelRevision() < 1 ||
            entry.implementationRevision() < 1 ||
            entry.modelChecksum() == null ||
            !SHA_256.matcher(entry.modelChecksum()).matches() ||
            entry.implementationChecksum() == null ||
            !SHA_256.matcher(entry.implementationChecksum()).matches() ||
            entry.dbtUniqueId() == null ||
            !DBT_UNIQUE_ID.matcher(entry.dbtUniqueId()).matches() ||
            entry.artifacts() == null ||
            entry.artifacts().isEmpty() ||
            entry.artifacts().stream().anyMatch(artifact ->
                artifact == null ||
                artifact.path() == null ||
                artifact.contentChecksum() == null ||
                !SHA_256.matcher(artifact.contentChecksum()).matches() ||
                artifact.content() == null
            )
        ) {
            throw new ScopedProjectException(
                "MATERIALIZATION_ARTIFACT_INVALID",
                "Candidate artifact entry is incomplete or invalid"
            );
        }
    }

    private String candidateArtifactPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new ScopedProjectException("MATERIALIZATION_ARTIFACT_INVALID", "Candidate artifact path is required");
        }
        Path path;
        try {
            path = Path.of(rawPath.replace('\\', '/')).normalize();
        } catch (RuntimeException failure) {
            throw new ScopedProjectException("MATERIALIZATION_ARTIFACT_INVALID", "Candidate artifact path is invalid");
        }
        String normalized = path.toString().replace('\\', '/');
        if (
            path.isAbsolute() ||
            normalized.equals("..") ||
            normalized.startsWith("../") ||
            !normalized.startsWith("models/") ||
            !(normalized.endsWith(".sql") || normalized.endsWith(".yml") || normalized.endsWith(".yaml"))
        ) {
            throw new ScopedProjectException(
                "MATERIALIZATION_ARTIFACT_INVALID",
                "Candidate artifact must be a SQL/YAML file below models/"
            );
        }
        return normalized;
    }

    private void copyCandidateDependencies(
        Path workspaceDir,
        Path scopedProjectDir,
        CandidateOverlay overlay
    ) throws IOException {
        Map<String, Path> workspaceNodes = new LinkedHashMap<>();
        for (Path modelRoot : modelRoots(workspaceDir)) {
            Path relativeRoot = workspaceDir.relativize(modelRoot);
            Path scopedModelRoot = scopedProjectDir.resolve(relativeRoot).normalize();
            ensureInside(scopedProjectDir, scopedModelRoot);
            Files.createDirectories(scopedModelRoot);
            indexResourceFiles(modelRoot, Set.of(".sql")).forEach(workspaceNodes::putIfAbsent);
        }
        Map<String, Path> seedsByName = indexResourceFiles(
            workspaceDir.resolve("seeds"),
            Set.of(".csv", ".tsv")
        );
        Map<String, Path> snapshotsByName = indexResourceFiles(
            workspaceDir.resolve("snapshots"),
            Set.of(".sql")
        );
        Set<String> requiredSources = new LinkedHashSet<>();
        Set<String> visitedWorkspaceNodes = new LinkedHashSet<>();
        ArrayDeque<String> pendingRefs = new ArrayDeque<>();
        for (CandidateArtifact artifact : overlay.artifactsByPath().values()) {
            requiredSources.addAll(extractSources(artifact.content()));
            pendingRefs.addAll(extractRefs(artifact.content()));
        }
        while (!pendingRefs.isEmpty()) {
            String reference = normalizeName(pendingRefs.removeFirst());
            if (!StringUtils.hasText(reference) || overlay.overlayNodes().contains(reference)) continue;
            if (seedsByName.containsKey(reference)) {
                copyResourceWithCompanions(workspaceDir, scopedProjectDir, seedsByName.get(reference));
                continue;
            }
            if (snapshotsByName.containsKey(reference)) {
                copyResourceWithCompanions(workspaceDir, scopedProjectDir, snapshotsByName.get(reference));
                continue;
            }
            Path workspaceNode = workspaceNodes.get(reference);
            if (workspaceNode == null) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_DEPENDENCY_MISSING",
                    "Candidate dbt dependency is unavailable: " + reference
                );
            }
            if (!visitedWorkspaceNodes.add(reference)) continue;
            String dependencyText = readWorkspaceModelDependencyText(workspaceNode);
            requiredSources.addAll(extractSources(dependencyText));
            pendingRefs.addAll(extractRefs(dependencyText));
            copyResourceWithCompanions(workspaceDir, scopedProjectDir, workspaceNode);
        }
        copyRelevantSourceFiles(workspaceDir, scopedProjectDir, requiredSources);
    }

    private void writeCandidateArtifacts(Path scopedProjectDir, CandidateOverlay overlay) throws IOException {
        Map<String, CandidateArtifactEntry> entriesBySelector = new LinkedHashMap<>();
        for (CandidateArtifactEntry entry : overlay.entries()) {
            entriesBySelector.put(dbtSelector(entry.dbtUniqueId()), entry);
        }
        for (CandidateArtifact artifact : overlay.artifactsByPath().values()) {
            Path target = scopedProjectDir.resolve(artifact.path()).normalize();
            ensureInside(scopedProjectDir, target);
            Files.createDirectories(target.getParent());
            CandidateArtifactEntry identity = entriesBySelector.get(
                sqlNodeName(artifact.path())
            );
            String content = identity == null
                ? artifact.content()
                : releaseIdentityConfig(identity) + artifact.content();
            Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }
    }

    private static String releaseIdentityConfig(
        CandidateArtifactEntry entry
    ) {
        return (
            "{{ config(meta={'modelSpecId': '%s', 'modelRevision': %d, " +
            "'modelChecksum': '%s', 'implementationRevision': %d, " +
            "'implementationChecksum': '%s'}) }}\n"
        ).formatted(
            entry.modelSpecId(),
            entry.modelRevision(),
            entry.modelChecksum(),
            entry.implementationRevision(),
            entry.implementationChecksum()
        );
    }

    private BundleDigest digestDirectory(Path directory) throws IOException {
        MessageDigest digest = messageDigest();
        long totalBytes = 0;
        try (var walk = Files.walk(directory)) {
            List<Path> files = walk
                .filter(Files::isRegularFile)
                .filter(path -> !ACTIVE_MARKER.equals(path.getFileName().toString()))
                .filter(path -> !isDbtRuntimeOutput(directory, path))
                .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                .toList();
            for (Path file : files) {
                byte[] content = Files.readAllBytes(file);
                if (content.length > MAX_ARTIFACT_BYTES) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_ARTIFACT_TOO_LARGE",
                        "Scoped project file exceeds 5 MiB: " + directory.relativize(file)
                    );
                }
                totalBytes += content.length;
                if (totalBytes > MAX_BUNDLE_BYTES) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_BUNDLE_TOO_LARGE",
                        "Scoped project exceeds 100 MiB"
                    );
                }
                digest.update(directory.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(content);
                digest.update((byte) 0);
            }
        }
        return new BundleDigest(HexFormat.of().formatHex(digest.digest()), totalBytes);
    }

    private static boolean isDbtRuntimeOutput(Path directory, Path path) {
        Path relative = directory.relativize(path);
        if (relative.getNameCount() == 0) {
            return false;
        }
        String root = relative.getName(0).toString();
        return "target".equals(root) || "logs".equals(root);
    }

    private void promoteCandidateProject(
        Path temporary,
        Path stableProject,
        String expectedChecksum
    ) throws IOException {
        if (Files.isDirectory(stableProject)) {
            if (!expectedChecksum.equals(digestDirectory(stableProject).checksum())) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_BUNDLE_CONFLICT",
                    "Existing candidate project does not match its bundle checksum"
                );
            }
            deleteRecursively(temporary);
            return;
        }
        Files.createDirectories(stableProject.getParent());
        try {
            Files.move(temporary, stableProject, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, stableProject);
        } catch (java.nio.file.FileAlreadyExistsException concurrent) {
            if (
                !Files.isDirectory(stableProject) ||
                !expectedChecksum.equals(digestDirectory(stableProject).checksum())
            ) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_BUNDLE_CONFLICT",
                    "Concurrent candidate project preparation did not converge",
                    concurrent
                );
            }
            deleteRecursively(temporary);
        }
    }

    private void touchActiveMarker(Path stableProject) throws IOException {
        Files.writeString(
            stableProject.resolve(ACTIVE_MARKER),
            Instant.now().toString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
        Files.setLastModifiedTime(stableProject, FileTime.from(Instant.now()));
    }

    private String dbtSelector(String uniqueId) {
        String[] parts = uniqueId.split("\\.", -1);
        return parts[2];
    }

    private String sqlNodeName(String path) {
        return path != null && path.endsWith(".sql")
            ? normalizeName(stripExtension(Path.of(path).getFileName().toString()))
            : null;
    }

    private static String sha256(byte[] value) {
        MessageDigest digest = messageDigest();
        return HexFormat.of().formatHex(digest.digest(value));
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
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

    private List<Path> modelRoots(Path workspaceDir) {
        Path projectFile = workspaceDir.resolve("dbt_project.yml");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(16);
        options.setCodePointLimit(256 * 1024);
        try {
            Object loaded = new Yaml(new SafeConstructor(options)).load(
                Files.readString(projectFile, StandardCharsets.UTF_8)
            );
            Object configured = loaded instanceof Map<?, ?> root
                ? root.get("model-paths")
                : null;
            List<?> values = configured instanceof List<?> list
                ? list
                : List.of("models");
            List<Path> roots = new ArrayList<>();
            for (Object value : values) {
                if (!(value instanceof String raw) || !StringUtils.hasText(raw)) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_WORKSPACE_INVALID",
                        "dbt model-paths must contain relative text paths"
                    );
                }
                Path relative = Path.of(raw.trim()).normalize();
                String normalized = relative.toString().replace('\\', '/');
                if (
                    relative.isAbsolute() ||
                    normalized.equals("..") ||
                    normalized.startsWith("../")
                ) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_WORKSPACE_INVALID",
                        "dbt model-paths cannot leave the project workspace"
                    );
                }
                Path root = workspaceDir.resolve(relative).normalize();
                ensureInside(workspaceDir, root);
                if (!Files.isDirectory(root)) {
                    throw new ScopedProjectException(
                        "MATERIALIZATION_WORKSPACE_INVALID",
                        "Configured dbt model path is unavailable: " + normalized
                    );
                }
                roots.add(root);
            }
            if (roots.isEmpty()) {
                throw new ScopedProjectException(
                    "MATERIALIZATION_WORKSPACE_INVALID",
                    "dbt model-paths cannot be empty"
                );
            }
            return List.copyOf(roots);
        } catch (ScopedProjectException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new ScopedProjectException(
                "MATERIALIZATION_WORKSPACE_INVALID",
                "Unable to read dbt model-paths",
                failure
            );
        }
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
        Set<String> normalizedSources = requiredSources.stream().map(this::normalizeName).filter(StringUtils::hasText).collect(
            LinkedHashSet::new,
            Set::add,
            Set::addAll
        );
        Set<String> remainingSources = new LinkedHashSet<>(normalizedSources);
        for (Path modelsDir : modelRoots(workspaceDir)) {
            try (var walk = Files.walk(modelsDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String lower = file.toString().toLowerCase(Locale.ROOT);
                    if (!(lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
                        continue;
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    Set<String> definitions = remainingSources.stream()
                        .filter(source -> containsRelevantSourceDefinition(content, Set.of(source)))
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);
                    if (definitions.isEmpty()) {
                        continue;
                    }
                    copyRelativeFile(workspaceDir, scopedProjectDir, file);
                    remainingSources.removeAll(definitions);
                    if (remainingSources.isEmpty()) return;
                }
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
                boolean active = Files.isRegularFile(directory.resolve(ACTIVE_MARKER));
                if (expired || (overflow && !active)) {
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

    public record CandidateArtifact(String path, String contentChecksum, String content) {}

    public record CandidateArtifactEntry(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        List<CandidateArtifact> artifacts
    ) {
        public CandidateArtifactEntry {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record ScopedCandidateProject(
        String projectDir,
        String selector,
        String bundleChecksum,
        List<CandidateArtifactEntry> entries
    ) {
        public ScopedCandidateProject {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public static final class ScopedProjectException extends IllegalArgumentException {

        private final String code;

        public ScopedProjectException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ScopedProjectException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private record CandidateOverlay(
        List<CandidateArtifactEntry> entries,
        List<String> selectors,
        Map<String, CandidateArtifact> artifactsByPath,
        Set<String> overlayNodes
    ) {}

    private record BundleDigest(String checksum, long bytes) {}
}
