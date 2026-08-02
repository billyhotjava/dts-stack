package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier.ClassificationInput;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DbtMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ImportIssue;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.SourceOnlySchemaContractReader.ModelContract;
import com.yuzhi.dts.platform.service.modeling.imports.converter.SourceOnlySchemaContractReader.ResolvedConfiguration;
import com.yuzhi.dts.platform.service.modeling.imports.converter.SourceOnlySchemaContractReader.SchemaCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a dbt source project without executing dbt or SQL.
 *
 * <p>This adapter deliberately limits itself to explicit project, inventory and SQL facts.
 * Business model type, grain and consumption semantics remain blocked for confirmation.
 */
final class DbtSourceProjectModelPackageAdapter {

    private static final long MAX_PROJECT_FILE_BYTES = 1024L * 1024;
    private static final long MAX_SQL_BYTES = 2L * 1024 * 1024;
    private static final long MAX_TOTAL_SQL_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_MACRO_BYTES = 4L * 1024 * 1024;
    private static final int MAX_GRAPH_NODES = 500;
    private static final int MAX_GRAPH_EDGES = 10_000;
    private static final int MAX_GRAPH_DEPTH = 128;
    private static final int MAX_GRAPH_TRAVERSAL_STEPS = MAX_GRAPH_NODES * MAX_GRAPH_NODES;
    private static final int MAX_PROJECT_PATHS = 16;
    private static final int MAX_MACRO_DECLARATIONS = 256;
    private static final int MAX_SCAN_DEPTH = 16;
    private static final int MAX_TSV_DEPTH = 4;

