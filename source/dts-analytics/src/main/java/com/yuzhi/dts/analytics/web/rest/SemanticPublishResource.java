package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingest endpoint for receiving semantic model definitions published
 * from dts-platform. Creates/updates AnalyticsMetric entries and
 * annotates AnalyticsField roles (dimension/measure).
 */
@RestController
@RequestMapping("/api/semantic")
public class SemanticPublishResource {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticPublishResource.class);

    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final ObjectMapper objectMapper;

    public SemanticPublishResource(
        AnalyticsDatabaseRepository databaseRepository,
        AnalyticsTableRepository tableRepository,
        AnalyticsFieldRepository fieldRepository,
        AnalyticsMetricRepository metricRepository,
        ObjectMapper objectMapper
    ) {
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> publish(@RequestBody JsonNode body) {
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty request body"));
        }

        String modelName = textOrNull(body, "modelName");
        String tableName = textOrNull(body, "tableName");
        String schemaName = textOrNull(body, "schemaName");
        String dataSourceName = textOrNull(body, "dataSourceName");
        String description = textOrNull(body, "description");

        if (tableName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tableName is required"));
        }
        if (schemaName == null) {
            schemaName = "public";
        }

        LOG.info("[semantic-publish] modelName={} tableName={} schemaName={} dataSourceName={}", modelName, tableName, schemaName, dataSourceName);

        // 1. Resolve the database (data source) in analytics
        AnalyticsDatabase database = resolveDatabase(dataSourceName);
        if (database == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No analytics database found for dataSourceName: " + dataSourceName,
                "hint", "Ensure the data source is registered in dts-analytics before publishing."
            ));
        }

        // 2. Find or create the analytics table
        AnalyticsTable table = resolveOrCreateTable(database.getId(), schemaName, tableName, description);

        // 3. Process metrics
        List<Map<String, Object>> metricsCreated = new ArrayList<>();
        List<Map<String, Object>> metricsUpdated = new ArrayList<>();
        JsonNode metricsNode = body.path("metrics");
        if (metricsNode.isArray()) {
            for (JsonNode metricNode : metricsNode) {
                Map<String, Object> result = processMetric(metricNode, table, database.getId());
                if (Boolean.TRUE.equals(result.get("created"))) {
                    metricsCreated.add(result);
                } else {
                    metricsUpdated.add(result);
                }
            }
        }

        // 4. Process dimensions (annotate fields)
        List<Map<String, Object>> dimensionsUpdated = new ArrayList<>();
        JsonNode dimensionsNode = body.path("dimensions");
        if (dimensionsNode.isArray()) {
            for (JsonNode dimNode : dimensionsNode) {
                Map<String, Object> result = processDimension(dimNode, table);
                if (!result.isEmpty()) {
                    dimensionsUpdated.add(result);
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("modelName", modelName);
        summary.put("tableId", table.getId());
        summary.put("tableName", table.getName());
        summary.put("metricsCreated", metricsCreated.size());
        summary.put("metricsUpdated", metricsUpdated.size());
        summary.put("dimensionsAnnotated", dimensionsUpdated.size());
        summary.put("metrics", concat(metricsCreated, metricsUpdated));
        summary.put("dimensions", dimensionsUpdated);

        LOG.info("[semantic-publish] completed: metricsCreated={} metricsUpdated={} dimensionsAnnotated={}",
            metricsCreated.size(), metricsUpdated.size(), dimensionsUpdated.size());

        return ResponseEntity.ok(summary);
    }

    private AnalyticsDatabase resolveDatabase(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isBlank()) {
            // Fall back to first non-sample database
            return databaseRepository.findAll().stream()
                .filter(db -> !db.isSample())
                .findFirst()
                .orElse(null);
        }
        // Find by name (case-insensitive match)
        return databaseRepository.findAll().stream()
            .filter(db -> dataSourceName.equalsIgnoreCase(db.getName()))
            .findFirst()
            // Fallback: partial match
            .or(() -> databaseRepository.findAll().stream()
                .filter(db -> db.getName() != null && db.getName().toLowerCase().contains(dataSourceName.toLowerCase()))
                .findFirst())
            .orElse(null);
    }

    private AnalyticsTable resolveOrCreateTable(Long databaseId, String schemaName, String tableName, String description) {
        Optional<AnalyticsTable> existing = tableRepository.findByDatabaseIdAndSchemaNameAndName(databaseId, schemaName, tableName);
        if (existing.isPresent()) {
            AnalyticsTable table = existing.get();
            if (description != null && !description.isBlank() && (table.getDescription() == null || table.getDescription().isBlank())) {
                table.setDescription(description);
                tableRepository.save(table);
            }
            return table;
        }
        // Create new table entry
        AnalyticsTable table = new AnalyticsTable();
        table.setDatabaseId(databaseId);
        table.setSchemaName(schemaName);
        table.setName(tableName);
        table.setDescription(description);
        table.setActive(true);
        table.setVisibilityType("normal");
        return tableRepository.save(table);
    }

    private Map<String, Object> processMetric(JsonNode metricNode, AnalyticsTable table, Long databaseId) {
        String name = textOrNull(metricNode, "name");
        String displayName = textOrNull(metricNode, "displayName");
        String aggregation = textOrNull(metricNode, "aggregation");
        String field = textOrNull(metricNode, "field");
        String unit = textOrNull(metricNode, "unit");
        String timeDimension = textOrNull(metricNode, "timeDimension");
        String timeGrain = textOrNull(metricNode, "timeGrain");
        String tags = textOrNull(metricNode, "tags");

        if (name == null) {
            return Map.of("error", "metric name is required");
        }

        // Find existing metric by baseTableId + name
        boolean created = false;
        AnalyticsMetric metric = metricRepository.findAll().stream()
            .filter(m -> table.getId().equals(m.getBaseTableId()) && name.equals(m.getName()) && !m.isArchived())
            .findFirst()
            .orElse(null);

        if (metric == null) {
            metric = new AnalyticsMetric();
            metric.setName(name);
            metric.setCreatorId(1L); // system user
            metric.setArchived(false);
            metric.setBaseTableId(table.getId());
            created = true;
        }

        // Update fields
        if (displayName != null) {
            metric.setDisplayName(displayName);
        }
        if (aggregation != null) {
            metric.setAggregation(aggregation.toUpperCase());
        }
        if (field != null) {
            metric.setExpressionField(field);
        }
        if (unit != null) {
            metric.setUnit(unit);
        }
        if (timeDimension != null) {
            metric.setTimeDimension(timeDimension);
        }
        if (timeGrain != null) {
            metric.setTimeGrain(timeGrain);
        }
        if (tags != null) {
            metric.setTags(tags);
        }
        metric.setVisibility("published");

        // Build metric_json
        Map<String, Object> metricJsonMap = new LinkedHashMap<>();
        metricJsonMap.put("name", metric.getName());
        metricJsonMap.put("display_name", metric.getDisplayName());
        metricJsonMap.put("table_id", table.getId());
        metricJsonMap.put("aggregation", metric.getAggregation());
        metricJsonMap.put("expression_field", metric.getExpressionField());
        metricJsonMap.put("unit", metric.getUnit());
        metricJsonMap.put("source", "semantic-publish");
        try {
            metric.setMetricJson(objectMapper.writeValueAsString(metricJsonMap));
        } catch (Exception ex) {
            metric.setMetricJson("{}");
        }

        metric = metricRepository.save(metric);

        // Also annotate the corresponding field as a measure
        if (field != null) {
            annotateFieldRole(table.getId(), field, "measure", aggregation, displayName);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricId", metric.getId());
        result.put("name", metric.getName());
        result.put("created", created);
        return result;
    }

    private Map<String, Object> processDimension(JsonNode dimNode, AnalyticsTable table) {
        String name = textOrNull(dimNode, "name");
        String displayName = textOrNull(dimNode, "displayName");
        String fieldRole = textOrNull(dimNode, "fieldRole");
        String timeGrain = textOrNull(dimNode, "timeGrain");

        if (name == null) {
            return Map.of();
        }
        if (fieldRole == null) {
            fieldRole = "dimension";
        }

        Optional<AnalyticsField> existing = fieldRepository.findByTableIdAndName(table.getId(), name);
        if (existing.isPresent()) {
            AnalyticsField af = existing.get();
            af.setFieldRole(fieldRole);
            if (displayName != null) {
                af.setDisplayName(displayName);
            }
            if (timeGrain != null && "dimension".equals(fieldRole)) {
                // Store time grain info in semantic type for time dimensions
                af.setSemanticType("type/DateTime");
            }
            fieldRepository.save(af);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fieldId", af.getId());
            result.put("name", name);
            result.put("fieldRole", fieldRole);
            result.put("updated", true);
            return result;
        }

        // Field does not exist yet in analytics - it may appear after metadata sync.
        // Log a warning but do not fail.
        LOG.warn("[semantic-publish] Field not found in analytics: table={} field={}. Will be annotated after next metadata sync.", table.getName(), name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("fieldRole", fieldRole);
        result.put("updated", false);
        result.put("note", "Field not yet synced; annotation deferred.");
        return result;
    }

    private void annotateFieldRole(Long tableId, String fieldName, String role, String aggregation, String displayName) {
        Optional<AnalyticsField> existing = fieldRepository.findByTableIdAndName(tableId, fieldName);
        if (existing.isPresent()) {
            AnalyticsField af = existing.get();
            af.setFieldRole(role);
            if (aggregation != null) {
                af.setDefaultAggregation(aggregation.toLowerCase());
            }
            if (displayName != null && (af.getDisplayName() == null || af.getDisplayName().isBlank())) {
                af.setDisplayName(displayName);
            }
            fieldRepository.save(af);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String text = node.path(field).asText(null);
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }
}
