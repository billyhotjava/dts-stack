package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stateless builder for the candidate dbt artifacts produced from a governed semantic graph draft.
 *
 * <p>Extracted from {@link MetricModelLifecycleService} (F5) so the lifecycle service stays focused on state
 * transitions/persistence while the SQL/YAML generation lives in one cohesive place. The only collaborator is
 * {@link MetricSecurityPolicyService}: the platform RLS/masking policy is injected into the generated SQL
 * exactly as the metric-pack path does (F2-T03). For an empty policy every injection is a no-op, so a model
 * with no platform policy renders byte-identical SQL — this is the invariant the golden-SQL tests assert.
 */
@Component
public class MetricCandidateArtifactBuilder {

    private static final Pattern UNSAFE_EXPRESSION = Pattern.compile("(?i)(;|--|/\\*|\\*/|\\bselect\\b|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b)");

    private final MetricSecurityPolicyService securityPolicyService;

    public MetricCandidateArtifactBuilder(MetricSecurityPolicyService securityPolicyService) {
        this.securityPolicyService = securityPolicyService;
    }

    /**
     * Build the candidate artifact bundle (dbt model SQL, schema/exposure YAML, metric doc, lineage hint,
     * masking macro + security policy/snapshot) for the resolved platform policy. Throws
     * {@link IllegalArgumentException} when a platform-masked column is used inside a metric formula; the
     * caller maps that to 422 (F2-T03 parity with the pack path).
     */
    public Map<String, Object> build(
        String modelName,
        Map<String, Object> graph,
        PlatformContractClient.RlsPolicyResult policy,
        String policySource,
        String predicateHash
    ) {
        List<String> dimensions = identifiers(graph.get("dimensions"));
        List<String> measures = identifiers(graph.get("measures"));
        List<Map<String, Object>> derivedMetrics = maps(graph.get("derived_metrics"));
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            rejectUnsafeExpression(text(derivedMetric.get("expression")));
        }
        String dialect = dialect(graph);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("dbtModelSql", dbtModelSql(modelName, MetricIdentifiers.safeIdentifier(text(graph.get("base"))), dimensions, measures, derivedMetrics, dialect, policy));
        artifacts.put("schemaYml", schemaYml(modelName, dimensions, measures, derivedMetrics));
        artifacts.put("exposureYml", exposureYml(modelName));
        artifacts.put("metricDoc", metricDoc(modelName, dimensions, measures, derivedMetrics));
        artifacts.put("lineageHint", Map.of("upstreamAsset", text(graph.get("base")), "model", modelName, "targetLayer", targetLayer(graph)));
        if (securityPolicyService.hasMaskedColumns(policy)) {
            artifacts.put("maskingMacroSql", securityPolicyService.maskingMacroSql());
        }
        artifacts.put("securityPolicyJson", securityPolicyService.securityPolicyJson(policy));
        artifacts.put("securitySnapshot", securitySnapshot(graph, policySource, predicateHash));
        return artifacts;
    }

    private static Map<String, Object> securitySnapshot(Map<String, Object> graph, String policySource, String predicateHash) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policySource", policySource);
        snapshot.put("predicateHash", predicateHash);
        snapshot.put("classification", firstText(graph.get("classification"), "INTERNAL"));
        snapshot.put("targetLayer", targetLayer(graph));
        snapshot.put("maskingRequired", true);
        snapshot.put("generatedAt", Instant.now().toString());
        return snapshot;
    }

    /**
     * Build the candidate dbt model SQL, injecting the platform RLS/masking policy exactly as the metric-pack
     * path does (F2-T03): masked dimensions are wrapped in the masking macro, a masked column used as a metric
     * is rejected, and a platform RLS {@code where} block is appended. For an empty policy every injection is a
     * no-op, so a model with no platform policy renders byte-identical SQL.
     */
    private String dbtModelSql(
        String modelName,
        String base,
        List<String> dimensions,
        List<String> measures,
        List<Map<String, Object>> derivedMetrics,
        String dialect,
        PlatformContractClient.RlsPolicyResult policy
    ) {
        Set<String> maskedColumns = securityPolicyService.maskedColumns(policy);
        securityPolicyService.validateMaskedMetricInputs(metricExpressions(measures, derivedMetrics), maskedColumns);
        List<String> selectRows = new ArrayList<>();
        List<String> groupExpressions = new ArrayList<>();
        for (String dimension : dimensions) {
            if (maskedColumns.contains(dimension.toLowerCase(Locale.ROOT))) {
                String masked = securityPolicyService.maskDimensionExpression(dimension, maskedColumns);
                selectRows.add("    " + masked + " as " + quoteIdentifier(dimension, dialect));
                groupExpressions.add(masked);
            } else {
                String quoted = quoteIdentifier(dimension, dialect);
                selectRows.add("    " + quoted);
                groupExpressions.add(quoted);
            }
        }
        for (String measure : measures) {
            selectRows.add("    sum(" + quoteIdentifier(measure, dialect) + ") as " + quoteIdentifier(measure, dialect));
        }
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            String id = MetricIdentifiers.safeIdentifier(text(derivedMetric.get("id")));
            String expression = compileDerivedExpression(text(derivedMetric.get("expression")), dialect);
            selectRows.add("    (" + expression + ") as " + quoteIdentifier(id, dialect));
        }
        if (selectRows.isEmpty()) {
            selectRows.add("    1 as metric_ready");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("{{ config(materialized='table', tags=['dts-metrics', 'sprint-35-candidate']) }}\n\n");
        sql.append("-- Candidate artifact generated from a governed graph draft. Publish only through platform/dbt gate.\n");
        sql.append("select\n");
        sql.append(String.join(",\n", selectRows));
        sql.append("\nfrom {{ ref('").append(base).append("') }}\n");
        securityPolicyService.appendRlsWhere(sql, policy);
        if (!groupExpressions.isEmpty()) {
            sql.append("group by\n");
            for (int i = 0; i < groupExpressions.size(); i++) {
                sql.append("    ").append(groupExpressions.get(i));
                sql.append(i + 1 < groupExpressions.size() ? ",\n" : "\n");
            }
        }
        return sql.toString();
    }

    private static List<MetricSecurityPolicyService.MetricExpression> metricExpressions(List<String> measures, List<Map<String, Object>> derivedMetrics) {
        List<MetricSecurityPolicyService.MetricExpression> expressions = new ArrayList<>();
        for (String measure : measures) {
            expressions.add(new MetricSecurityPolicyService.MetricExpression(measure, measure));
        }
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            expressions.add(
                new MetricSecurityPolicyService.MetricExpression(
                    MetricIdentifiers.safeIdentifier(text(derivedMetric.get("id"))),
                    text(derivedMetric.get("expression"))
                )
            );
        }
        return expressions;
    }

    private static String schemaYml(String modelName, List<String> dimensions, List<String> measures, List<Map<String, Object>> derivedMetrics) {
        StringBuilder yml = new StringBuilder();
        yml.append("version: 2\n\nmodels:\n");
        yml.append("  - name: ").append(modelName).append("\n");
        yml.append("    description: Sprint-35 candidate model generated by dts-metrics.\n");
        yml.append("    columns:\n");
        for (String dimension : dimensions) {
            yml.append("      - name: ").append(dimension).append("\n");
            yml.append("        tests:\n          - not_null\n");
        }
        for (String measure : measures) {
            yml.append("      - name: ").append(measure).append("\n");
            yml.append("        description: Aggregated metric candidate.\n");
        }
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            yml.append("      - name: ").append(MetricIdentifiers.safeIdentifier(text(derivedMetric.get("id")))).append("\n");
            yml.append("        description: Derived metric candidate.\n");
        }
        return yml.toString();
    }

    private static String exposureYml(String modelName) {
        return "version: 2\n\nexposures:\n  - name: " + modelName + "_bi_dataset\n    type: dashboard\n    depends_on:\n      - ref('" + modelName + "')\n";
    }

    private static String metricDoc(String modelName, List<String> dimensions, List<String> measures, List<Map<String, Object>> derivedMetrics) {
        return "# " + modelName + "\n\nDimensions: " + dimensions + "\n\nMeasures: " + measures + "\n\nDerived: " + derivedMetrics.size() + "\n";
    }

    private static String targetLayer(Map<String, Object> graph) {
        for (Map<String, Object> node : maps(graph.get("nodes"))) {
            String layer = text(node.get("warehouseLayer")).toUpperCase(Locale.ROOT);
            if ("ADS".equals(layer)) {
                return "ADS";
            }
        }
        return "DWS";
    }

    private static List<String> identifiers(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("id");
                if (id != null) {
                    result.add(MetricIdentifiers.safeIdentifier(text(id)));
                }
                continue;
            }
            result.add(MetricIdentifiers.safeIdentifier(text(item)));
        }
        return result.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private static void rejectUnsafeExpression(String expression) {
        if (UNSAFE_EXPRESSION.matcher(expression).find()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
    }

    private static String compileDerivedExpression(String expression, String dialect) {
        rejectUnsafeExpression(expression);
        int start = expression.indexOf('(');
        int end = expression.lastIndexOf(')');
        if (start <= 0 || end <= start) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
        String function = expression.substring(0, start).trim().toLowerCase(Locale.ROOT);
        List<String> args = splitArgs(expression.substring(start + 1, end));
        return switch (function) {
            case "sum", "count", "avg", "min", "max" -> function + "(" + quoteIdentifier(requiredArg(args, 0), dialect) + ")";
            case "count_distinct" -> "count(distinct " + quoteIdentifier(requiredArg(args, 0), dialect) + ")";
            case "ratio" ->
                "sum(" +
                quoteIdentifier(requiredArg(args, 0), dialect) +
                ") / nullif(sum(" +
                quoteIdentifier(requiredArg(args, 1), dialect) +
                "), 0)";
            case "date_trunc" -> dateTruncSql(requiredArg(args, 0), requiredArg(args, 1), dialect);
            case "count_if" -> "sum(case when " + conditionSql(requiredArg(args, 0), requiredArg(args, 1), requiredArg(args, 2), dialect) + " then 1 else 0 end)";
            case "sum_if" ->
                "sum(case when " +
                conditionSql(requiredArg(args, 1), requiredArg(args, 2), requiredArg(args, 3), dialect) +
                " then " +
                quoteIdentifier(requiredArg(args, 0), dialect) +
                " else 0 end)";
            case "case_when" ->
                "case when " +
                conditionSql(requiredArg(args, 0), requiredArg(args, 1), requiredArg(args, 2), dialect) +
                " then " +
                literalSql(requiredArg(args, 3)) +
                " else " +
                literalSql(requiredArg(args, 4)) +
                " end";
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        };
    }

    private static String dateTruncSql(String grain, String field, String dialect) {
        String safeGrain = MetricIdentifiers.safeIdentifier(grain).toLowerCase(Locale.ROOT);
        if ("doris".equals(dialect)) {
            return "date_trunc(" + quoteIdentifier(field, dialect) + ", '" + safeGrain + "')";
        }
        return "date_trunc('" + safeGrain + "', " + quoteIdentifier(field, dialect) + ")";
    }

    private static String conditionSql(String field, String op, String value, String dialect) {
        return quoteIdentifier(field, dialect) + " " + comparisonOperator(op) + " " + literalSql(value);
    }

    private static String comparisonOperator(String op) {
        return switch (op.toLowerCase(Locale.ROOT)) {
            case "eq" -> "=";
            case "ne" -> "<>";
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        };
    }

    private static String literalSql(String value) {
        String literal = text(value);
        if (literal.matches("-?\\d+(\\.\\d+)?")) {
            return literal;
        }
        return "'" + literal.replace("'", "''") + "'";
    }

    private static List<String> splitArgs(String value) {
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            result.add(item.trim());
        }
        return result;
    }

    private static String requiredArg(List<String> args, int index) {
        if (index >= args.size() || !StringUtils.hasText(args.get(index))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
        return args.get(index);
    }

    private static String quoteIdentifier(String value, String dialect) {
        String identifier = MetricIdentifiers.safeIdentifier(value);
        if (!StringUtils.hasText(identifier)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
        String quote = "doris".equals(dialect) ? "`" : "\"";
        return quote + identifier + quote;
    }

    private static String dialect(Map<String, Object> graph) {
        String dialect = text(graph.get("dialect")).toLowerCase(Locale.ROOT);
        return "doris".equals(dialect) ? "doris" : "postgres";
    }

    // ---- Local read helpers (graph map traversal) ----

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(value)) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                result.add(typed);
            }
        }
        return result;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