    private static final Pattern PROJECT_NAME = Pattern.compile(
        "(?m)^(?:\\uFEFF)?name\\s*:\\s*[\"']?([a-zA-Z0-9_-]+)[\"']?\\s*(?:#.*)?$"
    );
    private static final Pattern PROJECT_VERSION = Pattern.compile(
        "(?m)^version\\s*:\\s*[\"']?([^\"'#\\s]+)[\"']?\\s*(?:#.*)?$"
    );
    private static final Pattern REF = Pattern.compile(
        "\\bref\\s*\\(\\s*([\"'])([^\"'\\r\\n]+)\\1\\s*(?:,\\s*([\"'])([^\"'\\r\\n]+)\\3)?\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REF_CALL = Pattern.compile("\\bref\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE = Pattern.compile(
        "\\bsource\\s*\\(\\s*([\"'])([^\"'\\r\\n]+)\\1\\s*,\\s*([\"'])([^\"'\\r\\n]+)\\3\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SOURCE_CALL = Pattern.compile("\\bsource\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATERIALIZED = Pattern.compile(
        "\\bmaterialized\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TAGS = Pattern.compile(
        "\\btags\\s*=\\s*\\[([^]]*)]",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUOTED_VALUE = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final Pattern JINJA_BLOCK = Pattern.compile("\\{\\{(.*?)}}|\\{%(.*?)%}", Pattern.DOTALL);
    private static final Pattern MACRO_DECLARATION = Pattern.compile(
        "\\{%[-\\s]*macro\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> LAYERS = Set.of("ODS", "STG", "DWD", "DWS", "ADS");

    private final LegacyTsvModelPackageAdapter legacyAdapter = new LegacyTsvModelPackageAdapter();
    private final ModelConversionClassifier classifier = new ModelConversionClassifier();

    Optional<ModelPackage> convertIfPresent(Path archiveRoot) {
        Path root = normalizedDirectory(archiveRoot);
        Optional<Path> locatedProject = locateProjectRoot(root);
        if (locatedProject.isEmpty()) {
            return Optional.empty();
        }

        Path projectRoot = locatedProject.orElseThrow();
        ProjectMetadata project = readProjectMetadata(projectRoot.resolve("dbt_project.yml"));
        List<ModelSeed> seeds = readInventory(projectRoot, project);
        if (seeds.isEmpty()) {
            throw error("SOURCE_PROJECT_EMPTY", "dbt 项目的 models 目录中没有可读取的 SQL 模型");
        }
        SchemaCatalog schemaCatalog = SourceOnlySchemaContractReader.read(projectRoot, project.modelPaths());
        seeds = applyTrustedConfigurations(seeds, schemaCatalog);

        Map<String, ModelSeed> byUniqueId = new TreeMap<>();
        Map<String, String> uniqueIdByName = new HashMap<>();
        for (ModelSeed seed : seeds) {
            String uniqueId = modelUniqueId(project.name(), seed.name());
            if (byUniqueId.putIfAbsent(uniqueId, seed) != null || uniqueIdByName.putIfAbsent(seed.name(), uniqueId) != null) {
                throw error("SOURCE_PROJECT_MODEL_DUPLICATE", "dbt 项目包含重复模型: " + seed.name());
            }
        }

        Map<String, SourceNode> sources = new TreeMap<>();
        Map<String, List<String>> dependencies = new TreeMap<>();
        Map<String, List<String>> unresolvedRefs = new TreeMap<>();
        Map<String, List<String>> dynamicCalls = new TreeMap<>();
        Map<String, SourceOnlyJinjaGuard.Risk> jinjaRisks = new TreeMap<>();
        int staticCallCount = 0;
        for (Map.Entry<String, ModelSeed> entry : byUniqueId.entrySet()) {
            ParsedDependencies parsed = parseDependencies(
                project.name(),
                entry.getValue().resourcePath(),
                entry.getValue().sql(),
                uniqueIdByName,
                byUniqueId.keySet(),
                sources
            );
            dependencies.put(entry.getKey(), parsed.dependencies());
            unresolvedRefs.put(entry.getKey(), parsed.unresolvedRefs());
            dynamicCalls.put(entry.getKey(), parsed.dynamicCalls());
            jinjaRisks.put(entry.getKey(), SourceOnlyJinjaGuard.inspect(entry.getValue().sql()));
            staticCallCount = boundedAdd(staticCallCount, parsed.callCount(), "dbt 静态调用数量超过安全上限");
        }

        MacroInventory macros = readMacros(projectRoot, project, uniqueIdByName, byUniqueId.keySet(), sources);
        staticCallCount = boundedAdd(staticCallCount, macros.staticCallCount(), "dbt 静态调用数量超过安全上限");
        validateGraphBudget(byUniqueId.keySet(), sources.keySet(), dependencies, macros.nodes(), staticCallCount);
        TraversalBudget reachabilityBudget = new TraversalBudget(MAX_GRAPH_TRAVERSAL_STEPS);

        Map<String, Set<String>> directBlocks = directBlockReasons(
            byUniqueId,
            schemaCatalog,
            unresolvedRefs,
            dynamicCalls,
            jinjaRisks
        );
        Map<String, Set<String>> closureBlocks = propagateBlockReasons(byUniqueId.keySet(), dependencies, directBlocks);

        List<TechnicalNode> technicalNodes = new ArrayList<>(macros.nodes());
        List<PackageModel> models = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>(macros.issues());
        addSourceOnlyBlockIssues(issues, closureBlocks);
        addLegacyDependencyIssues(issues, unresolvedRefs, dynamicCalls);
        issues.add(
            new ImportIssue(
                "DBT_SOURCE_PROJECT_STATIC_ANALYSIS",
                "INFO",
                "$.dbt",
                null,
                "已从 dbt 源项目静态读取模型、ref/source 和显式配置，未执行 dbt 或模型 SQL",
                "在普通预检中确认业务语义；需要运行验证时继续使用高级建模入口"
            )
        );

        for (Map.Entry<String, ModelSeed> entry : byUniqueId.entrySet()) {
            String uniqueId = entry.getKey();
            ModelSeed seed = entry.getValue();
            ModelContract schemaContract = schemaCatalog.contracts().get(seed.name());
            List<Column> declaredColumns = schemaContract != null && schemaContract.verified()
                ? schemaContract.columns()
                : List.of();
            List<String> reachableSources = reachableSources(uniqueId, dependencies, reachabilityBudget);
            List<SourceRef> sourceRefs = reachableSources
                .stream()
                .map(sourceId -> new SourceRef("TABLE", sourceId, null))
                .toList();
            boolean technical = isExplicitTechnical(seed);
            SemanticMetadata semantics = new SemanticMetadata(
                null,
                seed.layer(),
                null,
                null,
                null,
                explicitDomainTag(seed.tags()),
                sourceRefs,
                List.of(),
                Map.of(),
                null,
                null,
                seed.evidenceSource(),
                technical
            );
            SqlArtifact sql = sql(seed.sql());
            ConversionResult conversion = classifier.classify(
                new ClassificationInput("model", seed.materialization(), seed.sql(), semantics, technical)
            );
            if (
                conversion.mode() == ConversionMode.BLOCKED &&
                conversion.reasonCodes() != null &&
                !conversion.reasonCodes().isEmpty() &&
                conversion.reasonCodes().stream().allMatch(reason -> reason != null && reason.startsWith("MISSING_"))
            ) {
                conversion = new ConversionResult(ConversionMode.BLOCKED, List.of("SOURCE_SEMANTICS_INCOMPLETE"));
            }
            Set<String> blockers = closureBlocks.getOrDefault(uniqueId, Set.of());
            if (!blockers.isEmpty()) {
                List<String> reasons = conversion.reasonCodes();
                for (String blocker : blockers) {
                    reasons = append(reasons, blocker);
                }
                conversion = new ConversionResult(ConversionMode.BLOCKED, reasons);
            }

            Map<String, Object> config = new LinkedHashMap<>(seed.config());
            config.put("sourceProjectEvidence", seed.evidenceSource());
            config.put("implementationOwnership", "DBT_MANAGED");
            config.put("sourceConfigurationProvenance", "DECLARED");
            config.put("sourceContractEnforced", schemaContract != null && schemaContract.verified());
            if (!declaredColumns.isEmpty()) {
                config.put("structureProvenance", "DECLARED");
                TreeMap<String, String> fieldProvenance = new TreeMap<>();
                declaredColumns.forEach(column -> fieldProvenance.put(column.name(), "DECLARED"));
                config.put("fieldProvenance", Map.copyOf(fieldProvenance));
                config.put(
                    "declaredColumns",
                    declaredColumns
                        .stream()
                        .map(column -> Map.of("name", column.name(), "dataType", column.dataType()))
                        .toList()
                );
            }
            config.put("semanticConfirmationRequired", conversion.reasonCodes());
            if (technical) {
                technicalNodes.add(
                    new TechnicalNode(
                        uniqueId,
                        seed.name(),
                        "model",
                        seed.resourcePath(),
                        sql,
                        Map.copyOf(config),
                        dependencies.getOrDefault(uniqueId, List.of()),
                        seed.tags(),
                        conversion
                    )
                );
                continue;
            }

            models.add(
                new PackageModel(
                    uniqueId,
                    seed.name(),
                    seed.description(),
                    seed.resourcePath(),
                    sql,
                    seed.materialization(),
                    Map.copyOf(config),
                    seed.tags(),
                    declaredColumns,
                    List.of(),
                    dependencies.getOrDefault(uniqueId, List.of()),
                    semantics,
                    conversion
                )
            );
            issues.add(
                new ImportIssue(
                    "SOURCE_SEMANTICS_INCOMPLETE",
                    "WARNING",
                    "$.models[" + uniqueId + "].semantics",
                    uniqueId,
                    "dbt 结构已识别，但业务模型类型、粒度和消费场景不能由 SQL 安全推断",
                    "COMPLETE_MAPPING"
                )
            );
        }

        technicalNodes.sort(Comparator.comparing(TechnicalNode::dbtUniqueId));
        models.sort(Comparator.comparing(PackageModel::dbtUniqueId));
        issues.sort(
            Comparator.comparing(ImportIssue::code)
                .thenComparing(issue -> issue.modelUniqueId() == null ? "" : issue.modelUniqueId())
        );

        String fingerprint = fingerprint(projectRoot, project, seeds, schemaCatalog.fingerprint());
        String packageId = normalizeSegment(project.name()) + "-" + ModelPackageChecksum.sha256Text(fingerprint);
        ModelPackage withoutChecksum = new ModelPackage(
            ModelPackageContract.SCHEMA_VERSION,
            packageId,
            null,
            new DbtMetadata(project.name(), project.version(), "source-project/v1", "static-no-execution"),
            new Defaults(null, null),
            List.copyOf(sources.values()),
            List.copyOf(technicalNodes),
            List.copyOf(models),
            List.copyOf(issues)
        );
        return Optional.of(ModelPackageChecksum.withChecksum(withoutChecksum));
    }

    private static List<ModelSeed> applyTrustedConfigurations(List<ModelSeed> seeds, SchemaCatalog schemaCatalog) {
        List<ModelSeed> configured = new ArrayList<>();
        for (ModelSeed seed : seeds) {
            ResolvedConfiguration resolved = schemaCatalog.configurationFor(seed.resourcePath(), seed.name());
            LinkedHashSet<String> tags = new LinkedHashSet<>(resolved.tags());
            tags.addAll(seed.tags());
            LinkedHashSet<String> blockers = new LinkedHashSet<>(resolved.blockers());
            String layer = seed.layer();
            Set<String> taggedLayers = tags
                .stream()
                .map(tag -> tag.toUpperCase(Locale.ROOT))
                .filter(LAYERS::contains)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            if (taggedLayers.size() > 1 || (layer != null && !taggedLayers.isEmpty() && !taggedLayers.contains(layer.toUpperCase(Locale.ROOT)))) {
                blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
            } else if (layer == null && taggedLayers.size() == 1) {
                layer = taggedLayers.iterator().next();
            }
            Map<String, Object> config = new LinkedHashMap<>(seed.config());
            if (resolved.materialization() != null) {
                config.put("declaredMaterialization", resolved.materialization());
            }
            if (!resolved.tags().isEmpty()) {
                config.put("declaredTags", resolved.tags());
            }
            configured.add(
                new ModelSeed(
                    seed.name(),
                    seed.resourcePath(),
                    seed.sql(),
                    layer,
                    firstNonBlank(seed.materialization(), resolved.materialization()),
                    List.copyOf(tags),
                    seed.description(),
                    Map.copyOf(config),
                    seed.evidenceSource(),
                    Set.copyOf(blockers)
                )
            );
        }
        return List.copyOf(configured);
    }

    private List<ModelSeed> readInventory(Path projectRoot, ProjectMetadata project) {
        List<ModelSeed> scanned = scanModels(projectRoot, project.modelPaths());
        Optional<ModelPackage> legacyInventory;
        try {
            legacyInventory = legacyAdapter.convertIfPresent(projectRoot);
        } catch (LegacyTsvModelPackageAdapter.LegacyArchiveException exception) {
            throw new SourceProjectException("SOURCE_PROJECT_INVENTORY_INVALID", exception.getMessage(), exception);
        }
        if (legacyInventory.isEmpty()) {
            return scanned;
        }

        TreeMap<String, ModelSeed> scannedByPath = new TreeMap<>();
        scanned.forEach(seed -> scannedByPath.put(seed.resourcePath(), seed));
        disabledInventoryPaths(projectRoot).forEach(scannedByPath::remove);
        List<ModelSeed> reconciled = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (PackageModel model : legacyInventory.orElseThrow().models()) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("inventory", "models.tsv");
            copyConfig(model.config(), config, "legacySourceDataSourceId", "sourceDataSourceId");
            ModelSeed scannedSeed = scannedByPath.remove(model.resourcePath());
            List<String> tags = mergeTags(model.tags(), scannedSeed == null ? List.of() : scannedSeed.tags());
            ModelSeed seed = new ModelSeed(
                model.name(),
                model.resourcePath(),
                model.sql().effectiveSql(),
                firstNonBlank(textConfig(model.config(), "legacyLayer"), scannedSeed == null ? null : scannedSeed.layer()),
                firstNonBlank(
                    textConfig(model.config(), "legacyMaterialized"),
                    scannedSeed == null ? null : scannedSeed.materialization()
                ),
                tags,
                model.description(),
                Map.copyOf(config),
                "models.tsv+sql",
                Set.of()
            );
            if (!names.add(seed.name())) {
                throw error("SOURCE_PROJECT_MODEL_DUPLICATE", "models.tsv 包含重复模型: " + seed.name());
            }
            reconciled.add(seed);
        }
        for (ModelSeed seed : scannedByPath.values()) {
            if (!names.add(seed.name())) {
                throw error(
                    "SOURCE_PROJECT_MODEL_DUPLICATE",
                    "models.tsv 与 model-paths 中存在同名不同路径模型: " + seed.name()
                );
            }
            reconciled.add(seed);
        }
        reconciled.sort(Comparator.comparing(ModelSeed::resourcePath));
        return List.copyOf(reconciled);
    }

    private static Set<String> disabledInventoryPaths(Path projectRoot) {
        List<Path> candidates;
        try (var paths = Files.walk(projectRoot, MAX_TSV_DEPTH)) {
            candidates = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> "models.tsv".equalsIgnoreCase(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> relative(projectRoot, path)))
                .toList();
        } catch (IOException exception) {
            throw new SourceProjectException("SOURCE_PROJECT_READ_FAILED", "无法复核 models.tsv 禁用项", exception);
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }

        Path tsv = candidates.getFirst();
        String value = decodeUtf8(
            readBounded(tsv, LegacyTsvModelPackageAdapter.MAX_TSV_BYTES, "SOURCE_PROJECT_INVENTORY_TOO_LARGE"),
            "SOURCE_PROJECT_INVENTORY_INVALID",
            "models.tsv 必须使用 UTF-8 编码"
        );
        List<String> lines = value.lines().toList();
        if (lines.isEmpty() || lines.size() > LegacyTsvModelPackageAdapter.MAX_TSV_LINES) {
            throw error("SOURCE_PROJECT_INVENTORY_INVALID", "models.tsv 行数无效");
        }
        String headerLine = lines.getFirst().startsWith("\uFEFF") ? lines.getFirst().substring(1) : lines.getFirst();
        String[] header = headerLine.split("\\t", -1);
        int enabledIndex = columnIndex(header, "enabled");
        int sqlPathIndex = columnIndex(header, "sql_path");
        if (enabledIndex < 0 || sqlPathIndex < 0) {
            return Set.of();
        }

        TreeSet<String> disabled = new TreeSet<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            String[] fields = lines.get(index).split("\\t", -1);
            if (
                enabledIndex >= fields.length ||
                sqlPathIndex >= fields.length ||
                !"false".equalsIgnoreCase(fields[enabledIndex].trim())
            ) {
                continue;
            }
            String rawSqlPath = fields[sqlPathIndex].trim();
            if (rawSqlPath.isEmpty()) {
                continue;
            }
            String sqlPath;
            try {
                sqlPath = safeProjectRelativePath(rawSqlPath, "models.tsv sql_path");
            } catch (SourceProjectException ignored) {
                // Legacy intentionally skips disabled rows before SQL-path validation.
                continue;
            }
            addDisabledPath(projectRoot, tsv.getParent().resolve(sqlPath).normalize(), disabled);
            addDisabledPath(projectRoot, projectRoot.resolve(sqlPath).normalize(), disabled);
        }
        return Set.copyOf(disabled);
    }

    private static int columnIndex(String[] header, String expected) {
        for (int index = 0; index < header.length; index++) {
            if (expected.equalsIgnoreCase(header[index].trim())) {
                return index;
            }
        }
        return -1;
    }

    private static void addDisabledPath(Path projectRoot, Path candidate, Set<String> disabled) {
        if (candidate.startsWith(projectRoot)) {
            disabled.add(relative(projectRoot, candidate));
        }
    }

    private static List<ModelSeed> scanModels(Path projectRoot, List<String> modelPaths) {
        TreeMap<String, Path> sqlFiles = new TreeMap<>();
        for (String configuredPath : modelPaths) {
            Path modelsRoot = resolveProjectPath(projectRoot, configuredPath, "model-paths");
            if (!Files.isDirectory(modelsRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(modelsRoot, MAX_SCAN_DEPTH + 1)) {
                paths
                    .peek(path -> requireScanDepth(modelsRoot, path, "model-paths"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .forEach(path -> sqlFiles.putIfAbsent(relative(projectRoot, path), path));
            } catch (IOException exception) {
                throw new SourceProjectException("SOURCE_PROJECT_READ_FAILED", "无法扫描 dbt model-paths", exception);
            }
        }
        if (sqlFiles.size() > MAX_GRAPH_NODES) {
            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 项目模型数量超过 " + MAX_GRAPH_NODES);
        }
        long totalBytes = 0;
        List<ModelSeed> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : sqlFiles.entrySet()) {
            Path sqlFile = entry.getValue();
            byte[] bytes = readBounded(sqlFile, MAX_SQL_BYTES, "SOURCE_PROJECT_SQL_TOO_LARGE");
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_SQL_BYTES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 模型 SQL 总量超过 16 MiB");
            }
            String sql = decodeUtf8(bytes, "SOURCE_PROJECT_SQL_INVALID", "dbt 模型 SQL 必须使用 UTF-8 编码");
            if (sql.isBlank()) {
                throw error("SOURCE_PROJECT_SQL_INVALID", "dbt 模型 SQL 不能为空: " + entry.getKey());
            }
            SourceOnlyJinjaGuard.Risk jinja = SourceOnlyJinjaGuard.inspect(sql);
            List<String> tags = jinja.tags();
            String layer = explicitLayer(tags);
            result.add(
                new ModelSeed(
                    withoutExtension(sqlFile.getFileName().toString()),
                    entry.getKey(),
                    sql,
                    layer,
                    jinja.materialization(),
                    tags,
                    null,
                    Map.of("inventory", "dbt_project.yml#model-paths"),
                    "sql-config",
                    Set.of()
                )
            );
        }
        return List.copyOf(result);
    }

    private static MacroInventory readMacros(
        Path projectRoot,
        ProjectMetadata project,
        Map<String, String> uniqueIdByName,
        Set<String> knownModelIds,
        Map<String, SourceNode> sources
    ) {
        TreeMap<String, Path> files = new TreeMap<>();
        for (String configuredPath : project.macroPaths()) {
            Path macrosRoot = resolveProjectPath(projectRoot, configuredPath, "macro-paths");
            if (!Files.isDirectory(macrosRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(macrosRoot, MAX_SCAN_DEPTH + 1)) {
                paths
                    .peek(path -> requireScanDepth(macrosRoot, path, "macro-paths"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .forEach(path -> files.putIfAbsent(relative(projectRoot, path), path));
            } catch (IOException exception) {
                throw new SourceProjectException("SOURCE_PROJECT_READ_FAILED", "无法扫描 dbt macro-paths", exception);
            }
        }

        long totalBytes = 0;
        int staticCallCount = 0;
        TreeMap<String, TechnicalNode> nodes = new TreeMap<>();
        List<ImportIssue> issues = new ArrayList<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            byte[] bytes = readBounded(entry.getValue(), MAX_SQL_BYTES, "SOURCE_PROJECT_SQL_TOO_LARGE");
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_MACRO_BYTES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", "dbt macro SQL 总量超过 4 MiB");
            }
            String macroSql = decodeUtf8(bytes, "SOURCE_PROJECT_SQL_INVALID", "dbt macro SQL 必须使用 UTF-8 编码");
            String staticSql = stripComments(macroSql);
            ParsedDependencies parsed = parseDependencies(
                project.name(),
                entry.getKey(),
                macroSql,
                uniqueIdByName,
                knownModelIds,
                sources
            );
            staticCallCount = boundedAdd(staticCallCount, parsed.callCount(), "dbt macro 静态调用数量超过安全上限");
            Matcher declarations = MACRO_DECLARATION.matcher(staticSql);
            int declarationCount = 0;
            while (declarations.find()) {
                declarationCount++;
                String name = declarations.group(1);
                String uniqueId = "macro." + normalizeSegment(project.name()) + "." + normalizeSegment(name);
                TechnicalNode node = new TechnicalNode(
                    uniqueId,
                    name,
                    "macro",
                    entry.getKey(),
                    sql(macroSql),
                    Map.of("sourceProjectEvidence", "macro-declaration"),
                    parsed.dependencies(),
                    List.of(),
                    new ConversionResult(ConversionMode.TECHNICAL_ONLY, List.of("TECHNICAL_RESOURCE"))
                );
                if (nodes.putIfAbsent(uniqueId, node) != null) {
                    throw error("SOURCE_PROJECT_MACRO_DUPLICATE", "dbt 项目包含重复 macro declaration: " + name);
                }
                if (nodes.size() > MAX_MACRO_DECLARATIONS) {
                    throw error("SOURCE_PROJECT_TOO_LARGE", "dbt macro declaration 数量超过 " + MAX_MACRO_DECLARATIONS);
                }
                addStaticDependencyIssues(issues, uniqueId, parsed);
            }
            if (declarationCount == 0 && !macroSql.isBlank()) {
                issues.add(
                    new ImportIssue(
                        "DBT_SOURCE_PROJECT_MACRO_DECLARATION_UNRESOLVED",
                        "WARNING",
                        "$.dbt.macros[" + entry.getKey() + "]",
                        null,
                        "macro SQL 文件中未识别到 literal macro declaration",
                        "使用标准 {% macro name(...) %} 声明，或随包提供 target/manifest.json"
                    )
                );
            }
        }
        return new MacroInventory(List.copyOf(nodes.values()), List.copyOf(issues), staticCallCount);
    }

    private static ParsedDependencies parseDependencies(
        String projectName,
        String resourcePath,
        String sql,
        Map<String, String> uniqueIdByName,
        Set<String> knownModelIds,
        Map<String, SourceNode> sources
    ) {
        String scanSql = jinjaExpressions(stripComments(sql));
        TreeSet<String> dependencies = new TreeSet<>();
        TreeSet<String> unresolved = new TreeSet<>();
        TreeSet<String> dynamic = new TreeSet<>();
        int literalRefCount = 0;
        Matcher refs = REF.matcher(scanSql);
        while (refs.find()) {
            literalRefCount++;
            String firstArgument = refs.group(2).trim();
            String secondArgument = refs.group(4) == null ? null : refs.group(4).trim();
            String uniqueId = secondArgument == null
                ? uniqueIdByName.get(firstArgument)
                : modelUniqueId(firstArgument, secondArgument);
            if (uniqueId == null || !knownModelIds.contains(uniqueId)) {
                unresolved.add(
                    secondArgument == null ? modelUniqueId(projectName, firstArgument) : modelUniqueId(firstArgument, secondArgument)
                );
            } else {
                dependencies.add(uniqueId);
            }
        }
        int refCallCount = matchCount(REF_CALL, scanSql);
        if (literalRefCount != refCallCount) {
            dynamic.add("ref(...)");
        }
        int literalSourceCount = 0;
        Matcher sourceCalls = SOURCE.matcher(scanSql);
        while (sourceCalls.find()) {
            literalSourceCount++;
            String sourceName = sourceCalls.group(2).trim();
            String tableName = sourceCalls.group(4).trim();
            String uniqueId =
                "source." +
                normalizeSegment(projectName) +
                "." +
                normalizeSegment(sourceName) +
                "." +
                normalizeSegment(tableName);
            dependencies.add(uniqueId);
            sources.putIfAbsent(uniqueId, new SourceNode(uniqueId, tableName, resourcePath, List.of()));
        }
        int sourceCallCount = matchCount(SOURCE_CALL, scanSql);
        if (literalSourceCount != sourceCallCount) {
            dynamic.add("source(...)");
        }
        return new ParsedDependencies(
            List.copyOf(dependencies),
            List.copyOf(unresolved),
            List.copyOf(dynamic),
            refCallCount + sourceCallCount
        );
    }

    private static String stripComments(String sql) {
        return sql
            .replaceAll("(?s)\\{#.*?#}", " ")
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)--[^\\r\\n]*", " ");
    }

    private static String jinjaExpressions(String sql) {
        StringBuilder result = new StringBuilder();
        Matcher blocks = JINJA_BLOCK.matcher(sql);
        while (blocks.find()) {
            result.append(blocks.group(1) == null ? blocks.group(2) : blocks.group(1)).append('\n');
        }
        return result.toString();
    }

    private static int matchCount(Pattern pattern, String value) {
        int result = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            result++;
        }
        return result;
    }

    private static void validateGraphBudget(
        Set<String> modelIds,
        Set<String> sourceIds,
        Map<String, List<String>> modelDependencies,
        List<TechnicalNode> macros,
        int staticCallCount
    ) {
        int nodeCount = modelIds.size() + sourceIds.size() + macros.size();
        if (nodeCount > MAX_GRAPH_NODES) {
            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 静态图节点数量超过 " + MAX_GRAPH_NODES);
        }
        int edgeCount = modelDependencies.values().stream().mapToInt(List::size).sum();
        edgeCount += macros.stream().mapToInt(node -> node.dependencies().size()).sum();
        if (edgeCount > MAX_GRAPH_EDGES || staticCallCount > MAX_GRAPH_EDGES) {
            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 静态图依赖数量超过 " + MAX_GRAPH_EDGES);
        }

        TreeMap<String, List<String>> graph = new TreeMap<>(modelDependencies);
        macros.forEach(node -> graph.put(node.dbtUniqueId(), node.dependencies()));
        TraversalBudget budget = new TraversalBudget(MAX_GRAPH_TRAVERSAL_STEPS);
        for (String start : graph.keySet()) {
            ArrayList<NodeDepth> pending = new ArrayList<>();
            Map<String, Integer> deepestVisit = new HashMap<>();
            pending.add(new NodeDepth(start, 0));
            deepestVisit.put(start, 0);
            for (int index = 0; index < pending.size(); index++) {
                budget.consume();
                NodeDepth current = pending.get(index);
                if (current.depth() > MAX_GRAPH_DEPTH) {
                    throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 静态图依赖深度超过 " + MAX_GRAPH_DEPTH);
                }
                for (String dependency : graph.getOrDefault(current.node(), List.of())) {
                    if (!graph.containsKey(dependency)) {
                        continue;
                    }
                    budget.consume();
                    int nextDepth = current.depth() + 1;
                    if (nextDepth > deepestVisit.getOrDefault(dependency, -1)) {
                        deepestVisit.put(dependency, nextDepth);
                        pending.add(new NodeDepth(dependency, nextDepth));
                    }
                }
            }
        }
    }

    private static List<String> reachableSources(
        String start,
        Map<String, List<String>> graph,
        TraversalBudget traversalBudget
    ) {
        TreeSet<String> sources = new TreeSet<>();
        Set<String> visited = new HashSet<>();
        ArrayList<String> pending = new ArrayList<>();
        pending.add(start);
        for (int index = 0; index < pending.size(); index++) {
            traversalBudget.consume();
            String current = pending.get(index);
            if (!visited.add(current)) {
                continue;
            }
            for (String dependency : graph.getOrDefault(current, List.of())) {
                traversalBudget.consume();
                if (dependency.startsWith("source.")) {
                    sources.add(dependency);
                } else if (graph.containsKey(dependency)) {
                    pending.add(dependency);
                }
            }
        }
        return List.copyOf(sources);
    }

    private static void addStaticDependencyIssues(
        List<ImportIssue> issues,
        String uniqueId,
        ParsedDependencies dependencies
    ) {
        if (!dependencies.unresolvedRefs().isEmpty()) {
            issues.add(
                new ImportIssue(
                    "DBT_SOURCE_PROJECT_REF_UNRESOLVED",
                    "WARNING",
                    "$.technicalNodes[" + uniqueId + "].dependencies",
                    uniqueId,
                    "macro 引用了包内不存在的 ref: " + String.join(", ", dependencies.unresolvedRefs()),
                    "补齐引用模型，或随包提供 target/manifest.json"
                )
            );
        }
        if (!dependencies.dynamicCalls().isEmpty()) {
            issues.add(
                new ImportIssue(
                    "DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE",
                    "WARNING",
                    "$.technicalNodes[" + uniqueId + "].dependencies",
                    uniqueId,
                    "macro 包含无法静态解析的动态调用: " + String.join(", ", dependencies.dynamicCalls()),
                    "改用 literal ref/source，或随包提供 target/manifest.json"
                )
            );
        }
    }

    private static Map<String, Set<String>> directBlockReasons(
        Map<String, ModelSeed> models,
        SchemaCatalog schemaCatalog,
        Map<String, List<String>> unresolvedRefs,
        Map<String, List<String>> dynamicCalls,
        Map<String, SourceOnlyJinjaGuard.Risk> jinjaRisks
    ) {
        TreeMap<String, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, ModelSeed> entry : models.entrySet()) {
            TreeSet<String> reasons = new TreeSet<>();
            reasons.addAll(entry.getValue().configurationBlockers());
            ModelContract contract = schemaCatalog.contracts().get(entry.getValue().name());
            if (contract == null || !contract.verified()) {
                reasons.add("SOURCE_FIELDS_UNVERIFIED");
            }
            if (!unresolvedRefs.getOrDefault(entry.getKey(), List.of()).isEmpty()) {
                reasons.add("SOURCE_PACKAGE_MISSING");
            }
            SourceOnlyJinjaGuard.Risk risk = jinjaRisks.getOrDefault(
                entry.getKey(),
                new SourceOnlyJinjaGuard.Risk(false, false, null, List.of())
            );
            if (!dynamicCalls.getOrDefault(entry.getKey(), List.of()).isEmpty() || risk.dynamic()) {
                reasons.add("SOURCE_DEPENDENCY_DYNAMIC");
            }
            if (risk.macro()) {
                reasons.add("SOURCE_MACRO_DEPENDENCY_UNVERIFIED");
            }
            if (!reasons.isEmpty()) {
                result.put(entry.getKey(), reasons);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> propagateBlockReasons(
        Set<String> modelIds,
        Map<String, List<String>> dependencies,
        Map<String, Set<String>> directBlocks
    ) {
        TreeMap<String, Set<String>> result = new TreeMap<>();
        directBlocks.forEach((model, reasons) -> result.put(model, new TreeSet<>(reasons)));
        TreeMap<String, Set<String>> downstream = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (modelIds.contains(dependency)) {
                    downstream.computeIfAbsent(dependency, ignored -> new TreeSet<>()).add(entry.getKey());
                }
            }
        }
        TraversalBudget budget = new TraversalBudget(MAX_GRAPH_TRAVERSAL_STEPS);
        for (Map.Entry<String, Set<String>> root : directBlocks.entrySet()) {
            ArrayList<String> pending = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            pending.add(root.getKey());
            for (int index = 0; index < pending.size(); index++) {
                budget.consume();
                String current = pending.get(index);
                if (!visited.add(current)) {
                    continue;
                }
                if (!current.equals(root.getKey())) {
                    result.computeIfAbsent(current, ignored -> new TreeSet<>()).addAll(root.getValue());
                }
                for (String caller : downstream.getOrDefault(current, Set.of())) {
                    budget.consume();
                    pending.add(caller);
                }
            }
        }
        TreeMap<String, Set<String>> immutable = new TreeMap<>();
        result.forEach((model, reasons) -> immutable.put(model, Set.copyOf(reasons)));
        return Map.copyOf(immutable);
    }

    private static void addSourceOnlyBlockIssues(List<ImportIssue> issues, Map<String, Set<String>> closureBlocks) {
        closureBlocks.forEach((uniqueId, reasons) ->
            reasons.forEach(reason ->
                issues.add(
                    new ImportIssue(
                        reason,
                        "WARNING",
                        "$.models[" + uniqueId + "]",
                        uniqueId,
                        blockMessage(reason),
                        blockRecovery(reason)
                    )
                )
            )
        );
    }

    private static void addLegacyDependencyIssues(
        List<ImportIssue> issues,
        Map<String, List<String>> unresolvedRefs,
        Map<String, List<String>> dynamicCalls
    ) {
        unresolvedRefs.forEach((uniqueId, missing) -> {
            if (!missing.isEmpty()) {
                issues.add(
                    new ImportIssue(
                        "DBT_SOURCE_PROJECT_REF_UNRESOLVED",
                        "WARNING",
                        "$.models[" + uniqueId + "].dependencies",
                        uniqueId,
                        "SQL 引用了 ZIP 内不存在的 literal ref",
                        "INCLUDE_DEPENDENCY"
                    )
                );
            }
        });
        dynamicCalls.forEach((uniqueId, dynamic) -> {
            if (!dynamic.isEmpty()) {
                issues.add(
                    new ImportIssue(
                        "DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE",
                        "WARNING",
                        "$.models[" + uniqueId + "].dependencies",
                        uniqueId,
                        "SQL 包含无法静态证明的 ref/source 调用",
                        "REUPLOAD"
                    )
                );
            }
        });
    }

    private static String blockMessage(String reason) {
        return switch (reason) {
            case "SOURCE_FIELDS_UNVERIFIED" -> "模型没有 enforced 且 name/data_type 完整唯一的 schema 字段契约";
            case "SOURCE_DEPENDENCY_DYNAMIC" -> "模型或其上游包含无法静态证明的动态依赖";
            case "SOURCE_MACRO_DEPENDENCY_UNVERIFIED" -> "模型或其上游包含可能隐藏结构或依赖的 macro";
            case "SOURCE_PACKAGE_MISSING" -> "模型或其上游引用了 ZIP 内缺失的 package/model";
            default -> "source-only 模型的技术结构或依赖无法静态证明";
        };
    }

    private static String blockRecovery(String reason) {
        return "SOURCE_PACKAGE_MISSING".equals(reason) ? "INCLUDE_DEPENDENCY" : "REUPLOAD";
    }

    private static Optional<Path> locateProjectRoot(Path archiveRoot) {
        List<Path> candidates = new ArrayList<>();
        addProjectRoot(candidates, archiveRoot);
        try (var children = Files.list(archiveRoot)) {
            children
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .forEach(path -> addProjectRoot(candidates, path));
        } catch (IOException exception) {
            throw new SourceProjectException("SOURCE_PROJECT_READ_FAILED", "无法检查 dbt 项目根目录", exception);
        }
        if (candidates.size() > 1) {
            throw error("SOURCE_PROJECT_MULTIPLE", "ZIP 中包含多个 dbt_project.yml，无法确定项目根");
        }
        return candidates.stream().findFirst();
    }

    private static void addProjectRoot(List<Path> candidates, Path candidate) {
        if (Files.isRegularFile(candidate.resolve("dbt_project.yml"), LinkOption.NOFOLLOW_LINKS)) {
            candidates.add(candidate);
        }
    }

    private static ProjectMetadata readProjectMetadata(Path projectFile) {
        byte[] bytes = readBounded(projectFile, MAX_PROJECT_FILE_BYTES, "SOURCE_PROJECT_PROJECT_FILE_TOO_LARGE");
        String yaml = decodeUtf8(
            bytes,
            "SOURCE_PROJECT_PROJECT_FILE_INVALID",
            "dbt_project.yml 必须使用 UTF-8 编码"
        );
        String name = firstMatch(PROJECT_NAME, yaml);
        if (name == null) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt_project.yml 缺少有效 name");
        }
        return new ProjectMetadata(
            name,
            firstMatch(PROJECT_VERSION, yaml),
            projectPaths(yaml, "model-paths", List.of("models")),
            projectPaths(yaml, "macro-paths", List.of("macros"))
        );
    }

