package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import org.springframework.util.StringUtils;

public final class IngestionExecutionLineageSnapshot {

    private IngestionExecutionLineageSnapshot() {}

    public static boolean apply(IngestionExecution execution, IngestionTask task) {
        if (execution == null || task == null) {
            return false;
        }
        JsonNode tableMapping = task.getTableMapping();
        if (tableMapping == null || !tableMapping.isArray() || tableMapping.isEmpty()) {
            return false;
        }
        ArrayNode sources = JsonNodeFactory.instance.arrayNode();
        ArrayNode targets = JsonNodeFactory.instance.arrayNode();
        for (JsonNode mapping : tableMapping) {
            if (mapping == null || !mapping.isObject()) {
                continue;
            }
            String source = text(mapping.get("source"));
            String target = text(mapping.get("target"));
            ObjectNode sourceNode = tableRef(source, "source");
            if (sourceNode != null) {
                if (task.getSourceDataSourceId() != null) {
                    sourceNode.put("dataSourceId", task.getSourceDataSourceId().toString());
                }
                if (StringUtils.hasText(task.getSourceType())) {
                    sourceNode.put("sourceType", task.getSourceType());
                }
                sources.add(sourceNode);
            }
            ObjectNode targetNode = tableRef(target, "target");
            if (targetNode != null) {
                if (StringUtils.hasText(task.getDestinationType())) {
                    targetNode.put("destinationType", task.getDestinationType());
                }
                targets.add(targetNode);
            }
        }
        if (sources.isEmpty() && targets.isEmpty()) {
            return false;
        }
        execution.setSourceTables(sources);
        execution.setTargetTables(targets);
        return true;
    }

    public static boolean applyIfMissing(IngestionExecution execution, IngestionTask task) {
        if (execution == null) {
            return false;
        }
        boolean hasSources = execution.getSourceTables() != null && execution.getSourceTables().isArray() && !execution.getSourceTables().isEmpty();
        boolean hasTargets = execution.getTargetTables() != null && execution.getTargetTables().isArray() && !execution.getTargetTables().isEmpty();
        if (hasSources && hasTargets) {
            return false;
        }
        return apply(execution, task);
    }

    private static ObjectNode tableRef(String value, String role) {
        String qualified = normalize(value);
        if (!StringUtils.hasText(qualified)) {
            return null;
        }
        String namespace = null;
        String name = qualified;
        int idx = qualified.lastIndexOf('.');
        if (idx >= 0) {
            namespace = qualified.substring(0, idx);
            name = qualified.substring(idx + 1);
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("role", role);
        node.put("name", name);
        node.put("qualifiedName", qualified);
        if (StringUtils.hasText(namespace)) {
            node.put("namespace", namespace);
        }
        return node;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return normalize(node.asText());
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
