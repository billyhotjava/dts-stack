package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Projects both legacy and versioned drift JSON into one structured API contract. */
@Component
public class SchemaDriftDetailsReader {

    public record SchemaDriftDetails(int contractVersion, String impactLevel, List<Map<String, Object>> changes) {
        public SchemaDriftDetails {
            impactLevel = StringUtils.hasText(impactLevel) ? impactLevel : "REVIEW_REQUIRED";
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    private final ObjectMapper objectMapper;

    public SchemaDriftDetailsReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SchemaDriftDetails read(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return legacyReview(List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            if (root == null || !root.isObject()) {
                return legacyReview(List.of());
            }
            int version = root.path("contractVersion").canConvertToInt() ? root.path("contractVersion").asInt() : 0;
            String impact = root.path("impactLevel").isTextual()
                ? root.path("impactLevel").asText()
                : "REVIEW_REQUIRED";
            List<Map<String, Object>> changes = root.path("changes").isArray()
                ? readChanges(root.path("changes"))
                : readLegacyChanges(root);
            return new SchemaDriftDetails(version, impact, changes);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return legacyReview(List.of());
        }
    }

    private List<Map<String, Object>> readChanges(JsonNode nodes) {
        List<Map<String, Object>> changes = new ArrayList<>();
        nodes.forEach(node -> changes.add(objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {})));
        return changes;
    }

    private List<Map<String, Object>> readLegacyChanges(JsonNode root) {
        List<Map<String, Object>> changes = new ArrayList<>();
        appendLegacy(changes, root.path("added"), "FIELD_ADDED");
        appendLegacy(changes, root.path("removed"), "FIELD_REMOVED");
        appendLegacy(changes, root.path("changed"), "FIELD_CHANGED");
        return changes;
    }

    private void appendLegacy(List<Map<String, Object>> changes, JsonNode nodes, String kind) {
        if (!nodes.isArray()) {
            return;
        }
        nodes.forEach(node -> {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", node.path("name").isTextual() ? node.path("name").asText() : null);
            change.put("kind", kind);
            change.put("before", "FIELD_ADDED".equals(kind) ? null : objectMapper.convertValue(node, Object.class));
            change.put("after", "FIELD_REMOVED".equals(kind) ? null : objectMapper.convertValue(node, Object.class));
            change.put("impact", "REVIEW_REQUIRED");
            changes.add(change);
        });
    }

    private SchemaDriftDetails legacyReview(List<Map<String, Object>> changes) {
        return new SchemaDriftDetails(0, "REVIEW_REQUIRED", changes);
    }
}
