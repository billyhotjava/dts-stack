package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsField;
import com.yuzhi.dts.analytics.domain.AnalyticsMetric;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticJoin;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticJoinRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.SemanticAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingest endpoint for receiving semantic model definitions published
 * from dts-platform. Creates/updates AnalyticsMetric entries, annotates
 * AnalyticsField roles and persists model/join metadata for the semantic layer.
 */
@RestController
@RequestMapping("/api/semantic")
public class SemanticPublishResource {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticPublishResource.class);

    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final AnalyticsMetricRepository metricRepository;
    private final AnalyticsSemanticModelRepository semanticModelRepository;
    private final AnalyticsSemanticJoinRepository semanticJoinRepository;
    private final ObjectMapper objectMapper;
    private final SemanticAuditService semanticAuditService;
    private final AnalyticsConsumerClassificationService classificationService;

    public SemanticPublishResource(
        AnalyticsDatabaseRepository databaseRepository,
        AnalyticsTableRepository tableRepository,
        AnalyticsFieldRepository fieldRepository,
        AnalyticsMetricRepository metricRepository,
        AnalyticsSemanticModelRepository semanticModelRepository,
        AnalyticsSemanticJoinRepository semanticJoinRepository,
        ObjectMapper objectMapper,
        SemanticAuditService semanticAuditService,
        AnalyticsConsumerClassificationService classificationService
    ) {
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.metricRepository = metricRepository;
        this.semanticModelRepository = semanticModelRepository;
        this.semanticJoinRepository = semanticJoinRepository;
        this.objectMapper = objectMapper;
        this.semanticAuditService = semanticAuditService;
        this.classificationService = classificationService;
    }

    @PostMapping(path = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ANALYTICS_SERVICE')")
    @Transactional
    public ResponseEntity<?> publish(@RequestBody JsonNode body, HttpServletRequest request) {
        if (body == null || body.isEmpty()) {
            semanticAuditService.logFailure(
                "SEMANTIC_CONTRACT_PUBLISH",
                "推送语义契约",
                null,
                request,
                null,
                null,
                "Empty request body"
            );
            return ResponseEntity.badRequest().body(Map.of("error", "Empty request body"));
        }

        String modelName = textOrNull(body, "modelName");
        String tableName = textOrNull(body, "tableName");
        String schemaName = textOrNull(body, "schemaName");
        String dataSourceName = textOrNull(body, "dataSourceName");
        String platformDataSourceId = textOrNull(body, "platformDataSourceId");
        String description = textOrNull(body, "description");

        if (tableName == null) {
            semanticAuditService.logFailure(
                "SEMANTIC_CONTRACT_PUBLISH",
                "推送语义契约",
                null,
                request,
                modelName,
                Map.of("dataSourceName", dataSourceName == null ? "" : dataSourceName),
                "tableName is required"
            );
            return ResponseEntity.badRequest().body(Map.of("error", "tableName is required"));
        }
        if (schemaName == null) {
            schemaName = "public";
        }

        LOG.info("[semantic-publish] modelName={} tableName={} schemaName={} dataSourceName={}", modelName, tableName, schemaName, dataSourceName);

        AnalyticsDatabase database = resolveDatabase(dataSourceName, platformDataSourceId);
        if (database == null) {
            semanticAuditService.logFailure(
                "SEMANTIC_CONTRACT_PUBLISH",
                "推送语义契约",
                null,
                request,
                modelName,
                Map.of(
                    "dataSourceName", dataSourceName == null ? "" : dataSourceName,
                    "platformDataSourceId", platformDataSourceId == null ? "" : platformDataSourceId,
                    "tableName", tableName
                ),
                "datasource not found"
            );
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No analytics database found for platformDataSourceId/dataSourceName: " +
                    firstNonBlank(platformDataSourceId, dataSourceName, "unspecified"),
                "hint", "Ensure the data source is registered in dts-analytics before publishing."
            ));
        }

        AnalyticsTable table = resolveOrCreateTable(database.getId(), schemaName, tableName, description);
        AnalyticsSemanticModel semanticModel = upsertSemanticModel(body, table, database.getId(), modelName, schemaName, tableName, description);

        List<Map<String, Object>> metricsCreated = new ArrayList<>();
        List<Map<String, Object>> metricsUpdated = new ArrayList<>();
        JsonNode metricsNode = body.path("metrics");
        if (metricsNode.isArray()) {
            for (JsonNode metricNode : metricsNode) {
                Map<String, Object> result = processMetric(metricNode, table, semanticModel);
                if (Boolean.TRUE.equals(result.get("created"))) {
                    metricsCreated.add(result);
                } else {
                    metricsUpdated.add(result);
                }
            }
        }

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

        List<Map<String, Object>> joinsUpdated = replaceJoins(semanticModel.getModelName(), body.path("joins"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("modelName", semanticModel.getModelName());
        summary.put("tableId", table.getId());
        summary.put("tableName", table.getName());
        summary.put("metricsCreated", metricsCreated.size());
        summary.put("metricsUpdated", metricsUpdated.size());
        summary.put("dimensionsAnnotated", dimensionsUpdated.size());
        summary.put("joinsUpdated", joinsUpdated.size());
        summary.put("metrics", concat(metricsCreated, metricsUpdated));
        summary.put("dimensions", dimensionsUpdated);
        summary.put("joins", joinsUpdated);

        LOG.info(
            "[semantic-publish] completed: metricsCreated={} metricsUpdated={} dimensionsAnnotated={} joinsUpdated={}",
            metricsCreated.size(),
            metricsUpdated.size(),
            dimensionsUpdated.size(),
            joinsUpdated.size()
        );

        Map<String, Object> auditAttrs = new LinkedHashMap<>();
        auditAttrs.put("modelName", semanticModel.getModelName());
        auditAttrs.put("tableId", table.getId());
        auditAttrs.put("metricsCreated", metricsCreated.size());
        auditAttrs.put("metricsUpdated", metricsUpdated.size());
        auditAttrs.put("dimensionsAnnotated", dimensionsUpdated.size());
        auditAttrs.put("joinsUpdated", joinsUpdated.size());
        semanticAuditService.logSuccess(
            "SEMANTIC_CONTRACT_PUBLISH",
            "推送语义契约",
            null,
            request,
            semanticModel.getModelName(),
            auditAttrs
        );

        return ResponseEntity.ok(summary);
    }

    private AnalyticsDatabase resolveDatabase(String dataSourceName, String platformDataSourceId) {
        List<AnalyticsDatabase> databases = databaseRepository.findAll();
        if (platformDataSourceId != null && !platformDataSourceId.isBlank()) {
            return databases.stream()
                .filter(database -> platformDataSourceId.equalsIgnoreCase(platformDataSourceId(database)))
                .findFirst()
                .orElse(null);
        }
        if (dataSourceName == null || dataSourceName.isBlank()) {
            return databases.stream()
                .filter(db -> !db.isSample())
                .findFirst()
                .orElse(null);
        }
        return databases.stream()
            .filter(db -> dataSourceName.equalsIgnoreCase(db.getName()))
            .findFirst()
            .or(() -> databases.stream()
                .filter(db -> db.getName() != null && db.getName().toLowerCase().contains(dataSourceName.toLowerCase()))
                .findFirst())
            .orElse(null);
    }

    private String platformDataSourceId(AnalyticsDatabase database) {
        if (database == null || database.getDetailsJson() == null || database.getDetailsJson().isBlank()) {
            return null;
        }
        try {
            JsonNode details = objectMapper.readTree(database.getDetailsJson());
            return firstNonBlank(
                textOrNull(details, "platformDataSourceId"),
                textOrNull(details, "platform_data_source_id"),
                textOrNull(details.path("platform"), "dataSourceId"),
                textOrNull(details.path("platform"), "id")
            );
        } catch (Exception invalidDetails) {
            LOG.warn("[semantic-publish] Invalid analytics database details id={}", database.getId());
            return null;
        }
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
        AnalyticsTable table = new AnalyticsTable();
        table.setDatabaseId(databaseId);
        table.setSchemaName(schemaName);
        table.setName(tableName);
        table.setDescription(description);
        table.setActive(true);
        table.setVisibilityType("normal");
        return tableRepository.save(table);
    }

    private AnalyticsSemanticModel upsertSemanticModel(
        JsonNode body,
        AnalyticsTable table,
        Long databaseId,
        String modelName,
        String schemaName,
        String tableName,
        String description
    ) {
        String resolvedModelName = firstNonBlank(modelName, tableName);
        AnalyticsSemanticModel semanticModel = semanticModelRepository.findByModelNameIgnoreCase(resolvedModelName)
            .orElseGet(AnalyticsSemanticModel::new);
        semanticModel.setDatabaseId(databaseId);
        semanticModel.setTableId(table.getId());
        semanticModel.setModelName(resolvedModelName);
        semanticModel.setSchemaName(schemaName);
        semanticModel.setTableName(tableName);
        semanticModel.setLabel(firstNonBlank(
            textOrNull(body, "label"),
            textOrNull(body, "displayName"),
            table.getDisplayName(),
            table.getName()
        ));
        semanticModel.setSubjectArea(firstNonBlank(textOrNull(body, "subjectArea"), textOrNull(body, "subject_area")));
        semanticModel.setSecurityLevel(normalizeSecurityLevel(firstNonBlank(textOrNull(body, "securityLevel"), textOrNull(body, "security_level"))));
        semanticModel.setGrain(textOrNull(body, "grain"));
        semanticModel.setRowSecurityPredicate(firstNonBlank(textOrNull(body, "rowSecurityPredicate"), textOrNull(body, "row_security_predicate")));
        semanticModel.setSpecVersion(firstNonBlank(textOrNull(body, "specVersion"), textOrNull(body, "spec_version"), "1"));
        semanticModel.setExposedToModeler(readBoolean(body, "exposedToModeler", "exposed_to_modeler", true));

        Map<String, Object> metaJson = new LinkedHashMap<>();
        putIfPresent(metaJson, "description", description);
        putIfPresent(metaJson, "description_rich", textOrNull(body, "descriptionRich"));
        if (body.path("synonyms").isArray()) {
            metaJson.put("synonyms", body.path("synonyms"));
        }
        if (body.path("sample_queries").isArray()) {
            metaJson.put("sample_queries", body.path("sample_queries"));
        }
        try {
            semanticModel.setMetaJson(objectMapper.writeValueAsString(metaJson));
        } catch (Exception ex) {
            semanticModel.setMetaJson("{}");
        }
        return semanticModelRepository.save(semanticModel);
    }

    private Map<String, Object> processMetric(JsonNode metricNode, AnalyticsTable table, AnalyticsSemanticModel semanticModel) {
        String name = textOrNull(metricNode, "name");
        String displayName = textOrNull(metricNode, "displayName");
        String aggregation = textOrNull(metricNode, "aggregation");
        String field = textOrNull(metricNode, "field");
        String unit = textOrNull(metricNode, "unit");
        String timeDimension = textOrNull(metricNode, "timeDimension");
        String timeGrain = textOrNull(metricNode, "timeGrain");
        String tags = textOrNull(metricNode, "tags");
        String securityLevel = normalizeSecurityLevel(firstNonBlank(
            textOrNull(metricNode, "securityLevel"),
            textOrNull(metricNode, "security_level"),
            semanticModel.getSecurityLevel()
        ));

        if (name == null) {
            return Map.of("error", "metric name is required");
        }

        boolean created = false;
        AnalyticsMetric metric = metricRepository.findAll().stream()
            .filter(m -> table.getId().equals(m.getBaseTableId()) && name.equals(m.getName()) && !m.isArchived())
            .findFirst()
            .orElse(null);

        if (metric == null) {
            metric = new AnalyticsMetric();
            metric.setName(name);
            metric.setCreatorId(1L);
            metric.setArchived(false);
            metric.setBaseTableId(table.getId());
            created = true;
        }

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

        Map<String, Object> metricJsonMap = new LinkedHashMap<>();
        metricJsonMap.put("name", metric.getName());
        metricJsonMap.put("display_name", metric.getDisplayName());
        metricJsonMap.put("table_id", table.getId());
        metricJsonMap.put("aggregation", metric.getAggregation());
        metricJsonMap.put("expression_field", metric.getExpressionField());
        metricJsonMap.put("unit", metric.getUnit());
        metricJsonMap.put("source", "semantic-publish");
        metricJsonMap.put("model_name", semanticModel.getModelName());
        metricJsonMap.put("security_level", securityLevel);
        putIfPresent(metricJsonMap, "description", textOrNull(metricNode, "description"));
        if (metricNode.has("format") && metricNode.get("format").isObject()) {
            try {
                metricJsonMap.put("format", objectMapper.convertValue(metricNode.get("format"), Map.class));
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            metric.setMetricJson(objectMapper.writeValueAsString(metricJsonMap));
        } catch (Exception ex) {
            metric.setMetricJson("{}");
        }

        metric = metricRepository.save(metric);
        classificationService.deriveMetric(metric);
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
            AnalyticsField af = existing.orElseThrow();
            af.setFieldRole(fieldRole);
            if (displayName != null) {
                af.setDisplayName(displayName);
            }
            if (timeGrain != null && "dimension".equals(fieldRole)) {
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

        LOG.warn("[semantic-publish] Field not found in analytics: table={} field={}. Will be annotated after next metadata sync.", table.getName(), name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("fieldRole", fieldRole);
        result.put("updated", false);
        result.put("note", "Field not yet synced; annotation deferred.");
        return result;
    }

    private List<Map<String, Object>> replaceJoins(String fromModel, JsonNode joinsNode) {
        semanticJoinRepository.deleteByFromModelIgnoreCase(fromModel);
        List<Map<String, Object>> result = new ArrayList<>();
        if (joinsNode == null || !joinsNode.isArray()) {
            return result;
        }
        for (JsonNode joinNode : joinsNode) {
            String toModel = firstNonBlank(textOrNull(joinNode, "to"), textOrNull(joinNode, "model"));
            String onClause = firstNonBlank(textOrNull(joinNode, "on"), textOrNull(joinNode, "path"));
            if (toModel == null || onClause == null) {
                continue;
            }

            AnalyticsSemanticJoin edge = new AnalyticsSemanticJoin();
            edge.setFromModel(fromModel);
            edge.setToModel(toModel);
            edge.setJoinType(firstNonBlank(textOrNull(joinNode, "type"), "many_to_one"));
            edge.setRelationship(firstNonBlank(textOrNull(joinNode, "relationship"), "left"));
            edge.setOnClause(onClause);
            edge.setFanoutWarning(readBoolean(joinNode, "fanoutWarning", "fanout_warning", false));
            edge.setApprovalRequired(readBoolean(joinNode, "approvalRequired", "approval_required", false));
            edge.setDescription(textOrNull(joinNode, "description"));
            try {
                edge.setMetaJson(objectMapper.writeValueAsString(objectMapper.convertValue(joinNode, Map.class)));
            } catch (Exception ex) {
                edge.setMetaJson("{}");
            }
            edge = semanticJoinRepository.save(edge);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", edge.getId());
            item.put("from", edge.getFromModel());
            item.put("to", edge.getToModel());
            item.put("type", edge.getJoinType());
            item.put("relationship", edge.getRelationship());
            result.add(item);
        }
        return result;
    }

    private void annotateFieldRole(Long tableId, String fieldName, String role, String aggregation, String displayName) {
        Optional<AnalyticsField> existing = fieldRepository.findByTableIdAndName(tableId, fieldName);
        if (existing.isPresent()) {
            AnalyticsField af = existing.orElseThrow();
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

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean readBoolean(JsonNode node, String camel, String snake, boolean defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (node.has(camel)) {
            return node.path(camel).asBoolean(defaultValue);
        }
        if (node.has(snake)) {
            return node.path(snake).asBoolean(defaultValue);
        }
        return defaultValue;
    }

    private static String normalizeSecurityLevel(String value) {
        if (value == null || value.isBlank()) {
            return "INTERNAL";
        }
        return switch (value.trim().toUpperCase()) {
            case "PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL" -> value.trim().toUpperCase();
            case "SENSITIVE" -> "SECRET";
            case "TOP_SECRET" -> "CONFIDENTIAL";
            default -> "INTERNAL";
        };
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }
}
