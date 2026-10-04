package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Safely converts an uploaded dbt project archive into the internal model-package contract. */
@Service
public class DbtModelArchiveInspectService {

    static final long MAX_MANIFEST_BYTES = 16L * 1024 * 1024;
    static final long MAX_CATALOG_BYTES = 16L * 1024 * 1024;
    static final int MAX_GRAPH_NODES = 500;
    static final int MAX_MACRO_NODES = 2_000;
    static final int MAX_GRAPH_EDGES = 10_000;
    static final int MAX_GRAPH_DEPTH = 128;
    static final int MAX_COLUMNS = 20_000;
    static final long MAX_MODEL_SQL_BYTES = 2L * 1024 * 1024;
    static final long MAX_TOTAL_MODEL_SQL_BYTES = 16L * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final SafeZipExtractor zipExtractor;
    private final ModelPackageValidator packageValidator;
    private final DbtSourceProjectModelPackageAdapter sourceProjectAdapter;
    private final LegacyTsvModelPackageAdapter legacyAdapter;

    public DbtModelArchiveInspectService(ObjectMapper objectMapper, SafeZipExtractor zipExtractor) {
        this.objectMapper = objectMapper;
        this.zipExtractor = zipExtractor;
        this.packageValidator = new ModelPackageValidator(objectMapper);
        this.sourceProjectAdapter = new DbtSourceProjectModelPackageAdapter();
        this.legacyAdapter = new LegacyTsvModelPackageAdapter();
    }

