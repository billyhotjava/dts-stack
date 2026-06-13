package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.domain.GraphDraft;
import com.yuzhi.dts.metrics.domain.repository.GraphDraftRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetricGraphDraftService {

    private static final String DRAFT_SOURCE = "dts-metrics graph draft store";

    private static final String DSL_IDENTIFIER = "[A-Za-z_][A-Za-z0-9_.]*";
    private static final String DSL_LITERAL = "[A-Za-z0-9_.-]+";
    private static final String DSL_COMPARISON = "(?:eq|ne|gt|gte|lt|lte)";
    private static final Pattern UNSAFE_DERIVED_EXPRESSION = Pattern.compile(
        "(?i)(\\b(select|insert|update|delete|drop|alter|truncate|merge|union|join|from|where|having|grant|revoke|call|exec)\\b|--|/\\*|\\*/|;|'|\"|`|\\\\)"
    );
    private static final Pattern CONTROLLED_DERIVED_DSL = Pattern.compile(
        "^(?:(?:sum|count|count_distinct|avg|min|max)\\(" +
        DSL_IDENTIFIER +
        "\\)|ratio\\(" +
        DSL_IDENTIFIER +
        "\\s*,\\s*" +
        DSL_IDENTIFIER +
        "\\)|date_trunc\\((?:day|week|month|quarter|year)\\s*,\\s*" +
        DSL_IDENTIFIER +
        "\\)|count_if\\(" +
        DSL_IDENTIFIER +
        "\\s*,\\s*" +
        DSL_COMPARISON +
        "\\s*,\\s*" +
        DSL_LITERAL +
        "\\)|sum_if\\(" +
        DSL_IDENTIFIER +
        "\\s*,\\s*" +
        DSL_IDENTIFIER +
        "\\s*,\\s*" +
        DSL_COMPARISON +
        "\\s*,\\s*" +
        DSL_LITERAL +
        "\\)|case_when\\(" +
        DSL_IDENTIFIER +
        "\\s*,\\s*" +
        DSL_COMPARISON +
        "\\s*,\\s*" +
        DSL_LITERAL +
        "\\s*,\\s*" +
        DSL_LITERAL +
        "\\s*,\\s*" +
        DSL_LITERAL +
        "\\))$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> DERIVED_DSL_KEYWORDS = Set.of(
        "sum",
        "count",
        "count_distinct",
        "avg",
        "min",
        "max",
        "count_if",
        "sum_if",
        "ratio",
        "case_when",
        "date_trunc",
        "day",
        "week",
        "month",
        "quarter",
        "year",
        "eq",
        "ne",
        "gt",
        "gte",
        "lt",
        "lte"
    );

    private final GraphDraftRepository graphDraftRepository;

    public MetricGraphDraftService(GraphDraftRepository graphDraftRepository) {
        this.graphDraftRepository = graphDraftRepository;
    }

    @Transactional
    public Map<String, Object> createDraft(Map<String, Object> graph) {
        requireGraph(graph);
        List<Map<String, Object>> diagnostics = diagnostics(graph);
        Instant now = Instant.now();
        GraphDraft entity = new GraphDraft();
        entity.setId(UUID.randomUUID().toString());
        entity.setStatus(state(diagnostics));
        entity.setGraph(new LinkedHashMap<>(graph));
        entity.setDiagnostics(diagnostics);
        entity.setSource(DRAFT_SOURCE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        GraphDraft saved = graphDraftRepository.save(entity);
        return toMap(saved, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDraft(String draftId) {
        return graphDraftRepository
            .findById(draftId)
            .map(entity -> toMap(entity, true))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "graph draft not found"));
    }

    @Transactional
    public Map<String, Object> updateDraft(String draftId, Map<String, Object> graph) {
        GraphDraft entity = graphDraftRepository
            .findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "graph draft not found"));
        requireGraph(graph);
        List<Map<String, Object>> diagnostics = diagnostics(graph);
        entity.setStatus(state(diagnostics));
        entity.setGraph(new LinkedHashMap<>(graph));
        entity.setDiagnostics(diagnostics);
        entity.setUpdatedAt(Instant.now());
        GraphDraft saved = graphDraftRepository.save(entity);
        return toMap(saved, false);
    }

    public Map<String, Object> preflightDraft(Map<String, Object> graph) {
        requireGraph(graph);
        List<Map<String, Object>> diagnostics = diagnostics(graph);
        String state = state(diagnostics);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("checkedAt", Instant.now().toString());
        meta.put("diagnosticCount", diagnostics.size());
        meta.put("warnings", diagnostics.stream().filter(item -> "WARNING".equals(item.get("severity"))).map(item -> item.get("message")).toList());
        meta.put("security_applied", List.of("platform permission and RLS/masking are enforced during artifact generation and publish"));
        meta.put("sql_preview", graphSummary(graph, state, diagnostics));
        return Map.of("status", state, "diagnostics", diagnostics, "meta", meta, "columns", List.of(), "rows", List.of());
    }

    public Map<String, Object> preflightStoredDraft(String draftId) {
        return preflightDraft(graph(draftId));
    }

    public Map<String, Object> graph(String draftId) {
        return graphFromDraft(getDraft(draftId));
    }

    /**
     * Reassemble the legacy public draft Map from entity columns so reads are byte-identical to the
     * old {@code ConcurrentHashMap}-backed shape. On create the {@code meta} bucket carries
     * {@code createdAt}; on update it omits {@code createdAt} to preserve the legacy quirk where
     * {@code updateDraft} dropped {@code createdAt} from {@code meta}.
     */
    private static Map<String, Object> toMap(GraphDraft entity, boolean includeCreatedAt) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", entity.getId());
        draft.put("status", entity.getStatus());
        draft.put("graph", entity.getGraph());
        draft.put("diagnostics", entity.getDiagnostics());
        Map<String, Object> meta = new LinkedHashMap<>();
        if (includeCreatedAt) {
            meta.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        }
        meta.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        meta.put("source", entity.getSource());
        draft.put("meta", meta);
        return draft;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> graphFromDraft(Map<String, Object> draft) {
        Object graph = draft.get("graph");
        return graph instanceof Map<?, ?> ? (Map<String, Object>) graph : Map.of();
    }

    public static List<Map<String, Object>> diagnostics(Map<String, Object> graph) {
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        if (!StringUtils.hasText(text(graph.get("base")))) {
            diagnostics.add(diagnostic("base", null, null, "ERROR", "base_asset_required", "必须选择一个 DWS/ADS 基础资产"));
        }
        if (list(graph.get("measures")).isEmpty() && list(graph.get("derived_metrics")).isEmpty()) {
            diagnostics.add(diagnostic("metrics", null, null, "ERROR", "metric_required", "至少选择一个指标或定义一个派生指标"));
        }
        if (list(graph.get("dimensions")).isEmpty()) {
            diagnostics.add(diagnostic("dimensions", null, null, "WARNING", "dimension_recommended", "建议选择至少一个维度以固定汇总粒度"));
        }
        if (!list(graph.get("joins")).isEmpty() && list(graph.get("dimensions")).isEmpty()) {
            diagnostics.add(diagnostic("joins", null, null, "ERROR", "grain_required_for_join", "包含 Join 时必须声明维度或粒度字段"));
        }
        for (Map<String, Object> node : nodes(graph)) {
            String nodeId = text(node.get("id"));
            String layer = text(node.get("warehouseLayer")).toUpperCase(Locale.ROOT);
            String role = text(node.get("role")).toUpperCase(Locale.ROOT);
            if ("ODS".equals(layer) || "STG".equals(layer)) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "invalid_layer", "ODS/STG 节点不能进入普通 graph preflight"));
            }
            if ("DWD".equals(layer) && list(node.get("grain")).isEmpty() && list(node.get("primaryKeys")).isEmpty()) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "grain_mismatch", "DWD 高级建模必须声明 grain 或 primary key"));
            }
            if ("DWD".equals(layer) && !StringUtils.hasText(text(node.get("standardCode"))) && list(node.get("standardCodes")).isEmpty()) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "standard_code_required", "DWD 高级建模必须绑定标准码"));
            }
            if ("DWD".equals(layer) && "PUBLISH".equals(role)) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "invalid_layer", "DWD 节点不能直接连接发布节点"));
            }
        }
        addDerivedMetricSafetyDiagnostics(graph, diagnostics);
        addDerivedMetricCycleDiagnostics(graph, diagnostics);
        return diagnostics;
    }

    public static String state(List<Map<String, Object>> diagnostics) {
        return diagnostics.stream().anyMatch(item -> "ERROR".equals(item.get("severity"))) ? "GRAPH_BLOCKED" : "GRAPH_READY";
    }

    private static void addDerivedMetricCycleDiagnostics(Map<String, Object> graph, List<Map<String, Object>> diagnostics) {
        List<Map<String, Object>> derivedMetrics = maps(graph.get("derived_metrics"));
        for (Map<String, Object> metric : derivedMetrics) {
            String id = text(metric.get("id"));
            String expression = text(metric.get("expression"));
            if (!StringUtils.hasText(id) || !StringUtils.hasText(expression)) {
                continue;
            }
            if (expression.contains(id)) {
                diagnostics.add(diagnostic(id, null, null, "ERROR", "metric_cycle", "派生指标不能直接引用自身"));
                continue;
            }
            for (Map<String, Object> other : derivedMetrics) {
                String otherId = text(other.get("id"));
                String otherExpression = text(other.get("expression"));
                if (!id.equals(otherId) && expression.contains(otherId) && otherExpression.contains(id)) {
                    diagnostics.add(diagnostic(id, null, null, "ERROR", "metric_cycle", "派生指标存在循环依赖"));
                }
            }
        }
    }

    private static void addDerivedMetricSafetyDiagnostics(Map<String, Object> graph, List<Map<String, Object>> diagnostics) {
        Set<String> allowedFields = knownFieldReferences(graph);
        Set<String> allowedFieldsLower = lowercased(allowedFields);
        for (Map<String, Object> metric : maps(graph.get("derived_metrics"))) {
            String id = text(metric.get("id"));
            String expression = text(metric.get("expression"));
            String nodeId = StringUtils.hasText(id) ? id : "derived_metric";
            if (!StringUtils.hasText(expression)) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "derived_expression_required", "派生指标必须由受控 DSL 生成表达式"));
                continue;
            }
            if (UNSAFE_DERIVED_EXPRESSION.matcher(expression).find() || !CONTROLLED_DERIVED_DSL.matcher(expression).matches()) {
                diagnostics.add(diagnostic(nodeId, null, null, "ERROR", "unsafe_expression", "派生指标只允许 sum/count/count_distinct/avg/ratio/date_trunc 受控 DSL"));
                continue;
            }
            for (String identifier : fieldReferences(expression)) {
                String normalized = identifier.toLowerCase(Locale.ROOT);
                if (DERIVED_DSL_KEYWORDS.contains(normalized)) {
                    continue;
                }
                if (!allowedFields.contains(identifier) && !allowedFieldsLower.contains(normalized)) {
                    diagnostics.add(diagnostic(nodeId, null, identifier, "ERROR", "unregistered_field", "派生指标引用的字段不在当前 graph schema contract 中"));
                }
            }
        }
    }

    private static Set<String> knownFieldReferences(Map<String, Object> graph) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : list(graph.get("measures"))) {
            addFieldReference(result, value);
        }
        for (Object value : list(graph.get("dimensions"))) {
            addFieldReference(result, value);
        }
        for (Map<String, Object> metric : maps(graph.get("derived_metrics"))) {
            addFieldReference(result, metric.get("id"));
        }
        return result;
    }

    private static Set<String> lowercased(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static void addFieldReference(Set<String> result, Object value) {
        String field = value instanceof Map<?, ?> map ? text(map.get("id")) : text(value);
        if (StringUtils.hasText(field)) {
            result.add(field);
        }
    }

    private static List<String> fieldReferences(String expression) {
        String function = expression.substring(0, expression.indexOf('(')).trim().toLowerCase(Locale.ROOT);
        List<String> args = splitArgs(expression);
        List<String> result = new ArrayList<>();
        if (Set.of("sum", "count", "count_distinct", "avg", "min", "max", "count_if").contains(function) && !args.isEmpty()) {
            result.add(args.get(0));
        } else if ("ratio".equals(function) && args.size() >= 2) {
            result.add(args.get(0));
            result.add(args.get(1));
        } else if ("date_trunc".equals(function) && args.size() >= 2) {
            result.add(args.get(1));
        } else if ("sum_if".equals(function) && args.size() >= 2) {
            result.add(args.get(0));
            result.add(args.get(1));
        } else if ("case_when".equals(function) && !args.isEmpty()) {
            result.add(args.get(0));
        }
        return result;
    }

    private static List<String> splitArgs(String expression) {
        int start = expression.indexOf('(');
        int end = expression.lastIndexOf(')');
        if (start < 0 || end <= start) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        for (String item : expression.substring(start + 1, end).split(",")) {
            args.add(item.trim());
        }
        return args;
    }

    private static Map<String, Object> diagnostic(
        String nodeId,
        String edgeId,
        String fieldId,
        String severity,
        String code,
        String message
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        result.put("edgeId", edgeId);
        result.put("fieldId", fieldId);
        result.put("severity", severity);
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    private static String graphSummary(Map<String, Object> graph, String state, List<Map<String, Object>> diagnostics) {
        return String.join(
            "\n",
            "Graph preflight: " + state,
            "base: " + text(graph.get("base")),
            "measures: " + list(graph.get("measures")).size(),
            "dimensions: " + list(graph.get("dimensions")).size(),
            "joins: " + list(graph.get("joins")).size(),
            "diagnostics: " + diagnostics.size(),
            "next: submit candidate artifact to platform/dbt validation gateway"
        );
    }

    private static void requireGraph(Map<String, Object> graph) {
        if (graph == null || graph.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "graph draft is required");
        }
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<Map<String, Object>> nodes(Map<String, Object> graph) {
        return maps(graph.get("nodes"));
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

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
