package com.yuzhi.dts.platform.service.modeling.imports.validator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionAttributeBlueprint;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionDefinitionBlueprint;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed structural and checksum validation for {@code dts.model-package/v1}. */
public final class ModelPackageValidator {

    public static final int MAX_PACKAGE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_VALIDATION_ISSUES = 200;
    public static final String VALIDATION_ISSUES_TRUNCATED_CODE = "MODEL_PACKAGE_VALIDATION_ISSUES_TRUNCATED";

    private static final Pattern PACKAGE_ID = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SEMANTIC_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Set<String> ROOT_FIELDS = Set.of(
        "schemaVersion",
        "packageId",
        "packageChecksum",
        "dbt",
        "defaults",
        "sources",
        "technicalNodes",
        "models",
        "issues"
    );
    private static final Set<String> DBT_FIELDS = Set.of("projectName", "projectVersion", "manifestVersion", "adapterType");
    private static final Set<String> DEFAULT_FIELDS = Set.of("planRef", "domainRef");
    private static final Set<String> SOURCE_FIELDS = Set.of("dbtUniqueId", "name", "resourcePath", "columns");
    private static final Set<String> TECHNICAL_FIELDS = Set.of(
        "dbtUniqueId",
        "name",
        "resourceType",
        "resourcePath",
        "sql",
        "config",
        "dependencies",
        "tags",
        "conversion"
    );
    private static final Set<String> MODEL_FIELDS = Set.of(
        "dbtUniqueId",
        "name",
        "description",
        "resourcePath",
        "sql",
        "materialization",
        "config",
        "tags",
        "columns",
        "tests",
        "dependencies",
        "semantics",
        "conversion"
    );
    private static final Set<String> COLUMN_FIELDS = Set.of(
        "name",
        "description",
        "dataType",
        "role",
        "dimensionAttributeCode",
        "tests"
    );
    private static final Set<String> REQUIRED_COLUMN_FIELDS = Set.of("name", "description", "dataType", "role", "tests");
    private static final Set<String> SQL_FIELDS = Set.of(
        "rawSql",
        "rawSqlChecksum",
        "compiledSql",
        "compiledSqlChecksum",
        "effectiveSql",
        "effectiveSqlChecksum",
        "effectiveSource"
    );
    private static final Set<String> CONVERSION_FIELDS = Set.of("mode", "reasonCodes");
    private static final Set<String> SEMANTIC_FIELDS = Set.of(
        "modelType",
        "layer",
        "grain",
        "factShape",
        "timeSemantics",
        "domainCode",
        "sourceRefs",
        "consumptionScenarios",
        "fieldRoles",
        "dimensionStrategy",
        "dimensionDefinitionCode",
        "dimensionDefinition",
        "overrideSource",
        "technicalOnly"
    );
    private static final Set<String> DIMENSION_DEFINITION_FIELDS = Set.of(
        "name",
        "abbreviation",
        "definition",
        "attributes"
    );
    private static final Set<String> DIMENSION_ATTRIBUTE_FIELDS = Set.of(
        "code",
        "name",
        "definition",
        "primaryKey",
        "standardRef",
        "standardVersion",
        "order"
    );
    private static final Set<String> GRAIN_FIELDS = Set.of("statement", "keys");
    private static final Set<String> TIME_FIELDS = Set.of("type", "fields");
    private static final Set<String> SOURCE_REF_FIELDS = Set.of("kind", "ref", "layer");
    private static final Set<String> ISSUE_FIELDS = Set.of(
        "code",
        "severity",
        "fieldPath",
        "modelUniqueId",
        "message",
        "recoveryAction"
    );
    private static final Set<String> CONVERSION_MODES = Set.of("DESIGNER_GENERATED", "DBT_BACKED", "BLOCKED", "TECHNICAL_ONLY");
    private static final Set<String> ISSUE_SEVERITIES = Set.of("INFO", "WARNING", "ERROR");

    private final ObjectMapper strictObjectMapper;

