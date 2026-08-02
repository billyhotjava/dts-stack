package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Bounded safe-YAML projection for source-only field and model configuration evidence. */
final class SourceOnlySchemaContractReader {

    private static final long MAX_YAML_FILE_BYTES = 1024L * 1024;
    private static final long MAX_TOTAL_SCHEMA_BYTES = 4L * 1024 * 1024;
    private static final int MAX_SCHEMA_FILES = 128;
    private static final int MAX_SCHEMA_DOCUMENTS = 256;
    private static final int MAX_MODELS = 500;
    private static final int MAX_COLUMNS_PER_MODEL = 2_000;
    private static final int MAX_TOTAL_COLUMNS = 20_000;
    private static final int MAX_SCAN_DEPTH = 16;
    private static final int MAX_CONFIG_RULES = 1_000;
    private static final int MAX_TAGS = 64;
    private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental", "ephemeral");
    private static final Set<String> GLOBAL_DYNAMIC_KEYS = Set.of("vars", "dispatch", "on-run-start", "on-run-end");
    private static final Set<String> MODEL_HOOK_KEYS = Set.of("pre-hook", "post-hook");

    private SourceOnlySchemaContractReader() {}

    static SchemaCatalog read(Path projectRoot, List<String> modelPaths) {
        LoaderOptions loaderOptions = loaderOptions();
        ProjectConfiguration projectConfiguration = readProjectConfiguration(projectRoot, modelPaths, loaderOptions);
        TreeMap<String, Path> schemaFiles = locateSchemaFiles(projectRoot, modelPaths);
        TreeMap<String, ModelContract> contracts = new TreeMap<>();
        StringBuilder fingerprint = new StringBuilder(projectConfiguration.fingerprint()).append('\n');
        long totalBytes = 0;
        int documents = 0;
        int totalColumns = 0;
        for (Map.Entry<String, Path> entry : schemaFiles.entrySet()) {
            byte[] bytes = readBounded(entry.getValue(), "dbt schema YAML");
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_SCHEMA_BYTES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema YAML 总量超过 4 MiB");
            }
            fingerprint
                .append(entry.getKey())
                .append('\t')
                .append(ModelPackageChecksum.sha256(bytes))
                .append('\n');
            try {
                Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
                for (Object document : yaml.loadAll(decodeUtf8(bytes, "dbt schema YAML"))) {
                    if (++documents > MAX_SCHEMA_DOCUMENTS) {
                        throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema YAML 文档数量超过安全上限");
                    }
                    if (document == null) {
                        continue;
                    }
                    Map<?, ?> root = requireMap(document, "schema YAML root");
                    Object modelValue = root.get("models");
                    if (modelValue == null) {
                        continue;
                    }
                    List<?> models = requireList(modelValue, "schema YAML models");
                    for (Object rawModel : models) {
                        String modelName = modelName(rawModel);
                        if (contracts.size() >= MAX_MODELS && !contracts.containsKey(modelName)) {
                            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema model 数量超过安全上限");
                        }
                        ModelContract parsed = parseModel(rawModel);
                        totalColumns += parsed.columns().size();
                        if (totalColumns > MAX_TOTAL_COLUMNS) {
                            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema column 数量超过安全上限");
                        }
                        ModelContract previous = contracts.putIfAbsent(parsed.modelName(), parsed);
                        if (previous != null) {
                            contracts.put(
                                parsed.modelName(),
                                ModelContract.unverified(
                                    parsed.modelName(),
                                    "duplicate schema model declaration",
                                    ModelConfiguration.blocked()
                                )
                            );
                        }
                    }
                }
            } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new DbtSourceProjectModelPackageAdapter.SourceProjectException(
                    "SOURCE_PROJECT_SCHEMA_INVALID",
                    "dbt schema YAML 无法安全解析: " + entry.getKey(),
                    exception
                );
            }
        }
        return new SchemaCatalog(
            Map.copyOf(contracts),
            projectConfiguration,
            ModelPackageChecksum.sha256Text(fingerprint.toString())
        );
    }

    private static ProjectConfiguration readProjectConfiguration(
        Path projectRoot,
        List<String> modelPaths,
        LoaderOptions loaderOptions
    ) {
        Path projectFile = projectRoot.resolve("dbt_project.yml");
        byte[] bytes = readBounded(projectFile, "dbt_project.yml");
        String fingerprint = "dbt_project.yml\t" + ModelPackageChecksum.sha256(bytes);
        try {
            Object loaded = new Yaml(new SafeConstructor(loaderOptions)).load(decodeUtf8(bytes, "dbt_project.yml"));
            Map<?, ?> root = requireMap(loaded, "dbt_project.yml root");
            String projectName = requiredLiteral(root.get("name"), "dbt project name", 128);
            LinkedHashSet<String> globalBlockers = new LinkedHashSet<>();
            for (String key : GLOBAL_DYNAMIC_KEYS) {
                if (nonEmpty(root.get(key))) {
                    globalBlockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                }
            }
            List<ProjectRule> rules = new ArrayList<>();
            Object rawModels = root.get("models");
            if (rawModels != null) {
                if (!(rawModels instanceof Map<?, ?> models)) {
                    globalBlockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                } else {
                    Object currentProject = models.get(projectName);
                    if (currentProject != null) {
                        if (currentProject instanceof Map<?, ?> projectModels) {
                            for (String modelPath : modelPaths) {
                                collectProjectRules(projectModels, modelPath, rules, 0);
                            }
                        } else {
                            globalBlockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                        }
                    }
                }
            }
            return new ProjectConfiguration(projectName, List.copyOf(rules), Set.copyOf(globalBlockers), fingerprint);
        } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DbtSourceProjectModelPackageAdapter.SourceProjectException(
                "SOURCE_PROJECT_PROJECT_FILE_INVALID",
                "dbt_project.yml 无法安全解析",
                exception
            );
        }
    }

    private static void collectProjectRules(
        Map<?, ?> tree,
        String pathPrefix,
        List<ProjectRule> rules,
        int depth
    ) {
        if (depth > MAX_SCAN_DEPTH || rules.size() >= MAX_CONFIG_RULES) {
            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt project models 配置超过安全上限");
        }
        ModelConfiguration local = modelConfiguration(tree, true);
        if (!local.empty()) {
            rules.add(new ProjectRule(pathPrefix, local));
        }
        for (Map.Entry<?, ?> entry : tree.entrySet()) {
            String key = optionalLiteral(entry.getKey(), 128);
            if (key == null || configKey(key)) {
                continue;
            }
            String childPath;
            try {
                childPath = safeRelativePath(pathPrefix + "/" + key);
            } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
                rules.add(new ProjectRule(pathPrefix, ModelConfiguration.blocked()));
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> child) {
                collectProjectRules(child, childPath, rules, depth + 1);
            } else {
                rules.add(new ProjectRule(childPath, ModelConfiguration.blocked()));
            }
        }
    }

    private static ModelContract parseModel(Object value) {
        Map<?, ?> model = requireMap(value, "schema model");
        String name = requiredLiteral(model.get("name"), "schema model name", 256);
        ModelConfiguration configuration = modelConfiguration(model, false);
        boolean enforced = isEnforced(model);
        Object columnValue = model.get("columns");
        if (!enforced || !(columnValue instanceof List<?> rawColumns) || rawColumns.isEmpty()) {
            return ModelContract.unverified(name, "contract not enforced or columns absent", configuration);
        }
        if (rawColumns.size() > MAX_COLUMNS_PER_MODEL) {
            throw error("SOURCE_PROJECT_TOO_LARGE", "单个 dbt schema model 的 column 数量超过安全上限");
        }
        List<Column> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object rawColumn : rawColumns) {
            if (!(rawColumn instanceof Map<?, ?> column)) {
                return ModelContract.unverified(name, "column declaration is not an object", configuration);
            }
            String columnName = optionalLiteral(column.get("name"), 256);
            String dataType = optionalLiteral(column.get("data_type"), 256);
            if (
                columnName == null ||
                dataType == null ||
                !names.add(columnName.toLowerCase(Locale.ROOT))
            ) {
                return ModelContract.unverified(name, "column name/type missing, dynamic, oversized or duplicate", configuration);
            }
            columns.add(
                new Column(
                    columnName,
                    optionalPlainText(column.get("description"), 4_096),
                    dataType,
                    null,
                    stringTests(column.get("tests"))
                )
            );
        }
        return new ModelContract(name, true, List.copyOf(columns), null, configuration);
    }

    private static ModelConfiguration modelConfiguration(Map<?, ?> value, boolean projectTree) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String materialization = null;
        Map<?, ?> config = projectTree ? value : optionalMap(value.get("config"));
        if (!projectTree && value.get("config") != null && config == null) {
            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
        }
        if (config != null) {
            for (Map.Entry<?, ?> entry : config.entrySet()) {
                String rawKey = optionalLiteral(entry.getKey(), 128);
                if (rawKey == null) {
                    blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                    continue;
                }
                String key = normalizedConfigKey(rawKey);
                switch (key) {
                    case "contract" -> {
                        if (!(entry.getValue() instanceof Map<?, ?>)) {
                            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                        }
                    }
                    case "materialized" -> {
                        String parsed = optionalLiteral(entry.getValue(), 64);
                        if (parsed == null || !MATERIALIZATIONS.contains(parsed.toLowerCase(Locale.ROOT))) {
                            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                        } else {
                            materialization = parsed.toLowerCase(Locale.ROOT);
                        }
                    }
                    case "tags" -> {
                        List<String> parsed = literalTags(entry.getValue());
                        if (parsed == null) {
                            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                        } else {
                            tags.addAll(parsed);
                        }
                    }
                    default -> {
                        if (rawKey.startsWith("+") || MODEL_HOOK_KEYS.contains(key) || !projectTree) {
                            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                        }
                    }
                }
            }
        }
        if (!projectTree) {
            String directMaterialization = optionalLiteral(value.get("materialized"), 64);
            if (value.get("materialized") != null) {
                if (
                    directMaterialization == null ||
                    !MATERIALIZATIONS.contains(directMaterialization.toLowerCase(Locale.ROOT)) ||
                    (materialization != null && !materialization.equalsIgnoreCase(directMaterialization))
                ) {
                    blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                } else {
                    materialization = directMaterialization.toLowerCase(Locale.ROOT);
                }
            }
            if (value.get("tags") != null) {
                List<String> directTags = literalTags(value.get("tags"));
                if (directTags == null) {
                    blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
                } else {
                    tags.addAll(directTags);
                }
            }
        }
        if (tags.size() > MAX_TAGS) {
            blockers.add("SOURCE_DEPENDENCY_DYNAMIC");
            tags.clear();
        }
        return new ModelConfiguration(materialization, List.copyOf(tags), Set.copyOf(blockers));
    }

    private static boolean isEnforced(Map<?, ?> model) {
        Map<?, ?> direct = optionalMap(model.get("contract"));
        Map<?, ?> config = optionalMap(model.get("config"));
        Map<?, ?> configured = config == null ? null : optionalMap(config.get("contract"));
        Object value = configured != null ? configured.get("enforced") : direct == null ? null : direct.get("enforced");
        return Boolean.TRUE.equals(value);
    }

    private static List<String> literalTags(Object value) {
        if (value instanceof String) {
            String tag = optionalLiteral(value, 128);
            return tag == null ? null : List.of(tag);
        }
        if (!(value instanceof List<?> list) || list.size() > MAX_TAGS) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object raw : list) {
            String tag = optionalLiteral(raw, 128);
            if (tag == null) {
                return null;
            }
            result.add(tag);
        }
        return List.copyOf(result);
    }

    private static List<String> stringTests(Object value) {
        if (!(value instanceof List<?> tests)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object test : tests) {
            if (test instanceof String) {
                String name = optionalLiteral(test, 128);
                if (name != null) {
                    result.add(name);
                }
            } else if (test instanceof Map<?, ?> map && map.size() == 1) {
                String name = optionalLiteral(map.keySet().iterator().next(), 128);
                if (name != null) {
                    result.add(name);
                }
            }
        }
        return List.copyOf(result);
    }

    private static TreeMap<String, Path> locateSchemaFiles(Path projectRoot, List<String> modelPaths) {
        TreeMap<String, Path> files = new TreeMap<>();
        for (String configuredPath : modelPaths) {
            Path modelsRoot = projectRoot.resolve(configuredPath).normalize();
            if (!modelsRoot.startsWith(projectRoot) || !Files.isDirectory(modelsRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(modelsRoot, MAX_SCAN_DEPTH + 1)) {
                paths
                    .peek(path -> {
                        if (modelsRoot.relativize(path).getNameCount() > MAX_SCAN_DEPTH) {
                            throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema 目录深度超过安全上限");
                        }
                    })
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(SourceOnlySchemaContractReader::isYaml)
                    .forEach(path -> files.putIfAbsent(relative(projectRoot, path), path));
            } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new DbtSourceProjectModelPackageAdapter.SourceProjectException(
                    "SOURCE_PROJECT_READ_FAILED",
                    "无法扫描 dbt schema YAML",
                    exception
                );
            }
            if (files.size() > MAX_SCHEMA_FILES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", "dbt schema YAML 文件数量超过安全上限");
            }
        }
        return files;
    }

    private static LoaderOptions loaderOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(32);
        options.setCodePointLimit((int) MAX_YAML_FILE_BYTES);
        return options;
    }

    private static byte[] readBounded(Path path, String label) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_YAML_FILE_BYTES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", label + " 缺失或超过 1 MiB");
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_YAML_FILE_BYTES) {
                throw error("SOURCE_PROJECT_TOO_LARGE", label + " 超过 1 MiB");
            }
            return bytes;
        } catch (DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DbtSourceProjectModelPackageAdapter.SourceProjectException(
                "SOURCE_PROJECT_READ_FAILED",
                "无法读取 " + label,
                exception
            );
        }
    }

    private static String decodeUtf8(byte[] bytes, String label) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new DbtSourceProjectModelPackageAdapter.SourceProjectException(
                "SOURCE_PROJECT_SCHEMA_INVALID",
                label + " 必须使用 UTF-8 编码",
                exception
            );
        }
    }

    private static Map<?, ?> requireMap(Object value, String label) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw error("SOURCE_PROJECT_SCHEMA_INVALID", label + " 必须是对象");
    }

    private static Map<?, ?> optionalMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static List<?> requireList(Object value, String label) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw error("SOURCE_PROJECT_SCHEMA_INVALID", label + " 必须是数组");
    }

    private static String modelName(Object value) {
        return requiredLiteral(requireMap(value, "schema model").get("name"), "schema model name", 256);
    }

    private static String requiredLiteral(Object value, String label, int maximumLength) {
        String literal = optionalLiteral(value, maximumLength);
        if (literal == null) {
            throw error("SOURCE_PROJECT_SCHEMA_INVALID", label + " 必须是有界 literal 字符串");
        }
        return literal;
    }

    private static String optionalLiteral(Object value, int maximumLength) {
        if (!(value instanceof String text)) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ||
            normalized.length() > maximumLength ||
            dynamicText(normalized) ||
            normalized.chars().anyMatch(Character::isISOControl)
            ? null
            : normalized;
    }

    private static String optionalPlainText(Object value, int maximumLength) {
        if (!(value instanceof String text)) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ||
            normalized.length() > maximumLength ||
            normalized.chars().anyMatch(Character::isISOControl)
            ? null
            : normalized;
    }

    private static boolean dynamicText(String value) {
        return value.contains("{{") || value.contains("}}") || value.contains("{%") || value.contains("%}") || value.contains("{#");
    }

    private static boolean nonEmpty(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private static String normalizedConfigKey(String value) {
        String normalized = value.startsWith("+") ? value.substring(1) : value;
        return normalized.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static boolean configKey(String value) {
        return value.startsWith("+") ||
        Set.of("materialized", "tags", "contract", "pre-hook", "post-hook").contains(normalizedConfigKey(value));
    }

    private static String safeRelativePath(String value) {
        String path = value.trim().replace('\\', '/');
        if (path.isEmpty() || path.startsWith("/") || path.chars().anyMatch(Character::isISOControl)) {
            throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt models 配置包含不安全路径");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || dynamicText(segment)) {
                throw error("SOURCE_PROJECT_PROJECT_FILE_INVALID", "dbt models 配置包含不安全路径");
            }
        }
        return path;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static DbtSourceProjectModelPackageAdapter.SourceProjectException error(String code, String message) {
        return new DbtSourceProjectModelPackageAdapter.SourceProjectException(code, message);
    }

    record SchemaCatalog(
        Map<String, ModelContract> contracts,
        ProjectConfiguration projectConfiguration,
        String fingerprint
    ) {

        ResolvedConfiguration configurationFor(String resourcePath, String modelName) {
            ModelConfiguration project = projectConfiguration.resolve(resourcePath);
            ModelContract contract = contracts.get(modelName);
            return ResolvedConfiguration.merge(project, contract == null ? ModelConfiguration.emptyConfiguration() : contract.configuration());
        }
    }

    record ModelContract(
        String modelName,
        boolean verified,
        List<Column> columns,
        String invalidReason,
        ModelConfiguration configuration
    ) {

        ModelContract {
            columns = columns == null ? List.of() : List.copyOf(columns);
            configuration = configuration == null ? ModelConfiguration.emptyConfiguration() : configuration;
        }

        static ModelContract unverified(String name, String reason, ModelConfiguration configuration) {
            return new ModelContract(name, false, List.of(), reason, configuration);
        }
    }

    record ResolvedConfiguration(String materialization, List<String> tags, Set<String> blockers) {

        ResolvedConfiguration {
            tags = tags == null ? List.of() : List.copyOf(tags);
            blockers = blockers == null ? Set.of() : Set.copyOf(blockers);
        }

        private static ResolvedConfiguration merge(ModelConfiguration lower, ModelConfiguration higher) {
            LinkedHashSet<String> tags = new LinkedHashSet<>(lower.tags());
            tags.addAll(higher.tags());
            LinkedHashSet<String> blockers = new LinkedHashSet<>(lower.blockers());
            blockers.addAll(higher.blockers());
            return new ResolvedConfiguration(
                higher.materialization() == null ? lower.materialization() : higher.materialization(),
                List.copyOf(tags),
                Set.copyOf(blockers)
            );
        }
    }

    private record ModelConfiguration(String materialization, List<String> tags, Set<String> blockers) {

        private ModelConfiguration {
            tags = tags == null ? List.of() : List.copyOf(tags);
            blockers = blockers == null ? Set.of() : Set.copyOf(blockers);
        }

        private boolean empty() {
            return materialization == null && tags.isEmpty() && blockers.isEmpty();
        }

        private static ModelConfiguration emptyConfiguration() {
            return new ModelConfiguration(null, List.of(), Set.of());
        }

        private static ModelConfiguration blocked() {
            return new ModelConfiguration(null, List.of(), Set.of("SOURCE_DEPENDENCY_DYNAMIC"));
        }
    }

    private record ProjectRule(String pathPrefix, ModelConfiguration configuration) {

        private boolean matches(String resourcePath) {
            return resourcePath.equals(pathPrefix + ".sql") ||
                resourcePath.equals(pathPrefix) ||
                resourcePath.startsWith(pathPrefix + "/");
        }
    }

    private record ProjectConfiguration(
        String projectName,
        List<ProjectRule> rules,
        Set<String> globalBlockers,
        String fingerprint
    ) {

        private ProjectConfiguration {
            rules = rules == null ? List.of() : List.copyOf(rules);
            globalBlockers = globalBlockers == null ? Set.of() : Set.copyOf(globalBlockers);
        }

        private ModelConfiguration resolve(String resourcePath) {
            String materialization = null;
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            LinkedHashSet<String> blockers = new LinkedHashSet<>(globalBlockers);
            for (ProjectRule rule : rules) {
                if (!rule.matches(resourcePath)) {
                    continue;
                }
                if (rule.configuration().materialization() != null) {
                    materialization = rule.configuration().materialization();
                }
                tags.addAll(rule.configuration().tags());
                blockers.addAll(rule.configuration().blockers());
            }
            return new ModelConfiguration(materialization, List.copyOf(tags), Set.copyOf(blockers));
        }
    }
}
