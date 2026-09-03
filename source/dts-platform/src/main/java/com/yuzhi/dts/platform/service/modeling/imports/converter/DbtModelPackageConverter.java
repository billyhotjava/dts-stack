package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier.ClassificationInput;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DbtMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionAttributeBlueprint;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionDefinitionBlueprint;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Grain;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ImportIssue;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TimeSemantics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Side-effect-free conversion of dbt artifacts into one {@code dts-model-package.json}.
 *
 * <p>The converter consumes manifest truth for graph/configuration, catalog truth for observed
 * data types, and only explicit {@code meta.dts}/override truth for business semantics.
 */
public final class DbtModelPackageConverter {

    private final ObjectMapper objectMapper;
    private final ModelConversionClassifier classifier;

    public DbtModelPackageConverter(ObjectMapper objectMapper, ModelConversionClassifier classifier) {
        this.objectMapper = objectMapper;
        this.classifier = classifier;
    }

    public ModelPackage convert(ConversionRequest request) {
        if (request == null || request.manifest() == null || !request.manifest().isObject()) {
            throw new IllegalArgumentException("DBT_MANIFEST_INVALID: manifest.json 不能为空");
        }
        JsonNode manifest = request.manifest();
        Map<String, JsonNode> nodes = objectFields(manifest.path("nodes"));
        Map<String, JsonNode> sources = objectFields(manifest.path("sources"));
        Map<String, JsonNode> macros = objectFields(manifest.path("macros"));
        List<ImportIssue> issues = new ArrayList<>();

        Set<String> selected = selectModels(nodes, request.selectedUniqueIds());
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("DBT_MODEL_NOT_FOUND: manifest 中没有可转换 model");
        }
        Set<String> includedNodes = new TreeSet<>();
        Set<String> includedSources = new TreeSet<>();
        Set<String> includedMacros = new TreeSet<>();
        Set<String> visiting = new HashSet<>();
        Set<String> cycleNodes = new HashSet<>();
        for (String uniqueId : selected) {
            includeDependencyClosure(
                uniqueId,
                nodes,
                sources,
                macros,
                includedNodes,
                includedSources,
                includedMacros,
                visiting,
                cycleNodes,
                issues
            );
        }
        includeAttachedTests(nodes, includedNodes, includedMacros);
        Set<String> macroClosureSeed = new TreeSet<>(includedMacros);
        Set<String> macroVisiting = new HashSet<>();
        for (String macroUniqueId : macroClosureSeed) {
            includeMacroClosure(macroUniqueId, macros, includedMacros, macroVisiting, issues);
        }

        if (request.catalog() == null || request.catalog().isNull() || request.catalog().isMissingNode()) {
            issues.add(
                issue(
                    "CATALOG_MISSING",
                    "WARNING",
                    "$.dbt.catalog",
                    null,
                    "catalog.json 缺失，运行时字段类型不可确认",
                    "执行 dbt docs generate 后重新生成，或在预检中补齐字段类型"
                )
            );
        }

        Map<String, List<String>> testsByNode = testsByDependency(nodes, includedNodes);
        Map<String, SemanticMetadata> overrides = request.semanticOverrides() == null ? Map.of() : request.semanticOverrides();
        List<SourceNode> packageSources = new ArrayList<>();
        for (String uniqueId : includedSources) {
            JsonNode source = sources.get(uniqueId);
            if (source == null) {
                continue;
            }
            packageSources.add(
                new SourceNode(
                    uniqueId,
                    firstNonBlank(text(source, "name"), suffix(uniqueId)),
                    text(source, "original_file_path"),
                    columns(uniqueId, source, request.catalog(), null, testsByNode, issues, true)
                )
            );
        }

