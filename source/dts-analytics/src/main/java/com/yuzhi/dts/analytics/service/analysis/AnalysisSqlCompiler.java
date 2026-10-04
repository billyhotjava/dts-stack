package com.yuzhi.dts.analytics.service.analysis;

import com.yuzhi.dts.analytics.service.DatasetQueryService;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AnalysisSqlCompiler {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Pattern EXPRESSION_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> EXPRESSION_FUNCTIONS = Set.of("ABS", "ROUND", "COALESCE", "NULLIF", "SUM", "AVG", "MIN", "MAX", "COUNT");
    private static final Set<String> AGGREGATIONS = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");

    private final AnalysisQuerySpecValidator validator;

    public AnalysisSqlCompiler(AnalysisQuerySpecValidator validator) {
        this.validator = validator;
    }

    public CompiledAnalysisQuery compile(
        AnalysisQuerySpec submitted,
        GovernedAnalysisDatasetContract contract,
        long databaseId,
        List<RowPredicate> rowPredicates
    ) {
        AnalysisQuerySpec spec = validator.validateAndNormalize(submitted, contract);
        if (databaseId <= 0) {
            throw invalid("ANALYSIS_DATABASE_BINDING_INVALID", "dataset", "analytics database binding is invalid");
        }
        String baseSql = normalizeBaseSql(contract.baseSql());
        if (!StringUtils.hasText(baseSql)) {
            throw invalid("ANALYSIS_DATASET_QUERY_MISSING", "dataset", "published dataset query is unavailable");
        }
        if (spec.dimensions().isEmpty() && spec.metrics().isEmpty() && spec.derivedMetrics().isEmpty()) {
            throw invalid("ANALYSIS_SELECTION_REQUIRED", "dimensions", "at least one dimension or metric is required");
        }

        Map<String, GovernedAnalysisDatasetContract.Dimension> dimensions = new HashMap<>();
        for (GovernedAnalysisDatasetContract.Dimension dimension : safe(contract.dimensions())) {
            dimensions.put(dimension.code(), dimension);
        }
        Map<String, GovernedAnalysisDatasetContract.Metric> metrics = new HashMap<>();
        for (GovernedAnalysisDatasetContract.Metric metric : safe(contract.metrics())) {
            metrics.put(metric.code(), metric);
        }

        List<String> selections = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        Map<String, String> outputAliases = new LinkedHashMap<>();
        for (AnalysisQuerySpec.DimensionSelection selected : spec.dimensions()) {
            String field = identifier(selected.field(), "dimensions.field");
            String alias = alias(selected.alias(), field, "dimensions.alias");
            String expression = "governed_dataset." + quote(field);
            selections.add(expression + " AS " + quote(alias));
            groups.add(expression);
            outputAliases.put(field, alias);
        }
        for (AnalysisQuerySpec.MetricSelection selected : spec.metrics()) {
            GovernedAnalysisDatasetContract.Metric metric = metrics.get(selected.code());
            String code = identifier(selected.code(), "metrics.code");
            String alias = alias(selected.alias(), code, "metrics.alias");
            selections.add(metricExpression(metric) + " AS " + quote(alias));
            outputAliases.put(code, alias);
        }

        List<Object> bindings = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        for (AnalysisQuerySpec.FilterSelection filter : spec.filters()) {
            predicates.add(compileFilter(filter.field(), filter.op(), filter.values(), dimensions, bindings, "filters"));
        }
        if (spec.timeRange() != null) {
            AnalysisQuerySpec.TimeRange range = spec.timeRange();
            String field = identifier(range.field(), "timeRange.field");
            if (StringUtils.hasText(range.start())) {
                predicates.add("governed_dataset." + quote(field) + " >= ?");
                bindings.add(range.start());
            }
            if (StringUtils.hasText(range.end())) {
                predicates.add("governed_dataset." + quote(field) + " <= ?");
                bindings.add(range.end());
            }
        }
        for (RowPredicate rowPredicate : safe(rowPredicates)) {
            predicates.add(compileFilter(
                rowPredicate.field(),
                rowPredicate.operator(),
                rowPredicate.values(),
                dimensions,
                bindings,
                "policy"
            ));
        }

        StringBuilder inner = new StringBuilder("SELECT ").append(String.join(", ", selections));
        inner.append(" FROM (").append(baseSql).append(") governed_dataset");
        if (!predicates.isEmpty()) inner.append(" WHERE ").append(String.join(" AND ", predicates));
        if (!spec.metrics().isEmpty() && !groups.isEmpty()) inner.append(" GROUP BY ").append(String.join(", ", groups));

        String sql = inner.toString();
        if (!spec.derivedMetrics().isEmpty()) {
            List<String> outerSelections = new ArrayList<>();
            outputAliases.values().forEach(value -> outerSelections.add("analysis_base." + quote(value)));
            for (AnalysisQuerySpec.DerivedMetric derived : spec.derivedMetrics()) {
                String code = identifier(derived.code(), "derivedMetrics.code");
                outerSelections.add(compileDerivedExpression(derived.expression(), outputAliases) + " AS " + quote(code));
                outputAliases.put(code, code);
            }
            sql = "SELECT " + String.join(", ", outerSelections) + " FROM (" + sql + ") analysis_base";
        }

        if (!spec.orderBy().isEmpty()) {
            List<String> order = spec.orderBy()
                .stream()
                .map(item -> {
                    String alias = outputAliases.get(item.field());
                    if (alias == null) alias = identifier(item.field(), "orderBy.field");
                    String direction = "DESC".equalsIgnoreCase(item.direction()) ? "DESC" : "ASC";
                    return quote(alias) + " " + direction;
                })
                .toList();
            sql += " ORDER BY " + String.join(", ", order);
        }
        int requestedLimit = spec.limit();
        int executionLimit = Math.min(AnalysisQuerySpecValidator.MAX_LIMIT + 1, requestedLimit + 1);
        sql += " LIMIT " + executionLimit;
        DatasetQueryService.DatasetConstraints constraints = new DatasetQueryService.DatasetConstraints(
            executionLimit,
            30,
            ZoneId.systemDefault().getId()
        );
        return new CompiledAnalysisQuery(databaseId, sql, List.copyOf(bindings), constraints, requestedLimit, spec);
    }

    private String compileFilter(
        String fieldValue,
        String operatorValue,
        List<?> values,
        Map<String, GovernedAnalysisDatasetContract.Dimension> dimensions,
        List<Object> bindings,
        String path
    ) {
        String field = identifier(fieldValue, path + ".field");
        if (!dimensions.containsKey(field)) {
            throw invalid("ANALYSIS_FILTER_FIELD_UNKNOWN", path + ".field", "filter field is not in the pinned contract");
        }
        String column = "governed_dataset." + quote(field);
        String operator = operatorValue == null ? "" : operatorValue.trim().toUpperCase(Locale.ROOT);
        List<?> safeValues = values == null ? List.of() : values;
        return switch (operator) {
            case "EQ" -> singleValue(column + " = ?", safeValues, bindings, path);
            case "NE" -> singleValue(column + " <> ?", safeValues, bindings, path);
            case "IN", "NOT_IN" -> {
                if (safeValues.isEmpty() || safeValues.size() > 1000) {
                    throw invalid("ANALYSIS_FILTER_VALUES_INVALID", path + ".values", "IN filter needs between 1 and 1000 values");
                }
                bindings.addAll(safeValues);
                String placeholders = String.join(", ", java.util.Collections.nCopies(safeValues.size(), "?"));
                yield column + ("NOT_IN".equals(operator) ? " NOT IN (" : " IN (") + placeholders + ")";
            }
            case "IS_NULL" -> column + " IS NULL";
            case "IS_NOT_NULL" -> column + " IS NOT NULL";
            default -> throw invalid("ANALYSIS_FILTER_OPERATOR_FORBIDDEN", path + ".op", "filter operator is unsupported");
        };
    }

    private String singleValue(String sql, List<?> values, List<Object> bindings, String path) {
        if (values.size() != 1) {
            throw invalid("ANALYSIS_FILTER_VALUES_INVALID", path + ".values", "filter requires exactly one value");
        }
        bindings.add(values.get(0));
        return sql;
    }

    private String metricExpression(GovernedAnalysisDatasetContract.Metric metric) {
        if (metric == null) throw invalid("ANALYSIS_METRIC_UNKNOWN", "metrics", "metric is unavailable");
        String aggregation = metric.aggregation() == null ? "" : metric.aggregation().trim().toUpperCase(Locale.ROOT);
        if (!AGGREGATIONS.contains(aggregation)) {
            throw invalid("ANALYSIS_METRIC_AGGREGATION_FORBIDDEN", "metrics", "metric aggregation is unsupported");
        }
        String expression = metric.expression() == null ? "" : metric.expression().trim();
        if ("COUNT".equals(aggregation) && "*".equals(expression)) return "COUNT(*)";
        return aggregation + "(governed_dataset." + quote(identifier(expression, "metrics.expression")) + ")";
    }

    private String compileDerivedExpression(String expression, Map<String, String> aliases) {
        Matcher matcher = EXPRESSION_TOKEN.matcher(expression == null ? "" : expression);
        StringBuffer compiled = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group();
            int cursor = matcher.end();
            while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) cursor++;
            boolean function = cursor < expression.length() && expression.charAt(cursor) == '(';
            String replacement;
            if (function && EXPRESSION_FUNCTIONS.contains(token.toUpperCase(Locale.ROOT))) {
                replacement = token.toUpperCase(Locale.ROOT);
            } else {
                String alias = aliases.get(token);
                if (alias == null) {
                    throw invalid("ANALYSIS_EXPRESSION_SYMBOL_UNKNOWN", "derivedMetrics.expression", "expression symbol is unavailable");
                }
                replacement = "analysis_base." + quote(alias);
            }
            matcher.appendReplacement(compiled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(compiled);
        return compiled.toString();
    }

    private String normalizeBaseSql(String value) {
        if (value == null) return null;
        String result = value.trim();
        while (result.endsWith(";")) result = result.substring(0, result.length() - 1).trim();
        return result;
    }

    private String alias(String value, String fallback, String field) {
        return identifier(StringUtils.hasText(value) ? value.trim() : fallback, field);
    }

    private String identifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw invalid("ANALYSIS_IDENTIFIER_UNSAFE", field, "identifier is not safe");
        }
        return value;
    }

    private String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private AnalysisSpecValidationException invalid(String code, String field, String message) {
        return new AnalysisSpecValidationException(code, field, message);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record RowPredicate(String field, String operator, List<Object> values) {}

    public record CompiledAnalysisQuery(
        long databaseId,
        String sql,
        List<Object> bindings,
        DatasetQueryService.DatasetConstraints constraints,
        int requestedLimit,
        AnalysisQuerySpec normalizedSpec
    ) {}
}