    private static String fingerprint(
        Path projectRoot,
        ProjectMetadata project,
        List<ModelSeed> seeds,
        String schemaFingerprint
    ) {
        StringBuilder value = new StringBuilder(project.name()).append('\n').append(project.version()).append('\n');
        seeds
            .stream()
            .sorted(Comparator.comparing(ModelSeed::resourcePath))
            .forEach(seed ->
                value
                    .append(seed.resourcePath())
                    .append('\t')
                    .append(ModelPackageChecksum.sha256Text(seed.sql()))
                    .append('\t')
                    .append(seed.layer())
                    .append('\t')
                    .append(seed.materialization())
                    .append('\n')
            );
        value
            .append(relative(projectRoot, projectRoot.resolve("dbt_project.yml")))
            .append('\n')
            .append(schemaFingerprint == null ? "" : schemaFingerprint);
        return value.toString();
    }

    private static SqlArtifact sql(String value) {
        String checksum = ModelPackageChecksum.sha256Text(value);
        return new SqlArtifact(null, null, null, null, value, checksum, "DBT_SOURCE_PROJECT_SQL");
    }

    private static boolean isExplicitTechnical(ModelSeed seed) {
        return (
            "STG".equalsIgnoreCase(seed.layer()) ||
            seed.tags().stream().anyMatch(tag -> "stg".equalsIgnoreCase(tag)) ||
            "ephemeral".equalsIgnoreCase(seed.materialization())
        );
    }

