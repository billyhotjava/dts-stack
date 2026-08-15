package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Projects one statically validated dbt schema contract into canonical ModelSpec fields. */
final class DbtModelFieldProjector {

    private DbtModelFieldProjector() {}

    static List<ModelField> project(ObjectMapper objectMapper, String schema, List<ModelField> existingFields) {
        JsonNode root;
        try {
            root = objectMapper.readTree(schema);
        } catch (JsonProcessingException exception) {
            throw invalidSchema("The validated dbt schema projection is not valid JSON");
        }
        JsonNode columns = root == null ? null : root.get("columns");
        if (columns == null || !columns.isArray() || columns.isEmpty()) {
            throw invalidSchema("The validated dbt model must declare at least one contract column");
        }

        Map<String, ModelField> existing = new LinkedHashMap<>();
        for (ModelField field : existingFields == null ? List.<ModelField>of() : existingFields) {
            if (field != null && field.name() != null) existing.put(field.name(), field);
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        List<ModelField> projected = new ArrayList<>();
        for (JsonNode column : columns) {
            String name = requiredText(column, "name");
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalizedName)) {
                throw invalidSchema("The validated dbt model contains duplicate contract columns");
            }
            String dataType = requiredText(column, "dataType");
            ModelField previous = existing.get(name);
            String description = optionalText(column, "description");
            FieldRole role = role(column, previous);
            boolean notNull = hasTest(column.get("tests"), "not_null");
            Boolean nullable = notNull ? Boolean.FALSE : previous == null || previous.nullable() == null
                ? Boolean.TRUE
                : previous.nullable();
            projected.add(
                new ModelField(
                    name,
                    description == null ? previous == null || previous.displayName() == null ? name : previous.displayName() : description,
                    dataType,
                    nullable,
                    previous == null ? null : previous.sourceFieldRef(),
                    role,
                    previous == null ? null : previous.securityLevel(),
                    previous == null ? null : previous.dimensionAttributeCode(),
                    previous != null && Boolean.TRUE.equals(previous.redundant()),
                    previous == null ? null : previous.redundancySourceRef()
                )
            );
        }
        return List.copyOf(projected);
    }

    private static FieldRole role(JsonNode column, ModelField previous) {
        String value = optionalText(column, "role");
        if (value == null) return previous == null || previous.role() == null ? FieldRole.ATTRIBUTE : previous.role();
        try {
            return FieldRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidSchema("The validated dbt schema contains an unsupported field role: " + value);
        }
    }

    private static boolean hasTest(JsonNode tests, String expected) {
        if (tests == null || !tests.isArray()) return false;
        for (JsonNode test : tests) {
            if (test.isTextual() && expected.equalsIgnoreCase(test.asText().trim())) return true;
        }
        return false;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) throw invalidSchema("The validated dbt schema column is missing " + field);
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) return null;
        return value.asText().trim();
    }

    private static DbtImplementationDraftContract.DraftException invalidSchema(String message) {
        return DbtImplementationDraftContract.unprocessable("DBT_DRAFT_SCHEMA_INVALID", message);
    }
}
