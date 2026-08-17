package com.yuzhi.dts.analytics.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnalysisQuerySpecParser {

    private static final Set<String> ROOT_FIELDS = Set.of(
        "apiVersion",
        "dataset",
        "dimensions",
        "metrics",
        "derivedMetrics",
        "filters",
        "timeRange",
        "orderBy",
        "limit",
        "visualization"
    );

    private final ObjectMapper objectMapper;

    public AnalysisQuerySpecParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisQuerySpec parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw invalid("ANALYSIS_SPEC_MALFORMED", "$", "analysis spec must be a JSON object");
            }
            ensureAllowed(root, ROOT_FIELDS, "$ ");
            ensureAllowed(root.path("dataset"), Set.of("id", "version", "contractVersion", "checksum"), "dataset");
            ensureArrayItems(root.path("dimensions"), Set.of("field", "alias"), "dimensions");
            ensureArrayItems(root.path("metrics"), Set.of("code", "alias"), "metrics");
            ensureArrayItems(root.path("derivedMetrics"), Set.of("code", "expression", "format"), "derivedMetrics");
            ensureArrayItems(root.path("filters"), Set.of("field", "op", "values", "required"), "filters");
            ensureAllowed(root.path("timeRange"), Set.of("field", "start", "end", "grain"), "timeRange");
            ensureArrayItems(root.path("orderBy"), Set.of("field", "direction"), "orderBy");
            ensureAllowed(root.path("visualization"), Set.of("type", "settings"), "visualization");
            return objectMapper.treeToValue(root, AnalysisQuerySpec.class);
        } catch (AnalysisSpecValidationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid("ANALYSIS_SPEC_MALFORMED", "$", "analysis spec cannot be parsed");
        }
    }

    public String write(AnalysisQuerySpec spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception failure) {
            throw invalid("ANALYSIS_SPEC_SERIALIZATION_FAILED", "$", "analysis spec cannot be serialized");
        }
    }

    public String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw invalid("ANALYSIS_SPEC_SERIALIZATION_FAILED", "$", "analysis value cannot be serialized");
        }
    }

    private void ensureArrayItems(JsonNode node, Set<String> allowed, String path) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (!node.isArray()) throw invalid("ANALYSIS_SPEC_MALFORMED", path, path + " must be an array");
        for (int index = 0; index < node.size(); index++) {
            ensureAllowed(node.get(index), allowed, path + "[" + index + "]");
        }
    }

    private void ensureAllowed(JsonNode node, Set<String> allowed, String path) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (!node.isObject()) throw invalid("ANALYSIS_SPEC_MALFORMED", path, path + " must be an object");
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!allowed.contains(field)) {
                throw invalid("ANALYSIS_SPEC_UNKNOWN_FIELD", path + "." + field, "unsupported field " + field);
            }
        }
    }

    private AnalysisSpecValidationException invalid(String code, String field, String message) {
        return new AnalysisSpecValidationException(code, field, message);
    }
}
