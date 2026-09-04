package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical, content-complete manifest pinned to an immutable implementation revision. */
public final class DbtProjectBundleManifest {

    private static final int MANIFEST_VERSION = 1;
    private static final int MAX_MANIFEST_BYTES = 24 * 1024 * 1024;
    private static final Pattern LOCAL_REF_PATTERN = Pattern.compile(
        "ref\\s*\\(\\s*(?:['\"]([^'\"]+)['\"]\\s*,\\s*)?['\"]([^'\"]+)['\"]\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MODEL_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private DbtProjectBundleManifest() {}

    public static BundleSnapshot freeze(ObjectMapper objectMapper, List<BundleFile> files, ValidatedProject project) {
        Objects.requireNonNull(objectMapper, "objectMapper is required");
        Objects.requireNonNull(project, "project is required");
        String projectKey = DbtImplementationDraftContract.requiredText(project.projectKey(), "projectKey", 128);
        String projectChecksum = DbtImplementationDraftContract.requiredChecksum(
            project.packageChecksum(),
            "projectChecksum"
        );
        List<BundleFileEntry> entries = verifiedFiles(files);
        Map<String, List<String>> closure = dependencyClosure(project.nodes());
        List<NodeDependencyClosure> dependencyEntries = closure
            .entrySet()
            .stream()
            .map(entry -> new NodeDependencyClosure(entry.getKey(), entry.getValue()))
            .toList();
        FrozenManifest frozen = new FrozenManifest(
            MANIFEST_VERSION,
            projectKey,
            projectChecksum,
            entries,
            dependencyEntries
        );
        try {
            String manifest = objectMapper.writeValueAsString(frozen);
            return new BundleSnapshot(
                projectChecksum,
                ModelPackageChecksum.sha256Text(manifest),
                manifest,
                closure,
                entries.size()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The dbt project bundle manifest could not be serialized", exception);
        }
    }

    /** Restores an already persisted manifest only after all bundle and per-file pins have been re-verified. */
    public static RestoredBundle restore(
        ObjectMapper objectMapper,
        String manifest,
        String expectedBundleChecksum,
        String expectedProjectChecksum
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper is required");
        if (manifest == null || manifest.isBlank() || manifest.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
            throw invalidBundle("The frozen dbt project bundle manifest is missing or exceeds its safe limit");
        }
        String bundleChecksum = DbtImplementationDraftContract.requiredChecksum(
            expectedBundleChecksum,
            "bundleChecksum"
        );
        String projectChecksum = DbtImplementationDraftContract.requiredChecksum(
            expectedProjectChecksum,
            "projectChecksum"
        );
        if (!bundleChecksum.equals(ModelPackageChecksum.sha256Text(manifest))) {
            throw invalidBundle("The frozen dbt project bundle checksum does not match its manifest");
        }
        try {
            JsonNode root = objectMapper.readTree(manifest);
            if (
                root == null ||
                !root.isObject() ||
                root.path("manifestVersion").asInt(-1) != MANIFEST_VERSION ||
                !root.path("files").isArray() ||
                !root.path("dependencyClosure").isArray()
            ) {
                throw invalidBundle("The frozen dbt project bundle manifest has an unsupported structure");
            }
            String projectKey = requiredNodeText(root, "projectKey", 128);
            String manifestProjectChecksum = DbtImplementationDraftContract.requiredChecksum(
                requiredNodeText(root, "projectChecksum", 64),
                "projectChecksum"
            );
            if (!projectChecksum.equals(manifestProjectChecksum)) {
                throw invalidBundle("The frozen dbt project checksum does not match its implementation pin");
            }
            List<BundleFile> parsed = new ArrayList<>();
            JsonNode fileNodes = root.path("files");
            if (fileNodes.isEmpty() || fileNodes.size() > DbtImplementationDraftContract.MAX_FILES) {
                throw invalidBundle("The frozen dbt project bundle file count is invalid");
            }
            for (JsonNode fileNode : fileNodes) {
                parsed.add(
                    new BundleFile(
                        requiredNodeText(fileNode, "path", 512),
                        fileContent(fileNode),
                        requiredNodeText(fileNode, "checksum", 64),
                        fileNode.path("byteSize").asLong(-1)
                    )
                );
            }
            List<BundleFile> verified = verifiedFiles(parsed)
                .stream()
                .map(file -> new BundleFile(file.path(), file.content(), file.checksum(), file.byteSize()))
                .toList();
            return new RestoredBundle(projectKey, projectChecksum, bundleChecksum, verified);
        } catch (JsonProcessingException exception) {
            throw invalidBundle("The frozen dbt project bundle manifest is not valid JSON");
        }
    }

    /** Resolves one-argument ref() calls against the exact frozen project, including transitive refs. */
    public static List<BundleFile> resolveLocalModelDependencies(
        List<BundleFile> files,
        List<String> rootSql,
        Set<String> alreadyAvailableModelNames
    ) {
        List<BundleFile> verified = verifiedFiles(files)
            .stream()
            .map(file -> new BundleFile(file.path(), file.content(), file.checksum(), file.byteSize()))
            .toList();
        LinkedHashMap<String, BundleFile> models = new LinkedHashMap<>();
        for (BundleFile file : verified) {
            if (!isModelSql(file.path())) continue;
            String name = modelName(file.path());
            BundleFile previous = models.putIfAbsent(name, file);
            if (previous != null && !Objects.equals(previous.path(), file.path())) {
                throw invalidBundle("The frozen dbt project contains duplicate model names");
            }
        }
        Set<String> available = new HashSet<>(
            alreadyAvailableModelNames == null ? Set.of() : alreadyAvailableModelNames
        );
        ArrayDeque<String> pending = new ArrayDeque<>();
        if (rootSql != null) rootSql.forEach(sql -> pending.addAll(localRefs(sql)));
        List<BundleFile> dependencies = new ArrayList<>();
        while (!pending.isEmpty()) {
            String reference = pending.removeFirst();
            if (!available.add(reference)) continue;
            BundleFile dependency = models.get(reference);
            if (dependency == null) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_DEPENDENCY_MISSING",
                    "The frozen dbt project does not contain local model dependency " + reference
                );
            }
            dependencies.add(dependency);
            pending.addAll(localRefs(dependency.content()));
        }
        return List.copyOf(dependencies);
    }