    public ModelPackage inspect(MultipartFile archive) {
        try (SafeZipExtractor.ExtractedArchive extracted = zipExtractor.extract(archive)) {
            Optional<ProjectArtifacts> locatedProject = locateProject(extracted.root());
            if (locatedProject.isEmpty()) {
                Optional<ModelPackage> sourceProjectPackage = sourceProjectAdapter.convertIfPresent(extracted.root());
                if (sourceProjectPackage.isPresent()) {
                    ModelPackage converted = sourceProjectPackage.orElseThrow();
                    validateConvertedPackage(converted);
                    return converted;
                }
                ModelPackage legacyPackage = legacyAdapter
                    .convertIfPresent(extracted.root())
                    .orElseThrow(() ->
                        new ArchiveInspectionException(
                            "MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING",
                            "未找到 dbt manifest.json 或 models.tsv，请重新生成 dbt 导入包"
                        )
                    );
                validateConvertedPackage(legacyPackage);
                return legacyPackage;
            }
            ProjectArtifacts artifacts = locatedProject.orElseThrow();
            JsonNode manifest = readJson(artifacts.manifest(), MAX_MANIFEST_BYTES, "MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING");
            JsonNode catalog = artifacts.catalog() == null
                ? null
                : readJson(artifacts.catalog(), MAX_CATALOG_BYTES, "MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED");
            validateArtifactComplexity(manifest, catalog, artifacts.projectRoot());
            requireModelSql(manifest, artifacts.projectRoot());
            ModelPackage modelPackage = new DbtModelPackageConverter(objectMapper, new ModelConversionClassifier()).convert(
                new DbtModelPackageConverter.ConversionRequest(
                    packageId(manifest, artifacts.manifest()),
                    manifest,
                    catalog,
                    artifacts.projectRoot(),
                    Map.of(),
                    Set.of(),
                    new Defaults(null, null)
                )
            );
            validateConvertedPackage(modelPackage);
            return modelPackage;
        } catch (SafeZipExtractor.ArchiveException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_" + exception.code(), safeZipMessage(exception.code()));
        } catch (LegacyTsvModelPackageAdapter.LegacyArchiveException exception) {
            throw legacyError(exception);
        } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
            throw sourceProjectError(exception);
        } catch (ArchiveInspectionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArchiveInspectionException(
                "MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED",
                "无法检查 dbt ZIP，请确认其中的 dbt 产物可读取后重试"
            );
        }
    }

    private Optional<ProjectArtifacts> locateProject(Path archiveRoot) {
        List<ProjectArtifacts> candidates = new ArrayList<>();
        addProjectCandidate(candidates, archiveRoot);
        try (var children = Files.list(archiveRoot)) {
            children
                .filter(Files::isDirectory)
                .filter(child -> !"target".equals(child.getFileName().toString()))
                .forEach(child -> addProjectCandidate(candidates, child));
        } catch (IOException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "无法检查 dbt ZIP 目录结构");
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() != 1) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "ZIP 中包含多个 dbt 项目，无法确定要导入的项目");
        }
        return Optional.of(candidates.getFirst());
    }

    private static void addProjectCandidate(List<ProjectArtifacts> candidates, Path projectRoot) {
        Path manifest = projectRoot.resolve("manifest.json");
        Path targetManifest = projectRoot.resolve("target/manifest.json");
        if (Files.isRegularFile(targetManifest)) {
            candidates.add(new ProjectArtifacts(projectRoot, targetManifest, catalog(projectRoot)));
        } else if (Files.isRegularFile(manifest)) {
            candidates.add(new ProjectArtifacts(projectRoot, manifest, catalog(projectRoot)));
        }
    }

    private static Path catalog(Path projectRoot) {
        Path targetCatalog = projectRoot.resolve("target/catalog.json");
        return Files.isRegularFile(targetCatalog)
            ? targetCatalog
            : Files.isRegularFile(projectRoot.resolve("catalog.json")) ? projectRoot.resolve("catalog.json") : null;
    }

    private JsonNode readJson(Path path, long maxBytes, String missingCode) {
        try {
            if (!Files.isRegularFile(path)) {
                throw new ArchiveInspectionException(missingCode, "dbt 产物文件缺失");
            }
            if (Files.size(path) > maxBytes) {
                throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_TOO_LARGE", "dbt 产物文件超过允许大小");
            }
            JsonNode value = objectMapper.readTree(path.toFile());
            if (value == null || !value.isObject()) {
                throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "dbt 产物 JSON 格式无效");
            }
            return value;
        } catch (ArchiveInspectionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "dbt 产物 JSON 无法解析");
        }
    }

    private static void requireModelSql(JsonNode manifest, Path projectRoot) {
        JsonNode nodes = manifest.path("nodes");
        if (!nodes.isObject()) {
            return;
        }
        var fields = nodes.fields();
        while (fields.hasNext()) {
            JsonNode node = fields.next().getValue();
            if (!"model".equalsIgnoreCase(node.path("resource_type").asText())) {
                continue;
            }
            if (hasText(node, "raw_code") || hasText(node, "raw_sql") || hasText(node, "compiled_code") || hasText(node, "compiled_sql")) {
                continue;
            }
            String originalFilePath = node.path("original_file_path").asText();
            Path sql = projectRoot.resolve(originalFilePath).normalize();
            if (
                originalFilePath.isBlank() || originalFilePath.contains("\\") || originalFilePath.startsWith("/") ||
                !sql.startsWith(projectRoot) || !Files.isRegularFile(sql)
            ) {
                throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_SQL_MISSING", "dbt 模型缺少可读取的 SQL 定义");
            }
        }
    }

    private static void validateArtifactComplexity(JsonNode manifest, JsonNode catalog, Path projectRoot) {
        JsonNode nodes = manifest.path("nodes");
        JsonNode sources = manifest.path("sources");
        JsonNode macros = manifest.path("macros");
        int graphNodes = objectSize(nodes) + objectSize(sources);
        if (graphNodes > MAX_GRAPH_NODES) {
            throw tooComplex("dbt 节点数量超过安全检查上限");
        }
        if (objectSize(macros) > MAX_MACRO_NODES) {
            throw tooComplex("dbt 宏数量超过安全检查上限");
        }

        Map<String, List<String>> businessGraph = new HashMap<>();
        int edges = 0;
        int columns = 0;
        long totalSqlBytes = 0;
        for (JsonNode collection : List.of(nodes, sources, macros)) {
            if (!collection.isObject()) {
                continue;
            }
            var fields = collection.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode node = entry.getValue();
                List<String> dependencies = dependencyIds(node);
                edges += dependencies.size();
                if (edges > MAX_GRAPH_EDGES) {
                    throw tooComplex("dbt 依赖数量超过安全检查上限");
                }
                if (collection == nodes || collection == sources) {
                    businessGraph.put(entry.getKey(), nodeDependencyIds(node));
                }
                columns += objectSize(node.path("columns"));
                if (columns > MAX_COLUMNS) {
                    throw tooComplex("dbt 字段数量超过安全检查上限");
                }
                if ("model".equalsIgnoreCase(node.path("resource_type").asText())) {
                    long sqlBytes = modelSqlBytes(node, projectRoot);
                    if (sqlBytes > MAX_MODEL_SQL_BYTES) {
                        throw tooComplex("单个 dbt 模型 SQL 超过安全检查上限");
                    }
                    totalSqlBytes += sqlBytes;
                    if (totalSqlBytes > MAX_TOTAL_MODEL_SQL_BYTES) {
                        throw tooComplex("dbt 模型 SQL 总量超过安全检查上限");
                    }
                }
            }
        }
        if (catalog != null) {
            int catalogNodes = objectSize(catalog.path("nodes")) + objectSize(catalog.path("sources"));
            if (catalogNodes > MAX_GRAPH_NODES) {
                throw tooComplex("dbt catalog 节点数量超过安全检查上限");
            }
            columns += countColumns(catalog.path("nodes")) + countColumns(catalog.path("sources"));
            if (columns > MAX_COLUMNS) {
                throw tooComplex("dbt manifest/catalog 字段数量超过安全检查上限");
            }
        }
        requireBoundedDepth(businessGraph);
    }

    private static int objectSize(JsonNode value) {
        return value != null && value.isObject() ? value.size() : 0;
    }

    private static int countColumns(JsonNode collection) {
        if (collection == null || !collection.isObject()) {
            return 0;
        }
        int columns = 0;
        var fields = collection.fields();
        while (fields.hasNext()) {
            columns += objectSize(fields.next().getValue().path("columns"));
            if (columns > MAX_COLUMNS) {
                return columns;
            }
        }
        return columns;
    }

    private static List<String> dependencyIds(JsonNode node) {
        List<String> dependencies = new ArrayList<>();
        JsonNode dependsOn = node.path("depends_on");
        for (String field : List.of("nodes", "macros")) {
            JsonNode values = dependsOn.path(field);
            if (!values.isArray()) {
                continue;
            }
            values.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    dependencies.add(value.asText());
                }
            });
        }
        return dependencies;
    }

    private static List<String> nodeDependencyIds(JsonNode node) {
        List<String> dependencies = new ArrayList<>();
        JsonNode values = node.path("depends_on").path("nodes");
        if (!values.isArray()) {
            return dependencies;
        }
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                dependencies.add(value.asText());
            }
        });
        return dependencies;
    }

    private static long modelSqlBytes(JsonNode node, Path projectRoot) {
        for (String field : List.of("raw_code", "raw_sql", "compiled_code", "compiled_sql")) {
            if (hasText(node, field)) {
                return utf8UpperBound(node.path(field).asText());
            }
        }
        String originalFilePath = node.path("original_file_path").asText();
        if (
            originalFilePath.isBlank() ||
            originalFilePath.contains("\\") ||
            originalFilePath.startsWith("/") ||
            originalFilePath.contains("\u0000")
        ) {
            return 0;
        }
        Path sql = projectRoot.resolve(originalFilePath).normalize();
        if (!sql.startsWith(projectRoot) || !Files.isRegularFile(sql)) {
            return 0;
        }
        try {
            return Files.size(sql);
        } catch (IOException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "dbt 模型 SQL 无法读取");
        }
    }

    private static long utf8UpperBound(String value) {
        return Math.min(Long.MAX_VALUE, (long) value.length() * 3L);
    }

    private static void requireBoundedDepth(Map<String, List<String>> graph) {
        for (String start : graph.keySet()) {
            var queue = new ArrayDeque<NodeDepth>();
            var deepestVisit = new HashMap<String, Integer>();
            queue.add(new NodeDepth(start, 0));
            deepestVisit.put(start, 0);
            while (!queue.isEmpty()) {
                NodeDepth current = queue.removeFirst();
                if (current.depth() > MAX_GRAPH_DEPTH) {
                    throw tooComplex("dbt 依赖深度超过安全检查上限");
                }
                for (String dependency : graph.getOrDefault(current.node(), List.of())) {
                    if (graph.containsKey(dependency)) {
                        int nextDepth = current.depth() + 1;
                        if (nextDepth > deepestVisit.getOrDefault(dependency, -1)) {
                            deepestVisit.put(dependency, nextDepth);
                            queue.addLast(new NodeDepth(dependency, nextDepth));
                        }
                    }
                }
            }
        }
    }

    private void validateConvertedPackage(ModelPackage modelPackage) {
        var issues = packageValidator.validate(modelPackage);
        if (!issues.isEmpty()) {
            throw new ArchiveInspectionException(
                "MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED",
                "dbt ZIP 转换结果不符合内部模型包契约：" + issues.getFirst().message()
            );
        }
        try {
            int convertedBytes = objectMapper.writeValueAsBytes(modelPackage).length;
            if (convertedBytes > ModelPackageValidator.MAX_PACKAGE_BYTES) {
                throw new ArchiveInspectionException(
                    "MODEL_IMPORT_ARCHIVE_TOO_LARGE",
                    "转换后的模型包为 " + convertedBytes + " bytes，超过允许大小 " + ModelPackageValidator.MAX_PACKAGE_BYTES + " bytes"
                );
            }
        } catch (ArchiveInspectionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "转换后的模型包无法验证");
        }
    }

    private static ArchiveInspectionException tooComplex(String message) {
        return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_TOO_LARGE", message);
    }

    private static ArchiveInspectionException legacyError(LegacyTsvModelPackageAdapter.LegacyArchiveException exception) {
        String code = exception.code();
        if (code != null && code.contains("TOO_LARGE")) {
            return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_TOO_LARGE", "legacy dbt ZIP 超过允许大小");
        }
        if ("LEGACY_SQL_MISSING".equals(code)) {
            return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_SQL_MISSING", "legacy dbt ZIP 缺少 models.tsv 引用的 SQL");
        }
        return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INVALID", "legacy dbt ZIP 的 models.tsv 或 SQL 格式无效");
    }

    private static ArchiveInspectionException sourceProjectError(
        DbtSourceProjectModelPackageAdapter.SourceProjectException exception
    ) {
        String code = exception.code();
        if (code != null && code.contains("TOO_LARGE")) {
            return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_TOO_LARGE", "dbt 源项目超过允许大小");
        }
        if ("SOURCE_PROJECT_EMPTY".equals(code) || "SOURCE_PROJECT_SQL_MISSING".equals(code)) {
            return new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_SQL_MISSING", "dbt 源项目缺少可读取的模型 SQL");
        }
        return new ArchiveInspectionException(
            "MODEL_IMPORT_ARCHIVE_SOURCE_PROJECT_INVALID",
            "dbt 源项目结构无法安全解析，请检查项目根、models 目录和 SQL 引用"
        );
    }

    private static boolean hasText(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }

    private static String packageId(JsonNode manifest, Path manifestPath) {
        try {
            String projectName = manifest.path("metadata").path("project_name").asText("dbt").trim();
            String normalizedProjectName = projectName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
            if (normalizedProjectName.isBlank()) {
                normalizedProjectName = "dbt";
            }
            return normalizedProjectName + "-" + ModelPackageChecksum.sha256(Files.readAllBytes(manifestPath));
        } catch (IOException exception) {
            throw new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_INSPECTION_FAILED", "无法生成 dbt 模型包标识");
        }
    }

    private static String safeZipMessage(String code) {
        return "TOO_LARGE".equals(code) ? "dbt ZIP 超过允许大小" : "dbt ZIP 不符合安全导入要求";
    }

    private record ProjectArtifacts(Path projectRoot, Path manifest, Path catalog) {}

    private record NodeDepth(String node, int depth) {}

    public static final class ArchiveInspectionException extends RuntimeException {

        private final String code;

        public ArchiveInspectionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
