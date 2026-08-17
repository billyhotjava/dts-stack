package com.yuzhi.dts.analytics.service.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AnalysisQuerySpecValidator {

    public static final String API_VERSION = "dts.analysis/v1";
    public static final int DEFAULT_LIMIT = 5000;
    public static final int MAX_LIMIT = 10000;
    private static final int MAX_DERIVED_METRICS = 20;
    private static final int MAX_FILTERS = 50;
    private static final int MAX_ORDER_BY = 10;
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Pattern SAFE_EXPRESSION = Pattern.compile("^[A-Za-z0-9_()+\\-*/.,\\s]+$");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> FUNCTIONS = Set.of("ABS", "ROUND", "COALESCE", "NULLIF", "SUM", "AVG", "MIN", "MAX", "COUNT");
    private static final Set<String> VISUALIZATIONS = Set.of("table", "bar", "line", "area", "pie", "number", "scatter");

    public AnalysisQuerySpec validateAndNormalize(
        AnalysisQuerySpec spec,
        GovernedAnalysisDatasetContract contract
    ) {
        require(spec != null, "ANALYSIS_SPEC_REQUIRED", "$", "analysis spec is required");
        require(API_VERSION.equals(spec.apiVersion()), "ANALYSIS_API_VERSION_UNSUPPORTED", "apiVersion", "apiVersion must be " + API_VERSION);
        require(contract != null && "PUBLISHED".equals(contract.status()), "ANALYSIS_DATASET_NOT_PUBLISHED", "dataset", "dataset contract is not published");
        validateDataset(spec.dataset(), contract);

        List<AnalysisQuerySpec.DimensionSelection> dimensions = copy(spec.dimensions());
        List<AnalysisQuerySpec.MetricSelection> metrics = copy(spec.metrics());
        List<AnalysisQuerySpec.DerivedMetric> derived = copy(spec.derivedMetrics());
        List<AnalysisQuerySpec.FilterSelection> filters = copy(spec.filters());
        List<AnalysisQuerySpec.OrderSelection> orderBy = copy(spec.orderBy());
        require(derived.size() <= MAX_DERIVED_METRICS, "ANALYSIS_DERIVED_METRIC_LIMIT_EXCEEDED", "derivedMetrics", "derived metric count exceeds 20");
        require(filters.size() <= MAX_FILTERS, "ANALYSIS_FILTER_LIMIT_EXCEEDED", "filters", "filter count exceeds 50");
        require(orderBy.size() <= MAX_ORDER_BY, "ANALYSIS_ORDER_LIMIT_EXCEEDED", "orderBy", "order count exceeds 10");

        Map<String, GovernedAnalysisDatasetContract.Dimension> availableDimensions = copy(contract.dimensions())
            .stream()
            .collect(Collectors.toMap(GovernedAnalysisDatasetContract.Dimension::code, value -> value, (left, right) -> left));
        Map<String, GovernedAnalysisDatasetContract.Metric> availableMetrics = copy(contract.metrics())
            .stream()
            .collect(Collectors.toMap(GovernedAnalysisDatasetContract.Metric::code, value -> value, (left, right) -> left));

        Set<String> selectedFields = new HashSet<>();
        for (AnalysisQuerySpec.DimensionSelection dimension : dimensions) {
            require(dimension != null && availableDimensions.containsKey(dimension.field()), "ANALYSIS_DIMENSION_UNKNOWN", "dimensions", "dimension is not in the pinned contract");
            require(selectedFields.add(dimension.field()), "ANALYSIS_FIELD_DUPLICATE", "dimensions", "dimension is duplicated");
        }
        for (AnalysisQuerySpec.MetricSelection metric : metrics) {
            require(metric != null && availableMetrics.containsKey(metric.code()), "ANALYSIS_METRIC_UNKNOWN", "metrics", "metric is not in the pinned contract");
            require(selectedFields.add(metric.code()), "ANALYSIS_FIELD_DUPLICATE", "metrics", "metric is duplicated");
        }

        Set<String> expressionSymbols = new HashSet<>();
        expressionSymbols.addAll(availableDimensions.keySet());
        expressionSymbols.addAll(availableMetrics.keySet());
        Set<String> derivedCodes = new HashSet<>();
        for (AnalysisQuerySpec.DerivedMetric metric : derived) {
            require(metric != null && SAFE_NAME.matcher(text(metric.code())).matches(), "ANALYSIS_DERIVED_METRIC_CODE_INVALID", "derivedMetrics", "derived metric code is invalid");
            require(derivedCodes.add(metric.code()), "ANALYSIS_DERIVED_METRIC_DUPLICATE", "derivedMetrics", "derived metric code is duplicated");
            validateExpression(metric.expression(), expressionSymbols);
            expressionSymbols.add(metric.code());
            selectedFields.add(metric.code());
        }

        for (AnalysisQuerySpec.FilterSelection filter : filters) {
            GovernedAnalysisDatasetContract.Dimension dimension = filter == null ? null : availableDimensions.get(filter.field());
            require(dimension != null, "ANALYSIS_FILTER_FIELD_UNKNOWN", "filters", "filter field is not in the pinned contract");
            String op = upper(filter.op());
            require(StringUtils.hasText(op), "ANALYSIS_FILTER_OPERATOR_REQUIRED", "filters", "filter operator is required");
            require(copy(dimension.filterOps()).stream().map(AnalysisQuerySpecValidator::upper).anyMatch(op::equals), "ANALYSIS_FILTER_OPERATOR_FORBIDDEN", "filters", "filter operator is not allowed by the contract");
        }

        if (spec.timeRange() != null) {
            GovernedAnalysisDatasetContract.Dimension dimension = availableDimensions.get(spec.timeRange().field());
            require(dimension != null, "ANALYSIS_TIME_FIELD_UNKNOWN", "timeRange.field", "time field is not in the pinned contract");
            require(copy(dimension.timeGrains()).stream().anyMatch(grain -> grain.equalsIgnoreCase(text(spec.timeRange().grain()))), "ANALYSIS_TIME_GRAIN_FORBIDDEN", "timeRange.grain", "time grain is not allowed by the contract");
        }
        for (AnalysisQuerySpec.OrderSelection order : orderBy) {
            require(order != null && selectedFields.contains(order.field()), "ANALYSIS_ORDER_FIELD_UNKNOWN", "orderBy", "order field must be selected");
            String direction = upper(order.direction());
            require("ASC".equals(direction) || "DESC".equals(direction), "ANALYSIS_ORDER_DIRECTION_INVALID", "orderBy", "order direction must be ASC or DESC");
        }

        int limit = spec.limit() == null ? DEFAULT_LIMIT : spec.limit();
        require(limit > 0 && limit <= MAX_LIMIT, "ANALYSIS_LIMIT_INVALID", "limit", "limit must be between 1 and 10000");
        AnalysisQuerySpec.Visualization visualization = spec.visualization() == null
            ? new AnalysisQuerySpec.Visualization("table", Map.of())
            : spec.visualization();
        require(VISUALIZATIONS.contains(text(visualization.type()).toLowerCase(Locale.ROOT)), "ANALYSIS_VISUALIZATION_INVALID", "visualization.type", "visualization type is unsupported");

        return new AnalysisQuerySpec(
            API_VERSION,
            spec.dataset(),
            dimensions,
            metrics,
            derived,
            filters,
            spec.timeRange(),
            orderBy,
            limit,
            new AnalysisQuerySpec.Visualization(visualization.type().toLowerCase(Locale.ROOT), visualization.settings() == null ? Map.of() : Map.copyOf(visualization.settings()))
        );
    }

    private void validateDataset(AnalysisQuerySpec.DatasetRef dataset, GovernedAnalysisDatasetContract contract) {
        require(dataset != null && dataset.id() != null, "ANALYSIS_DATASET_REQUIRED", "dataset", "dataset reference is required");
        require(dataset.id().equals(contract.datasetId()), "ANALYSIS_DATASET_CONFLICT", "dataset.id", "dataset id does not match the runtime contract");
        require(dataset.version() == contract.version(), "ANALYSIS_DATASET_VERSION_CONFLICT", "dataset.version", "dataset version does not match the runtime contract");
        require(text(dataset.contractVersion()).equals(contract.contractVersion()), "ANALYSIS_CONTRACT_VERSION_CONFLICT", "dataset.contractVersion", "contract version has changed");
        require(text(dataset.checksum()).equals(contract.contractChecksum()), "ANALYSIS_CONTRACT_CHECKSUM_CONFLICT", "dataset.checksum", "contract checksum has changed");
    }

    private void validateExpression(String expression, Set<String> symbols) {
        String value = text(expression);
        require(StringUtils.hasText(value) && SAFE_EXPRESSION.matcher(value).matches(), "ANALYSIS_EXPRESSION_INVALID", "derivedMetrics.expression", "expression contains unsupported syntax");
        Matcher matcher = IDENTIFIER.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            int cursor = matcher.end();
            while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) cursor++;
            boolean function = cursor < value.length() && value.charAt(cursor) == '(';
            if (function) {
                require(FUNCTIONS.contains(token.toUpperCase(Locale.ROOT)), "ANALYSIS_EXPRESSION_FUNCTION_FORBIDDEN", "derivedMetrics.expression", "expression function is not allowed");
            } else {
                require(symbols.contains(token), "ANALYSIS_EXPRESSION_SYMBOL_UNKNOWN", "derivedMetrics.expression", "expression references an unknown symbol");
            }
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private void require(boolean condition, String code, String field, String message) {
        if (!condition) throw new AnalysisSpecValidationException(code, field, message);
    }
}