    private static List<String> localRefs(String sql) {
        if (sql == null || sql.isBlank()) return List.of();
        Set<String> refs = new java.util.LinkedHashSet<>();
        Matcher matcher = LOCAL_REF_PATTERN.matcher(sql);
        while (matcher.find()) {
            if (matcher.group(1) != null) continue;
            String reference = matcher.group(2) == null ? null : matcher.group(2).trim();
            if (reference == null || !MODEL_NAME.matcher(reference).matches()) {
                throw invalidBundle("The frozen dbt project contains an invalid local model reference");
            }
            refs.add(reference);
        }
        return List.copyOf(refs);
    }

    private static boolean isModelSql(String path) {
        return path != null && path.startsWith("models/") && path.endsWith(".sql");
    }

    private static String modelName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.length() - ".sql".length());
    }

    private static List<BundleFileEntry> verifiedFiles(List<BundleFile> files) {
        if (files == null) {
            throw DbtImplementationDraftContract.unprocessable(
                "DBT_DRAFT_BUNDLE_INVALID",
                "The dbt project bundle files are required"
            );
        }
        List<FileInput> rawFiles = files
            .stream()
            .map(file -> new FileInput(file == null ? null : file.path(), file == null ? null : file.content()))
            .toList();
        List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(rawFiles);
        Map<String, BundleFile> byPath = new LinkedHashMap<>();
        for (BundleFile file : files) {
            String normalizedPath = DbtImplementationDraftContract.normalizePath(file.path());
            if (!normalizedPath.equals(file.path())) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_PATH_MISMATCH",
                    "Persisted dbt project paths must already be normalized"
                );
            }
            if (byPath.putIfAbsent(file.path(), file) != null) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_PATH_DUPLICATE",
                    "Persisted dbt project paths must be unique"
                );
            }
        }
        List<BundleFileEntry> result = new ArrayList<>(normalized.size());
        for (FileInput normalizedFile : normalized) {
            BundleFile persisted = byPath.get(normalizedFile.path());
            if (!Objects.equals(normalizedFile.content(), persisted.content())) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_CONTENT_MISMATCH",
                    "Persisted dbt project file content must already use canonical line endings"
                );
            }
            byte[] bytes = persisted.content().getBytes(StandardCharsets.UTF_8);
            String checksum = DbtImplementationDraftContract.requiredChecksum(
                persisted.checksum(),
                "content checksum"
            );
            if (!checksum.equals(ModelPackageChecksum.sha256(bytes))) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_HASH_MISMATCH",
                    "Persisted dbt project file checksum does not match its content"
                );
            }
            if (persisted.byteSize() != bytes.length) {
                throw DbtImplementationDraftContract.unprocessable(
                    "DBT_DRAFT_BUNDLE_SIZE_MISMATCH",
                    "Persisted dbt project file byte size does not match its content"
                );
            }
            result.add(new BundleFileEntry(persisted.path(), persisted.byteSize(), checksum, persisted.content()));
        }
        return List.copyOf(result);
    }

    private static String requiredNodeText(JsonNode parent, String field, int maxLength) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual()) throw invalidBundle("The frozen dbt project bundle is incomplete");
        return DbtImplementationDraftContract.requiredText(value.textValue(), field, maxLength);
    }

    private static String fileContent(JsonNode fileNode) {
        JsonNode value = fileNode == null ? null : fileNode.get("content");
        if (
            value == null ||
            !value.isTextual() ||
            value.textValue().length() > DbtImplementationDraftContract.MAX_FILE_BYTES
        ) {
            throw invalidBundle("The frozen dbt project bundle file content is invalid");
        }
        return value.textValue();
    }

    private static DbtImplementationDraftContract.DraftException invalidBundle(String message) {
        return DbtImplementationDraftContract.unprocessable("DBT_DRAFT_BUNDLE_INVALID", message);
    }

    private static Map<String, List<String>> dependencyClosure(List<ValidatedNode> nodes) {
        TreeMap<String, List<String>> direct = new TreeMap<>();
        for (ValidatedNode node : Objects.requireNonNull(nodes, "validated nodes are required")) {
            if (node == null) throw new IllegalArgumentException("validated nodes cannot contain null");
            direct.put(node.dbtUniqueId(), node.dependencies().stream().distinct().sorted().toList());
        }
        LinkedHashMap<String, List<String>> closure = new LinkedHashMap<>();
        direct.forEach((nodeId, ignored) -> {
            Set<String> visited = new HashSet<>();
            visited.add(nodeId);
            Set<String> dependencies = new java.util.TreeSet<>();
            collectDependencies(nodeId, direct, visited, dependencies);
            closure.put(nodeId, List.copyOf(dependencies));
        });
        return java.util.Collections.unmodifiableMap(closure);
    }

    private static void collectDependencies(
        String nodeId,
        Map<String, List<String>> direct,
        Set<String> visited,
        Set<String> result
    ) {
        for (String dependency : direct.getOrDefault(nodeId, List.of())) {
            if (!visited.add(dependency)) continue;
            result.add(dependency);
            if (direct.containsKey(dependency)) collectDependencies(dependency, direct, visited, result);
        }
    }

    public record BundleFile(String path, String content, String checksum, long byteSize) {}

    public record RestoredBundle(
        String projectKey,
        String projectChecksum,
        String bundleChecksum,
        List<BundleFile> files
    ) {
        public RestoredBundle {
            files = List.copyOf(files == null ? List.of() : files);
        }
    }

    public record BundleSnapshot(
        String projectChecksum,
        String bundleChecksum,
        String manifest,
        Map<String, List<String>> dependencyClosure,
        int fileCount
    ) {
        public BundleSnapshot {
            dependencyClosure = Map.copyOf(dependencyClosure);
        }
    }

    private record FrozenManifest(
        int manifestVersion,
        String projectKey,
        String projectChecksum,
        List<BundleFileEntry> files,
        List<NodeDependencyClosure> dependencyClosure
    ) {}

    private record BundleFileEntry(String path, long byteSize, String checksum, String content) {}

    private record NodeDependencyClosure(String dbtUniqueId, List<String> dependencies) {}
}