        List<TechnicalNode> technicalNodes = new ArrayList<>();
        List<PackageModel> packageModels = new ArrayList<>();
        for (String uniqueId : includedNodes) {
            JsonNode node = nodes.get(uniqueId);
            if (node == null) {
                continue;
            }
            String resourceType = firstNonBlank(text(node, "resource_type"), "model");
            SemanticMetadata semantics = withStructuralSourceRefs(
                firstNonNull(overrides.get(uniqueId), semanticFromManifest(node)),
                reachableSourceRefs(uniqueId, nodes, sources)
            );
            SqlArtifact sql = sqlArtifact(node, request.projectRoot(), resourceType);
            String materialization = text(node.path("config"), "materialized");
            ConversionResult conversion = classifier.classify(
                new ClassificationInput(
                    resourceType,
                    materialization,
                    sql == null ? null : sql.effectiveSql(),
                    semantics,
                    !selected.contains(uniqueId) || (semantics != null && semantics.technicalOnly())
                )
            );
            if (conversion.mode() != ConversionMode.TECHNICAL_ONLY && requiresDimensionDefinition(semantics)) {
                String code = semantics.dimensionDefinitionCode();
                if (code == null || code.isBlank()) {
                    issues.add(
                        issue(
                            "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_REQUIRED",
                            "ERROR",
                            "$.nodes[" + uniqueId + "].meta.dts.dimensionDefinitionCode",
                            uniqueId,
                            "DIMENSION 模型必须提供跨环境稳定的 dimensionDefinitionCode",
                            "在 meta.dts 或 semantic override 中填写目标维度定义的 system_code"
                        )
                    );
                    conversion = new ConversionResult(
                        ConversionMode.BLOCKED,
                        append(conversion.reasonCodes(), "MISSING_DIMENSION_DEFINITION_CODE")
                    );
                } else if (!ModelPackageContract.isDimensionDefinitionCode(code)) {
                    issues.add(
                        issue(
                            "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_INVALID",
                            "ERROR",
                            "$.nodes[" + uniqueId + "].meta.dts.dimensionDefinitionCode",
                            uniqueId,
                            "dimensionDefinitionCode 必须匹配 modeling_dimension_definition.system_code",
                            "使用形如 dim_ 加 32 位小写十六进制字符的 system_code"
                        )
                    );
                    conversion = new ConversionResult(
                        ConversionMode.BLOCKED,
                        append(conversion.reasonCodes(), "INVALID_DIMENSION_DEFINITION_CODE")
                    );
                }
            }
            if (cycleNodes.contains(uniqueId) && conversion.mode() != ConversionMode.TECHNICAL_ONLY) {
                conversion = new ConversionResult(ConversionMode.BLOCKED, append(conversion.reasonCodes(), "DEPENDENCY_CYCLE"));
            }
            if (conversion.mode() == ConversionMode.TECHNICAL_ONLY || !"model".equalsIgnoreCase(resourceType)) {
                technicalNodes.add(technicalNode(uniqueId, node, resourceType, sql, conversion));
                continue;
            }
            packageModels.add(
                new PackageModel(
                    uniqueId,
                    firstNonBlank(text(node, "name"), suffix(uniqueId)),
                    text(node, "description"),
                    text(node, "original_file_path", "path"),
                    sql,
                    materialization,
                    config(node.path("config")),
                    strings(node.path("tags")),
                    columns(uniqueId, node, request.catalog(), semantics, testsByNode, issues, false),
                    testsByNode.getOrDefault(uniqueId, List.of()),
                    dependencies(node),
                    semantics,
                    conversion
                )
            );
        }
        for (String uniqueId : includedMacros) {
            JsonNode macro = macros.get(uniqueId);
            if (macro == null) {
                continue;
            }
            ConversionResult conversion = classifier.classify(
                new ClassificationInput("macro", null, text(macro, "macro_sql"), null, true)
            );
            technicalNodes.add(technicalNode(uniqueId, macro, "macro", sqlArtifact(macro, request.projectRoot(), "macro"), conversion));
        }
        technicalNodes.sort(java.util.Comparator.comparing(TechnicalNode::dbtUniqueId));
        packageModels.sort(java.util.Comparator.comparing(PackageModel::dbtUniqueId));
        packageSources.sort(java.util.Comparator.comparing(SourceNode::dbtUniqueId));
        issues.sort(
            java.util.Comparator.comparing(ImportIssue::code)
                .thenComparing(issue -> issue.modelUniqueId() == null ? "" : issue.modelUniqueId())
                .thenComparing(issue -> issue.fieldPath() == null ? "" : issue.fieldPath())
        );