    public ModelPackageValidator(ObjectMapper objectMapper) {
        this.strictObjectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public ModelPackage parseAndValidate(byte[] json) {
        List<ValidationIssue> issues = validateBytes(json);
        if (!issues.isEmpty()) {
            throw new ModelPackageValidationException(issues);
        }
        try {
            return strictObjectMapper.readValue(json, ModelPackage.class);
        } catch (Exception exception) {
            throw new ModelPackageValidationException(
                List.of(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$", "模型包不是有效的 v1 JSON", "按 JSON Schema 修复模型包"))
            );
        }
    }

    public List<ValidationIssue> validateBytes(byte[] json) {
        if (json == null || json.length == 0) {
            return List.of(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$", "模型包为空", "提供 dts-model-package.json"));
        }
        if (json.length > MAX_PACKAGE_BYTES) {
            return List.of(
                issue(
                    "MODEL_PACKAGE_TOO_LARGE",
                    "$",
                    "模型包超过 " + MAX_PACKAGE_BYTES + " bytes",
                    "减少包内节点或 SQL 体积后重新生成"
                )
            );
        }
        try {
            JsonNode tree = strictObjectMapper.readTree(json);
            if (
                tree != null &&
                tree.isObject() &&
                tree.has("schemaVersion") &&
                tree.path("schemaVersion").isTextual() &&
                !ModelPackageContract.SCHEMA_VERSION.equals(tree.path("schemaVersion").asText())
            ) {
                return List.of(
                    issue(
                        "MODEL_PACKAGE_SCHEMA_VERSION_UNSUPPORTED",
                        "$.schemaVersion",
                        "仅支持 " + ModelPackageContract.SCHEMA_VERSION,
                        "使用兼容生成器重新生成"
                    )
                );
            }
            List<ValidationIssue> schemaIssues = validateSchemaTree(tree);
            if (!schemaIssues.isEmpty()) {
                return schemaIssues;
            }
            ModelPackage modelPackage = strictObjectMapper.treeToValue(tree, ModelPackage.class);
            return validate(modelPackage);
        } catch (Exception exception) {
            return List.of(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$", "模型包 JSON 结构无效", "按 JSON Schema 修复模型包"));
        }
    }

    private static List<ValidationIssue> validateSchemaTree(JsonNode root) {
        List<ValidationIssue> issues = new BoundedIssueList();
        if (!requireObject(root, "$", ROOT_FIELDS, ROOT_FIELDS, issues)) {
            return List.copyOf(issues);
        }
        requireText(root, "schemaVersion", "$.schemaVersion", false, issues);
        requireText(root, "packageId", "$.packageId", false, issues);
        requireSha256(root, "packageChecksum", "$.packageChecksum", false, issues);
        validateDbt(root.get("dbt"), "$.dbt", issues);
        validateDefaults(root.get("defaults"), "$.defaults", issues);
        validateObjectArray(root.get("sources"), "$.sources", ModelPackageValidator::validateSource, issues);
        validateObjectArray(root.get("technicalNodes"), "$.technicalNodes", ModelPackageValidator::validateTechnicalNode, issues);
        validateObjectArray(root.get("models"), "$.models", ModelPackageValidator::validateModel, issues);
        validateObjectArray(root.get("issues"), "$.issues", ModelPackageValidator::validateIssue, issues);
        return List.copyOf(issues);
    }

    private static void validateDbt(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, DBT_FIELDS, DBT_FIELDS, issues)) {
            return;
        }
        requireText(node, "projectName", path + ".projectName", false, issues);
        requireText(node, "projectVersion", path + ".projectVersion", true, issues);
        requireText(node, "manifestVersion", path + ".manifestVersion", false, issues);
        requireText(node, "adapterType", path + ".adapterType", true, issues);
    }

    private static void validateDefaults(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, DEFAULT_FIELDS, DEFAULT_FIELDS, issues)) {
            return;
        }
        requireText(node, "planRef", path + ".planRef", true, issues);
        requireText(node, "domainRef", path + ".domainRef", true, issues);
    }