    private static String explicitLayer(List<String> tags) {
        Set<String> layers = tags
            .stream()
            .map(tag -> tag.toUpperCase(Locale.ROOT))
            .filter(LAYERS::contains)
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (layers.size() > 1) {
            throw error("SOURCE_PROJECT_CONFIG_INVALID", "SQL config.tags 包含冲突的 layer 标记: " + layers);
        }
        return layers.stream().findFirst().orElse(null);
    }

    private static String explicitDomainTag(List<String> tags) {
        TreeSet<String> domains = new TreeSet<>();
        for (String rawTag : tags) {
            String tag = rawTag == null ? "" : rawTag.trim();
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.startsWith("domain:") || lower.startsWith("domain=")) {
                String value = tag.substring("domain:".length()).trim();
                if (value.isEmpty() || value.chars().anyMatch(Character::isISOControl)) {
                    throw error("SOURCE_PROJECT_CONFIG_INVALID", "domain tag 缺少安全的显式值");
                }
                domains.add(value);
            }
        }
        if (domains.size() > 1) {
            throw error("SOURCE_PROJECT_CONFIG_INVALID", "SQL/models.tsv 包含冲突的显式 domain tag: " + domains);
        }
        return domains.stream().findFirst().orElse(null);
    }

    private static List<String> inlineTags(String sql) {
        Matcher tags = TAGS.matcher(sql);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        while (tags.find()) {
            Matcher quoted = QUOTED_VALUE.matcher(tags.group(1));
            while (quoted.find()) {
                String value = quoted.group(1).trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> projectPaths(String yaml, String key, List<String> defaults) {
        List<String> lines = yaml.lines().toList();
        List<String> result = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = index == 0 && lines.get(index).startsWith("\uFEFF")
                ? lines.get(index).substring(1)
                : lines.get(index);
            if (!line.startsWith(key + ":")) {
                continue;
            }
            if (result != null) {
                throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt_project.yml 重复定义 " + key);
            }
            String value = stripYamlComment(line.substring(key.length() + 1)).trim();
            result = new ArrayList<>();
            if (value.startsWith("[") && value.endsWith("]")) {
                for (String item : splitInlineList(value.substring(1, value.length() - 1))) {
                    if (!item.isBlank()) {
                        result.add(safeProjectRelativePath(yamlScalar(item), key));
                    }
                }
            } else if (value.isEmpty()) {
                for (int nested = index + 1; nested < lines.size(); nested++) {
                    String nestedLine = lines.get(nested);
                    if (nestedLine.isBlank() || nestedLine.stripLeading().startsWith("#")) {
                        continue;
                    }
                    if (!Character.isWhitespace(nestedLine.charAt(0))) {
                        break;
                    }
                    String item = stripYamlComment(nestedLine).trim();
                    if (!item.startsWith("-")) {
                        throw error(
                            "SOURCE_PROJECT_PROJECT_FILE_UNSUPPORTED",
                            "dbt_project.yml 的 " + key + " 仅支持 literal 字符串列表"
                        );
                    }
                    result.add(safeProjectRelativePath(yamlScalar(item.substring(1)), key));
                }
            } else {
                throw error(
                    "SOURCE_PROJECT_PROJECT_FILE_UNSUPPORTED",
                    "dbt_project.yml 的 " + key + " 仅支持 literal 字符串列表"
                );
            }
            if (result.isEmpty()) {
                throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt_project.yml 的 " + key + " 不能为空");
            }
            if (result.size() > MAX_PROJECT_PATHS || new HashSet<>(result).size() != result.size()) {
                throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt_project.yml 的 " + key + " 过多或重复");
            }
        }
        return result == null ? defaults : List.copyOf(result);
    }

    private static List<String> splitInlineList(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character == '\'' || character == '"') && (quote == 0 || quote == character)) {
                quote = quote == 0 ? character : 0;
                current.append(character);
            } else if (character == ',' && quote == 0) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quote != 0) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt_project.yml 包含未闭合的引号");
        }
        result.add(current.toString());
        return result;
    }

    private static String yamlScalar(String value) {
        String scalar = value.trim();
        if (
            scalar.length() >= 2 &&
            ((scalar.startsWith("\"") && scalar.endsWith("\"")) || (scalar.startsWith("'") && scalar.endsWith("'")))
        ) {
            scalar = scalar.substring(1, scalar.length() - 1).trim();
        }
        if (
            scalar.isEmpty() ||
            scalar.contains("{{") ||
            scalar.contains("}}") ||
            scalar.contains("{") ||
            scalar.contains("}") ||
            scalar.contains("[") ||
            scalar.contains("]") ||
            scalar.contains(":") ||
            scalar.startsWith("&") ||
            scalar.startsWith("*") ||
            scalar.startsWith("!")
        ) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_UNSUPPORTED", "dbt_project.yml 路径必须是 literal 字符串");
        }
        return scalar;
    }

    private static String stripYamlComment(String value) {
        char quote = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character == '\'' || character == '"') && (quote == 0 || quote == character)) {
                quote = quote == 0 ? character : 0;
            } else if (character == '#' && quote == 0) {
                return value.substring(0, index);
            }
        }
        return value;
    }

    private static String safeProjectRelativePath(String value, String key) {
        String path = value.trim();
        if (
            path.isEmpty() ||
            path.startsWith("/") ||
            path.contains("\\") ||
            path.chars().anyMatch(Character::isISOControl)
        ) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", key + " 包含不安全路径");
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", key + " 包含非规范或越界路径");
            }
        }
        Path normalized = Path.of(path).normalize();
        if (normalized.isAbsolute() || !normalized.toString().replace('\\', '/').equals(path)) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", key + " 包含非规范或越界路径");
        }
        return path;
    }

    private static Path resolveProjectPath(Path projectRoot, String configuredPath, String key) {
        String relative = safeProjectRelativePath(configuredPath, key);
        Path resolved = projectRoot.resolve(relative).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", key + " 越出 dbt 项目根");
        }
        return resolved;
    }

    private static void requireScanDepth(Path configuredRoot, Path candidate, String key) {
        if (configuredRoot.relativize(candidate).getNameCount() > MAX_SCAN_DEPTH) {
            throw error("SOURCE_PROJECT_TOO_LARGE", key + " 目录深度超过 " + MAX_SCAN_DEPTH);
        }
    }

    private static void copyConfig(Map<String, Object> source, Map<String, Object> target, String sourceKey, String targetKey) {
        Object value = source == null ? null : source.get(sourceKey);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(targetKey, value);
        }
    }

    private static String textConfig(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static List<String> mergeTags(List<String> first, List<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(first == null ? List.of() : first);
        result.addAll(second == null ? List.of() : second);
        return List.copyOf(result);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String modelUniqueId(String projectName, String modelName) {
        return "model." + normalizeSegment(projectName) + "." + normalizeSegment(modelName);
    }

    private static String normalizeSegment(String value) {
        String normalized = Normalizer
            .normalize(value == null ? "" : value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_")
            .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw error("SOURCE_PROJECT_IDENTIFIER_INVALID", "dbt 项目标识无法安全规范化");
        }
        return normalized;
    }

    private static Path normalizedDirectory(Path path) {
        if (path == null) {
            throw error("SOURCE_PROJECT_ARCHIVE_INVALID", "解压目录不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw error("SOURCE_PROJECT_ARCHIVE_INVALID", "解压目录不存在");
        }
        return normalized;
    }

    private static byte[] readBounded(Path path, long maximum, String tooLargeCode) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) {
                throw error(tooLargeCode, path.getFileName() + " 缺失或超过允许大小");
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > maximum) {
                throw error(tooLargeCode, path.getFileName() + " 超过允许大小");
            }
            return bytes;
        } catch (SourceProjectException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SourceProjectException("SOURCE_PROJECT_READ_FAILED", "无法读取 " + path.getFileName(), exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String code, String message) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new SourceProjectException(code, message, exception);
        }
    }

    private static String withoutExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static List<String> append(List<String> values, String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values == null ? List.of() : values);
        result.add(value);
        return List.copyOf(result);
    }

    private static int boundedAdd(int current, int increment, String message) {
        if (increment < 0 || current > MAX_GRAPH_EDGES - increment) {
            throw error("SOURCE_PROJECT_TOO_LARGE", message);
        }
        return current + increment;
    }

    private static SourceProjectException error(String code, String message) {
        return new SourceProjectException(code, message);
    }

    private record ProjectMetadata(String name, String version, List<String> modelPaths, List<String> macroPaths) {}

    private record ParsedDependencies(
        List<String> dependencies,
        List<String> unresolvedRefs,
        List<String> dynamicCalls,
        int callCount
    ) {}

    private record MacroInventory(List<TechnicalNode> nodes, List<ImportIssue> issues, int staticCallCount) {}

    private record NodeDepth(String node, int depth) {}

    private record ModelSeed(
        String name,
        String resourcePath,
        String sql,
        String layer,
        String materialization,
        List<String> tags,
        String description,
        Map<String, Object> config,
        String evidenceSource,
        Set<String> configurationBlockers
    ) {}

    private static final class TraversalBudget {

        private int remaining;

        private TraversalBudget(int maximum) {
            this.remaining = maximum;
        }

        private void consume() {
            if (--remaining < 0) {
                throw error("SOURCE_PROJECT_TOO_LARGE", "dbt 静态图遍历超过安全预算");
            }
        }
    }

    static final class SourceProjectException extends IllegalArgumentException {

        private final String code;

        SourceProjectException(String code, String message) {
            super(message);
            this.code = code;
        }

        SourceProjectException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
