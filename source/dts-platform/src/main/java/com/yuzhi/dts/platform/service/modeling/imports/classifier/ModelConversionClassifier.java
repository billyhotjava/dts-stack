package com.yuzhi.dts.platform.service.modeling.imports.classifier;

import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conservative conversion classifier. It recognizes only a deliberately small designer-safe
 * SQL subset and never derives business model type, layer or grain from a dbt name or path.
 */
public final class ModelConversionClassifier {

    private static final Set<String> DESIGNER_MATERIALIZATIONS = Set.of("table", "view", "incremental");
    private static final Pattern AGGREGATION = Pattern.compile("\\b(sum|count|avg|min|max|array_agg|string_agg)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOW = Pattern.compile("\\b(over\\s*\\(|row_number\\s*\\(|rank\\s*\\(|dense_rank\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASE = Pattern.compile("\\bcase\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUES = Pattern.compile("\\bvalues\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MACRO = Pattern.compile("\\{[{%]");
    private static final Pattern STRING_CONSTANT = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern TYPE_PRECISION = Pattern.compile(
        "\\b(numeric|decimal|dec|varchar|char|character|timestamp|time|bit|varbit)\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_STRING_CONSTANT = Pattern.compile(
        "(?<![a-z0-9_])(?:0x[0-9a-f]+|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:e[+\\-]?\\d+)?|true|false|null)(?![a-z0-9_])",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ARITHMETIC = Pattern.compile(
        "(?<=[a-z0-9_.)])\\s*(?:[+\\-/%]|\\*(?!\\s*\\bfrom\\b))\\s*(?=[a-z0-9_(.])",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FUNCTION_CALL = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSERVATIVE_COMPLEXITY = Pattern.compile(
        "\\b(with|union|intersect|except|lateral)\\b|\\(\\s*select\\b|::|\\s[+/\\-]\\s",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> EXPLAINED_FUNCTIONS = Set.of(
        "cast",
        "sum",
        "count",
        "avg",
        "min",
        "max",
        "array_agg",
        "string_agg",
        "row_number",
        "rank",
        "dense_rank"
    );

    public ConversionResult classify(ClassificationInput input) {
        if (input == null) {
            return new ConversionResult(ConversionMode.BLOCKED, List.of("MISSING_MODEL_TYPE"));
        }
        String resourceType = normalized(input.resourceType());
        String materialization = normalized(input.materialization());
        SemanticMetadata semantics = input.semantics();

        if ("macro".equals(resourceType) || "test".equals(resourceType)) {
            return new ConversionResult(ConversionMode.TECHNICAL_ONLY, List.of("TECHNICAL_RESOURCE"));
        }
        if ("ephemeral".equals(materialization)) {
            return new ConversionResult(ConversionMode.TECHNICAL_ONLY, List.of("EPHEMERAL_MATERIALIZATION"));
        }
        if (input.explicitTechnical() || (semantics != null && (semantics.technicalOnly() || "STG".equalsIgnoreCase(semantics.modelType())))) {
            return new ConversionResult(ConversionMode.TECHNICAL_ONLY, List.of("EXPLICIT_TECHNICAL_SEMANTICS"));
        }

        List<String> missing = missingSemantics(semantics);
        if (!missing.isEmpty()) {
            return new ConversionResult(ConversionMode.BLOCKED, missing);
        }

        List<String> complexReasons = new ArrayList<>();
        String sql = input.sql() == null ? "" : input.sql();
        boolean hasStringConstant = STRING_CONSTANT.matcher(sql).find();
        String lexicalSql = stripComments(STRING_CONSTANT.matcher(sql).replaceAll("''"));
        String literalScanSql = TYPE_PRECISION.matcher(lexicalSql).replaceAll("$1");
        if (AGGREGATION.matcher(sql).find()) {
            complexReasons.add("SQL_AGGREGATION");
        }
        if (WINDOW.matcher(sql).find()) {
            complexReasons.add("SQL_WINDOW");
        }
        if (CASE.matcher(sql).find()) {
            complexReasons.add("SQL_CASE");
        }
        if (VALUES.matcher(sql).find()) {
            complexReasons.add("SQL_VALUES");
        }
        if (MACRO.matcher(sql).find()) {
            complexReasons.add("SQL_MACRO");
        }
        if (hasStringConstant || NON_STRING_CONSTANT.matcher(literalScanSql).find()) {
            complexReasons.add("SQL_CONSTANT");
        }
        if (ARITHMETIC.matcher(literalScanSql).find()) {
            complexReasons.add("SQL_ARITHMETIC");
        }
        if (materialization != null && !DESIGNER_MATERIALIZATIONS.contains(materialization)) {
            complexReasons.add("CUSTOM_MATERIALIZATION");
        }
        var functions = FUNCTION_CALL.matcher(literalScanSql);
        boolean unsupportedFunction = false;
        while (functions.find()) {
            if (!EXPLAINED_FUNCTIONS.contains(functions.group(1).toLowerCase(Locale.ROOT))) {
                unsupportedFunction = true;
                break;
            }
        }
        if (unsupportedFunction || CONSERVATIVE_COMPLEXITY.matcher(literalScanSql).find()) {
            complexReasons.add("SQL_COMPLEX_EXPRESSION");
        }
        if (!complexReasons.isEmpty()) {
            return new ConversionResult(ConversionMode.DBT_BACKED, List.copyOf(complexReasons));
        }
        return new ConversionResult(ConversionMode.DESIGNER_GENERATED, List.of("SAFE_SQL_SUBSET"));
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--[^\\r\\n]*", " ");
    }

    private static List<String> missingSemantics(SemanticMetadata semantics) {
        List<String> missing = new ArrayList<>();
        if (semantics == null || blank(semantics.modelType())) {
            missing.add("MISSING_MODEL_TYPE");
        }
        if (semantics == null || blank(semantics.layer())) {
            missing.add("MISSING_LAYER");
        }
        if (
            semantics == null ||
            semantics.grain() == null ||
            blank(semantics.grain().statement()) ||
            semantics.grain().keys() == null ||
            semantics.grain().keys().isEmpty()
        ) {
            missing.add("MISSING_GRAIN");
        }
        boolean staticDimension = semantics != null &&
        "DIMENSION".equalsIgnoreCase(semantics.modelType()) &&
        !blank(semantics.dimensionStrategy()) &&
        !blank(semantics.dimensionDefinitionCode());
        if (
            (semantics == null || semantics.sourceRefs() == null || semantics.sourceRefs().isEmpty()) &&
            !staticDimension
        ) {
            missing.add("MISSING_SOURCE");
        }
        if (
            semantics != null &&
            "APPLICATION".equalsIgnoreCase(semantics.modelType()) &&
            (semantics.consumptionScenarios() == null || semantics.consumptionScenarios().isEmpty())
        ) {
            missing.add("MISSING_CONSUMPTION_SCENARIO");
        }
        return List.copyOf(missing);
    }

    private static String normalized(String value) {
        return blank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ClassificationInput(
        String resourceType,
        String materialization,
        String sql,
        SemanticMetadata semantics,
        boolean explicitTechnical
    ) {}
}
