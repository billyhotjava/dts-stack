package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsQueryTrace;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsQueryTraceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetricLensService {

    private final AnalyticsMetricRepository metricRepository;
    private final AnalyticsCardRepository cardRepository;
    private final AnalyticsQueryTraceRepository queryTraceRepository;
    private final QueryTraceService queryTraceService;
    private final ObjectMapper objectMapper;

    public MetricLensService(
            AnalyticsMetricRepository metricRepository,
            AnalyticsCardRepository cardRepository,
            AnalyticsQueryTraceRepository queryTraceRepository,
            QueryTraceService queryTraceService,
            ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.cardRepository = cardRepository;
        this.queryTraceRepository = queryTraceRepository;
        this.queryTraceService = queryTraceService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list() {
        return metricRepository.findAllByArchivedFalseOrderByIdAsc().stream()
                .map(this::toLensSummary)
                .toList();
    }

    public Map<String, Object> get(long metricId) {
        AnalyticsMetric metric = metricRepository
                .findById(metricId)
                .orElseThrow(() -> new IllegalArgumentException("metric not found"));
        if (metric.isArchived()) {
            throw new IllegalArgumentException("metric not found");
        }
        return toLensDetail(metric);
    }

    public Map<String, Object> compareVersions(long metricId, String leftVersion, String rightVersion) {
        AnalyticsMetric metric = metricRepository
                .findById(metricId)
                .orElseThrow(() -> new IllegalArgumentException("metric not found"));
        if (metric.isArchived()) {
            throw new IllegalArgumentException("metric not found");
        }

        String left = trimToNull(leftVersion);
        String right = trimToNull(rightVersion);
        if (left == null || right == null) {
            throw new IllegalArgumentException("leftVersion and rightVersion are required");
        }

        var leftRows = queryTraceRepository
                .findAllByMetricIdAndMetricVersion(metricId, left, PageRequest.of(0, 200))
                .getContent();
        var rightRows = queryTraceRepository
                .findAllByMetricIdAndMetricVersion(metricId, right, PageRequest.of(0, 200))
                .getContent();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricId", metricId);
        out.put("leftVersion", summarizeVersion(left, leftRows));
        out.put("rightVersion", summarizeVersion(right, rightRows));
        out.put("delta", buildVersionDelta(leftRows, rightRows));
        return out;
    }

    public List<Map<String, Object>> detectConflicts() {
        List<AnalyticsMetric> metrics = metricRepository.findAllByArchivedFalseOrderByIdAsc();
        Map<String, List<AnalyticsMetric>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        it -> normalizeMetricName(it.getName()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<AnalyticsMetric>> entry : grouped.entrySet()) {
            List<AnalyticsMetric> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            Map<String, List<Long>> signatureToIds = new LinkedHashMap<>();
            for (AnalyticsMetric metric : group) {
                signatureToIds.computeIfAbsent(signature(metric), ignored -> new ArrayList<>()).add(metric.getId());
            }
            if (signatureToIds.size() <= 1) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metricName", group.getFirst().getName());
            row.put("conflictLevel", signatureToIds.size() >= 3 ? "high" : "medium");
            row.put("type", List.of("aggregation-diff", "filter-diff", "time-grain-diff"));
            row.put("metricIds", group.stream().map(AnalyticsMetric::getId).toList());
            row.put("signatures", signatureToIds);
            row.put("recommendations", List.of("统一指标口径", "保留主指标并废弃重复定义", "必要时重命名并明确业务域"));
            conflicts.add(row);
        }
        return conflicts;
    }

    private Map<String, Object> toLensSummary(AnalyticsMetric metric) {
        JsonNode metricJson = parseMetricJson(metric.getMetricJson());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricId", metric.getId());
        out.put("name", metric.getName());
        out.put("owner", metric.getCreatorId());
        out.put("aggregation", readPath(metricJson, "aggregation", "definition.aggregation"));
        out.put("timeGrain", readPath(metricJson, "timeGrain", "definition.timeGrain", "definition.time_grain"));
        out.put("aclScope", readPath(metricJson, "visibilityScope", "definition.visibilityScope"));
        out.put("latestVersion", queryTraceService.listMetricVersions(metric.getId()).stream().findFirst().orElse(null));
        return out;
    }

    private Map<String, Object> toLensDetail(AnalyticsMetric metric) {
        JsonNode metricJson = parseMetricJson(metric.getMetricJson());
        List<String> versions = queryTraceService.listMetricVersions(metric.getId());
        List<Long> cardIds = queryTraceRepository.findDistinctCardIdsByMetricId(metric.getId());
        List<Map<String, Object>> impactedCards = cardRepository.findAllById(cardIds).stream()
                .map(this::toCardRef)
                .toList();

        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("metricId", metric.getId());
        lineage.put("cards", impactedCards);
        lineage.put("tables", readArray(metricJson, "tables", "definition.tables"));
        lineage.put("fields", readArray(metricJson, "fields", "definition.fields"));

        Map<String, Object> lens = new LinkedHashMap<>();
        lens.put("metricId", metric.getId());
        lens.put("name", metric.getName());
        lens.put("definition", metricJson.path("definition").isObject() ? metricJson.path("definition") : metricJson);
        lens.put("aggregation", readPath(metricJson, "aggregation", "definition.aggregation"));
        lens.put("timeGrain", readPath(metricJson, "timeGrain", "definition.timeGrain", "definition.time_grain"));
        lens.put("owner", metric.getCreatorId());
        lens.put("version", versions.isEmpty() ? null : versions.getFirst());
        lens.put("versions", versions);
        lens.put("aclScope", readPath(metricJson, "visibilityScope", "definition.visibilityScope"));
        lens.put("lineage", lineage);
        lens.put("conflicts", detectConflicts().stream()
                .filter(item -> metric.getName().equalsIgnoreCase(String.valueOf(item.get("metricName"))))
                .toList());
        return lens;
    }

    private Map<String, Object> summarizeVersion(String version, List<AnalyticsQueryTrace> rows) {
        long success = rows.stream().filter(it -> "success".equalsIgnoreCase(it.getStatus())).count();
        long failed = rows.size() - success;
        double avgDuration = rows.stream()
                .map(AnalyticsQueryTrace::getDurationMs)
                .filter(it -> it != null && it > 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", version);
        out.put("sampleCount", rows.size());
        out.put("success", success);
        out.put("failed", failed);
        out.put("avgDurationMs", Math.round(avgDuration));
        return out;
    }

    private Map<String, Object> buildVersionDelta(List<AnalyticsQueryTrace> leftRows, List<AnalyticsQueryTrace> rightRows) {
        long leftSuccess = leftRows.stream().filter(it -> "success".equalsIgnoreCase(it.getStatus())).count();
        long rightSuccess = rightRows.stream().filter(it -> "success".equalsIgnoreCase(it.getStatus())).count();
        double leftRate = leftRows.isEmpty() ? 0d : (double) leftSuccess / (double) leftRows.size();
        double rightRate = rightRows.isEmpty() ? 0d : (double) rightSuccess / (double) rightRows.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passRateDelta", rightRate - leftRate);
        out.put("sampleDelta", rightRows.size() - leftRows.size());
        out.put("successDelta", rightSuccess - leftSuccess);
        return out;
    }

    private Map<String, Object> toCardRef(AnalyticsCard card) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", card.getId());
        out.put("name", card.getName());
        out.put("collectionId", card.getCollectionId());
        return out;
    }

    private String signature(AnalyticsMetric metric) {
        JsonNode metricJson = parseMetricJson(metric.getMetricJson());
        String aggregation = readPath(metricJson, "aggregation", "definition.aggregation");
        String timeGrain = readPath(metricJson, "timeGrain", "definition.timeGrain", "definition.time_grain");
        String filter = readPath(metricJson, "filter", "definition.filter");
        return String.join("|", nullToEmpty(aggregation), nullToEmpty(timeGrain), nullToEmpty(filter));
    }

    private String normalizeMetricName(String name) {
        String text = trimToNull(name);
        return text == null ? "__empty__" : text.toLowerCase(Locale.ROOT);
    }

    private JsonNode parseMetricJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String readPath(JsonNode node, String... paths) {
        if (node == null || paths == null) {
            return null;
        }
        for (String path : paths) {
            String value = readPathSingle(node, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String readPathSingle(JsonNode node, String path) {
        if (node == null || path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = node;
        for (String key : path.split("\\.")) {
            current = current.path(key);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return trimToNull(current.asText(null));
    }

    private List<String> readArray(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String key : path.split("\\.")) {
                current = current.path(key);
            }
            if (current.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : current) {
                    String value = trimToNull(item == null ? null : item.asText(null));
                    if (value != null) {
                        values.add(value);
                    }
                }
                return values;
            }
        }
        return List.of();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
