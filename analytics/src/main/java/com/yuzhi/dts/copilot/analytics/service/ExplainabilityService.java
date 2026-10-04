package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsQueryTrace;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsMetricRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExplainabilityService {

    private static final Pattern SQL_TABLE_PATTERN = Pattern.compile(
            "\\b(from|join)\\s+([a-zA-Z0-9_\\.\\\"]+)",
            Pattern.CASE_INSENSITIVE);

    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final QueryTraceService queryTraceService;
    private final ObjectMapper objectMapper;

    public ExplainabilityService(
            AnalyticsCardRepository cardRepository,
            AnalyticsMetricRepository metricRepository,
            QueryTraceService queryTraceService,
            ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.metricRepository = metricRepository;
        this.queryTraceService = queryTraceService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> explainCard(
            long cardId, Long metricId, String metricVersion, JsonNode filterContext, String componentId) {
        AnalyticsCard card = cardRepository
                .findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("card not found"));
        JsonNode datasetQuery = parseJson(card.getDatasetQueryJson());
        if (datasetQuery == null || !datasetQuery.isObject()) {
            throw new IllegalArgumentException("invalid card dataset_query");
        }

        Map<String, Object> metricDefinition = buildMetricDefinition(metricId, metricVersion, datasetQuery);
        Map<String, Object> querySummary = buildQuerySummary(datasetQuery);
        Map<String, Object> dataLineage = buildDataLineage(datasetQuery, querySummary);
        Map<String, Object> filterImpact = buildFilterImpact(datasetQuery, filterContext);
        List<String> nextActions = buildNextActions(querySummary, filterImpact);
        Map<String, Object> traceSummary = buildTraceSummary(cardId);

        Map<String, Object> explainCard = new LinkedHashMap<>();
        explainCard.put("metricDefinition", metricDefinition);
        explainCard.put("filterContext", filterImpact);
        explainCard.put("dataLineage", dataLineage);
        explainCard.put("querySummary", querySummary);
        explainCard.put("nextActions", nextActions);
        explainCard.put("trace", traceSummary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("cardName", card.getName());
        result.put("componentId", trimToNull(componentId));
        result.put("generatedAt", Instant.now());
        result.put("explainCard", explainCard);
        result.put("copyJson", toJson(explainCard));
        return result;
    }

    private Map<String, Object> buildMetricDefinition(Long metricId, String metricVersion, JsonNode datasetQuery) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("metricId", metricId);
        definition.put("metricVersion", trimToNull(metricVersion));
        definition.put("metricLensUrl", metricId != null && metricId > 0 ? "/analytics/api/metric-lens/" + metricId : null);
        definition.put("aggregation", "unknown");
        definition.put("timeGrain", "unknown");
        definition.put("owner", null);

        if (metricId != null && metricId > 0) {
            AnalyticsMetric metric = metricRepository.findById(metricId).orElse(null);
            if (metric != null && !metric.isArchived()) {
                definition.put("metricName", metric.getName());
                definition.put("description", metric.getDescription());
                definition.put("owner", metric.getCreatorId());
                JsonNode metricJson = parseJson(metric.getMetricJson());
                if (metricJson != null && metricJson.isObject()) {
                    definition.put("definition", metricJson);
                    String aggregation = trimToNull(metricJson.path("aggregation").asText(null));
                    if (aggregation != null) {
                        definition.put("aggregation", aggregation);
                    }
                    String timeGrain = trimToNull(metricJson.path("time_grain").asText(null));
                    if (timeGrain == null) {
                        timeGrain = trimToNull(metricJson.path("timeGrain").asText(null));
                    }
                    if (timeGrain != null) {
                        definition.put("timeGrain", timeGrain);
                    }
                }
            }
        }

        JsonNode queryNode = datasetQuery.path("query");
        if (queryNode.isObject() && queryNode.path("aggregation").isArray()) {
            definition.put("aggregation", queryNode.path("aggregation").toString());
        }
        if (queryNode.isObject() && queryNode.path("breakout").isArray()) {
            definition.put("timeGrain", queryNode.path("breakout").toString());
        }
        return definition;
    }

    private Map<String, Object> buildQuerySummary(JsonNode datasetQuery) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String queryType = trimToNull(datasetQuery.path("type").asText(null));
        if (queryType == null) {
            queryType = datasetQuery.has("native") ? "native" : "query";
        }
        summary.put("queryType", queryType);
        summary.put("databaseId", datasetQuery.path("database").asLong(0));

        if ("native".equalsIgnoreCase(queryType)) {
            String sql = trimToNull(datasetQuery.path("native").path("query").asText(null));
            summary.put("sqlPreview", preview(sql, 500));
            summary.put("source", "native-sql");
        } else {
            JsonNode queryNode = datasetQuery.path("query");
            summary.put("mbql", queryNode.isObject() ? queryNode : objectMapper.createObjectNode());
            summary.put("source", "mbql");
        }
        return summary;
    }

    private Map<String, Object> buildDataLineage(JsonNode datasetQuery, Map<String, Object> querySummary) {
        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("databaseId", datasetQuery.path("database").asLong(0));
        lineage.put("queryType", querySummary.get("queryType"));

        Set<String> tables = new HashSet<>();
        Set<String> fields = new HashSet<>();
        if ("native".equalsIgnoreCase(String.valueOf(querySummary.get("queryType")))) {
            String sql = String.valueOf(querySummary.getOrDefault("sqlPreview", ""));
            Matcher matcher = SQL_TABLE_PATTERN.matcher(sql == null ? "" : sql);
            while (matcher.find()) {
                String table = trimToNull(matcher.group(2));
                if (table != null) {
                    tables.add(table.replace("\"", ""));
                }
            }
        } else {
            JsonNode queryNode = datasetQuery.path("query");
            if (queryNode.path("source-table").canConvertToLong()) {
                tables.add("table#" + queryNode.path("source-table").asLong());
            }
            JsonNode breakout = queryNode.path("breakout");
            if (breakout.isArray()) {
                for (JsonNode item : breakout) {
                    if (item != null && item.isArray() && item.size() > 1) {
                        fields.add(item.get(1).asText(""));
                    }
                }
            }
        }
        lineage.put("tables", tables.stream().sorted().toList());
        lineage.put("fields", fields.stream().sorted().toList());
        lineage.put("lineageMode", "rule-based");
        return lineage;
    }

    private Map<String, Object> buildFilterImpact(JsonNode datasetQuery, JsonNode filterContext) {
        Map<String, Object> impact = new LinkedHashMap<>();
        ObjectNode normalizedFilterContext = normalizeFilterContext(filterContext);
        ArrayNode globalVariables = readArray(normalizedFilterContext.path("globalVariables"));
        ArrayNode localFilters = readArray(normalizedFilterContext.path("localFilters"));

        ArrayNode affectedByGlobal = objectMapper.createArrayNode();
        for (JsonNode item : globalVariables) {
            String key = trimToNull(item.path("key").asText(null));
            if (key != null) {
                affectedByGlobal.add(key);
            }
        }
        ArrayNode affectedByLocal = objectMapper.createArrayNode();
        for (JsonNode item : localFilters) {
            String key = trimToNull(item.path("field").asText(null));
            if (key == null) {
                key = trimToNull(item.path("key").asText(null));
            }
            if (key != null) {
                affectedByLocal.add(key);
            }
        }

        JsonNode mbqlFilter = datasetQuery.path("query").path("filter");
        impact.put("globalVariables", globalVariables);
        impact.put("localFilters", localFilters);
        impact.put("mbqlFilter", mbqlFilter.isArray() ? mbqlFilter : objectMapper.createArrayNode());
        impact.put("affectedByGlobalVariables", affectedByGlobal);
        impact.put("affectedByLocalFilters", affectedByLocal);
        impact.put("impactScope", buildImpactScope(affectedByGlobal, affectedByLocal));
        return impact;
    }

    private List<String> buildNextActions(Map<String, Object> querySummary, Map<String, Object> filterImpact) {
        List<String> actions = new java.util.ArrayList<>();
        String queryType = String.valueOf(querySummary.getOrDefault("queryType", "query")).toLowerCase(Locale.ROOT);
        if ("native".equals(queryType)) {
            actions.add("建议将关键 WHERE 条件参数化，便于解释链路展示筛选影响。");
        } else {
            actions.add("建议补充指标定义（aggregation/timeGrain），便于业务复盘。");
        }
        ArrayNode globals = readArray(objectMapper.valueToTree(filterImpact.get("affectedByGlobalVariables")));
        if (!globals.isEmpty()) {
            actions.add("建议在组件说明中标注受哪些全局变量影响，减少口径误解。");
        } else {
            actions.add("当前未检测到全局变量影响，可考虑增加时间范围变量。");
        }
        actions.add("可复制解释 JSON 并附在审计记录中。");
        return actions;
    }

    private Map<String, Object> buildTraceSummary(long cardId) {
        List<AnalyticsQueryTrace> traces = queryTraceService.listRecent(null, cardId, 5);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("sampleCount", traces.size());
        if (traces.isEmpty()) {
            trace.put("lastStatus", null);
            trace.put("lastErrorCode", null);
            return trace;
        }
        AnalyticsQueryTrace latest = traces.getFirst();
        trace.put("lastStatus", latest.getStatus());
        trace.put("lastErrorCode", latest.getErrorCode());
        trace.put("lastRequestId", latest.getRequestId());
        trace.put("lastCreatedAt", latest.getCreatedAt());
        return trace;
    }

    private ObjectNode normalizeFilterContext(JsonNode filterContext) {
        if (filterContext == null || !filterContext.isObject()) {
            return objectMapper.createObjectNode();
        }
        return (ObjectNode) filterContext.deepCopy();
    }

    private ArrayNode readArray(JsonNode node) {
        if (node != null && node.isArray()) {
            return (ArrayNode) node;
        }
        return objectMapper.createArrayNode();
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String preview(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private List<Map<String, Object>> buildImpactScope(ArrayNode globals, ArrayNode locals) {
        List<Map<String, Object>> scope = new java.util.ArrayList<>();
        for (JsonNode item : globals) {
            String key = trimToNull(item.asText(null));
            if (key != null) {
                scope.add(Map.of("source", "globalVariable", "key", key));
            }
        }
        for (JsonNode item : locals) {
            String key = trimToNull(item.asText(null));
            if (key != null) {
                scope.add(Map.of("source", "localFilter", "key", key));
            }
        }
        return scope;
    }
}