        JsonNode metadata = manifest.path("metadata");
        ModelPackage withoutChecksum = new ModelPackage(
            ModelPackageContract.SCHEMA_VERSION,
            request.packageId(),
            null,
            new DbtMetadata(
                text(metadata, "project_name"),
                text(metadata, "dbt_version"),
                manifestVersion(text(metadata, "dbt_schema_version")),
                text(metadata, "adapter_type")
            ),
            request.defaults() == null ? new Defaults(null, null) : request.defaults(),
            List.copyOf(packageSources),
            List.copyOf(technicalNodes),
            List.copyOf(packageModels),
            List.copyOf(issues)
        );
        return ModelPackageChecksum.withChecksum(withoutChecksum);
    }

    private Set<String> selectModels(Map<String, JsonNode> nodes, Set<String> requested) {
        Set<String> selected = new TreeSet<>();
        if (requested != null && !requested.isEmpty()) {
            for (String uniqueId : requested) {
                JsonNode node = nodes.get(uniqueId);
                if (node == null || !"model".equalsIgnoreCase(text(node, "resource_type"))) {
                    throw new IllegalArgumentException("DBT_MODEL_NOT_FOUND: " + uniqueId);
                }
                selected.add(uniqueId);
            }
            return selected;
        }
        nodes.forEach((uniqueId, node) -> {
            if ("model".equalsIgnoreCase(text(node, "resource_type"))) {
                selected.add(uniqueId);
            }
        });
        return selected;
    }

    private void includeDependencyClosure(
        String uniqueId,
        Map<String, JsonNode> nodes,
        Map<String, JsonNode> sources,
        Map<String, JsonNode> macros,
        Set<String> includedNodes,
        Set<String> includedSources,
        Set<String> includedMacros,
        Set<String> visiting,
        Set<String> cycleNodes,
        List<ImportIssue> issues
    ) {
        if (sources.containsKey(uniqueId)) {
            includedSources.add(uniqueId);
            return;
        }
        if (macros.containsKey(uniqueId)) {
            if (includedMacros.add(uniqueId)) {
                for (String macroDependency : strings(macros.get(uniqueId).path("depends_on").path("macros"))) {
                    includeDependencyClosure(
                        macroDependency,
                        nodes,
                        sources,
                        macros,
                        includedNodes,
                        includedSources,
                        includedMacros,
                        visiting,
                        cycleNodes,
                        issues
                    );
                }
            }
            return;
        }
        JsonNode node = nodes.get(uniqueId);
        if (node == null) {
            issues.add(
                issue(
                    "DEPENDENCY_MISSING",
                    "ERROR",
                    "$.nodes[" + uniqueId + "]",
                    uniqueId,
                    "manifest 依赖节点不存在",
                    "修复 manifest 依赖闭包后重新生成"
                )
            );
            return;
        }
        if (!visiting.add(uniqueId)) {
            cycleNodes.add(uniqueId);
            issues.add(
                issue(
                    "DEPENDENCY_CYCLE",
                    "ERROR",
                    "$.nodes[" + uniqueId + "].depends_on",
                    uniqueId,
                    "dbt 节点存在循环依赖",
                    "移除循环依赖后重新生成"
                )
            );
            return;
        }
        if (includedNodes.add(uniqueId)) {
            for (String dependency : strings(node.path("depends_on").path("nodes"))) {
                includeDependencyClosure(
                    dependency,
                    nodes,
                    sources,
                    macros,
                    includedNodes,
                    includedSources,
                    includedMacros,
                    visiting,
                    cycleNodes,
                    issues
                );
            }
            for (String dependency : strings(node.path("depends_on").path("macros"))) {
                includeDependencyClosure(
                    dependency,
                    nodes,
                    sources,
                    macros,
                    includedNodes,
                    includedSources,
                    includedMacros,
                    visiting,
                    cycleNodes,
                    issues
                );
            }
        }
        visiting.remove(uniqueId);
    }

    private static void includeAttachedTests(Map<String, JsonNode> nodes, Set<String> includedNodes, Set<String> includedMacros) {
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
                JsonNode node = entry.getValue();
                if (!"test".equalsIgnoreCase(text(node, "resource_type"))) {
                    continue;
                }
                if (strings(node.path("depends_on").path("nodes")).stream().anyMatch(includedNodes::contains)) {
                    changed |= includedNodes.add(entry.getKey());
                    includedMacros.addAll(strings(node.path("depends_on").path("macros")));
                }
            }
        } while (changed);
    }

    private static void includeMacroClosure(
        String uniqueId,
        Map<String, JsonNode> macros,
        Set<String> includedMacros,
        Set<String> visiting,
        List<ImportIssue> issues
    ) {
        JsonNode macro = macros.get(uniqueId);
        if (macro == null) {
            issues.add(
                issue(
                    "DEPENDENCY_MISSING",
                    "ERROR",
                    "$.macros[" + uniqueId + "]",
                    uniqueId,
                    "manifest macro 依赖不存在",
                    "修复 manifest macro 依赖闭包后重新生成"
                )
            );
            return;
        }
        if (!visiting.add(uniqueId)) {
            return;
        }
        includedMacros.add(uniqueId);
        for (String dependency : strings(macro.path("depends_on").path("macros"))) {
            includeMacroClosure(dependency, macros, includedMacros, visiting, issues);
        }
        visiting.remove(uniqueId);
    }

    private TechnicalNode technicalNode(
        String uniqueId,
        JsonNode node,
        String resourceType,
        SqlArtifact sql,
        ConversionResult conversion
    ) {
        return new TechnicalNode(
            uniqueId,
            firstNonBlank(text(node, "name"), suffix(uniqueId)),
            resourceType.toLowerCase(Locale.ROOT),
            text(node, "original_file_path", "path"),
            sql,
            config(node.path("config")),
            dependencies(node),
            strings(node.path("tags")),
            conversion
        );
    }

    private List<Column> columns(
        String uniqueId,
        JsonNode manifestNode,
        JsonNode catalog,
        SemanticMetadata semantics,
        Map<String, List<String>> testsByNode,
        List<ImportIssue> issues,
        boolean source
    ) {
        Map<String, JsonNode> manifestColumns = orderedObjectFields(manifestNode.path("columns"));
        JsonNode catalogRoot = catalog == null ? null : catalog.path(source ? "sources" : "nodes").path(uniqueId);
        Map<String, JsonNode> catalogColumns = catalogRoot == null ? Map.of() : orderedObjectFields(catalogRoot.path("columns"));
        if (catalog != null) {
            for (String column : catalogColumns.keySet()) {
                if (!manifestColumns.containsKey(column)) {
                    issues.add(
                        issue(
                            "CATALOG_COLUMN_NOT_IN_MANIFEST",
                            "WARNING",
                            "$.nodes[" + uniqueId + "].columns[" + column + "]",
                            uniqueId,
                            "catalog 字段未在 manifest/schema 中声明：" + column,
                            "核对 schema YAML 与运行 relation"
                        )
                    );
                }
            }
            for (String column : manifestColumns.keySet()) {
                if (!catalogColumns.containsKey(column)) {
                    issues.add(
                        issue(
                            "CATALOG_COLUMN_MISSING",
                            "WARNING",
                            "$.nodes[" + uniqueId + "].columns[" + column + "]",
                            uniqueId,
                            "manifest 字段未在 catalog relation 中出现：" + column,
                            "重新生成 catalog 或核对模型输出"
                        )
                    );
                }
            }
        }
        List<String> names = orderedColumnNames(manifestColumns, catalogColumns);
        List<Column> result = new ArrayList<>();
        for (String name : names) {
            JsonNode manifestColumn = manifestColumns.get(name);
            JsonNode catalogColumn = catalogColumns.get(name);
            List<String> columnTests = testsForColumn(uniqueId, name, testsByNode, manifestNode);
            result.add(
                new Column(
                    name,
                    manifestColumn == null ? null : text(manifestColumn, "description"),
                    catalogColumn == null ? firstNonBlank(text(manifestColumn, "data_type")) : text(catalogColumn, "type"),
                    semantics == null || semantics.fieldRoles() == null ? null : semantics.fieldRoles().get(name),
                    dimensionAttributeCode(manifestColumn),
                    columnTests
                )
            );
        }
        return List.copyOf(result);
    }

    private static String dimensionAttributeCode(JsonNode manifestColumn) {
        if (manifestColumn == null) return null;
        return firstNonBlank(
            text(manifestColumn.path("meta").path("dts"), "dimensionAttributeCode", "dimension_attribute_code"),
            text(manifestColumn.path("config").path("meta").path("dts"), "dimensionAttributeCode", "dimension_attribute_code")
        );
    }

    private static List<String> orderedColumnNames(
        Map<String, JsonNode> manifestColumns,
        Map<String, JsonNode> catalogColumns
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        catalogColumns
            .entrySet()
            .stream()
            .sorted(Comparator.comparingInt(entry -> catalogColumnIndex(entry.getValue())))
            .map(Map.Entry::getKey)
            .forEach(result::add);
        result.addAll(manifestColumns.keySet());
        return List.copyOf(result);
    }

    private static int catalogColumnIndex(JsonNode column) {
        int index = column == null ? -1 : column.path("index").asInt(-1);
        return index > 0 ? index : Integer.MAX_VALUE;
    }

    private static List<String> testsForColumn(
        String uniqueId,
        String column,
        Map<String, List<String>> testsByNode,
        JsonNode manifestNode
    ) {
        Set<String> result = new TreeSet<>();
        JsonNode manifestColumn = manifestNode.path("columns").path(column);
        result.addAll(strings(manifestColumn.path("tests")));
        for (String test : testsByNode.getOrDefault(uniqueId, List.of())) {
            if (test.endsWith("_" + column) || test.contains("_" + column + ".")) {
                result.add(test);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, List<String>> testsByDependency(Map<String, JsonNode> nodes, Set<String> includedNodes) {
        Map<String, Set<String>> collected = new TreeMap<>();
        for (String uniqueId : includedNodes) {
            JsonNode node = nodes.get(uniqueId);
            if (node == null || !"test".equalsIgnoreCase(text(node, "resource_type"))) {
                continue;
            }
            String testName = firstNonBlank(text(node, "name"), uniqueId);
            for (String dependency : strings(node.path("depends_on").path("nodes"))) {
                collected.computeIfAbsent(dependency, ignored -> new TreeSet<>()).add(testName);
            }
        }
        Map<String, List<String>> result = new TreeMap<>();
        collected.forEach((key, values) -> result.put(key, List.copyOf(values)));
        return result;
    }

    private SqlArtifact sqlArtifact(JsonNode node, Path projectRoot, String resourceType) {
        String resourcePath = text(node, "original_file_path", "path");
        String projectSql = readProjectSql(projectRoot, resourcePath);
        String manifestRaw = "macro".equalsIgnoreCase(resourceType)
            ? text(node, "macro_sql")
            : firstNonBlank(text(node, "raw_code"), text(node, "raw_sql"));
        String compiled = text(node, "compiled_code", "compiled_sql");
        String raw = firstNonBlank(projectSql, manifestRaw);
        String effective = firstNonBlank(projectSql, compiled, text(node, "raw_code"), text(node, "raw_sql"), text(node, "macro_sql"));
        if (raw == null && compiled == null && effective == null) {
            return null;
        }
        String source = projectSql != null
            ? "PROJECT_FILE"
            : compiled != null
                ? "COMPILED_CODE"
                : text(node, "raw_code") != null
                    ? "RAW_CODE"
                    : text(node, "raw_sql") != null ? "RAW_SQL" : "MACRO_SQL";
        return new SqlArtifact(
            raw,
            checksum(raw),
            compiled,
            checksum(compiled),
            effective,
            checksum(effective),
            source
        );
    }

    private static String readProjectSql(Path projectRoot, String resourcePath) {
        if (
            projectRoot == null ||
            resourcePath == null ||
            resourcePath.isBlank() ||
            resourcePath.contains("\\") ||
            resourcePath.startsWith("/") ||
            !resourcePath.toLowerCase(Locale.ROOT).endsWith(".sql")
        ) {
            return null;
        }
        try {
            Path root = projectRoot.toAbsolutePath().normalize();
            Path candidate = root.resolve(resourcePath).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
                return null;
            }
            return Files.readString(candidate);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private Map<String, Object> config(JsonNode config) {
        if (config == null || !config.isObject()) {
            return Map.of();
        }
        Map<String, Object> raw = objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() {});
        return normalizeMap(raw);
    }

    static SemanticMetadata semanticFrom(JsonNode dts, String overrideSource) {
        if (dts == null || !dts.isObject()) {
            return null;
        }
        JsonNode grain = dts.path("grain");
        JsonNode time = field(dts, "timeSemantics", "time_semantics");
        return new SemanticMetadata(
            text(dts, "modelType", "model_type"),
            text(dts, "layer", "target_layer"),
            grain.isObject() ? new Grain(text(grain, "statement"), strings(grain.path("keys"))) : null,
            text(dts, "factShape", "fact_shape"),
            time != null && time.isObject() ? new TimeSemantics(text(time, "type"), strings(time.path("fields"))) : null,
            text(dts, "domainCode", "domain_code"),
            sourceRefs(field(dts, "sourceRefs", "source_refs")),
            strings(field(dts, "consumptionScenarios", "consumption_scenarios")),
            stringMap(field(dts, "fieldRoles", "field_roles")),
            text(dts, "dimensionStrategy", "dimension_strategy"),
            text(dts, "dimensionDefinitionCode", "dimension_definition_code"),
            dimensionDefinition(field(dts, "dimensionDefinition", "dimension_definition")),
            firstNonBlank(text(dts, "overrideSource", "override_source"), overrideSource),
            booleanValue(dts, "technicalOnly", "technical_only")
        );
    }

    private static DimensionDefinitionBlueprint dimensionDefinition(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        List<DimensionAttributeBlueprint> attributes = new ArrayList<>();
        JsonNode attributeNodes = field(node, "attributes");
        if (attributeNodes != null && attributeNodes.isArray()) {
            attributeNodes.forEach(attribute -> {
                if (attribute != null && attribute.isObject()) {
                    attributes.add(
                        new DimensionAttributeBlueprint(
                            text(attribute, "code"),
                            text(attribute, "name"),
                            text(attribute, "definition"),
                            booleanValue(attribute, "primaryKey", "primary_key"),
                            text(attribute, "standardRef", "standard_ref"),
                            text(attribute, "standardVersion", "standard_version"),
                            attribute.path("order").asInt(0)
                        )
                    );
                }
            });
        }
        return new DimensionDefinitionBlueprint(
            text(node, "name"),
            text(node, "abbreviation"),
            text(node, "definition"),
            List.copyOf(attributes)
        );
    }

    private static boolean requiresDimensionDefinition(SemanticMetadata semantics) {
        return semantics != null && "DIMENSION".equals(semantics.modelType()) && !semantics.technicalOnly();
    }

    private static SemanticMetadata semanticFromManifest(JsonNode node) {
        JsonNode meta = node.path("meta");
        JsonNode dts = meta.path("dts");
        if (!dts.isObject()) {
            dts = node.path("config").path("meta").path("dts");
        }
        String resourcePath = text(node, "original_file_path");
        return semanticFrom(dts, resourcePath == null ? "manifest.meta.dts" : resourcePath + "#meta.dts");
    }

    private static List<SourceRef> reachableSourceRefs(
        String startUniqueId,
        Map<String, JsonNode> nodes,
        Map<String, JsonNode> sources
    ) {
        Set<String> visited = new HashSet<>();
        Set<String> reachable = new TreeSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(startUniqueId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            JsonNode node = nodes.get(current);
            if (node == null) {
                continue;
            }
            for (String dependency : strings(node.path("depends_on").path("nodes"))) {
                if (sources.containsKey(dependency)) {
                    reachable.add(dependency);
                } else if (nodes.containsKey(dependency)) {
                    pending.addLast(dependency);
                }
            }
        }
        return reachable.stream().map(sourceId -> new SourceRef("TABLE", sourceId, null)).toList();
    }

    private static SemanticMetadata withStructuralSourceRefs(SemanticMetadata semantics, List<SourceRef> structuralRefs) {
        if (semantics == null || structuralRefs == null || structuralRefs.isEmpty()) {
            return semantics;
        }
        Map<String, SourceRef> merged = new TreeMap<>();
        structuralRefs.forEach(source -> merged.put(source.ref(), source));
        if (semantics.sourceRefs() != null) {
            semantics
                .sourceRefs()
                .stream()
                .filter(source -> source != null && source.ref() != null && !source.ref().isBlank())
                .forEach(source -> merged.put(source.ref(), source));
        }
        return new SemanticMetadata(
            semantics.modelType(),
            semantics.layer(),
            semantics.grain(),
            semantics.factShape(),
            semantics.timeSemantics(),
            semantics.domainCode(),
            List.copyOf(merged.values()),
            semantics.consumptionScenarios(),
            semantics.fieldRoles(),
            semantics.dimensionStrategy(),
            semantics.dimensionDefinitionCode(),
            semantics.dimensionDefinition(),
            semantics.overrideSource(),
            semantics.technicalOnly()
        );
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new TreeMap<>();
        source.forEach((key, value) -> {
            Object normalized = normalizeValue(value);
            if (key != null && normalized != null) {
                result.put(String.valueOf(key), normalized);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> {
                Object normalized = normalizeValue(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            });
            return List.copyOf(result);
        }
        return value;
    }

    private static List<SourceRef> sourceRefs(JsonNode sourceRefs) {
        if (sourceRefs == null || !sourceRefs.isArray()) {
            return List.of();
        }
        List<SourceRef> result = new ArrayList<>();
        sourceRefs.forEach(source -> {
            if (source.isObject()) {
                result.add(new SourceRef(text(source, "kind"), text(source, "ref"), text(source, "layer")));
            }
        });
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new TreeMap<>();
        node.fields().forEachRemaining(entry -> {
            String value = entry.getValue().isNull() ? null : entry.getValue().asText();
            if (value != null && !value.isBlank()) {
                result.put(entry.getKey(), value);
            }
        });
        return Map.copyOf(result);
    }

    private static List<String> dependencies(JsonNode node) {
        Set<String> result = new TreeSet<>();
        result.addAll(strings(node.path("depends_on").path("nodes")));
        result.addAll(strings(node.path("depends_on").path("macros")));
        return List.copyOf(result);
    }

    private static Map<String, JsonNode> objectFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, JsonNode> result = new TreeMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Map<String, JsonNode> orderedObjectFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, JsonNode> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> result = new TreeSet<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        });
        return List.copyOf(result);
    }

    private static String manifestVersion(String schemaUrl) {
        if (schemaUrl == null) {
            return null;
        }
        int slash = schemaUrl.lastIndexOf('/');
        String file = slash >= 0 ? schemaUrl.substring(slash + 1) : schemaUrl;
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }

    private static String checksum(String value) {
        return value == null ? null : ModelPackageChecksum.sha256Text(value);
    }

    private static List<String> append(List<String> values, String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values == null ? List.of() : values);
        result.add(value);
        return List.copyOf(result);
    }

    private static JsonNode field(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... names) {
        JsonNode value = field(node, names);
        if (value == null || !value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean booleanValue(JsonNode node, String... names) {
        JsonNode value = field(node, names);
        return value != null && value.asBoolean(false);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private static String suffix(String uniqueId) {
        int separator = uniqueId == null ? -1 : uniqueId.lastIndexOf('.');
        return separator < 0 ? uniqueId : uniqueId.substring(separator + 1);
    }

    private static ImportIssue issue(
        String code,
        String severity,
        String fieldPath,
        String modelUniqueId,
        String message,
        String recoveryAction
    ) {
        return new ImportIssue(code, severity, fieldPath, modelUniqueId, message, recoveryAction);
    }

    public record ConversionRequest(
        String packageId,
        JsonNode manifest,
        JsonNode catalog,
        Path projectRoot,
        Map<String, SemanticMetadata> semanticOverrides,
        Set<String> selectedUniqueIds,
        Defaults defaults
    ) {}
}