    private static void validateSource(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, SOURCE_FIELDS, SOURCE_FIELDS, issues)) {
            return;
        }
        requireText(node, "dbtUniqueId", path + ".dbtUniqueId", false, issues);
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "resourcePath", path + ".resourcePath", false, issues);
        validateObjectArray(node.get("columns"), path + ".columns", ModelPackageValidator::validateColumn, issues);
    }

    private static void validateTechnicalNode(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, TECHNICAL_FIELDS, TECHNICAL_FIELDS, issues)) {
            return;
        }
        requireText(node, "dbtUniqueId", path + ".dbtUniqueId", false, issues);
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "resourceType", path + ".resourceType", false, issues);
        requireText(node, "resourcePath", path + ".resourcePath", false, issues);
        validateSqlShape(node.get("sql"), path + ".sql", true, issues);
        requireObject(node.get("config"), path + ".config", Set.of(), Set.of(), true, issues);
        validateStringArray(node.get("dependencies"), path + ".dependencies", issues);
        validateStringArray(node.get("tags"), path + ".tags", issues);
        validateConversion(node.get("conversion"), path + ".conversion", issues);
    }

    private static void validateModel(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, MODEL_FIELDS, MODEL_FIELDS, issues)) {
            return;
        }
        requireText(node, "dbtUniqueId", path + ".dbtUniqueId", false, issues);
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "description", path + ".description", true, issues);
        requireText(node, "resourcePath", path + ".resourcePath", false, issues);
        validateSqlShape(node.get("sql"), path + ".sql", true, issues);
        requireText(node, "materialization", path + ".materialization", true, issues);
        requireObject(node.get("config"), path + ".config", Set.of(), Set.of(), true, issues);
        validateStringArray(node.get("tags"), path + ".tags", issues);
        validateObjectArray(node.get("columns"), path + ".columns", ModelPackageValidator::validateColumn, issues);
        validateStringArray(node.get("tests"), path + ".tests", issues);
        validateStringArray(node.get("dependencies"), path + ".dependencies", issues);
        validateSemantics(node.get("semantics"), path + ".semantics", issues);
        validateConversion(node.get("conversion"), path + ".conversion", issues);
    }

    private static void validateColumn(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, REQUIRED_COLUMN_FIELDS, COLUMN_FIELDS, issues)) {
            return;
        }
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "description", path + ".description", true, issues);
        requireText(node, "dataType", path + ".dataType", true, issues);
        requireText(node, "role", path + ".role", true, issues);
        requireText(node, "dimensionAttributeCode", path + ".dimensionAttributeCode", true, issues);
        validateStringArray(node.get("tests"), path + ".tests", issues);
    }

    private static void validateSqlShape(JsonNode node, String path, boolean nullable, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) {
            if (!nullable) {
                issues.add(schemaIssue(path, "必须是 SQL 对象"));
            }
            return;
        }
        if (!requireObject(node, path, SQL_FIELDS, SQL_FIELDS, issues)) {
            return;
        }
        requireText(node, "rawSql", path + ".rawSql", true, issues);
        requireSha256(node, "rawSqlChecksum", path + ".rawSqlChecksum", true, issues);
        requireText(node, "compiledSql", path + ".compiledSql", true, issues);
        requireSha256(node, "compiledSqlChecksum", path + ".compiledSqlChecksum", true, issues);
        requireText(node, "effectiveSql", path + ".effectiveSql", true, issues);
        requireSha256(node, "effectiveSqlChecksum", path + ".effectiveSqlChecksum", true, issues);
        requireText(node, "effectiveSource", path + ".effectiveSource", true, issues);
    }

    private static void validateConversion(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, CONVERSION_FIELDS, CONVERSION_FIELDS, issues)) {
            return;
        }
        requireEnum(node, "mode", path + ".mode", CONVERSION_MODES, issues);
        validateStringArray(node.get("reasonCodes"), path + ".reasonCodes", issues);
    }

    private static void validateSemantics(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!requireObject(node, path, SEMANTIC_FIELDS, SEMANTIC_FIELDS, issues)) {
            return;
        }
        requireText(node, "modelType", path + ".modelType", true, issues);
        requireText(node, "layer", path + ".layer", true, issues);
        validateGrain(node.get("grain"), path + ".grain", issues);
        requireText(node, "factShape", path + ".factShape", true, issues);
        validateTimeSemantics(node.get("timeSemantics"), path + ".timeSemantics", issues);
        requireText(node, "domainCode", path + ".domainCode", true, issues);
        validateObjectArray(node.get("sourceRefs"), path + ".sourceRefs", ModelPackageValidator::validateSourceRef, issues);
        validateStringArray(node.get("consumptionScenarios"), path + ".consumptionScenarios", issues);
        validateStringMap(node.get("fieldRoles"), path + ".fieldRoles", issues);
        requireText(node, "dimensionStrategy", path + ".dimensionStrategy", true, issues);
        validateDimensionDefinitionCode(node, path, issues);
        validateDimensionDefinition(node.get("dimensionDefinition"), path + ".dimensionDefinition", issues);
        requireText(node, "overrideSource", path + ".overrideSource", true, issues);
        requireBoolean(node, "technicalOnly", path + ".technicalOnly", issues);
    }

    private static void validateGrain(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!requireObject(node, path, GRAIN_FIELDS, GRAIN_FIELDS, issues)) {
            return;
        }
        requireText(node, "statement", path + ".statement", false, issues);
        validateStringArray(node.get("keys"), path + ".keys", issues);
    }

    private static void validateTimeSemantics(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!requireObject(node, path, TIME_FIELDS, TIME_FIELDS, issues)) {
            return;
        }
        requireText(node, "type", path + ".type", false, issues);
        validateStringArray(node.get("fields"), path + ".fields", issues);
    }

    private static void validateSourceRef(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, SOURCE_REF_FIELDS, SOURCE_REF_FIELDS, issues)) {
            return;
        }
        requireText(node, "kind", path + ".kind", false, issues);
        requireText(node, "ref", path + ".ref", false, issues);
        requireText(node, "layer", path + ".layer", true, issues);
    }

    private static void validateDimensionDefinition(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!requireObject(node, path, DIMENSION_DEFINITION_FIELDS, DIMENSION_DEFINITION_FIELDS, issues)) {
            return;
        }
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "abbreviation", path + ".abbreviation", true, issues);
        requireText(node, "definition", path + ".definition", false, issues);
        validateObjectArray(
            node.get("attributes"),
            path + ".attributes",
            ModelPackageValidator::validateDimensionAttribute,
            issues
        );
    }

    private static void validateDimensionAttribute(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, DIMENSION_ATTRIBUTE_FIELDS, DIMENSION_ATTRIBUTE_FIELDS, issues)) {
            return;
        }
        requireText(node, "code", path + ".code", false, issues);
        requireText(node, "name", path + ".name", false, issues);
        requireText(node, "definition", path + ".definition", false, issues);
        requireBoolean(node, "primaryKey", path + ".primaryKey", issues);
        requireText(node, "standardRef", path + ".standardRef", true, issues);
        requireText(node, "standardVersion", path + ".standardVersion", true, issues);
        if (!node.has("order") || !node.path("order").isIntegralNumber() || node.path("order").asInt() < 1) {
            issues.add(schemaIssue(path + ".order", "必须是正整数"));
        }
    }

    private static void validateIssue(JsonNode node, String path, List<ValidationIssue> issues) {
        if (!requireObject(node, path, ISSUE_FIELDS, ISSUE_FIELDS, issues)) {
            return;
        }
        requireText(node, "code", path + ".code", false, issues);
        requireEnum(node, "severity", path + ".severity", ISSUE_SEVERITIES, issues);
        requireText(node, "fieldPath", path + ".fieldPath", true, issues);
        requireText(node, "modelUniqueId", path + ".modelUniqueId", true, issues);
        requireText(node, "message", path + ".message", false, issues);
        requireText(node, "recoveryAction", path + ".recoveryAction", false, issues);
    }

    private static boolean requireObject(
        JsonNode node,
        String path,
        Set<String> required,
        Set<String> allowed,
        List<ValidationIssue> issues
    ) {
        return requireObject(node, path, required, allowed, false, issues);
    }

    private static boolean requireObject(
        JsonNode node,
        String path,
        Set<String> required,
        Set<String> allowed,
        boolean allowAdditionalProperties,
        List<ValidationIssue> issues
    ) {
        if (node == null || !node.isObject()) {
            issues.add(schemaIssue(path, "必须是对象且不能为 null"));
            return false;
        }
        for (String field : required) {
            if (!node.has(field)) {
                issues.add(schemaIssue(path + "." + field, "缺少 required 字段"));
            }
        }
        if (!allowAdditionalProperties) {
            node.fieldNames().forEachRemaining(field -> {
                if (!allowed.contains(field)) {
                    issues.add(schemaIssue(path + "." + field, "不允许 additionalProperties"));
                }
            });
        }
        return true;
    }

    private static void validateObjectArray(
        JsonNode node,
        String path,
        ObjectValidator validator,
        List<ValidationIssue> issues
    ) {
        if (node == null || !node.isArray()) {
            issues.add(schemaIssue(path, "必须是数组且不能为 null"));
            return;
        }
        for (int index = 0; index < node.size(); index++) {
            validator.validate(node.get(index), path + "[" + index + "]", issues);
        }
    }

    private static void validateStringArray(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || !node.isArray()) {
            issues.add(schemaIssue(path, "必须是字符串数组且不能为 null"));
            return;
        }
        for (int index = 0; index < node.size(); index++) {
            if (!node.get(index).isTextual()) {
                issues.add(schemaIssue(path + "[" + index + "]", "数组项必须是字符串"));
            }
        }
    }

    private static void validateStringMap(JsonNode node, String path, List<ValidationIssue> issues) {
        if (node == null || !node.isObject()) {
            issues.add(schemaIssue(path, "必须是字符串映射且不能为 null"));
            return;
        }
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                issues.add(schemaIssue(path + "." + entry.getKey(), "映射值必须是字符串"));
            }
        });
    }

    private static void requireText(
        JsonNode parent,
        String field,
        String path,
        boolean nullable,
        List<ValidationIssue> issues
    ) {
        if (parent == null || !parent.has(field)) {
            return;
        }
        JsonNode value = parent.get(field);
        if (value.isNull() && nullable) {
            return;
        }
        if (!value.isTextual() || (!nullable && value.asText().isEmpty())) {
            issues.add(schemaIssue(path, nullable ? "必须是字符串或 null" : "必须是非空字符串"));
        }
    }

    private static void requireSha256(
        JsonNode parent,
        String field,
        String path,
        boolean nullable,
        List<ValidationIssue> issues
    ) {
        if (parent == null || !parent.has(field)) {
            return;
        }
        JsonNode value = parent.get(field);
        if (value.isNull() && nullable) {
            return;
        }
        if (!value.isTextual() || !SHA_256.matcher(value.asText()).matches()) {
            issues.add(schemaIssue(path, nullable ? "必须是 SHA-256 字符串或 null" : "必须是 SHA-256 字符串"));
        }
    }

    private static void requireEnum(
        JsonNode parent,
        String field,
        String path,
        Set<String> values,
        List<ValidationIssue> issues
    ) {
        if (parent == null || !parent.has(field)) {
            return;
        }
        JsonNode value = parent.get(field);
        if (!value.isTextual() || !values.contains(value.asText())) {
            issues.add(schemaIssue(path, "枚举值无效"));
        }
    }

    private static void requireBoolean(
        JsonNode parent,
        String field,
        String path,
        List<ValidationIssue> issues
    ) {
        if (parent != null && parent.has(field) && !parent.get(field).isBoolean()) {
            issues.add(schemaIssue(path, "必须是 boolean"));
        }
    }

    private static ValidationIssue schemaIssue(String path, String message) {
        return issue("MODEL_PACKAGE_SCHEMA_INVALID", path, message, "按 dts-model-package-v1.schema.json 修复");
    }

    @FunctionalInterface
    private interface ObjectValidator {
        void validate(JsonNode node, String path, List<ValidationIssue> issues);
    }

    public List<ValidationIssue> validate(ModelPackage modelPackage) {
        List<ValidationIssue> issues = new BoundedIssueList();
        if (modelPackage == null) {
            return List.of(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$", "模型包为空", "提供 dts-model-package.json"));
        }
        if (!ModelPackageContract.SCHEMA_VERSION.equals(modelPackage.schemaVersion())) {
            issues.add(
                issue(
                    "MODEL_PACKAGE_SCHEMA_VERSION_UNSUPPORTED",
                    "$.schemaVersion",
                    "仅支持 " + ModelPackageContract.SCHEMA_VERSION,
                    "使用兼容生成器重新生成"
                )
            );
        }
        if (blank(modelPackage.packageId()) || !PACKAGE_ID.matcher(modelPackage.packageId()).matches()) {
            issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", "$.packageId", "packageId 非法或为空", "使用稳定小写业务标识"));
        }
        if (modelPackage.dbt() == null || blank(modelPackage.dbt().projectName()) || blank(modelPackage.dbt().manifestVersion())) {
            issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", "$.dbt", "dbt projectName/manifestVersion 不能为空", "补齐 dbt 元数据"));
        }
        if (modelPackage.defaults() == null) {
            issues.add(schemaIssue("$.defaults", "必须是对象且不能为 null"));
        }
        if (modelPackage.sources() == null) {
            issues.add(schemaIssue("$.sources", "必须是数组且不能为 null"));
        }
        if (modelPackage.technicalNodes() == null) {
            issues.add(schemaIssue("$.technicalNodes", "必须是数组且不能为 null"));
        }
        if (modelPackage.models() == null) {
            issues.add(schemaIssue("$.models", "必须是数组且不能为 null"));
        }
        if (modelPackage.issues() == null) {
            issues.add(schemaIssue("$.issues", "必须是数组且不能为 null"));
        }
        if (modelPackage.packageChecksum() == null || !SHA_256.matcher(modelPackage.packageChecksum()).matches()) {
            issues.add(issue("MODEL_PACKAGE_CHECKSUM_MISMATCH", "$.packageChecksum", "packageChecksum 不是 SHA-256", "重新生成 checksum"));
        } else if (!modelPackage.packageChecksum().equals(ModelPackageChecksum.compute(modelPackage))) {
            issues.add(issue("MODEL_PACKAGE_CHECKSUM_MISMATCH", "$.packageChecksum", "packageChecksum 与规范化内容不一致", "重新生成模型包"));
        }

        List<SourceNode> sources = safe(modelPackage.sources());
        List<TechnicalNode> technicalNodes = safe(modelPackage.technicalNodes());
        List<PackageModel> models = safe(modelPackage.models());
        Set<String> allIds = new HashSet<>();
        sources.forEach(source -> validateNodeId(source == null ? null : source.dbtUniqueId(), "$.sources", allIds, issues));
        technicalNodes.forEach(node -> validateNodeId(node == null ? null : node.dbtUniqueId(), "$.technicalNodes", allIds, issues));
        models.forEach(model -> validateNodeId(model == null ? null : model.dbtUniqueId(), "$.models", allIds, issues));

        for (SourceNode source : sources) {
            if (source == null) {
                issues.add(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$.sources", "source 不能为空", "删除空节点"));
                continue;
            }
            validatePath(source.resourcePath(), "$.sources[" + source.dbtUniqueId() + "].resourcePath", issues);
            if (source.columns() == null) {
                issues.add(schemaIssue("$.sources[" + source.dbtUniqueId() + "].columns", "必须是数组且不能为 null"));
            }
            validateColumns(source.columns(), "$.sources[" + source.dbtUniqueId() + "].columns", issues);
        }
        for (TechnicalNode node : technicalNodes) {
            if (node == null) {
                issues.add(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$.technicalNodes", "technicalNode 不能为空", "删除空节点"));
                continue;
            }
            validatePath(node.resourcePath(), "$.technicalNodes[" + node.dbtUniqueId() + "].resourcePath", issues);
            validateSql(node.sql(), "$.technicalNodes[" + node.dbtUniqueId() + "].sql", false, issues);
            if (node.config() == null || node.dependencies() == null || node.tags() == null || node.conversion() == null) {
                issues.add(
                    schemaIssue(
                        "$.technicalNodes[" + node.dbtUniqueId() + "]",
                        "config/dependencies/tags/conversion 必须存在且不能为 null"
                    )
                );
            }
            validateDependencies(node.dependencies(), node.dbtUniqueId(), allIds, issues);
        }
        for (PackageModel model : models) {
            if (model == null) {
                issues.add(issue("MODEL_PACKAGE_SCHEMA_INVALID", "$.models", "model 不能为空", "删除空节点"));
                continue;
            }
            validatePath(model.resourcePath(), "$.models[" + model.dbtUniqueId() + "].resourcePath", issues);
            validateSql(model.sql(), "$.models[" + model.dbtUniqueId() + "].sql", true, issues);
            if (
                model.config() == null ||
                model.tags() == null ||
                model.columns() == null ||
                model.tests() == null ||
                model.dependencies() == null ||
                model.conversion() == null
            ) {
                issues.add(
                    schemaIssue(
                        "$.models[" + model.dbtUniqueId() + "]",
                        "config/tags/columns/tests/dependencies/conversion 必须存在且不能为 null"
                    )
                );
            }
            validateColumns(model.columns(), "$.models[" + model.dbtUniqueId() + "].columns", issues);
            validateDependencies(model.dependencies(), model.dbtUniqueId(), allIds, issues);
            validateDimensionDefinitionCode(model.semantics(), model.dbtUniqueId(), issues);
            validateDimensionDefinitionBlueprint(model, issues);
            if (model.conversion() == null || model.conversion().mode() == null) {
                issues.add(
                    issue(
                        "MODEL_PACKAGE_REQUIRED_FIELD_MISSING",
                        "$.models[" + model.dbtUniqueId() + "].conversion",
                        "转换结论不能为空",
                        "重新运行分类器"
                    )
                );
            }
        }
        return List.copyOf(issues);
    }

    private static void validateNodeId(String id, String path, Set<String> allIds, List<ValidationIssue> issues) {
        if (blank(id)) {
            issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", path, "dbtUniqueId 不能为空", "补齐 manifest unique_id"));
        } else if (!allIds.add(id)) {
            issues.add(issue("MODEL_PACKAGE_DUPLICATE_UNIQUE_ID", path, "dbtUniqueId 重复：" + id, "删除重复节点"));
        }
    }

    private static void validatePath(String value, String fieldPath, List<ValidationIssue> issues) {
        if (blank(value) || value.contains("\\") || value.startsWith("/")) {
            issues.add(issue("MODEL_PACKAGE_PATH_INVALID", fieldPath, "resourcePath 必须是安全相对路径", "使用 dbt 项目内 POSIX 路径"));
            return;
        }
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || path.normalize().startsWith("..") || !path.normalize().toString().replace('\\', '/').equals(value)) {
                issues.add(issue("MODEL_PACKAGE_PATH_INVALID", fieldPath, "resourcePath 包含越界或非规范路径", "移除 .、.. 或绝对路径"));
            }
        } catch (InvalidPathException exception) {
            issues.add(issue("MODEL_PACKAGE_PATH_INVALID", fieldPath, "resourcePath 无效", "使用 dbt 项目内 POSIX 路径"));
        }
    }

    private static void validateColumns(List<Column> columns, String fieldPath, List<ValidationIssue> issues) {
        Set<String> names = new HashSet<>();
        for (Column column : safe(columns)) {
            if (column == null || blank(column.name())) {
                issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", fieldPath, "column.name 不能为空", "补齐字段名"));
            } else if (!names.add(column.name())) {
                issues.add(issue("MODEL_PACKAGE_SCHEMA_INVALID", fieldPath, "字段重复：" + column.name(), "删除重复字段"));
            }
        }
    }

    private static void validateDependencies(
        List<String> dependencies,
        String modelUniqueId,
        Set<String> allIds,
        List<ValidationIssue> issues
    ) {
        for (String dependency : safe(dependencies)) {
            if (!allIds.contains(dependency)) {
                issues.add(
                    issue(
                        "MODEL_PACKAGE_DEPENDENCY_MISSING",
                        "$.nodes[" + modelUniqueId + "].dependencies",
                        "依赖未进入模型包：" + dependency,
                        "重新生成并保留完整依赖闭包"
                    )
                );
            }
        }
    }

    private static void validateDimensionDefinitionCode(JsonNode semantics, String path, List<ValidationIssue> issues) {
        if (semantics == null || !semantics.has("dimensionDefinitionCode") || semantics.path("dimensionDefinitionCode").isNull()) {
            return;
        }
        String code = text(semantics.path("dimensionDefinitionCode"));
        if (!ModelPackageContract.isDimensionDefinitionCode(code)) {
            issues.add(
                issue(
                    "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_INVALID",
                    path + ".dimensionDefinitionCode",
                    "dimensionDefinitionCode 必须匹配 modeling_dimension_definition.system_code",
                    "使用形如 dim_ 加 32 位小写十六进制字符的 system_code"
                )
            );
        }
    }

    private static void validateDimensionDefinitionCode(
        ModelPackageContract.SemanticMetadata semantics,
        String modelUniqueId,
        List<ValidationIssue> issues
    ) {
        if (semantics == null || !"DIMENSION".equals(semantics.modelType()) || semantics.technicalOnly()) {
            return;
        }
        String code = semantics.dimensionDefinitionCode();
        if (blank(code)) {
            issues.add(
                issue(
                    "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_REQUIRED",
                    "$.models[" + modelUniqueId + "].semantics.dimensionDefinitionCode",
                    "DIMENSION 模型必须提供跨环境稳定的 dimensionDefinitionCode",
                    "在 meta.dts 或 semantic override 中填写目标维度定义的 system_code"
                )
            );
        } else if (!ModelPackageContract.isDimensionDefinitionCode(code)) {
            issues.add(
                issue(
                    "MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_INVALID",
                    "$.models[" + modelUniqueId + "].semantics.dimensionDefinitionCode",
                    "dimensionDefinitionCode 必须匹配 modeling_dimension_definition.system_code",
                    "使用形如 dim_ 加 32 位小写十六进制字符的 system_code"
                )
            );
        }
    }

    private static void validateDimensionDefinitionBlueprint(PackageModel model, List<ValidationIssue> issues) {
        if (model.semantics() == null || model.semantics().dimensionDefinition() == null) {
            return;
        }
        String path = "$.models[" + model.dbtUniqueId() + "].semantics.dimensionDefinition";
        if (!"DIMENSION".equalsIgnoreCase(model.semantics().modelType()) || model.semantics().technicalOnly()) {
            issues.add(dimensionDefinitionIssue(path, "内嵌维度定义只能用于业务维度模型"));
            return;
        }
        DimensionDefinitionBlueprint definition = model.semantics().dimensionDefinition();
        if (blank(definition.name()) || blank(definition.definition()) || definition.attributes().size() > 200) {
            issues.add(dimensionDefinitionIssue(path, "内嵌维度定义的名称、定义或属性数量无效"));
            return;
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int primaryKeys = 0;
        for (DimensionAttributeBlueprint attribute : definition.attributes()) {
            if (
                attribute == null ||
                blank(attribute.code()) ||
                !SEMANTIC_CODE.matcher(attribute.code()).matches() ||
                blank(attribute.name()) ||
                blank(attribute.definition()) ||
                attribute.order() < 1 ||
                !codes.add(attribute.code()) ||
                !orders.add(attribute.order()) ||
                blank(attribute.standardRef()) != blank(attribute.standardVersion())
            ) {
                issues.add(dimensionDefinitionIssue(path + ".attributes", "维度属性编码、定义、标准绑定或顺序无效"));
                return;
            }
            if (attribute.primaryKey()) {
                primaryKeys++;
            }
        }
        for (int order = 1; order <= definition.attributes().size(); order++) {
            if (!orders.contains(order)) {
                issues.add(dimensionDefinitionIssue(path + ".attributes", "维度属性顺序必须从 1 连续递增"));
                return;
            }
        }
        if (primaryKeys > 1) {
            issues.add(dimensionDefinitionIssue(path + ".attributes", "维度属性最多只能有一个主键"));
            return;
        }
        Set<String> mappedCodes = new HashSet<>();
        for (Column column : safe(model.columns())) {
            if (column != null && !blank(column.dimensionAttributeCode())) {
                mappedCodes.add(column.dimensionAttributeCode());
            }
        }
        if (!mappedCodes.equals(codes)) {
            issues.add(dimensionDefinitionIssue(path + ".attributes", "模型字段映射必须与内嵌维度属性一一对应"));
        }
    }

    private static ValidationIssue dimensionDefinitionIssue(String path, String message) {
        return issue(
            "MODEL_PACKAGE_DIMENSION_DEFINITION_INVALID",
            path,
            message,
            "修正内嵌 dimensionDefinition 蓝图后重新生成模型包"
        );
    }

    private static void validateSql(SqlArtifact sql, String fieldPath, boolean required, List<ValidationIssue> issues) {
        if (sql == null) {
            if (required) {
                issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", fieldPath, "SQL artifact 不能为空", "补齐模型 SQL"));
            }
            return;
        }
        validateSqlChecksum(sql.rawSql(), sql.rawSqlChecksum(), fieldPath + ".rawSqlChecksum", issues);
        validateSqlChecksum(sql.compiledSql(), sql.compiledSqlChecksum(), fieldPath + ".compiledSqlChecksum", issues);
        validateSqlChecksum(sql.effectiveSql(), sql.effectiveSqlChecksum(), fieldPath + ".effectiveSqlChecksum", issues);
        if (required && blank(sql.effectiveSql())) {
            issues.add(issue("MODEL_PACKAGE_REQUIRED_FIELD_MISSING", fieldPath + ".effectiveSql", "effectiveSql 不能为空", "补齐模型 SQL"));
        }
    }

    private static void validateSqlChecksum(String sql, String checksum, String fieldPath, List<ValidationIssue> issues) {
        if (sql == null && checksum == null) {
            return;
        }
        if (sql == null || checksum == null || !checksum.equals(ModelPackageChecksum.sha256Text(sql))) {
            issues.add(issue("MODEL_PACKAGE_SQL_CHECKSUM_MISMATCH", fieldPath, "SQL checksum 不匹配", "重新生成模型包"));
        }
    }

    private static ValidationIssue issue(String code, String path, String message, String recoveryAction) {
        return new ValidationIssue(code, path, message, recoveryAction);
    }

    private static String text(JsonNode node) {
        return node == null || !node.isTextual() || node.asText().isBlank() ? null : node.asText();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class BoundedIssueList extends ArrayList<ValidationIssue> {

        private static final long serialVersionUID = 1L;
        private static final ValidationIssue TRUNCATED = new ValidationIssue(
            VALIDATION_ISSUES_TRUNCATED_CODE,
            "$",
            "模型包校验问题超过 " + MAX_VALIDATION_ISSUES + " 条，后续问题已截断",
            "先修复已返回的问题，再重新提交模型包"
        );

        private boolean truncated;

        @Override
        public boolean add(ValidationIssue issue) {
            if (truncated) {
                return false;
            }
            if (size() < MAX_VALIDATION_ISSUES) {
                return super.add(issue);
            }
            set(MAX_VALIDATION_ISSUES - 1, TRUNCATED);
            truncated = true;
            return false;
        }
    }

    public record ValidationIssue(String code, String fieldPath, String message, String recoveryAction) {}

    public static final class ModelPackageValidationException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final transient List<ValidationIssue> issues;

        public ModelPackageValidationException(List<ValidationIssue> issues) {
            super(issues == null || issues.isEmpty() ? "模型包校验失败" : issues.getFirst().code() + ": " + issues.getFirst().message());
            this.issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public List<ValidationIssue> issues() {
            return issues;
        }
    }
}
