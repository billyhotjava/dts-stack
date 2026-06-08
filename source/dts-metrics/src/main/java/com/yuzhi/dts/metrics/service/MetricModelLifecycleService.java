package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetricModelLifecycleService {

    private static final Pattern UNSAFE_EXPRESSION = Pattern.compile("(?i)(;|--|/\\*|\\*/|\\bselect\\b|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b)");

    private final MetricGraphDraftService graphDraftService;
    private final PlatformContractClient platformContractClient;
    private final Map<String, Map<String, Object>> modelStates = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> modelVersions = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> rollbackEvents = new ConcurrentHashMap<>();

    public MetricModelLifecycleService(MetricGraphDraftService graphDraftService, PlatformContractClient platformContractClient) {
        this.graphDraftService = graphDraftService;
        this.platformContractClient = platformContractClient;
    }

    public Map<String, Object> generateArtifacts(String modelId, Map<String, Object> request) {
        Map<String, Object> graph = graph(modelId, request);
        Map<String, Object> preflight = graphDraftService.preflightDraft(graph);
        if (!"GRAPH_READY".equals(preflight.get("status"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
        String modelName = safeModelName(firstText(value(request, "modelName"), "dws_" + safeIdentifier(modelId) + "_summary"));
        String policySource = "platform-policy-required";
        String predicateHash = MetricPolicyAuditSupport.predicateHash(List.of(), List.of(), policySource);
        Map<String, Object> artifacts = artifacts(modelName, graph, policySource, predicateHash);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", modelName);
        result.put("status", MetricLifecycleStatus.ARTIFACT_GENERATED.code());
        result.put("artifactRef", "candidate://dts-metrics/" + modelName);
        result.put("graphStatus", preflight.get("status"));
        result.put("diagnostics", preflight.get("diagnostics"));
        result.put("artifacts", artifacts);
        result.put("appliedPolicySource", policySource);
        result.put("appliedPredicateHash", predicateHash);
        result.put("warnings", List.of("Candidate artifacts must pass platform/dbt validation before review or publish."));
        result.put("meta", Map.of("generatedAt", Instant.now().toString(), "source", "dts-metrics model lifecycle"));
        modelStates.put(modelId, result);
        return result;
    }

    public Map<String, Object> validateModel(String modelId, Map<String, Object> request) {
        Map<String, Object> state = artifactState(modelId, request);
        try {
            Map<String, Object> validation = platformContractClient.validateMetricModel(
                new PlatformContractClient.MetricModelValidationRequest(
                    modelId,
                    text(state.get("modelName")),
                    text(state.get("artifactRef")),
                    graph(modelId, request),
                    object(state.get("artifacts")),
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            if (!platformPassed(validation)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.DBT_VALIDATION_FAILED.code());
            }
            Map<String, Object> next = new LinkedHashMap<>(state);
            next.put("status", MetricLifecycleStatus.DBT_VALIDATED.code());
            next.put("platformValidation", validation);
            next.put("validatedAt", Instant.now().toString());
            modelStates.put(modelId, next);
            return next;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    public Map<String, Object> submitReview(String modelId) {
        Map<String, Object> state = requireStatus(modelId, MetricLifecycleStatus.DBT_VALIDATED.code());
        Map<String, Object> next = publicState(state, MetricLifecycleStatus.REVIEW_SUBMITTED.code());
        next.put("reviewReference", "platform-review://" + modelId);
        next.put("submittedAt", Instant.now().toString());
        modelStates.put(modelId, next);
        return next;
    }

    public Map<String, Object> publishDryRun(String modelId) {
        Map<String, Object> state = requireStatus(modelId, MetricLifecycleStatus.DBT_VALIDATED.code());
        try {
            Map<String, Object> releaseGate = platformContractClient.checkDbtReleaseGate(
                new PlatformContractClient.DbtReleaseGateRequest(
                    text(state.get("modelName")),
                    null,
                    null,
                    true,
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            Map<String, Object> next = publicState(state, MetricLifecycleStatus.PUBLISH_DRY_RUN_READY.code());
            next.put("releaseGate", releaseGate);
            next.put("checkedAt", Instant.now().toString());
            modelStates.put(modelId, next);
            return next;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    public Map<String, Object> publish(String modelId) {
        Map<String, Object> state = requireStatus(
            modelId,
            MetricLifecycleStatus.DBT_VALIDATED.code(),
            MetricLifecycleStatus.PUBLISH_DRY_RUN_READY.code(),
            MetricLifecycleStatus.REVIEW_SUBMITTED.code()
        );
        try {
            Map<String, Object> submitted = platformContractClient.submitDbtRelease(
                new PlatformContractClient.DbtReleaseSubmitRequest(
                    modelId,
                    text(state.get("modelName")),
                    text(state.get("artifactRef")),
                    false,
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("modelId", modelId);
            result.put("modelName", state.get("modelName"));
            result.put("status", MetricLifecycleStatus.PUBLISHED.code());
            result.put("artifactRef", state.get("artifactRef"));
            result.put("version", nextVersion(modelId));
            result.put("activeVersion", result.get("version"));
            result.put("previousVersion", latestVersion(modelId));
            result.put("platformPublishReference", firstText(submitted.get("publishReference"), submitted.get("id"), "platform-release://" + modelId));
            result.put("releaseDecision", firstText(submitted.get("decision"), submitted.get("status"), "SUBMITTED"));
            result.put("rollbackAvailable", modelVersions.getOrDefault(modelId, List.of()).size() > 0);
            result.put("publishedAt", Instant.now().toString());
            modelStates.put(modelId, result);
            appendModelVersion(modelId, result);
            return result;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    public Map<String, Object> rollback(String modelId, Map<String, Object> request) {
        Map<String, Object> state = currentState(modelId);
        List<Map<String, Object>> versions = modelVersions.getOrDefault(modelId, List.of());
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.PUBLISHED_VERSION_REQUIRED.code());
        }
        String fromVersion = firstText(state.get("activeVersion"), state.get("version"), latestVersion(modelId));
        Map<String, Object> target = rollbackTarget(versions, text(value(request, "targetVersion")), fromVersion);
        String rollbackToVersion = text(target.get("version"));
        String eventVersion = "rollback-" + (rollbackEvents.getOrDefault(modelId, List.of()).size() + 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", target.get("modelName"));
        result.put("status", MetricLifecycleStatus.ROLLED_BACK.code());
        result.put("version", eventVersion);
        result.put("activeVersion", rollbackToVersion);
        result.put("rollbackFromVersion", fromVersion);
        result.put("rollbackToVersion", rollbackToVersion);
        result.put("platformPublishReference", target.get("platformPublishReference"));
        result.put("platformRollbackReference", "platform-rollback://" + modelId + "/" + eventVersion);
        result.put("releaseDecision", target.get("releaseDecision"));
        result.put("reason", firstText(value(request, "reason"), "manual rollback"));
        result.put("rollbackPlan", List.of("freeze current publish reference", "restore semantic graph/artifact pointer", "request BI consumer refresh"));
        result.put("consumerLockImpact", List.of("BI Dataset consumers must refresh against rollbackToVersion before the next publish."));
        result.put("rolledBackAt", Instant.now().toString());
        modelStates.put(modelId, result);
        appendRollbackEvent(modelId, result);
        return result;
    }

    public Map<String, Object> versionHistory(String modelId) {
        Map<String, Object> state = currentState(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", state.get("modelName"));
        result.put("status", state.get("status"));
        result.put("activeVersion", firstText(state.get("activeVersion"), state.get("version")));
        result.put("versions", modelVersions.getOrDefault(modelId, List.of()));
        result.put("rollbackEvents", rollbackEvents.getOrDefault(modelId, List.of()));
        result.put("rollbackAvailable", modelVersions.getOrDefault(modelId, List.of()).size() > 1);
        return result;
    }

    private Map<String, Object> artifactState(String modelId, Map<String, Object> request) {
        Map<String, Object> current = modelStates.get(modelId);
        if (current != null && current.containsKey("artifacts")) {
            return current;
        }
        return generateArtifacts(modelId, request);
    }

    private Map<String, Object> requireStatus(String modelId, String... allowed) {
        Map<String, Object> state = modelStates.get(modelId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ARTIFACT_REQUIRED.code());
        }
        String status = text(state.get("status"));
        for (String item : allowed) {
            if (item.equals(status)) {
                return state;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.DBT_VALIDATION_REQUIRED.code());
    }

    private Map<String, Object> currentState(String modelId) {
        Map<String, Object> state = modelStates.get(modelId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, MetricContractErrorCode.MODEL_LIFECYCLE_STATE_NOT_FOUND.code());
        }
        return state;
    }

    private Map<String, Object> publicState(Map<String, Object> state, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", state.get("modelId"));
        result.put("modelName", state.get("modelName"));
        result.put("status", status);
        result.put("artifactRef", state.get("artifactRef"));
        result.put("appliedPolicySource", state.get("appliedPolicySource"));
        result.put("appliedPredicateHash", state.get("appliedPredicateHash"));
        return result;
    }

    private void appendModelVersion(String modelId, Map<String, Object> state) {
        List<Map<String, Object>> versions = new ArrayList<>(modelVersions.getOrDefault(modelId, List.of()));
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("version", state.get("version"));
        version.put("modelName", state.get("modelName"));
        version.put("status", state.get("status"));
        version.put("platformPublishReference", state.get("platformPublishReference"));
        version.put("releaseDecision", state.get("releaseDecision"));
        version.put("publishedAt", state.get("publishedAt"));
        version.put("artifactRef", state.get("artifactRef"));
        versions.add(version);
        modelVersions.put(modelId, List.copyOf(versions));
    }

    private void appendRollbackEvent(String modelId, Map<String, Object> state) {
        List<Map<String, Object>> events = new ArrayList<>(rollbackEvents.getOrDefault(modelId, List.of()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("version", state.get("version"));
        event.put("status", state.get("status"));
        event.put("rollbackFromVersion", state.get("rollbackFromVersion"));
        event.put("rollbackToVersion", state.get("rollbackToVersion"));
        event.put("platformRollbackReference", state.get("platformRollbackReference"));
        event.put("reason", state.get("reason"));
        event.put("rolledBackAt", state.get("rolledBackAt"));
        events.add(event);
        rollbackEvents.put(modelId, List.copyOf(events));
    }

    private String nextVersion(String modelId) {
        return "v" + (modelVersions.getOrDefault(modelId, List.of()).size() + 1);
    }

    private String latestVersion(String modelId) {
        List<Map<String, Object>> versions = modelVersions.getOrDefault(modelId, List.of());
        return versions.isEmpty() ? "" : text(versions.get(versions.size() - 1).get("version"));
    }

    private Map<String, Object> rollbackTarget(List<Map<String, Object>> versions, String requestedVersion, String fromVersion) {
        if (StringUtils.hasText(requestedVersion)) {
            for (Map<String, Object> version : versions) {
                if (requestedVersion.equals(text(version.get("version")))) {
                    if (requestedVersion.equals(fromVersion)) {
                        throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            MetricContractErrorCode.ROLLBACK_TARGET_MUST_DIFFER.code()
                        );
                    }
                    return version;
                }
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ROLLBACK_TARGET_NOT_FOUND.code());
        }
        int currentIndex = versions.size() - 1;
        for (int i = 0; i < versions.size(); i++) {
            if (fromVersion.equals(text(versions.get(i).get("version")))) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ROLLBACK_TARGET_REQUIRED.code());
        }
        return versions.get(currentIndex - 1);
    }

    private Map<String, Object> graph(String modelId, Map<String, Object> request) {
        Object graph = value(request, "graph");
        if (graph instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return graphDraftService.graph(modelId);
    }

    private Map<String, Object> artifacts(String modelName, Map<String, Object> graph, String policySource, String predicateHash) {
        List<String> dimensions = identifiers(graph.get("dimensions"));
        List<String> measures = identifiers(graph.get("measures"));
        List<Map<String, Object>> derivedMetrics = maps(graph.get("derived_metrics"));
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            rejectUnsafeExpression(text(derivedMetric.get("expression")));
        }
        String dialect = dialect(graph);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("dbtModelSql", dbtModelSql(modelName, safeIdentifier(text(graph.get("base"))), dimensions, measures, derivedMetrics, dialect));
        artifacts.put("schemaYml", schemaYml(modelName, dimensions, measures, derivedMetrics));
        artifacts.put("exposureYml", exposureYml(modelName));
        artifacts.put("metricDoc", metricDoc(modelName, dimensions, measures, derivedMetrics));
        artifacts.put("lineageHint", Map.of("upstreamAsset", text(graph.get("base")), "model", modelName, "targetLayer", targetLayer(graph)));
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

    private static String dbtModelSql(
        String modelName,
        String base,
        List<String> dimensions,
        List<String> measures,
        List<Map<String, Object>> derivedMetrics,
        String dialect
    ) {
        List<String> selectRows = new ArrayList<>();
        for (String dimension : dimensions) {
            selectRows.add("    " + quoteIdentifier(dimension, dialect));
        }
        for (String measure : measures) {
            selectRows.add("    sum(" + quoteIdentifier(measure, dialect) + ") as " + quoteIdentifier(measure, dialect));
        }
        for (Map<String, Object> derivedMetric : derivedMetrics) {
            String id = safeIdentifier(text(derivedMetric.get("id")));
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
        if (!dimensions.isEmpty()) {
            sql.append("group by\n");
            for (int i = 0; i < dimensions.size(); i++) {
                sql.append("    ").append(quoteIdentifier(dimensions.get(i), dialect));
                sql.append(i + 1 < dimensions.size() ? ",\n" : "\n");
            }
        }
        return sql.toString();
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
            yml.append("      - name: ").append(safeIdentifier(text(derivedMetric.get("id")))).append("\n");
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

    private static boolean platformPassed(Map<String, Object> validation) {
        Object decision = validation.get("decision");
        Object status = validation.get("status");
        Object valid = validation.get("valid");
        return Boolean.TRUE.equals(valid) || "PASS".equals(decision) || MetricLifecycleStatus.DBT_VALIDATED.code().equals(status);
    }

    private static List<String> identifiers(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("id");
                if (id != null) {
                    result.add(safeIdentifier(text(id)));
                }
                continue;
            }
            result.add(safeIdentifier(text(item)));
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
        String safeGrain = safeIdentifier(grain).toLowerCase(Locale.ROOT);
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

    private static String safeModelName(String value) {
        String modelName = safeIdentifier(value).toLowerCase(Locale.ROOT);
        if (!modelName.startsWith("dws_") && !modelName.startsWith("ads_")) {
            return "dws_" + modelName;
        }
        return modelName;
    }

    private static String quoteIdentifier(String value, String dialect) {
        String identifier = safeIdentifier(value);
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

    private static String safeIdentifier(String value) {
        String identifier = text(value).replaceAll("[^A-Za-z0-9_]", "_");
        while (identifier.contains("__")) {
            identifier = identifier.replace("__", "_");
        }
        return identifier.replaceAll("^_+|_+$", "");
    }

    private static Object value(Map<String, Object> request, String key) {
        return request != null ? request.get(key) : null;
    }

    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return Map.of();
    }

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
